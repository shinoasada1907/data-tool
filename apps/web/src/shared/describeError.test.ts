import { describe, expect, test } from 'vitest'
import { ApiError } from '../api/apiError'
import { describeApiError } from './describeError'

describe('describeApiError', () => {
  test('mã đã biết: dòng chính là thông điệp tiếng Việt, detail của BE xuống dòng phụ', () => {
    const error = new ApiError({
      kind: 'http',
      status: 422,
      code: 'FILE_PARSE_ERROR',
      title: 'Unprocessable Entity',
      detail: 'CSV syntax error near row 12.',
    })

    expect(describeApiError(error)).toEqual({
      headline: 'Không đọc được file; CSV phải mã hoá UTF-8 và phân cách bằng dấu phẩy',
      detail: 'CSV syntax error near row 12.',
      code: 'FILE_PARSE_ERROR',
    })
  })

  test('mã FE chưa biết: dòng chính là detail của BE, không có dòng phụ', () => {
    const error = new ApiError({ kind: 'http', status: 400, code: 'SOMETHING_NEW', detail: 'Chi tiết' })

    expect(describeApiError(error)).toEqual({ headline: 'Chi tiết', code: 'SOMETHING_NEW' })
  })

  test('code trùng tên thuộc tính có sẵn của object (ví dụ "constructor") vẫn coi là mã lạ', () => {
    const error = new ApiError({ kind: 'http', status: 400, code: 'constructor', detail: 'Chi tiết' })

    expect(describeApiError(error)).toEqual({ headline: 'Chi tiết', code: 'constructor' })
  })

  test('không có code và detail thì dùng title', () => {
    const error = new ApiError({ kind: 'http', status: 409, title: 'Conflict' })

    expect(describeApiError(error)).toEqual({ headline: 'Conflict' })
  })

  test('5xx không có gì khác thì báo máy chủ đang lỗi kèm status', () => {
    expect(describeApiError(new ApiError({ kind: 'http', status: 502 }))).toEqual({
      headline: 'Máy chủ đang lỗi (502)',
    })
  })

  test('4xx không có gì khác thì báo yêu cầu không thành công kèm status', () => {
    expect(describeApiError(new ApiError({ kind: 'http', status: 400 }))).toEqual({
      headline: 'Yêu cầu không thành công (400)',
    })
  })

  test('lỗi mạng', () => {
    expect(describeApiError(new ApiError({ kind: 'network' }))).toEqual({ headline: 'Không kết nối được máy chủ' })
  })

  test('request bị huỷ thì không có gì để hiển thị', () => {
    expect(describeApiError(new ApiError({ kind: 'aborted' }))).toBeNull()
  })
})
