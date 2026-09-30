/**
 * XMLHttpRequest giả cho unit test của api/upload.ts. Dùng qua `vi.stubGlobal('XMLHttpRequest', FakeXhr)`.
 * Test điều khiển phía server bằng respond / failNetwork / progress.
 */
export class FakeXhr {
  static instances: FakeXhr[] = []

  method = ''
  url = ''
  body: unknown = undefined
  requestHeaders: Record<string, string> = {}
  /** abort() đã được gọi hay chưa, kể cả khi gọi lúc request đã xong. */
  aborted = false
  private done = false

  status = 0
  responseText = ''
  private responseHeaders: Record<string, string> = {}

  upload: { onprogress: ((event: ProgressEvent) => void) | null } = { onprogress: null }
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  onabort: (() => void) | null = null
  onloadend: (() => void) | null = null

  constructor() {
    FakeXhr.instances.push(this)
  }

  static last(): FakeXhr {
    const xhr = FakeXhr.instances.at(-1)
    if (!xhr) throw new Error('Chưa có XMLHttpRequest nào được tạo')
    return xhr
  }

  open(method: string, url: string) {
    this.method = method
    this.url = url
  }

  setRequestHeader(name: string, value: string) {
    this.requestHeaders[name] = value
  }

  send(body: unknown) {
    this.body = body
  }

  abort() {
    this.aborted = true
    // Như XHR thật: request đã xong (DONE) thì abort() không phát sự kiện nào.
    if (this.done) return
    this.done = true
    this.onabort?.()
    this.onloadend?.()
  }

  getResponseHeader(name: string): string | null {
    return this.responseHeaders[name.toLowerCase()] ?? null
  }

  respond(status: number, body: string, headers: Record<string, string> = {}) {
    this.done = true
    this.status = status
    this.responseText = body
    this.responseHeaders = Object.fromEntries(Object.entries(headers).map(([k, v]) => [k.toLowerCase(), v]))
    this.onload?.()
    this.onloadend?.()
  }

  failNetwork() {
    this.done = true
    this.onerror?.()
    this.onloadend?.()
  }

  progress(loaded: number, total: number) {
    this.upload.onprogress?.({ lengthComputable: total > 0, loaded, total } as ProgressEvent)
  }
}
