import { expect, test } from 'vitest'
import { formatBytes } from './format'

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
