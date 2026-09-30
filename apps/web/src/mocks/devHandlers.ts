import { http, HttpResponse, type RequestHandler } from 'msw'
import type {
  ImportErrorDto,
  MappingConfigDto,
  PipelineResultDto,
  PipelineSummaryDto,
  SourceFileType,
  SourcePreviewDto,
  TargetSchemaDto,
} from '../api/dto'
import { configUpdateFixture, csvPreviewFixture, importSessionFixture, problemFixture, xlsxPreviewFixture } from './fixtures'

// BE giả cho chế độ `pnpm dev:mock` (design D15): đi hết luồng upload → export mà không cần BE.
// - Dữ liệu nguồn là bảng mẫu cố định theo loại file (CSV hoặc XLSX, trong fixtures.ts), không đọc file upload.
// - Kết quả dựng từ bảng mẫu theo schema và mapping đã PUT, nên đổi tên field hay đổi mapping thì thấy ngay. Không chạy
//   biến đổi hay kiểm tra (design D8): dòng mẫu thứ hai luôn lỗi ở field đầu tiên, các dòng khác hợp lệ.
// - Như BE: PUT làm cấu hình đổi thì xoá kết quả (result và export trả 409); PUT giống hệt bản đã có thì giữ kết quả.
// Không dùng cho test của từng bước: test tự khai báo từng response để kiểm đúng tình huống của nó.

type ConfigSection = 'schema' | 'mapping' | 'transformations' | 'validations'

interface MockSession {
  id: string
  fileName: string
  fileType: SourceFileType
  sizeBytes: number
  /** Body đã PUT của từng phần cấu hình, để nhận ra PUT không đổi gì (BE so `configHash`). */
  config: Partial<Record<ConfigSection, string>>
  /** Kết quả của lần process gần nhất; null khi chưa chạy hoặc cấu hình đã đổi. */
  result: MockRow[] | null
}

interface MockRow {
  rowNumber: number
  valid: boolean
  /** Theo thứ tự schema (mảng song song với `fieldNames`), để export giữ đúng thứ tự dù tên field là số. */
  values: (string | null)[]
  errors: ImportErrorDto[]
}

const SESSIONS = '/api/import-sessions'
const SECTIONS: readonly ConfigSection[] = ['schema', 'mapping', 'transformations', 'validations']

