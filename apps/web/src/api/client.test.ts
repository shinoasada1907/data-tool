import { delay, http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { problemFixture } from '../mocks/fixtures'
import { server } from '../mocks/node'
import { problemResponse } from '../test/http'
import { request } from './client'

const URL = '/api/import-sessions/s-1/thing'

interface Thing {
  name: string
}

function isThing(value: unknown): value is Thing {
  return typeof value === 'object' && value !== null && typeof (value as Thing).name === 'string'
}

function getThing(signal?: AbortSignal) {
  return request(URL, { validate: isThing, signal })
}

describe('request', () => {
  test('2xx có JSON đúng dạng thì trả về body, và gửi Accept cho cả JSON lẫn problem+json', async () => {
    let accept: string | null = null
    server.use(
      http.get(URL, ({ request }) => {
        accept = request.headers.get('Accept')
        return HttpResponse.json({ name: 'An' })
      }),
    )

    await expect(getThing()).resolves.toEqual({ name: 'An' })
    expect(accept).toBe('application/json, application/problem+json')
  })

  test.each([
    ['sai dạng so với contract', () => HttpResponse.json({ id: 1 })],
    ['không phải JSON', () => new HttpResponse('<!doctype html>', { headers: { 'Content-Type': 'text/html' } })],
  ])('2xx mà body %s thì báo INVALID_RESPONSE', async (_, respond) => {
    server.use(http.get(URL, respond))

    await expect(getThing()).rejects.toMatchObject({ kind: 'http', status: 200, code: 'INVALID_RESPONSE' })
  })

  test('ProblemDetail thành ApiError có đủ status, code, detail và errors[]', async () => {
    const problem = problemFixture(422, 'SCHEMA_INVALID', 'Schema has errors.', {
      errors: [{ field: 'email', code: 'SCHEMA_INVALID', message: 'Name is duplicated.' }],
    })
    server.use(
      http.get(URL, () =>
        HttpResponse.json(problem, { status: 422, headers: { 'Content-Type': 'application/problem+json' } }),
      ),
    )

    await expect(getThing()).rejects.toMatchObject({
      kind: 'http',
      status: 422,
      code: 'SCHEMA_INVALID',
      detail: 'Schema has errors.',
      fieldErrors: [{ field: 'email', code: 'SCHEMA_INVALID', message: 'Name is duplicated.' }],
    })
  })

  // Cùng status 404 nhưng khác nghĩa: session hết hạn khác gọi sai endpoint (design D12).
  test.each([
    [404, 'SESSION_NOT_FOUND'],
    [404, 'REQUEST_INVALID'],
    [409, 'SESSION_STATE_INVALID'],
    [409, 'RESULT_NOT_AVAILABLE'],
  ])('%i giữ đúng code %s', async (status, code) => {
    server.use(http.get(URL, () => problemResponse(status, code, 'Something happened.')))

    await expect(getThing()).rejects.toMatchObject({ kind: 'http', status, code })
  })

  test('502 với body HTML từ proxy: code null, không lộ HTML ra detail', async () => {
    server.use(
      http.get(
        URL,
        () => new HttpResponse('<html>Bad Gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }),
      ),
    )

    await expect(getThing()).rejects.toMatchObject({ kind: 'http', status: 502, code: null, detail: null })
  })

  test('mất kết nối thì kind=network', async () => {
    server.use(http.get(URL, () => HttpResponse.error()))

    await expect(getThing()).rejects.toMatchObject({ kind: 'network' })
  })

  test('huỷ giữa chừng thì kind=aborted', async () => {
    server.use(
      http.get(URL, async () => {
        await delay('infinite')
        return HttpResponse.json({ name: 'An' })
      }),
    )
    const controller = new AbortController()
    const promise = getThing(controller.signal)

    controller.abort()

    await expect(promise).rejects.toMatchObject({ kind: 'aborted' })
  })

  test('signal đã huỷ từ trước thì không gửi request', async () => {
    let calls = 0
    server.use(
      http.get(URL, () => {
        calls += 1
        return HttpResponse.json({ name: 'An' })
      }),
    )
    const controller = new AbortController()
    controller.abort()

    await expect(getThing(controller.signal)).rejects.toMatchObject({ kind: 'aborted' })
    expect(calls).toBe(0)
  })
})
