import { http, HttpResponse } from 'msw'
import { problemFixture } from '../mocks/fixtures'
import { server } from '../mocks/node'

export const UPLOAD_URL = '/api/import-sessions'

type Responder = () => Response | Promise<Response>

export function problemResponse(status: number, code: string, detail: string): Response {
  return HttpResponse.json(problemFixture(status, code, detail), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

/**
 * Handler cho POST /api/import-sessions trả lần lượt từng response; lần gọi vượt quá danh sách dùng
 * response cuối. `calls()` cho biết đã có bao nhiêu request thật sự tới "server".
 */
export function mockUpload(...responders: Responder[]) {
  let calls = 0
  server.use(
    http.post(UPLOAD_URL, () => {
      const responder = responders[Math.min(calls, responders.length - 1)]
      calls += 1
      return responder()
    }),
  )
  return { calls: () => calls }
}

/** Promise do test tự mở, để giữ request ở trạng thái đang chạy. */
export function gate() {
  let open!: () => void
  const promise = new Promise<void>((resolve) => {
    open = resolve
  })
  return { promise, open }
}
