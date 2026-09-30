import type { RefObject } from 'react'
import { messages } from '../shared/messages'
import { ErrorBanner } from '../shared/ui/ErrorBanner'
import { useWizard } from './context'
import type { LoadFailure } from './loadFailure'

interface LoadFailureBannerProps {
  failure: LoadFailure
  onRetry: () => void
  /** Nhận focus khi bấm "Thử lại" (thường là tiêu đề bước). */
  retryFocusRef: RefObject<HTMLElement | null>
}

/**
 * Khối lỗi của request đọc, với "Thử lại" hoặc "Upload lại". Dùng chung cho các bước (review FE-F08/F09): nút "Thử lại"
 * biến mất cùng khối lỗi, nên focus được đưa tới `retryFocusRef` trước, thay vì rơi về đầu trang (design D14).
 */
export function LoadFailureBanner({ failure, onRetry, retryFocusRef }: LoadFailureBannerProps) {
  const { dispatch } = useWizard()
  const action =
    failure.action === 'retry'
      ? {
          label: messages.retry,
          onClick: () => {
            retryFocusRef.current?.focus()
            onRetry()
          },
        }
      : failure.action === 'reupload'
        ? { label: messages.sessionUnusableAction, onClick: () => dispatch({ type: 'reset' }) }
        : undefined

  return <ErrorBanner text={failure.text} action={action} />
}
