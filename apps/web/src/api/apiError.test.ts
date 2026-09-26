import { describe, expect, test } from 'vitest'
import { errorFromHttpResponse } from './apiError'

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
