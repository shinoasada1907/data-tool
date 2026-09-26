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
