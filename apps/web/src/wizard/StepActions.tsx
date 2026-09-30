import { useId } from 'react'
import { messages } from '../shared/messages'
import { ArrowRightIcon } from '../shared/ui/icons'
import { SpinnerMark } from '../shared/ui/Spinner'
import { useWizard } from './context'
import { isBusy } from './state'
import styles from './StepActions.module.css'

interface StepActionsProps {
  onBack?: () => void
  onNext?: () => void
  /** Nhãn nút chính; mặc định "Tiếp" (bước Biến đổi & kiểm tra dùng "Chạy xử lý"). */
  nextLabel?: string
  /** Lý do nút chính bị khoá. Luôn hiện cạnh nút và là mô tả của nút cho screen reader. */
  nextBlockedReason?: string
  /**
   * Việc đang chạy sau khi bấm nút chính (ví dụ "Đang xử lý…"), hiện trong vùng status cạnh nút. Bước có việc chạy
   * dài truyền `null` khi đang rảnh, để vùng status có sẵn trong DOM trước khi đổi nội dung (design D14); bước không
   * truyền gì thì không có vùng này.
   */
  progressLabel?: string | null
}

/** Nút "Quay lại" và "Tiếp" ở chân mỗi bước; bị khoá khi wizard đang bận (spec import-wizard). */
export function StepActions({
  onBack,
  onNext,
  nextLabel = messages.nav.next,
  nextBlockedReason,
  progressLabel,
}: StepActionsProps) {
  const { state } = useWizard()
  const busy = isBusy(state)
  const reasonId = useId()

  return (
    <div className={styles.actions}>
      {onBack && (
        <button type="button" className={styles.back} disabled={busy} onClick={onBack}>
          {messages.nav.back}
        </button>
      )}
      {onNext && (
        <div className={styles.nextGroup}>
          {progressLabel !== undefined && (
            <p role="status" className={styles.progress}>
              {progressLabel && (
                <>
                  <SpinnerMark />
                  {progressLabel}
                </>
              )}
            </p>
          )}
          {nextBlockedReason && (
            <p id={reasonId} className={styles.reason}>
              {nextBlockedReason}
            </p>
          )}
          <button
            type="button"
            className={styles.next}
            disabled={busy || nextBlockedReason !== undefined}
            aria-describedby={nextBlockedReason ? reasonId : undefined}
            onClick={onNext}
          >
            {nextLabel}
            <ArrowRightIcon size={16} />
          </button>
        </div>
      )}
    </div>
  )
}
