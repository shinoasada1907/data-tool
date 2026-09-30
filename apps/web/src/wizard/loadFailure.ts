import { ApiError, isRetryable, isSessionUnusable } from '../api/apiError'
import { describeApiError, type ErrorText } from '../shared/describeError'
import { messages } from '../shared/messages'

/** Lỗi của một request đọc (GET preview, GET result); hiển thị bằng `LoadFailureBanner`. */
export interface LoadFailure {
  text: ErrorText
  /** Lỗi mạng/5xx thì thử lại được; session hết hạn hoặc hỏng thì chỉ còn cách upload lại (design D12). */
  action: 'retry' | 'reupload' | null
}

export function toLoadFailure(error: unknown): LoadFailure {
  if (!(error instanceof ApiError)) {
    // Lỗi lập trình (mapper, reducer…): user chỉ thấy câu chung, nên stack phải nằm ở console.
    console.error(error)
    return { text: { headline: messages.unexpected }, action: null }
  }
  const text = describeApiError(error) ?? { headline: messages.unexpected }
  if (isSessionUnusable(error)) return { text, action: 'reupload' }
  return { text, action: isRetryable(error) ? 'retry' : null }
}
