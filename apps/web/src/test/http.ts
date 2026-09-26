import { http, HttpResponse } from 'msw'
import { onTestFinished } from 'vitest'
import { problemFixture } from '../mocks/fixtures'
import { server } from '../mocks/node'

export const UPLOAD_URL = '/api/import-sessions'
export const PREVIEW_URL = '/api/import-sessions/:id/preview'
export const SCHEMA_URL = '/api/import-sessions/:id/schema'
export const MAPPING_URL = '/api/import-sessions/:id/mapping'
export const TRANSFORMATIONS_URL = '/api/import-sessions/:id/transformations'
export const VALIDATIONS_URL = '/api/import-sessions/:id/validations'
export const PROCESS_URL = '/api/import-sessions/:id/process'
export const RESULT_URL = '/api/import-sessions/:id/result'

type Responder = () => Response | Promise<Response>

export function problemResponse(status: number, code: string, detail: string): Response {
  return HttpResponse.json(problemFixture(status, code, detail), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

/**
 * Handler trả lần lượt từng response; lần gọi vượt quá danh sách dùng response cuối. `calls()` cho biết đã có
 * bao nhiêu request thật sự tới "server".
 */
function mockSequence(method: typeof http.get, url: string, responders: Responder[]) {
  let calls = 0
  server.use(
    method(url, () => {
      const responder = responders[Math.min(calls, responders.length - 1)]
      calls += 1
      return responder()
    }),
  )
  return { calls: () => calls }
}

/** POST /api/import-sessions. */
export function mockUpload(...responders: Responder[]) {
  return mockSequence(http.post, UPLOAD_URL, responders)
}

/** GET /api/import-sessions/{id}/preview. */
export function mockPreview(...responders: Responder[]) {
  return mockSequence(http.get, PREVIEW_URL, responders)
}

/** Handler PUT ghi lại body JSON của từng lần gửi (`bodies`, theo thứ tự) rồi trả lần lượt từng response. */
function mockPutJson(url: string, responders: Responder[]) {
  const bodies: unknown[] = []
  server.use(
    http.put(url, async ({ request }) => {
      bodies.push(await request.json())
      return responders[Math.min(bodies.length - 1, responders.length - 1)]()
    }),
  )
  return { calls: () => bodies.length, bodies }
}

/** PUT /api/import-sessions/{id}/schema. */
export function mockSaveSchema(...responders: Responder[]) {
  return mockPutJson(SCHEMA_URL, responders)
}

/** PUT /api/import-sessions/{id}/mapping. */
export function mockSaveMapping(...responders: Responder[]) {
  return mockPutJson(MAPPING_URL, responders)
}

/** PUT /api/import-sessions/{id}/transformations. */
export function mockSaveTransformations(...responders: Responder[]) {
  return mockPutJson(TRANSFORMATIONS_URL, responders)
}

/** PUT /api/import-sessions/{id}/validations. */
export function mockSaveValidations(...responders: Responder[]) {
  return mockPutJson(VALIDATIONS_URL, responders)
}

/** POST /api/import-sessions/{id}/process. */
export function mockProcess(...responders: Responder[]) {
  return mockSequence(http.post, PROCESS_URL, responders)
}

type ResultResponder = (query: Record<string, string>) => Response | Promise<Response>

/** GET /api/import-sessions/{id}/result; `queries` ghi query của từng lần gọi, responder nhận query đó. */
export function mockResult(...responders: ResultResponder[]) {
  const queries: Record<string, string>[] = []
  server.use(
    http.get(RESULT_URL, ({ request }) => {
      const query = Object.fromEntries(new URL(request.url).searchParams)
      queries.push(query)
      return responders[Math.min(queries.length - 1, responders.length - 1)](query)
    }),
  )
  return { calls: () => queries.length, queries }
}

/**
 * Ghi mọi request theo đúng thứ tự gửi, dạng "PUT transformations" hay "GET result?view=invalid&page=0&size=50"
 * (đoạn cuối của path cộng query), để test trình tự giữa nhiều endpoint.
 */
export function recordRequests() {
  const log: string[] = []
  const listener = ({ request }: { request: Request }) => {
    const url = new URL(request.url)
    log.push(`${request.method} ${url.pathname.split('/').pop()}${decodeURIComponent(url.search)}`)
  }
  server.events.on('request:start', listener)
  onTestFinished(() => {
    server.events.removeListener('request:start', listener)
  })
  return log
}

/** Promise do test tự mở, để giữ request ở trạng thái đang chạy. */
export function gate() {
  let open!: () => void
  const promise = new Promise<void>((resolve) => {
    open = resolve
  })
  return { promise, open }
}