/** Mỗi lần gọi là một BE giả mới, không có session nào. */
export function createDevHandlers(): RequestHandler[] {
  const sessions = new Map<string, MockSession>()

  function find(id: string): MockSession | Response {
    return sessions.get(id) ?? problem(404, 'SESSION_NOT_FOUND', 'Import session not found.')
  }

  return [
    http.post(SESSIONS, async ({ request }) => {
      const upload = await readUpload(request)
      if (!upload) return problem(400, 'REQUEST_INVALID', 'Part "file" is missing.')
      const session: MockSession = {
        id: crypto.randomUUID(),
        fileName: upload.name,
        fileType: upload.name.toLowerCase().endsWith('.xlsx') ? 'XLSX' : 'CSV',
        sizeBytes: upload.size,
        config: {},
        result: null,
      }
      sessions.set(session.id, session)
      return HttpResponse.json(toSessionDto(session), { status: 201 })
    }),

    http.get<{ id: string }>(`${SESSIONS}/:id`, ({ params }) => {
      const session = find(params.id)
      return session instanceof Response ? session : HttpResponse.json(toSessionDto(session))
    }),

    http.get<{ id: string }>(`${SESSIONS}/:id/preview`, ({ params }) => {
      const session = find(params.id)
      return session instanceof Response ? session : HttpResponse.json(samplePreview(session))
    }),

    ...SECTIONS.map((section) =>
      http.put<{ id: string }>(`${SESSIONS}/:id/${section}`, async ({ params, request }) => {
        const session = find(params.id)
        if (session instanceof Response) return session
        const body = await request.text()
        if (session.config[section] !== body) {
          session.config[section] = body
          session.result = null
        }
        return HttpResponse.json(configUpdateFixture())
      }),
    ),

    http.post<{ id: string }>(`${SESSIONS}/:id/process`, ({ params }) => {
      const session = find(params.id)
      if (session instanceof Response) return session
      if (fieldNames(session).length === 0) {
        return problem(409, 'SESSION_NOT_READY', 'Session is not ready.', [
          { field: null, code: 'SCHEMA_EMPTY', message: 'Target schema has no fields.' },
        ])
      }
      session.result = runPipeline(session)
      return HttpResponse.json(summaryOf(session, session.result))
    }),

    http.get<{ id: string }>(`${SESSIONS}/:id/result`, ({ params, request }) => {
      const session = find(params.id)
      if (session instanceof Response) return session
      if (!session.result) return problem(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.')
      return HttpResponse.json(resultPage(session, session.result, new URL(request.url).searchParams))
    }),

    http.get<{ id: string }>(`${SESSIONS}/:id/export`, ({ params, request }) => {
      const session = find(params.id)
      if (session instanceof Response) return session
      if (!session.result) return problem(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.')
      const names = fieldNames(session)
      const rows = session.result.filter((row) => row.valid)
      if (new URL(request.url).searchParams.get('format') === 'json') {
        // Ghép chuỗi thay vì dựng object: object của JS đưa key dạng số ("1", "2024") lên đầu, lệch thứ tự schema.
        const objects = rows.map(
          (row) => `{${names.map((name, i) => `${JSON.stringify(name)}:${JSON.stringify(row.values[i])}`).join(',')}}`,
        )
        return file(`[${objects.join(',')}]`, 'application/json', exportName(session.fileName, '-valid.json'))
      }
      const lines = [names, ...rows.map((row) => row.values.map((value) => value ?? ''))]
      return file(`﻿${toCsv(lines)}`, 'text/csv;charset=UTF-8', exportName(session.fileName, '-valid.csv'))
    }),

    http.get<{ id: string }>(`${SESSIONS}/:id/errors/export`, ({ params }) => {
      const session = find(params.id)
      if (session instanceof Response) return session
      if (!session.result) return problem(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.')
      const header = ['rowNumber', 'fieldName', 'stage', 'rule', 'step', 'code', 'message', 'sourceValue']
      const lines = session.result.flatMap((row) =>
        row.errors.map((error) => [
          String(error.rowNumber),
          error.fieldName,
          error.stage,
          error.rule,
          error.step === null ? '' : String(error.step),
          error.code,
          error.message,
          error.sourceValue ?? '',
        ]),
      )
      return file(`﻿${toCsv([header, ...lines])}`, 'text/csv;charset=UTF-8', exportName(session.fileName, '-errors.csv'))
    }),
  ]
}

/** Bảng mẫu theo loại file; `totalRows` là đúng số dòng mẫu, để "Xem trước x / y dòng" khớp với thẻ Tổng. */
function samplePreview(session: MockSession): SourcePreviewDto {
  const preview = session.fileType === 'XLSX' ? xlsxPreviewFixture() : csvPreviewFixture()
  return { ...preview, sessionId: session.id, totalRows: preview.rows.length }
}

function fieldNames(session: MockSession): string[] {
  const schema = parse<TargetSchemaDto>(session.config.schema)
  return (schema?.fields ?? []).map((field) => field.name)
}

/**
 * Giá trị từng field theo mapping đã PUT: cột nguồn, hằng số, hoặc null nếu chưa map. Dòng mẫu thứ hai lỗi ở field đầu
 * tiên, để màn Kết quả luôn có cả dòng hợp lệ lẫn dòng lỗi.
 */
function runPipeline(session: MockSession): MockRow[] {
  const preview = samplePreview(session)
  const names = fieldNames(session)
  const mappings = parse<MappingConfigDto>(session.config.mapping)?.mappings ?? []
  const valueOf = (name: string, source: (string | null)[]): string | null => {
    const mapping = mappings.find((candidate) => candidate.targetField === name)
    if (!mapping) return null
    if (mapping.mappingType === 'CONSTANT') return mapping.constantValue
    const column = preview.columns.findIndex((candidate) => candidate.name === mapping.sourceColumn)
    return column === -1 ? null : (source[column] ?? null)
  }

  return preview.rows.map((row, index) => {
    const values = names.map((name) => valueOf(name, row.values))
    if (index !== 1) return { rowNumber: row.rowNumber, valid: true, values, errors: [] }
    const error: ImportErrorDto = {
      rowNumber: row.rowNumber,
      fieldName: names[0],
      stage: 'VALIDATION',
      rule: 'type',
      step: null,
      code: 'VALIDATION_TYPE',
      message: 'Mock: the second sample row always fails on the first field.',
      sourceValue: values[0],
    }
    return { rowNumber: row.rowNumber, valid: false, values, errors: [error] }
  })
}

function summaryOf(session: MockSession, rows: MockRow[]): PipelineSummaryDto {
  const errors = rows.flatMap((row) => row.errors)
  const countBy = (key: (error: ImportErrorDto) => string) =>
    errors.reduce<Record<string, number>>((counts, error) => ({ ...counts, [key(error)]: (counts[key(error)] ?? 0) + 1 }), {})
  const valid = rows.filter((row) => row.valid).length
  return {
    sessionId: session.id,
    status: 'PROCESSED',
    total: rows.length,
    valid,
    invalid: rows.length - valid,
    errorCountsByCode: countBy((error) => error.code),
    errorCountsByField: countBy((error) => error.fieldName),
    processedAt: new Date().toISOString(),
  }
}

/** Một trang kết quả; bộ lọc chỉ áp cho tab Lỗi, giữ dòng có ít nhất một lỗi khớp cả field lẫn mã (như BE-F09). */
function resultPage(session: MockSession, rows: MockRow[], query: URLSearchParams): PipelineResultDto {
  const invalid = query.get('view') === 'invalid'
  const field = query.get('field')
  const code = query.get('code')
  const page = Number(query.get('page') ?? 0)
  const size = Number(query.get('size') ?? 50)
  const names = fieldNames(session)
  const matching = rows.filter(
    (row) =>
      row.valid !== invalid &&
      (!invalid || row.errors.some((error) => (!field || error.fieldName === field) && (!code || error.code === code))),
  )
  return {
    summary: summaryOf(session, rows),
    view: invalid ? 'invalid' : 'valid',
    page: { number: page, size, totalElements: matching.length, totalPages: Math.ceil(matching.length / size) },
    rows: matching.slice(page * size, (page + 1) * size).map((row) => ({
      rowNumber: row.rowNumber,
      valid: row.valid,
      values: Object.fromEntries(names.map((name, i) => [name, row.values[i]])),
      errors: row.errors,
    })),
  }
}

/**
 * Tên và dung lượng của part `file`. Trình duyệt đọc được bằng `formData()`; Node (undici, khi chạy test trong jsdom)
 * không đọc được multipart do XHR của jsdom tạo, nên dự phòng bằng cách đọc thẳng header của part.
 */
async function readUpload(request: Request): Promise<{ name: string; size: number } | null> {
  const copy = request.clone()
  try {
    const file = (await request.formData()).get('file')
    return file instanceof File ? { name: file.name, size: file.size } : null
  } catch {
    const body = await copy.text()
    const name = /name="file"; filename="([^"]*)"/.exec(body)?.[1]
    return name === undefined ? null : { name, size: body.length }
  }
}

function toSessionDto(session: MockSession) {
  return importSessionFixture({
    id: session.id,
    status: session.result ? 'PROCESSED' : 'CONFIGURING',
    originalFileName: session.fileName,
    fileType: session.fileType,
    sizeBytes: session.sizeBytes,
  })
}

function parse<T>(json: string | undefined): T | null {
  if (json === undefined) return null
  try {
    return JSON.parse(json) as T
  } catch {
    return null
  }
}

function problem(
  status: number,
  code: string,
  detail: string,
  errors?: { field: string | null; code: string; message: string }[],
): Response {
  return HttpResponse.json(problemFixture(status, code, detail, errors ? { errors } : {}), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function file(body: string, contentType: string, fileName: string): Response {
  return new HttpResponse(body, {
    headers: {
      'Content-Type': contentType,
      'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(fileName)}`,
    },
  })
}

/** Cùng luật đặt tên với BE (be-f10 F10-D6), bản rút gọn. */
function exportName(originalFileName: string, suffix: string): string {
  const dot = originalFileName.lastIndexOf('.')
  return `${(dot === -1 ? originalFileName : originalFileName.slice(0, dot)) || 'export'}${suffix}`
}

/** RFC 4180: CRLF, chỉ bọc ngoặc kép khi cần. */
function toCsv(lines: string[][]): string {
  const cell = (value: string) => (/[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value)
  return lines.map((line) => line.map(cell).join(',')).join('\r\n') + '\r\n'
}
