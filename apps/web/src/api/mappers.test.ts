import { describe, expect, test } from 'vitest'
import { csvPreviewFixture, importSessionFixture, xlsxPreviewFixture } from '../mocks/fixtures'
import {
  toMappingConfigDto,
  toSessionInfo,
  toSourcePreview,
  toTargetSchemaDto,
  toTransformationConfigDto,
  toValidationConfigDto,
} from './mappers'

test('toSessionInfo lấy đúng id của session (F02 dùng id này để gọi /preview) và thông tin file', () => {
  const dto = importSessionFixture({
    id: '9b2f7c1e-0a4d-4e8b-b6f3-2c5d8e1a7f90',
    originalFileName: 'bao-cao.xlsx',
    fileType: 'XLSX',
    sizeBytes: 2048,
  })

  expect(toSessionInfo(dto)).toEqual({
    id: '9b2f7c1e-0a4d-4e8b-b6f3-2c5d8e1a7f90',
    fileName: 'bao-cao.xlsx',
    fileType: 'XLSX',
    sizeBytes: 2048,
  })
})

describe('toSourcePreview', () => {
  test('giữ đúng thứ tự columns[], kể cả cột tên dạng số, và values theo vị trí', () => {
    const preview = toSourcePreview(csvPreviewFixture())

    // Nếu đổi sang object theo tên cột, JS tự đưa key dạng số ("1", "2024") lên đầu và làm lệch cột.
    expect(preview.columns).toEqual(['2024', 'Họ tên', '1', 'Email'])
    expect(preview.rows[1]).toEqual({ rowNumber: 3, values: ['A02', '  ', null, 'binh@example.com'] })
  })

  test('mang theo sheetName và totalRows', () => {
    expect(toSourcePreview(xlsxPreviewFixture())).toMatchObject({
      sheetName: 'Khách hàng',
      totalRows: 2,
    })
    expect(toSourcePreview(csvPreviewFixture()).sheetName).toBeNull()
  })
})

describe('toTargetSchemaDto', () => {
  test('tên gửi lên ở dạng NFC, để BE không coi hai tên nhìn giống hệt nhau là hai field khác nhau', () => {
    const dto = toTargetSchemaDto([{ key: 'f1', name: ' Họ tên '.normalize('NFD'), type: 'string', required: false }])

    expect(dto.fields[0].name).toBe('Họ tên'.normalize('NFC'))
  })

  test('tên đã trim, order theo vị trí hiển thị từ 0, không gửi key nội bộ', () => {
    const dto = toTargetSchemaDto([
      { key: 'f3', name: ' email ', type: 'email', required: true },
      { key: 'f1', name: 'Họ tên', type: 'string', required: false },
    ])

    expect(dto).toEqual({
      fields: [
        { name: 'email', type: 'email', required: true, order: 0 },
        { name: 'Họ tên', type: 'string', required: false, order: 1 },
      ],
    })
  })
})

describe('toMappingConfigDto', () => {
  test('sourceColumn gửi nguyên văn tên cột của file (không chuẩn hoá), vì BE so khớp chính xác với preview', () => {
    const nfd = 'Họ tên'.normalize('NFD')

    const dto = toMappingConfigDto([{ key: 'f1', name: 'name', type: 'string', required: false }], {
      f1: { kind: 'column', column: nfd },
    })

    expect(dto.mappings[0].sourceColumn).toBe(nfd)
  })

  test('chỉ gồm field đã map, theo thứ tự schema, targetField là tên hiện tại (đã chuẩn hoá)', () => {
    const dto = toMappingConfigDto(
      [
        { key: 'f1', name: ' email ', type: 'email', required: true },
        { key: 'f2', name: 'note', type: 'string', required: false },
        { key: 'f3', name: 'country', type: 'string', required: false },
      ],
      { f3: { kind: 'constant', value: 'VN' }, f1: { kind: 'column', column: 'E-mail' } },
    )

    expect(dto).toEqual({
      mappings: [
        { targetField: 'email', mappingType: 'SOURCE_COLUMN', sourceColumn: 'E-mail', constantValue: null },
        { targetField: 'country', mappingType: 'CONSTANT', sourceColumn: null, constantValue: 'VN' },
      ],
    })
  })
})

describe('toTransformationConfigDto', () => {
  const fields = [
    { key: 'f1', name: ' name ', type: 'string' as const, required: false },
    { key: 'f2', name: 'dob', type: 'date' as const, required: false },
    { key: 'f3', name: 'note', type: 'string' as const, required: false },
  ]

  test('theo thứ tự schema rồi thứ tự bước, order từ 0 trong từng field; trim/uppercase/lowercase không có params', () => {
    const dto = toTransformationConfigDto(fields, {
      f2: [{ id: 't3', type: 'dateFormat', inputFormat: 'dd/MM/yyyy', outputFormat: 'yyyy-MM-dd' }],
      f1: [
        { id: 't1', type: 'trim' },
        { id: 't2', type: 'uppercase' },
        { id: 't4', type: 'defaultValue', value: 'N/A' },
      ],
    })

    expect(dto).toEqual({
      transformations: [
        { targetField: 'name', order: 0, type: 'trim' },
        { targetField: 'name', order: 1, type: 'uppercase' },
        { targetField: 'name', order: 2, type: 'defaultValue', params: { value: 'N/A' } },
        {
          targetField: 'dob',
          order: 0,
          type: 'dateFormat',
          params: { inputFormat: 'dd/MM/yyyy', outputFormat: 'yyyy-MM-dd' },
        },
      ],
    })
  })

  test('field kiểu date luôn gửi outputFormat yyyy-MM-dd', () => {
    const dto = toTransformationConfigDto(fields, {
      f2: [{ id: 't1', type: 'dateFormat', inputFormat: 'dd/MM/yyyy', outputFormat: 'dd.MM.yyyy' }],
    })

    expect(dto.transformations[0].params).toEqual({ inputFormat: 'dd/MM/yyyy', outputFormat: 'yyyy-MM-dd' })
  })
})

describe('toValidationConfigDto', () => {
  test('chỉ rule do user bật, theo thứ tự schema rồi email trước unique, không có params', () => {
    const dto = toValidationConfigDto(
      [
        { key: 'f1', name: 'email', type: 'string', required: true },
        { key: 'f2', name: 'code', type: 'number', required: false },
        { key: 'f3', name: 'note', type: 'string', required: false },
      ],
      { f2: ['unique'], f1: ['unique', 'email'] },
    )

    expect(dto).toEqual({
      validations: [
        { targetField: 'email', type: 'email' },
        { targetField: 'email', type: 'unique' },
        { targetField: 'code', type: 'unique' },
      ],
    })
  })

  test('rule email ở field không phải string (kể cả kiểu email) bị bỏ, unique giữ nguyên', () => {
    const dto = toValidationConfigDto(
      [
        { key: 'f1', name: 'age', type: 'number', required: false },
        { key: 'f2', name: 'mail', type: 'email', required: false },
      ],
      { f1: ['email', 'unique'], f2: ['email', 'unique'] },
    )

    expect(dto).toEqual({
      validations: [
        { targetField: 'age', type: 'unique' },
        { targetField: 'mail', type: 'unique' },
      ],
    })
  })

  test('không bật rule nào thì gửi danh sách rỗng', () => {
    expect(toValidationConfigDto([{ key: 'f1', name: 'email', type: 'email', required: true }], {})).toEqual({
      validations: [],
    })
  })
})
