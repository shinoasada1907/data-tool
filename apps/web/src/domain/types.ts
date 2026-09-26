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
