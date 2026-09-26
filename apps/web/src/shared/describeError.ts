import type { ApiError } from '../api/apiError'
import { errorCodeMessages, messages } from './messages'

export interface ErrorText {
  headline: string
  /** Dòng phụ: `detail` tiếng Anh của BE khi dòng chính đã là thông điệp của FE. */
  detail?: string
  code?: string
}

/**
 * Dòng chính theo thứ tự: thông điệp FE theo `code` → `detail` → `title` → thông điệp chung theo status
 * (spec import-wizard, "Hiển thị lỗi API nhất quán"). Trả null khi không có gì để báo (request bị huỷ).
 */
export function describeApiError(error: ApiError): ErrorText | null {
  if (error.kind === 'aborted') return null
  if (error.kind === 'network') return { headline: messages.network }
  if (error.kind === 'timeout') return { headline: messages.timeout }

  const code = error.code ?? undefined
  const known = code ? codeMessage(code) : undefined
  if (known) {
    return error.detail ? { headline: known, detail: error.detail, code } : { headline: known, code }
  }

  const headline = error.detail ?? error.title ?? statusMessage(error.status)
  return code ? { headline, code } : { headline }
}

/** Thông điệp FE của một `code` (lỗi API, lỗi theo dòng, readiness issue); mã lạ thì undefined. */
export function codeMessage(code: string): string | undefined {
  // hasOwn: code như "constructor" không được lấy nhầm thuộc tính kế thừa của object.
  return Object.hasOwn(errorCodeMessages, code) ? errorCodeMessages[code] : undefined
}

function statusMessage(status: number | null): string {
  if (status === null) return messages.network
  return status >= 500 ? messages.serverError(status) : messages.requestFailed(status)
}
