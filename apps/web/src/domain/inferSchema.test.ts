import { describe, expect, test } from 'vitest'
import { fieldsFromPreview, inferFieldType } from './inferSchema'
import type { SourcePreview } from './types'

describe('inferFieldType', () => {
  // Cùng luật với kiểm kiểu của BE-F07, để kiểu đoán ra không tự sinh lỗi VALIDATION_TYPE.
  test.each([
    ['boolean', ['TRUE', '0', 'false', '1']],
    ['number', ['42', '-3.50', '007']],
    ['date', ['2024-02-29', '1990-12-25']],
    ['email', ['an@example.com', 'binh@x.vn']],
    ['string', ['An', 'Bình']],
  ] as const)('mọi ô hợp lệ → %s', (expected, values) => {
    expect(inferFieldType([...values])).toBe(expected)
  })

  test('cột chỉ có 1 và 0 là number, vì hay gặp ở cột số lượng hơn cột đúng/sai', () => {
    expect(inferFieldType(['1', '0', '1'])).toBe('number')
  })

  test('ô rỗng (null, chuỗi rỗng, chỉ khoảng trắng kể cả NBSP) bị bỏ qua', () => {
    expect(inferFieldType([null, '42', '', '  ', ' ', '7'])).toBe('number')
  })

  test('cột không có ô nào có giá trị là string', () => {
    expect(inferFieldType([null, '  '])).toBe('string')
    expect(inferFieldType([])).toBe('string')
  })

  test.each([
    ['lẫn chữ và số', ['42', 'abc']],
    ['số có khoảng trắng (BE không trim)', ['42', ' 7']],
    ['số kiểu 1,234 hoặc 1e3', ['1,234', '1e3']],
    ['ngày không có thật', ['2024-02-30']],
    ['ngày có giờ', ['2024-12-25T13:45:30']],
    ['ngày thiếu số 0', ['1990-1-5']],
    ['email thiếu tên miền cấp cao', ['an@example']],
    ['boolean kiểu yes/no', ['yes', 'no']],
  ] as const)('%s → string', (_, values) => {
    expect(inferFieldType([...values])).toBe('string')
  })
})

describe('fieldsFromPreview', () => {
  const preview: SourcePreview = {
    sheetName: null,
    columns: ['Mã', 'Email', 'Số lượng'],
    rows: [
      { rowNumber: 2, values: ['A01', 'an@example.com', '10'] },
      { rowNumber: 3, values: ['A02', null, '0'] },
    ],
    totalRows: 2,
  }

  test('mỗi cột thành một field cùng tên, cùng thứ tự, không bắt buộc, kiểu đoán theo cột đó', () => {
    expect(fieldsFromPreview(preview, 5)).toEqual({
      fields: [
        { key: 'f5', name: 'Mã', type: 'string', required: false },
        { key: 'f6', name: 'Email', type: 'email', required: false },
        { key: 'f7', name: 'Số lượng', type: 'number', required: false },
      ],
      nextFieldSeq: 8,
    })
  })

  test('file chỉ có header: field vẫn được sinh, kiểu string', () => {
    expect(fieldsFromPreview({ ...preview, rows: [] }, 1).fields.map((field) => field.type)).toEqual([
      'string',
      'string',
      'string',
    ])
  })
})
