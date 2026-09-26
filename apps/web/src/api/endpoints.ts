import { request } from './client'
import { RESULT_PAGE_SIZE, type ResultQuery } from '../domain/types'
import type {
  MappingConfigDto,
  PipelineResultDto,
  PipelineSummaryDto,
  SourcePreviewDto,
  TargetSchemaDto,
  TransformationConfigDto,
  ValidationConfigDto,
} from './dto'

// Các endpoint FE dùng (design → API contract V0.1). Upload nằm riêng ở upload.ts vì cần XHR.

const SESSIONS = '/api/import-sessions'

/** Số dòng xem trước; BE mặc định cũng là 50. */
export const PREVIEW_LIMIT = 50

export function getPreview(sessionId: string, { signal }: { signal?: AbortSignal } = {}): Promise<SourcePreviewDto> {
  return request(`${SESSIONS}/${encodeURIComponent(sessionId)}/preview?limit=${PREVIEW_LIMIT}`, {
    validate: isSourcePreviewDto,
    signal,
  })
}

/** Ghi đè toàn bộ schema. Body `{ session, warnings }` của BE được bỏ qua (design D6). */
export async function putSchema(sessionId: string, schema: TargetSchemaDto): Promise<void> {
  await request(`${SESSIONS}/${encodeURIComponent(sessionId)}/schema`, {
    method: 'PUT',
    body: schema,
    validate: isConfigUpdateResponse,
  })
}

/** Ghi đè toàn bộ mapping (chỉ gồm field đã map). Body `{ session, warnings }` được bỏ qua (design D6). */
export async function putMapping(sessionId: string, mapping: MappingConfigDto): Promise<void> {
  await request(`${SESSIONS}/${encodeURIComponent(sessionId)}/mapping`, {
    method: 'PUT',
    body: mapping,
    validate: isConfigUpdateResponse,
  })
}

/** Ghi đè toàn bộ transformations (gửi trong trình tự "Chạy xử lý", design D4). Body 200 được bỏ qua. */
export async function putTransformations(sessionId: string, body: TransformationConfigDto): Promise<void> {
  await request(`${SESSIONS}/${encodeURIComponent(sessionId)}/transformations`, {
    method: 'PUT',
    body,
    validate: isConfigUpdateResponse,
  })
}

/** Ghi đè toàn bộ validations do user bật (gửi trong trình tự "Chạy xử lý", design D4). Body 200 được bỏ qua. */
export async function putValidations(sessionId: string, body: ValidationConfigDto): Promise<void> {
  await request(`${SESSIONS}/${encodeURIComponent(sessionId)}/validations`, {
    method: 'PUT',
    body,
    validate: isConfigUpdateResponse,
  })
}

/**
 * Chạy pipeline trên toàn bộ file. BE xử lý đồng bộ nên file lớn có thể mất vài phút; timeout nới lên
 * `PROCESS_TIMEOUT_MS` thay vì 30 giây mặc định (design D6).
 */
export const PROCESS_TIMEOUT_MS = 300_000

export function postProcess(sessionId: string): Promise<PipelineSummaryDto> {
  return request(`${SESSIONS}/${encodeURIComponent(sessionId)}/process`, {
    method: 'POST',
    validate: isPipelineSummaryDto,
    timeoutMs: PROCESS_TIMEOUT_MS,
  })
}

/** Một trang kết quả của lần chạy gần nhất; `field`/`code` chỉ gửi khi có (spec result-review). */
export function getResult(sessionId: string, query: ResultQuery): Promise<PipelineResultDto> {
  const params = new URLSearchParams({ view: query.view, page: String(query.page), size: String(RESULT_PAGE_SIZE) })
  if (query.field !== null) params.set('field', query.field)
  if (query.code !== null) params.set('code', query.code)
  return request(`${SESSIONS}/${encodeURIComponent(sessionId)}/result?${params}`, { validate: isPipelineResultDto })
}

/** Chỉ kiểm đó là body của PUT cấu hình (không phải trang HTML từ proxy); nội dung không dùng tới. */
function isConfigUpdateResponse(value: unknown): value is { session: unknown } {
  return isRecord(value) && isRecord(value.session)
}

/** Kiểm những gì bảng preview đọc tới; lệch contract thì báo lỗi thay vì vỡ lúc render. */
function isSourcePreviewDto(value: unknown): value is SourcePreviewDto {
  if (!isRecord(value)) return false
  return (
    typeof value.sessionId === 'string' &&
    (value.sheetName === null || typeof value.sheetName === 'string') &&
    typeof value.totalRows === 'number' &&
    Array.isArray(value.columns) &&
    value.columns.every((column) => isRecord(column) && typeof column.name === 'string') &&
    Array.isArray(value.rows) &&
    value.rows.every(
      (row) =>
        isRecord(row) &&
        typeof row.rowNumber === 'number' &&
        Array.isArray(row.values) &&
        row.values.every((cell) => cell === null || typeof cell === 'string'),
    )
  )
}

function isPipelineSummaryDto(value: unknown): value is PipelineSummaryDto {
  return (
    isRecord(value) &&
    typeof value.total === 'number' &&
    typeof value.valid === 'number' &&
    typeof value.invalid === 'number' &&
    isCountMap(value.errorCountsByCode) &&
    isCountMap(value.errorCountsByField) &&
    typeof value.processedAt === 'string'
  )
}

function isPipelineResultDto(value: unknown): value is PipelineResultDto {
  if (!isRecord(value) || !isPipelineSummaryDto(value.summary) || !isRecord(value.page)) return false
  const page = value.page
  return (
    typeof page.number === 'number' &&
    typeof page.totalElements === 'number' &&
    typeof page.totalPages === 'number' &&
    Array.isArray(value.rows) &&
    value.rows.every(
      (row) =>
        isRecord(row) &&
        typeof row.rowNumber === 'number' &&
        typeof row.valid === 'boolean' &&
        isRecord(row.values) &&
        Array.isArray(row.errors) &&
        row.errors.every(isImportErrorDto),
    )
  )
}

function isImportErrorDto(value: unknown): boolean {
  return (
    isRecord(value) &&
    typeof value.fieldName === 'string' &&
    (value.stage === 'TRANSFORMATION' || value.stage === 'VALIDATION') &&
    typeof value.rule === 'string' &&
    (value.step === null || typeof value.step === 'number') &&
    typeof value.code === 'string' &&
    typeof value.message === 'string' &&
    (value.sourceValue === null || typeof value.sourceValue === 'string')
  )
}

function isCountMap(value: unknown): value is Record<string, number> {
  return isRecord(value) && Object.values(value).every((count) => typeof count === 'number')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
