import { useId } from 'react'
import { messages } from '../shared/messages'
import { ArrowRightIcon } from '../shared/ui/icons'
import { useWizard } from './context'
import { isBusy } from './state'
import styles from './StepActions.module.css'

interface StepActionsProps {
  onBack?: () => void
  onNext?: () => void
  /** Lý do nút "Tiếp" bị khoá. Luôn hiện cạnh nút và là mô tả của nút cho screen reader. */
  nextBlockedReason?: string
}

/** Nút "Quay lại" và "Tiếp" ở chân mỗi bước; bị khoá khi wizard đang bận (spec import-wizard). */
export function StepActions({ onBack, onNext, nextBlockedReason }: StepActionsProps) {
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
            {messages.nav.next}
            <ArrowRightIcon size={16} />
          </button>
        </div>
      )}
    </div>
  )
}
