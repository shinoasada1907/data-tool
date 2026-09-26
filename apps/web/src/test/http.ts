import { http, HttpResponse } from 'msw'
import { problemFixture } from '../mocks/fixtures'
import { server } from '../mocks/node'

export const UPLOAD_URL = '/api/import-sessions'
export const PREVIEW_URL = '/api/import-sessions/:id/preview'
export const SCHEMA_URL = '/api/import-sessions/:id/schema'
export const MAPPING_URL = '/api/import-sessions/:id/mapping'

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

/** Promise do test tự mở, để giữ request ở trạng thái đang chạy. */
export function gate() {
  let open!: () => void
  const promise = new Promise<void>((resolve) => {
    open = resolve
  })
  return { promise, open }
}
