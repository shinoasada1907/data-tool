import { describe, expect, test } from 'vitest'
import { ApiError, errorFromHttpResponse, isRetryable, isSessionUnusable } from './apiError'

const PROBLEM = 'application/problem+json'

describe('errorFromHttpResponse', () => {
  test('đọc đủ code, title, detail và errors[] từ ProblemDetail', () => {
    const body = JSON.stringify({
      type: 'about:blank',
      title: 'Unprocessable Entity',
      status: 422,
      detail: 'Target field is required.',
      instance: '/api/import-sessions/1/mapping',
      code: 'MAPPING_INVALID',
      errors: [{ field: 'email', code: 'TARGET_FIELD_REQUIRED', message: 'Field email is required.' }],
    })

    const error = errorFromHttpResponse(422, PROBLEM, body)

    expect(error).toMatchObject({
      kind: 'http',
      status: 422,
      code: 'MAPPING_INVALID',
      title: 'Unprocessable Entity',
      detail: 'Target field is required.',
      fieldErrors: [{ field: 'email', code: 'TARGET_FIELD_REQUIRED', message: 'Field email is required.' }],
    })
  })

  test('phần tử errors[] sai dạng bị bỏ qua, field vắng thì thành null', () => {
    const body = JSON.stringify({
      title: 'Unprocessable Entity',
      status: 422,
      code: 'SCHEMA_INVALID',
      errors: [{ code: 'SCHEMA_INVALID', message: 'Name is blank.' }, { field: 'x' }, 'rác'],
    })

    expect(errorFromHttpResponse(422, PROBLEM, body).fieldErrors).toEqual([
      { field: null, code: 'SCHEMA_INVALID', message: 'Name is blank.' },
    ])
  })

  test('body HTML (ví dụ 502 từ proxy khi BE tắt) thì không có code và không lộ nội dung HTML', () => {
    const error = errorFromHttpResponse(502, 'text/html', '<html><body>Bad Gateway</body></html>')

    expect(error).toMatchObject({ kind: 'http', status: 502, code: null, title: null, detail: null, fieldErrors: [] })
  })

  test('content-type là JSON nhưng body hỏng thì coi như không có ProblemDetail', () => {
    expect(errorFromHttpResponse(500, PROBLEM, '{hỏng').code).toBeNull()
  })

  test('413 không có code (ví dụ trả từ proxy) thì nhận code FILE_TOO_LARGE', () => {
    expect(errorFromHttpResponse(413, 'text/html', '<html>Too large</html>').code).toBe('FILE_TOO_LARGE')
  })
})

describe('isRetryable', () => {
  test.each([
    ['lỗi mạng', true, new ApiError({ kind: 'network' })],
    ['502 từ proxy', true, new ApiError({ kind: 'http', status: 502 })],
    ['500 INTERNAL_ERROR', true, new ApiError({ kind: 'http', status: 500, code: 'INTERNAL_ERROR' })],
    ['422 FILE_PARSE_ERROR', false, new ApiError({ kind: 'http', status: 422, code: 'FILE_PARSE_ERROR' })],
    ['404 SESSION_NOT_FOUND', false, new ApiError({ kind: 'http', status: 404, code: 'SESSION_NOT_FOUND' })],
    ['bị huỷ', false, new ApiError({ kind: 'aborted' })],
  ])('%s → %s', (_, expected, error) => {
    expect(isRetryable(error)).toBe(expected)
  })
})

describe('isSessionUnusable', () => {
  // Nhận biết theo code, không theo status (design D12).
  test.each([
    ['404 SESSION_NOT_FOUND', true, new ApiError({ kind: 'http', status: 404, code: 'SESSION_NOT_FOUND' })],
    ['409 SESSION_STATE_INVALID', true, new ApiError({ kind: 'http', status: 409, code: 'SESSION_STATE_INVALID' })],
    ['404 REQUEST_INVALID (sai endpoint)', false, new ApiError({ kind: 'http', status: 404, code: 'REQUEST_INVALID' })],
    ['409 SESSION_NOT_READY', false, new ApiError({ kind: 'http', status: 409, code: 'SESSION_NOT_READY' })],
    ['lỗi mạng', false, new ApiError({ kind: 'network' })],
  ])('%s → %s', (_, expected, error) => {
    expect(isSessionUnusable(error)).toBe(expected)
  })
})
