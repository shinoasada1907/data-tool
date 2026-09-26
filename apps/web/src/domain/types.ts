// Model nội bộ của UI; tách khỏi DTO của BE (design D5).

export type SourceFileType = 'CSV' | 'XLSX'

export interface SessionInfo {
  id: string
  fileName: string
  fileType: SourceFileType
  sizeBytes: number
}

export interface PreviewRow {
  /** Số dòng như khi mở file bằng Excel: header là dòng 1. */
  rowNumber: number
  /** `values[i]` ứng với `columns[i]`; giá trị gốc, không trim; ô rỗng là null. */
  values: (string | null)[]
}

export interface SourcePreview {
  /** XLSX: tên sheet đã đọc; CSV: null. */
  sheetName: string | null
  /** Tên cột theo đúng thứ tự trong file; luôn duy nhất và không rỗng. */
  columns: string[]
  rows: PreviewRow[]
  /** Tổng số dòng dữ liệu không trống của cả file. */
  totalRows: number
}

/** Thứ tự các lựa chọn kiểu trên màn Schema; `FieldType` suy ra từ đây để hai nơi không lệch nhau. */
export const FIELD_TYPES = ['string', 'number', 'boolean', 'date', 'email'] as const

export type FieldType = (typeof FIELD_TYPES)[number]

/** Key nội bộ cố định của field (`f1`, `f2`, …), không phụ thuộc tên (design D3). */
export type FieldKey = string

export interface TargetField {
  key: FieldKey
  /** Tên đang nhập, chưa trim; chỉ trim khi kiểm và khi gửi lên BE. */
  name: string
  type: FieldType
  required: boolean
}

/** Nguồn giá trị của một target field. Field chưa map thì không có mặt trong mapping. */
export type FieldMapping = { kind: 'column'; column: string } | { kind: 'constant'; value: string }

/** Mapping theo key của field (design D3), nên đổi tên field không làm mất mapping. */
export type MappingDraft = Readonly<Record<FieldKey, FieldMapping>>

export const TRANSFORMATION_TYPES = ['trim', 'uppercase', 'lowercase', 'defaultValue', 'dateFormat'] as const
export type TransformationType = (typeof TRANSFORMATION_TYPES)[number]

/** Id cố định của một bước biến đổi (`t1`, `t2`, …): đổi thứ tự hay sửa tham số không làm đổi id. */
export type TransformationId = string

export type Transformation =
  | { id: TransformationId; type: 'trim' | 'uppercase' | 'lowercase' }
  | { id: TransformationId; type: 'defaultValue'; value: string }
  | { id: TransformationId; type: 'dateFormat'; inputFormat: string; outputFormat: string }

/** Các bước biến đổi theo key của field, đúng thứ tự chạy. */
export type TransformationsDraft = Readonly<Record<FieldKey, readonly Transformation[]>>

/** Rule do user bật; `required` và `type` do BE suy ra từ schema nên không nằm ở đây (design D7). */
export const USER_RULES = ['email', 'unique'] as const
export type UserRule = (typeof USER_RULES)[number]

export type ValidationsDraft = Readonly<Record<FieldKey, readonly UserRule[]>>

/** Kiểu `date` chỉ nhận ISO sau transformation (design D19). */
export const ISO_DATE_FORMAT = 'yyyy-MM-dd'

export type ResultView = 'valid' | 'invalid'

/** Số dòng mỗi trang kết quả (spec result-review). */
export const RESULT_PAGE_SIZE = 50

export interface PipelineSummary {
  total: number
  valid: number
  invalid: number
  /** Số lỗi theo mã, key sắp theo tên mã. */
  errorCountsByCode: Readonly<Record<string, number>>
  /** Số lỗi theo field, key theo thứ tự schema lúc chạy. */
  errorCountsByField: Readonly<Record<string, number>>
  processedAt: string
}

export interface RowError {
  fieldName: string
  stage: 'TRANSFORMATION' | 'VALIDATION'
  rule: string
  /** `order` của transformation (đếm từ 0); null với validation. */
  step: number | null
  code: string
  message: string
  /** Giá trị trước transformation. */
  sourceValue: string | null
}

export interface ResultRow {
  rowNumber: number
  valid: boolean
  /** Theo tên field; hiển thị theo thứ tự schema, không theo thứ tự key. */
  values: Readonly<Record<string, unknown>>
  errors: readonly RowError[]
}

/** Trang kết quả đang xem: tab, số trang (từ 0) và bộ lọc (chỉ có nghĩa ở tab Lỗi). */
export interface ResultQuery {
  view: ResultView
  page: number
  field: string | null
  code: string | null
}

export interface ResultPage {
  number: number
  totalElements: number
  totalPages: number
  rows: readonly ResultRow[]
}
