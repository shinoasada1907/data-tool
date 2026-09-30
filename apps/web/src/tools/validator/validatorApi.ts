import { deleteQuietly, request } from '../../api/client'
import { download, type Download } from '../../api/download'
import type { SourceDto } from '../../shared/dataset/datasetApi'
import type { OutputFormat } from '../../shared/output/formats'
import type { FieldType } from '../../domain/types'

// Contract của Validator: openspec/changes/archive/2026-09-29-tool-05-data-validator/design.md (gốc repo).

export const VALIDATOR_RUNS_URL = '/api/validator/runs'

/** BE chạy đồng bộ trên toàn file; file 20MB có thể mất cỡ phút, nên nới timeout như process của Importer. */
export const RUN_TIMEOUT_MS = 300_000

export const RESULT_PAGE_SIZE = 50

export interface SchemaConstraintsDto {
  unique?: boolean
  min?: number
  max?: number
  minLength?: number
  maxLength?: number
  pattern?: string
  format?: string
  defaultValue?: string
}

export interface SchemaFieldDto {
  name: string
  type: FieldType
  required: boolean
  constraints?: SchemaConstraintsDto
}

export interface SchemaDto {
  name: string
  fields: SchemaFieldDto[]
}

export interface ValidatorRunDto {
  id: string
  expiresAt: string
  schemaName: string
  /** Tên field theo thứ tự schema: thứ tự cột của bảng kết quả và của `rows[].values`. */
  fields: string[]
  compatibility: { matched: { field: string; column: string }[]; missingOptional: string[]; extraColumns: string[] }
  summary: {
    totalRows: number
    validRows: number
    invalidRows: number
    errorCount: number
    errorCountsByCode: Readonly<Record<string, number>>
    /** Chỉ field có lỗi. Duyệt theo `fields`, không theo key (JS đưa key dạng số lên đầu). */
    errorCountsByField: Readonly<Record<string, number>>
  }
}

export type RowsView = 'VALID' | 'INVALID'

export interface RowError {
  field: string
  code: string
  rule: string
  message: string
  /** Giá trị ô gốc. */
  value: string | null
}

export interface ValidatorRow {
  rowNumber: number
  values: (string | null)[]
  errors: RowError[]
}

export interface ValidatorRowsDto {
  view: RowsView
  page: { number: number; size: number; totalElements: number; totalPages: number }
  rows: ValidatorRow[]
}

/** Trang đang xem: tab, số trang (từ 0) và bộ lọc (chỉ có nghĩa ở tab INVALID). */
export interface RowsQuery {
  view: RowsView
  page: number
  field: string | null
  code: string | null
}

export type ExportContent = 'VALID' | 'INVALID' | 'ERRORS'

export function createRun(source: SourceDto, schema: SchemaDto): Promise<ValidatorRunDto> {
  return request(VALIDATOR_RUNS_URL, {
    method: 'POST',
    body: { source, schema },
    validate: isValidatorRunDto,
    timeoutMs: RUN_TIMEOUT_MS,
  })
}

export function getRows(runId: string, query: RowsQuery, { signal }: { signal?: AbortSignal } = {}) {
  const params = new URLSearchParams({ view: query.view, page: String(query.page), size: String(RESULT_PAGE_SIZE) })
  if (query.field !== null) params.set('field', query.field)
  if (query.code !== null) params.set('code', query.code)
  return request(`${runUrl(runId)}/rows?${params}`, { validate: isValidatorRowsDto, signal })
}

export function exportRun(
  runId: string,
  content: ExportContent,
  format: OutputFormat,
  { signal }: { signal?: AbortSignal } = {},
): Promise<Download> {
  return download(`${runUrl(runId)}/export`, { method: 'POST', body: { content, output: { format } }, signal })
}

/** Run cũ không còn dùng (đã có run mới): dọn trên máy chủ, không chờ (design V8). */
export function deleteRun(runId: string): void {
  deleteQuietly(runUrl(runId))
}

function runUrl(runId: string): string {
  return `${VALIDATOR_RUNS_URL}/${encodeURIComponent(runId)}`
}

function isValidatorRunDto(value: unknown): value is ValidatorRunDto {
  if (!isRecord(value) || !isRecord(value.summary) || !isRecord(value.compatibility)) return false
  const { summary, compatibility } = value
  return (
    typeof value.id === 'string' &&
    Array.isArray(value.fields) &&
    value.fields.every((field) => typeof field === 'string') &&
    Array.isArray(compatibility.matched) &&
    Array.isArray(compatibility.missingOptional) &&
    Array.isArray(compatibility.extraColumns) &&
    typeof summary.totalRows === 'number' &&
    typeof summary.validRows === 'number' &&
    typeof summary.invalidRows === 'number' &&
    typeof summary.errorCount === 'number' &&
    isCountMap(summary.errorCountsByCode) &&
    isCountMap(summary.errorCountsByField)
  )
}

function isValidatorRowsDto(value: unknown): value is ValidatorRowsDto {
  if (!isRecord(value) || !isRecord(value.page)) return false
  return (
    (value.view === 'VALID' || value.view === 'INVALID') &&
    typeof value.page.number === 'number' &&
    typeof value.page.totalPages === 'number' &&
    typeof value.page.totalElements === 'number' &&
    Array.isArray(value.rows) &&
    value.rows.every(
      (row) =>
        isRecord(row) &&
        typeof row.rowNumber === 'number' &&
        Array.isArray(row.values) &&
        row.values.every((cell) => cell === null || typeof cell === 'string') &&
        Array.isArray(row.errors) &&
        row.errors.every(
          (error) =>
            isRecord(error) &&
            typeof error.field === 'string' &&
            typeof error.code === 'string' &&
            typeof error.message === 'string',
        ),
    )
  )
}

function isCountMap(value: unknown): value is Record<string, number> {
  return isRecord(value) && Object.values(value).every((count) => typeof count === 'number')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
