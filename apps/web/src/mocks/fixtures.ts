import type {
  ApiProblemDto,
  ConfigUpdateResponseDto,
  ImportSessionDto,
  PipelineResultDto,
  PipelineSummaryDto,
  SourcePreviewDto,
} from '../api/dto'

// Fixture theo đúng shape của contract V0.1 (đủ mọi field, không chỉ field test đọc).

export function importSessionFixture(overrides: Partial<ImportSessionDto> = {}): ImportSessionDto {
  return {
    id: SESSION_ID,
    status: 'CONFIGURING',
    originalFileName: 'khach-hang.csv',
    fileType: 'CSV',
    sizeBytes: 1_258_291,
    createdAt: '2026-09-26T08:00:00Z',
    updatedAt: '2026-09-26T08:00:00Z',
    // Như BE-F04 trả cho một session vừa upload.
    config: { schema: { fields: [] } },
    readiness: {
      ready: false,
      issues: [{ field: null, code: 'SCHEMA_EMPTY', message: 'Target schema has no fields.' }],
    },
    ...overrides,
  }
}

export const SESSION_ID = '3f6c2a8e-1b7d-4c1e-9a52-0d6f5b8e7c41'

/**
 * Preview CSV: có cột tên dạng số ("2024", "1") để bắt lỗi sắp cột theo key của object; có ô null, ô chỉ có
 * khoảng trắng, và số dòng nhảy cóc (dòng 4 trống bị BE bỏ qua nhưng vẫn giữ chỗ).
 */
export function csvPreviewFixture(overrides: Partial<SourcePreviewDto> = {}): SourcePreviewDto {
  return {
    sessionId: SESSION_ID,
    fileType: 'CSV',
    sheetName: null,
    columns: [
      { index: 0, name: '2024' },
      { index: 1, name: 'Họ tên' },
      { index: 2, name: '1' },
      { index: 3, name: 'Email' },
    ],
    rows: [
      { rowNumber: 2, values: ['A01', 'Nguyễn An', '10', 'an@example.com'] },
      { rowNumber: 3, values: ['A02', '  ', null, 'binh@example.com'] },
      { rowNumber: 5, values: ['A03', 'Lê Chi', '7', null] },
    ],
    previewLimit: 50,
    totalRows: 1200,
    ...overrides,
  }
}

/** Preview XLSX: cùng contract với CSV, thêm `sheetName`; giá trị ô đã được BE đổi sang chuỗi. */
export function xlsxPreviewFixture(overrides: Partial<SourcePreviewDto> = {}): SourcePreviewDto {
  return {
    sessionId: SESSION_ID,
    fileType: 'XLSX',
    sheetName: 'Khách hàng',
    columns: [
      { index: 0, name: 'Mã KH' },
      { index: 1, name: 'Ngày sinh' },
      { index: 2, name: 'Số dư' },
    ],
    rows: [
      { rowNumber: 2, values: ['KH001', '1990-02-28', '84901234567'] },
      { rowNumber: 3, values: ['KH002', '2024-12-25T13:45:30', '-5.25'] },
    ],
    previewLimit: 50,
    totalRows: 2,
    ...overrides,
  }
}

/** Body 200 của mọi PUT cấu hình; FE bỏ qua nội dung (design D6) nhưng fixture vẫn đúng shape. */
export function configUpdateFixture(overrides: Partial<ConfigUpdateResponseDto> = {}): ConfigUpdateResponseDto {
  return { session: importSessionFixture(), warnings: [], ...overrides }
}

/** Summary sau khi chạy trên preview mặc định (4 field sinh từ csvPreviewFixture). */
export function pipelineSummaryFixture(overrides: Partial<PipelineSummaryDto> = {}): PipelineSummaryDto {
  return {
    sessionId: SESSION_ID,
    status: 'PROCESSED',
    total: 120,
    valid: 100,
    invalid: 20,
    errorCountsByCode: { TRANSFORMATION_FAILED: 5, VALIDATION_EMAIL: 15 },
    errorCountsByField: { '1': 5, Email: 15 },
    processedAt: '2026-09-26T09:00:00Z',
    ...overrides,
  }
}

/**
 * Một trang kết quả. Key của `values` cố ý lệch thứ tự schema (JS đưa key dạng số "1", "2024" lên đầu), để bắt lỗi
 * hiển thị cột theo thứ tự key.
 */
export function pipelineResultFixture(overrides: Partial<PipelineResultDto> = {}): PipelineResultDto {
  return {
    summary: pipelineSummaryFixture(),
    view: 'invalid',
    page: { number: 0, size: 50, totalElements: 20, totalPages: 1 },
    rows: [
      {
        rowNumber: 3,
        valid: false,
        values: { Email: 'binh@', 'Họ tên': 'Trần Bình', '1': null, '2024': 'A02' },
        errors: [
          {
            rowNumber: 3,
            fieldName: '1',
            stage: 'TRANSFORMATION',
            rule: 'dateFormat',
            step: 1,
            code: 'TRANSFORMATION_FAILED',
            message: 'Value does not match pattern dd/MM/yyyy.',
            sourceValue: '31/02/2024',
          },
          {
            rowNumber: 3,
            fieldName: 'Email',
            stage: 'VALIDATION',
            rule: 'email',
            step: null,
            code: 'VALIDATION_EMAIL',
            message: 'Value is not a valid email address.',
            sourceValue: 'binh@',
          },
        ],
      },
    ],
    ...overrides,
  }
}

/** Trang dòng hợp lệ: giá trị đã ép kiểu (số, boolean, null). */
export function validResultFixture(overrides: Partial<PipelineResultDto> = {}): PipelineResultDto {
  return pipelineResultFixture({
    view: 'valid',
    page: { number: 0, size: 50, totalElements: 100, totalPages: 2 },
    rows: [
      {
        rowNumber: 2,
        valid: true,
        values: { Email: 'an@example.com', 'Họ tên': 'Nguyễn An', '1': 10, '2024': 'A01' },
        errors: [],
      },
      { rowNumber: 5, valid: true, values: { Email: null, 'Họ tên': 'Lê Chi', '1': 7, '2024': 'A03' }, errors: [] },
    ],
    ...overrides,
  })
}

const STATUS_TITLES: Record<number, string> = {
  400: 'Bad Request',
  404: 'Not Found',
  409: 'Conflict',
  413: 'Payload Too Large',
  415: 'Unsupported Media Type',
  422: 'Unprocessable Entity',
  500: 'Internal Server Error',
}

export function problemFixture(
  status: number,
  code: string,
  detail: string,
  extra: Partial<ApiProblemDto> = {},
): ApiProblemDto {
  return {
    type: 'about:blank',
    title: STATUS_TITLES[status] ?? 'Error',
    status,
    detail,
    instance: '/api/import-sessions',
    code,
    ...extra,
  }
}
