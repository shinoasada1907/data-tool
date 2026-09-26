import { useId } from 'react'
import styles from './ConfirmPanel.module.css'
import { AlertTriangleIcon } from './icons'

interface ConfirmPanelProps {
  message: string
  confirmLabel: string
  cancelLabel: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmPanel({ message, confirmLabel, cancelLabel, onConfirm, onCancel }: ConfirmPanelProps) {
  const messageId = useId()

  return (
    <div role="group" aria-labelledby={messageId} className={styles.panel}>
      <span className={styles.icon}>
        <AlertTriangleIcon />
      </span>
      <p id={messageId} className={styles.message}>
        {message}
      </p>
      <div className={styles.actions}>
        {/* Panel hiện ra do một thao tác của user: đưa focus tới nút xác nhận, và screen reader đọc
            nhãn nhóm (câu hỏi) khi focus đi vào. */}
        <button type="button" className={styles.confirm} onClick={onConfirm} autoFocus>
          {confirmLabel}
        </button>
        <button type="button" className={styles.cancel} onClick={onCancel}>
          {cancelLabel}
        </button>
      </div>
    </div>
  )
}
