import type { ApiProblemDto, ImportSessionDto, SourcePreviewDto } from '../api/dto'

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
