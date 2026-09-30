import type { DatasetDto, DatasetPreviewDto } from '../shared/dataset/datasetApi'
import type { ValidatorRowsDto, ValidatorRunDto } from '../tools/validator/validatorApi'

// Dữ liệu mẫu theo contract Toolbox v1 (dataset) và tool-05 (validator).

export const DATASET_ID = '9b1f6c2e-4d3a-4f7e-8a21-5c0d7e9f1a33'
export const RUN_ID = 'c2d4e6f8-1a3b-4c5d-9e7f-0a1b2c3d4e5f'

export function datasetFixture(overrides: Partial<DatasetDto> = {}): DatasetDto {
  return {
    id: DATASET_ID,
    originalFileName: 'khach.csv',
    format: 'CSV',
    sizeBytes: 2048,
    sheets: null,
    expiresAt: '2026-09-30T10:00:00Z',
    ...overrides,
  }
}

export function datasetPreviewFixture(overrides: Partial<DatasetPreviewDto> = {}): DatasetPreviewDto {
  return {
    datasetId: DATASET_ID,
    format: 'CSV',
    options: { sheet: null, delimiter: 'SEMICOLON', encoding: 'UTF-8', hasHeader: true },
    autoDetected: ['delimiter', 'encoding'],
    sheetName: null,
    columns: [
      { index: 0, name: 'ma', inferredType: 'string', emptyCount: 0 },
      { index: 1, name: 'Email', inferredType: 'email', emptyCount: 1 },
      { index: 2, name: 'gia', inferredType: 'number', emptyCount: 0 },
    ],
    totalRows: 3,
    blankRowsSkipped: 1,
    rows: [
      { rowNumber: 2, values: ['A01', 'an@x.vn', '5000'] },
      { rowNumber: 3, values: ['A02', null, '7000'] },
      { rowNumber: 5, values: ['A01', 'binh@x', 'abc'] },
    ],
    ...overrides,
  }
}

export function validatorRunFixture(overrides: Partial<ValidatorRunDto> = {}): ValidatorRunDto {
  return {
    id: RUN_ID,
    expiresAt: '2026-09-30T10:00:00Z',
    schemaName: 'khach',
    fields: ['ma', 'email', 'gia'],
    compatibility: { matched: [{ field: 'ma', column: 'ma' }, { field: 'email', column: 'Email' }, { field: 'gia', column: 'gia' }], missingOptional: [], extraColumns: [] },
    summary: {
      totalRows: 3,
      validRows: 1,
      invalidRows: 2,
      errorCount: 3,
      errorCountsByCode: { VALIDATION_EMAIL: 1, VALIDATION_TYPE: 1, VALIDATION_UNIQUE: 1 },
      errorCountsByField: { ma: 1, email: 1, gia: 1 },
    },
    ...overrides,
  }
}

export function validatorRowsFixture(overrides: Partial<ValidatorRowsDto> = {}): ValidatorRowsDto {
  return {
    view: 'INVALID',
    page: { number: 0, size: 50, totalElements: 2, totalPages: 1 },
    rows: [
      {
        rowNumber: 3,
        values: ['A02', null, '7000'],
        errors: [],
      },
      {
        rowNumber: 5,
        values: ['A01', 'binh@x', 'abc'],
        errors: [
          { field: 'ma', code: 'VALIDATION_UNIQUE', rule: 'unique', message: 'Value duplicates row 2.', value: 'A01' },
          { field: 'email', code: 'VALIDATION_EMAIL', rule: 'email', message: 'Invalid email.', value: 'binh@x' },
          { field: 'gia', code: 'VALIDATION_TYPE', rule: 'type', message: 'Not a number.', value: 'abc' },
        ],
      },
    ],
    ...overrides,
  }
}
