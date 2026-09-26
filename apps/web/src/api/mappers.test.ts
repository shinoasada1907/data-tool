import { expect, test } from 'vitest'
import { importSessionFixture } from '../mocks/fixtures'
import { toSessionInfo } from './mappers'

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
