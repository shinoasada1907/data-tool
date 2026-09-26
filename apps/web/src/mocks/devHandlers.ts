import { http, HttpResponse, type RequestHandler } from 'msw'
import type { PipelineResultDto, SourceFileType } from '../api/dto'
import {
  configUpdateFixture,
  csvPreviewFixture,
  importSessionFixture,
  pipelineResultFixture,
  pipelineSummaryFixture,
  problemFixture,
  validResultFixture,
  xlsxPreviewFixture,
} from './fixtures'

// BE giả cho chế độ `pnpm dev:mock` (design D15): đi hết luồng upload → export mà không cần BE. Dữ liệu là mẫu cố định
// trong fixtures.ts, không phụ thuộc file upload hay cấu hình: FE không chạy logic dữ liệu (design D8). Không dùng cho
// test, vì test tự khai báo từng response để kiểm đúng tình huống của nó.

interface MockSession {
  id: string
  fileName: string
  fileType: SourceFileType
  sizeBytes: number
  /** Tên field của schema đã PUT, theo thứ tự: key của file export. */
  fieldNames: string[]
  /** Đã process và cấu hình chưa đổi từ đó: result và export dùng được. */
  processed: boolean
}

const SESSIONS = '/api/import-sessions'

/** Mỗi lần gọi là một BE giả mới, không có session nào. */
export function createDevHandlers(): RequestHandler[] {
  const sessions = new Map<string, MockSession>()

  function find(id: string): MockSession | Response {
    return sessions.get(id) ?? problem(404, 'SESSION_NOT_FOUND', 'Import session not found.')
  }

  function configChanged(id: string, fieldNames?: string[]) {
    const session = find(id)
    if (session instanceof Response) return session
    session.processed = false
    if (fieldNames) session.fieldNames = fieldNames
    return HttpResponse.json(configUpdateFixture())
  }

  return [
    http.post(SESSIONS, async ({ request }) => {
      const upload = await readUpload(request)
      if (!upload) return problem(400, 'REQUEST_INVALID', 'Part "file" is missing.')
      const fileType: SourceFileType = upload.name.toLowerCase().endsWith('.xlsx') ? 'XLSX' : 'CSV'
      const session: MockSession = {
        id: crypto.randomUUID(),
        fileName: upload.name,
        fileType,
        sizeBytes: upload.size,
        fieldNames: [],
        processed: false,
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
      if (session instanceof Response) return session
      const preview = session.fileType === 'XLSX' ? xlsxPreviewFixture() : csvPreviewFixture()
      return HttpResponse.json({ ...preview, sessionId: session.id })
    }),

    http.put<{ id: string }>(`${SESSIONS}/:id/schema`, async ({ params, request }) => {
      const body = (await request.json()) as { fields?: { name: string }[] }
      return configChanged(params.id, (body.fields ?? []).map((field) => field.name))
    }),
    http.put<{ id: string }>(`${SESSIONS}/:id/mapping`, ({ params }) => configChanged(params.id)),
    http.put<{ id: string }>(`${SESSIONS}/:id/transformations`, ({ params }) => configChanged(params.id)),
    http.put<{ id: string }>(`${SESSIONS}/:id/validations`, ({ params }) => configChanged(params.id)),

    http.post<{ id: string }>(`${SESSIONS}/:id/process`, ({ params }) => {
      const session = find(params.id)
      if (session instanceof Response) return session
      session.processed = true
      return HttpResponse.json(summaryOf(session.id))
    }),

    http.get<{ id: string }>(`${SESSIONS}/:id/result`, ({ params, request }) => {
      const session = find(params.id)
      if (session instanceof Response) return session
      if (!session.processed) return problem(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.')
      const query = new URL(request.url).searchParams
      const page = Number(query.get('page') ?? 0)
      const result = resultPage(query.get('view') === 'invalid', page, query.get('field'), query.get('code'))
      return HttpResponse.json({ ...result, summary: summaryOf(session.id) })
    }),

    http.get<{ id: string }>(`${SESSIONS}/:id/export`, ({ params, request }) => {
      const session = find(params.id)
      if (session instanceof Response) return session
      if (!session.processed) return problem(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.')
      const rows = validResultFixture().rows.map((row) => session.fieldNames.map((name) => row.values[name] ?? null))
      if (new URL(request.url).searchParams.get('format') === 'json') {
        // Ghép chuỗi thay vì dựng object: object của JS đưa key dạng số ("1", "2024") lên đầu, lệch thứ tự schema.
        const objects = rows.map(
          (values) => `{${session.fieldNames.map((name, i) => `${JSON.stringify(name)}:${JSON.stringify(values[i])}`).join(',')}}`,
        )
        return file(`[${objects.join(',')}]`, 'application/json', exportName(session.fileName, '-valid.json'))
      }
      const lines = [session.fieldNames, ...rows.map((values) => values.map((value) => (value === null ? '' : String(value))))]
      return file(`﻿${toCsv(lines)}`, 'text/csv;charset=UTF-8', exportName(session.fileName, '-valid.csv'))
    }),

    http.get<{ id: string }>(`${SESSIONS}/:id/errors/export`, ({ params }) => {
      const session = find(params.id)
      if (session instanceof Response) return session
      if (!session.processed) return problem(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.')
      const header = ['rowNumber', 'fieldName', 'stage', 'rule', 'step', 'code', 'message', 'sourceValue']
      const lines = pipelineResultFixture().rows.flatMap((row) =>
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

/** Summary khớp với các dòng mẫu: 2 dòng hợp lệ, 1 dòng lỗi mang 2 lỗi. */
function summaryOf(sessionId: string) {
  const invalidRows = pipelineResultFixture().rows
  const errors = invalidRows.flatMap((row) => row.errors)
  const countBy = (key: (error: (typeof errors)[number]) => string) =>
    errors.reduce<Record<string, number>>((counts, error) => ({ ...counts, [key(error)]: (counts[key(error)] ?? 0) + 1 }), {})
  const valid = validResultFixture().rows.length
  return pipelineSummaryFixture({
    sessionId,
    total: valid + invalidRows.length,
    valid,
    invalid: invalidRows.length,
    errorCountsByCode: countBy((error) => error.code),
    errorCountsByField: countBy((error) => error.fieldName),
  })
}

function toSessionDto(session: MockSession) {
  return importSessionFixture({
    id: session.id,
    status: session.processed ? 'PROCESSED' : 'CONFIGURING',
    originalFileName: session.fileName,
    fileType: session.fileType,
    sizeBytes: session.sizeBytes,
  })
}

/** Trang kết quả từ mẫu cố định; bộ lọc giữ các dòng có ít nhất một lỗi khớp cả field lẫn mã (như BE-F09). */
function resultPage(invalid: boolean, page: number, field: string | null, code: string | null): PipelineResultDto {
  const base = invalid ? pipelineResultFixture() : validResultFixture()
  const rows = base.rows.filter(
    (row) =>
      !invalid ||
      row.errors.some((error) => (!field || error.fieldName === field) && (!code || error.code === code)),
  )
  const size = base.page.size
  return {
    ...base,
    page: { number: page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) },
    rows: rows.slice(page * size, (page + 1) * size),
  }
}

function problem(status: number, code: string, detail: string): Response {
  return HttpResponse.json(problemFixture(status, code, detail), {
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
