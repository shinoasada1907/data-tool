import type { ApiProblemDto, ImportSessionDto } from '../api/dto'

// Fixture theo đúng shape của contract V0.1 (đủ mọi field, không chỉ field test đọc).

export function importSessionFixture(overrides: Partial<ImportSessionDto> = {}): ImportSessionDto {
  return {
    id: '3f6c2a8e-1b7d-4c1e-9a52-0d6f5b8e7c41',
    status: 'CONFIGURING',
    originalFileName: 'khach-hang.csv',
    fileType: 'CSV',
    sizeBytes: 1_258_291,
    createdAt: '2026-09-26T08:00:00Z',
    updatedAt: '2026-09-26T08:00:00Z',
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
