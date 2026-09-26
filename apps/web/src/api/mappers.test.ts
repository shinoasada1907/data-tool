import { describe, expect, test } from 'vitest'
import { csvPreviewFixture, importSessionFixture, xlsxPreviewFixture } from '../mocks/fixtures'
import { toSessionInfo, toSourcePreview, toTargetSchemaDto } from './mappers'

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
