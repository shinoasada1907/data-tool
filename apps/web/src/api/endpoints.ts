import { request } from './client'
import type { SourcePreviewDto, TargetSchemaDto } from './dto'

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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
