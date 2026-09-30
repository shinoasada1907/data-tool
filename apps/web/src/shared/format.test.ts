import { expect, test } from 'vitest'
import { formatBytes, formatNumber } from './format'

test.each([
  [0, '0 B'],
  [512, '512 B'],
  [1536, '1,5 KB'],
  // Làm tròn lên đủ 1024 thì phải lên đơn vị kế tiếp, không ra "1.024 KB".
  [1_048_575, '1 MB'],
  [1_258_291, '1,2 MB'],
  [20 * 1024 * 1024, '20 MB'],
  [3 * 1024 * 1024 * 1024, '3 GB'],
])('formatBytes(%i) = %j theo định dạng vi-VN', (bytes, expected) => {
  expect(formatBytes(bytes)).toBe(expected)
})

test.each([
  [0, '0'],
  [50, '50'],
  // Spec source-preview: "Xem trước 50 / 1.200 dòng", nên số 4 chữ số cũng phải có dấu phân cách.
  [1200, '1.200'],
  [1_234_567, '1.234.567'],
])('formatNumber(%i) = %j theo định dạng vi-VN', (value, expected) => {
  expect(formatNumber(value)).toBe(expected)
})
