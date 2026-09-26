import { describe, expect, test } from 'vitest'
import { readConfig } from './config'

describe('readConfig', () => {
  test('không có biến nào thì giới hạn upload là 20 MB (khớp IMPORTER_MAX_FILE_SIZE của BE) và tắt mock', () => {
    expect(readConfig({})).toEqual({ maxUploadMb: 20, useMock: false })
  })

  test('nhận giới hạn upload hợp lệ từ VITE_MAX_UPLOAD_MB', () => {
    expect(readConfig({ VITE_MAX_UPLOAD_MB: '15' }).maxUploadMb).toBe(15)
  })

  test.each(['', 'abc', '0', '-5'])('giá trị không hợp lệ %j thì dùng mặc định 20 MB', (raw) => {
    expect(readConfig({ VITE_MAX_UPLOAD_MB: raw }).maxUploadMb).toBe(20)
  })

  test('chỉ bật mock khi VITE_USE_MOCK đúng bằng "true"', () => {
    expect(readConfig({ VITE_USE_MOCK: 'true' }).useMock).toBe(true)
    expect(readConfig({ VITE_USE_MOCK: '1' }).useMock).toBe(false)
  })
})
