import { ApiError, errorFromHttpResponse } from './apiError'

export interface RequestOptions<T> {
  /** Kiểm tối thiểu body 2xx theo contract; sai dạng thì báo INVALID_RESPONSE thay vì chạy tiếp với dữ liệu hỏng. */
  validate: (value: unknown) => value is T
  signal?: AbortSignal
}

/**
 * Gọi endpoint JSON bằng fetch (design D6). Lỗi HTTP, lỗi mạng, huỷ và response 2xx sai dạng đều reject
 * bằng ApiError. Không tự retry.
 */
export async function request<T>(path: string, { validate, signal }: RequestOptions<T>): Promise<T> {
  let response: Response
  let body: string
  try {
    response = await fetch(path, {
      headers: { Accept: 'application/json, application/problem+json' },
      signal,
    })
    body = await response.text()
  } catch (error) {
    // fetch chỉ reject khi bị huỷ hoặc không tới được máy chủ; lỗi HTTP vẫn resolve.
    throw new ApiError({ kind: signal?.aborted || isAbortError(error) ? 'aborted' : 'network' })
  }

  if (!response.ok) {
    throw errorFromHttpResponse(response.status, response.headers.get('Content-Type'), body)
  }

  const value = parseJson(body)
  if (value === undefined || !validate(value)) {
    throw new ApiError({ kind: 'http', status: response.status, code: 'INVALID_RESPONSE' })
  }
  return value
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function parseJson(body: string): unknown {
  try {
    return JSON.parse(body)
  } catch {
    return undefined
  }
}
