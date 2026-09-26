import type { ErrorText } from '../describeError'
import { messages } from '../messages'
import styles from './ErrorBanner.module.css'
import { AlertCircleIcon } from './icons'

interface ErrorBannerProps {
  text: ErrorText
  action?: { label: string; onClick: () => void }
}

/** Thành phần báo lỗi dùng chung cho mọi lỗi API và lỗi kiểm tra phía client (design D14). */
export function ErrorBanner({ text, action }: ErrorBannerProps) {
  return (
    <div role="alert" className={styles.banner}>
      <span className={styles.icon}>
        <AlertCircleIcon />
      </span>
      <div className={styles.body}>
        <p className={styles.headline}>{text.headline}</p>
        {text.detail && <p className={styles.detail}>{text.detail}</p>}
        {text.code && (
          <p className={styles.code}>
            <span>{messages.errorCodeLabel}</span> <code className={styles.codeChip}>{text.code}</code>
          </p>
        )}
        {action && (
          <button type="button" className={styles.action} onClick={action.onClick}>
            {action.label}
          </button>
        )}
      </div>
    </div>
  )
}
