// Contract V0.1 với BE. Nguồn chuẩn: openspec/changes/archive/2026-09-25-be-f01-import-session/design.md
// (mục "API contract V0.1"). Component và reducer không import file này; đi qua api/mappers.ts (design D5).

export type SessionStatus = 'UPLOADED' | 'CONFIGURING' | 'READY' | 'PROCESSED' | 'FAILED'
export type SourceFileType = 'CSV' | 'XLSX'
export type FieldType = 'string' | 'number' | 'boolean' | 'date' | 'email'
export type MappingType = 'SOURCE_COLUMN' | 'CONSTANT'
export type TransformationType = 'trim' | 'uppercase' | 'lowercase' | 'defaultValue' | 'dateFormat'
/** `required` và `type` do BE suy ra từ schema, không gửi lên. */
export type UserValidationType = 'email' | 'unique'

export interface ProblemItemDto {
  /** Tên target field, hoặc null. */
  field: string | null
  code: string
  message: string
}

/** Body của mọi lỗi (`application/problem+json`). */
export interface ApiProblemDto {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  code: string
  errors?: ProblemItemDto[]
}

export interface ImportSessionDto {
  id: string
  status: SessionStatus
  originalFileName: string
  fileType: SourceFileType
  sizeBytes: number
  createdAt: string
  updatedAt: string
  /** Từ BE-F04; FE V0.1 không dùng. */
  config?: {
    schema: TargetSchemaDto
    mapping: MappingConfigDto
    transformations: TransformationConfigDto
    validations: ValidationConfigDto
  }
  /** Từ BE-F04; FE V0.1 không dùng. */
  readiness?: { ready: boolean; issues: ProblemItemDto[] }
}

/** Body của mọi lệnh PUT config; FE bỏ qua (design D6). */
export interface ConfigUpdateResponseDto {
  session: ImportSessionDto
  warnings: ProblemItemDto[]
}

export interface SourcePreviewDto {
  sessionId: string
  fileType: SourceFileType
  /** XLSX: sheet hiển thị đầu tiên; CSV: null. */
  sheetName: string | null
  /** Đúng thứ tự gốc; `name` luôn duy nhất và không rỗng. */
  columns: { index: number; name: string }[]
  /** `values[i]` ứng với `columns[i]`; giá trị gốc, không trim; ô rỗng là null. */
  rows: { rowNumber: number; values: (string | null)[] }[]
  previewLimit: number
  /** Số dòng dữ liệu không trống. */
  totalRows: number
}

export interface TargetSchemaDto {
  fields: { name: string; type: FieldType; required: boolean; order: number }[]
}

export interface MappingConfigDto {
  /** Chỉ gồm field đã map. */
  mappings: {
    targetField: string
    mappingType: MappingType
    sourceColumn: string | null
    constantValue: string | null
  }[]
}

export interface TransformationConfigDto {
  /** `order` bắt đầu từ 0, không trùng trong cùng một targetField. */
  transformations: {
    targetField: string
    order: number
    type: TransformationType
    /** defaultValue: { value }; dateFormat: { inputFormat, outputFormat? }; loại khác: không gửi. */
    params?: Record<string, string>
  }[]
}

export interface ValidationConfigDto {
  validations: { targetField: string; type: UserValidationType; params?: Record<string, string> }[]
}

export interface PipelineSummaryDto {
  sessionId: string
  status: SessionStatus
  total: number
  valid: number
  invalid: number
  errorCountsByCode: Record<string, number>
  errorCountsByField: Record<string, number>
  processedAt: string
}

export interface ImportErrorDto {
  /** Như khi mở file bằng Excel: header là dòng 1. */
  rowNumber: number
  fieldName: string
  stage: 'TRANSFORMATION' | 'VALIDATION'
  rule: string
  /** `order` của transformation; null với validation. */
  step: number | null
  code: string
  message: string
  /** Giá trị trước transformation. */
  sourceValue: string | null
}

export interface PipelineResultDto {
  summary: PipelineSummaryDto
  view: 'valid' | 'invalid'
  page: { number: number; size: number; totalElements: number; totalPages: number }
  /**
   * Row hợp lệ: `values` đã ép kiểu. Row lỗi: chuỗi sau transformation, hoặc null ở field có
   * transformation lỗi. FE hiển thị theo thứ tự schema, không theo thứ tự key.
   */
  rows: { rowNumber: number; valid: boolean; values: Record<string, unknown>; errors: ImportErrorDto[] }[]
}
