import { deleteQuietly, request } from '../../api/client'
import { uploadFile, type UploadOptions } from '../../api/upload'

// Dataset dùng chung của toolbox (contract Toolbox v1, mục "Dataset"): upload một lần, đọc lại theo tuỳ chọn.

export const DATASETS_URL = '/api/datasets'

export type DataFormat = 'CSV' | 'XLSX' | 'JSON'
export type Delimiter = 'COMMA' | 'SEMICOLON' | 'TAB' | 'PIPE'
export type Encoding = 'UTF-8' | 'UTF-16' | 'WINDOWS-1258' | 'WINDOWS-1252'

export const DELIMITERS: readonly Delimiter[] = ['COMMA', 'SEMICOLON', 'TAB', 'PIPE']
export const ENCODINGS: readonly Encoding[] = ['UTF-8', 'UTF-16', 'WINDOWS-1258', 'WINDOWS-1252']

/** Đuôi file mà toolbox nhận. */
export const DATASET_TYPES: Readonly<Record<string, DataFormat>> = { csv: 'CSV', xlsx: 'XLSX', json: 'JSON' }

/** Lựa chọn của user; khoá vắng mặt = để BE tự nhận hoặc dùng mặc định. */
export interface ReadOptions {
  sheet?: string
  delimiter?: Delimiter
  encoding?: Encoding
  hasHeader?: boolean
}

/** Giá trị BE thực dùng khi đọc (không áp dụng với định dạng đó thì null/vắng). */
export interface ResolvedOptions {
  sheet?: string | null
  delimiter?: Delimiter | null
  encoding?: Encoding | null
  hasHeader: boolean
}

export interface SheetInfo {
  name: string
  visible: boolean
}

export interface DatasetDto {
  id: string
  originalFileName: string
  format: DataFormat
  sizeBytes: number
  sheets: SheetInfo[] | null
  expiresAt: string
}

export type InferredType = 'string' | 'number' | 'boolean' | 'date' | 'email' | 'empty'

export interface DatasetColumn {
  index: number
  name: string
  inferredType: InferredType
  emptyCount: number
}

export interface DatasetPreviewDto {
  datasetId: string
  format: DataFormat
  options: ResolvedOptions
  autoDetected: string[]
  sheetName: string | null
  columns: DatasetColumn[]
  totalRows: number
  blankRowsSkipped: number
  rows: { rowNumber: number; values: (string | null)[] }[]
}

/** `source` trong body của mọi công cụ. */
export interface SourceDto {
  datasetId: string
  options: ReadOptions
}

export const PREVIEW_LIMIT = 50

export function uploadDataset(file: File, options: UploadOptions = {}): Promise<DatasetDto> {
  return uploadFile(DATASETS_URL, file, parseDataset, options)
}

export function getDatasetPreview(
  datasetId: string,
  options: ReadOptions,
  { signal }: { signal?: AbortSignal } = {},
): Promise<DatasetPreviewDto> {
  const params = new URLSearchParams({ limit: String(PREVIEW_LIMIT) })
  for (const [key, value] of Object.entries(options)) {
    if (value !== undefined) params.set(key, String(value))
  }
  return request(`${DATASETS_URL}/${encodeURIComponent(datasetId)}/preview?${params}`, {
    validate: isDatasetPreviewDto,
    signal,
  })
}

/** Dọn dataset không còn dùng (đổi hoặc xoá file); không chờ, lỗi thì bỏ qua (design V3). */
export function deleteDataset(datasetId: string): void {
  deleteQuietly(`${DATASETS_URL}/${encodeURIComponent(datasetId)}`)
}

/**
 * Nguồn gửi cho công cụ: đúng cách đọc mà preview đã dùng (design V3), bỏ các khoá không áp dụng, để lượt chạy đọc
 * file y như bảng user vừa xem.
 */
export function sourceOf(preview: DatasetPreviewDto): SourceDto {
  const { sheet, delimiter, encoding, hasHeader } = preview.options
  const options: ReadOptions = {}
  if (sheet) options.sheet = sheet
  if (delimiter) options.delimiter = delimiter
  if (encoding) options.encoding = encoding
  if (preview.format !== 'JSON') options.hasHeader = hasHeader
  return { datasetId: preview.datasetId, options }
}

function parseDataset(body: string): DatasetDto | null {
  let value: unknown
  try {
    value = JSON.parse(body)
  } catch {
    return null
  }
  return isDatasetDto(value) ? value : null
}

function isDatasetDto(value: unknown): value is DatasetDto {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.originalFileName === 'string' &&
    isFormat(value.format) &&
    typeof value.sizeBytes === 'number' &&
    (value.sheets === null ||
      value.sheets === undefined ||
      (Array.isArray(value.sheets) && value.sheets.every((sheet) => isRecord(sheet) && typeof sheet.name === 'string')))
  )
}

/** Kiểm những gì FE đọc tới; lệch contract thì báo lỗi thay vì vỡ lúc render. */
function isDatasetPreviewDto(value: unknown): value is DatasetPreviewDto {
  if (!isRecord(value) || !isRecord(value.options)) return false
  return (
    typeof value.datasetId === 'string' &&
    isFormat(value.format) &&
    typeof value.options.hasHeader === 'boolean' &&
    Array.isArray(value.autoDetected) &&
    typeof value.totalRows === 'number' &&
    typeof value.blankRowsSkipped === 'number' &&
    Array.isArray(value.columns) &&
    value.columns.every(
      (column) => isRecord(column) && typeof column.name === 'string' && typeof column.inferredType === 'string',
    ) &&
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

function isFormat(value: unknown): value is DataFormat {
  return value === 'CSV' || value === 'XLSX' || value === 'JSON'
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
