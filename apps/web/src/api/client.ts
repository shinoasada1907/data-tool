import { ApiError, errorFromHttpResponse } from './apiError'

/**
 * Thời gian chờ mặc định. Không có nó thì BE hoặc proxy treo làm spinner quay mãi; với PUT, stepper và nút điều hướng
 * đang khoá nên user không còn lối thoát nào ngoài tải lại trang và mất cấu hình (design D6).
 */
export const DEFAULT_TIMEOUT_MS = 30_000

export interface RequestOptions<T> {
  method?: 'GET' | 'PUT' | 'POST'
  /** Gửi dạng JSON. */
  body?: unknown
  /** Kiểm tối thiểu body 2xx theo contract; sai dạng thì báo INVALID_RESPONSE thay vì chạy tiếp với dữ liệu hỏng. */
  validate: (value: unknown) => value is T
  signal?: AbortSignal
  timeoutMs?: number
  /**
   * Số JSON mà kiểu number của JS làm sai (quá khoảng 15 chữ số, hay có 0 ở cuối như `10.50`) được giữ nguyên văn dưới
   * dạng chuỗi. Dùng cho dữ liệu của user (giá trị ô trong kết quả): BE gửi đúng từng chữ số (BE-F09).
   */
  exactNumbers?: boolean
}

/**
 * Gọi endpoint JSON bằng fetch (design D6). Lỗi HTTP, lỗi mạng, hết giờ, huỷ và response 2xx sai dạng đều reject
 * bằng ApiError. Không tự retry.
 */
export async function request<T>(
  path: string,
  { method = 'GET', body, validate, signal, timeoutMs = DEFAULT_TIMEOUT_MS, exactNumbers = false }: RequestOptions<T>,
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const { response, text } = await fetchWithin(
    path,
    { method, headers, body: body === undefined ? undefined : JSON.stringify(body) },
    { signal, timeoutMs },
    async (response) => ({ response, text: await response.text() }),
  )

  if (!response.ok) {
    throw errorFromHttpResponse(response.status, response.headers.get('Content-Type'), text)
  }

  const value = parseJson(text, exactNumbers)
  if (value === undefined || !validate(value)) {
    throw new ApiError({ kind: 'http', status: response.status, code: 'INVALID_RESPONSE' })
  }
  return value
}

/**
 * `fetch` rồi đọc body bằng `read`, cùng một tín hiệu huỷ (design D6). Lỗi mạng (kể cả kết nối đứt giữa lúc đọc
 * body), hết giờ và huỷ đều thành ApiError; response lỗi HTTP vẫn được trả cho `read`.
 *
 * `timeoutMs` là thời gian im lặng tối đa: tính từ lúc gửi, và tính lại mỗi khi `read` gọi `stillReceiving()` (đã nhận
 * thêm dữ liệu). `read` cũng nhận `signal` của lượt gọi, để tự kiểm đã bị huỷ chưa khi đọc body theo chunk. `request()` đọc cả body một lần nên với nó đây là thời gian chờ toàn phần; tải file thì gọi
 * `stillReceiving` sau mỗi chunk, nên file lớn trên mạng chậm không bị cắt (review FE-F10).
 */
export async function fetchWithin<R>(
  path: string,
  init: Omit<RequestInit, 'signal'>,
  { signal, timeoutMs }: { signal?: AbortSignal; timeoutMs: number },
  read: (response: Response, progress: { stillReceiving: () => void; signal: AbortSignal }) => Promise<R>,
): Promise<R> {
  // Một controller riêng gom cả hai nguồn huỷ (user và hết giờ), để còn phân biệt được nguồn nào khi fetch reject.
  const controller = new AbortController()
  let timedOut = false
  let timer: ReturnType<typeof setTimeout> | undefined
  const restartTimer = () => {
    clearTimeout(timer)
    timer = setTimeout(() => {
      timedOut = true
      controller.abort()
    }, timeoutMs)
  }
  restartTimer()
  const forwardAbort = () => controller.abort()
  if (signal?.aborted) controller.abort()
  else signal?.addEventListener('abort', forwardAbort, { once: true })

  try {
    const response = await fetch(path, { ...init, signal: controller.signal })
    return await read(response, { stillReceiving: restartTimer, signal: controller.signal })
  } catch (error) {
    // fetch chỉ reject khi bị huỷ, hết giờ, hoặc không tới được máy chủ; lỗi HTTP vẫn resolve.
    const kind = timedOut ? 'timeout' : signal?.aborted || isAbortError(error) ? 'aborted' : 'network'
    throw new ApiError({ kind })
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', forwardAbort)
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function parseJson(body: string, exactNumbers: boolean): unknown {
  try {
    return exactNumbers ? JSON.parse(body, keepNumberText) : JSON.parse(body)
  } catch {
    return undefined
  }
}

/**
 * Reviver giữ chữ số: số nào mà JS in lại khác chuỗi nguồn (bị làm tròn, mất 0 ở cuối, viết dạng mũ) thì trả chuỗi
 * nguồn. Số in lại y nguyên (mọi số đếm, số dòng, số trang) vẫn là number. Trình duyệt chưa có `context.source`
 * (JSON.parse source text access) thì giữ số đã làm tròn như trước.
 */
function keepNumberText(_key: string, value: unknown, context?: { source?: string }): unknown {
  if (typeof value !== 'number' || context?.source === undefined) return value
  return String(value) === context.source ? value : context.source
}
