import { useState } from 'react'
import { CheckIcon } from './icons'
import styles from './Stepper.module.css'

export interface StepperItem<Id extends string> {
  id: Id
  label: string
  state: 'current' | 'done' | 'available' | 'locked'
  /** Lý do bị khoá, hiện ra khi user bấm vào bước đó. */
  reason?: string
}

interface StepperProps<Id extends string> {
  label: string
  /** Chữ đọc thêm cho screen reader ở bước đã xong, ví dụ "(đã xong)". */
  doneLabel: string
  items: readonly StepperItem<Id>[]
  /** Khoá toàn bộ, ví dụ khi đang có request chạy. */
  disabled: boolean
  onSelect: (id: Id) => void
}

export function Stepper<Id extends string>({ label, doneLabel, items, disabled, onSelect }: StepperProps<Id>) {
  const current = items.find((item) => item.state === 'current')?.id
  const [notice, setNotice] = useState<string | null>(null)
  const [noticeStep, setNoticeStep] = useState(current)

  // Lý do khoá chỉ có nghĩa ở bước đang đứng lúc bấm; đổi bước thì xoá luôn, để không hiện lại khi quay về.
  if (noticeStep !== current) {
    setNoticeStep(current)
    setNotice(null)
  }

  return (
    <nav aria-label={label} className={styles.stepper} data-disabled={disabled || undefined}>
      <ol className={styles.list}>
        {items.map((item, index) => (
          <li key={item.id} className={styles.cell} data-state={item.state}>
            <button
              type="button"
              className={styles.step}
              disabled={disabled}
              aria-current={item.state === 'current' ? 'step' : undefined}
              aria-disabled={item.state === 'locked' ? true : undefined}
              onClick={() => {
                if (item.state === 'locked') {
                  setNotice(item.reason ?? null)
                  return
                }
                setNotice(null)
                if (item.state !== 'current') onSelect(item.id)
              }}
            >
              <span className={styles.number}>
                {item.state === 'done' ? (
                  <>
                    <CheckIcon size={26} strokeWidth={3} />
                    <span className="sr-only">{index + 1}</span>
                  </>
                ) : (
                  index + 1
                )}
              </span>{' '}
              <span className={styles.label}>{item.label}</span>
              {item.state === 'done' && (
                <>
                  {' '}
                  <span className="sr-only">{doneLabel}</span>
                </>
              )}
            </button>
          </li>
        ))}
      </ol>
      {/* Luôn có trong DOM để screen reader bắt được khi lý do khoá xuất hiện. */}
      <p role="status" className={styles.notice}>
        {notice}
      </p>
    </nav>
  )
}
