import { useEffect, useRef } from 'react'
import { messages } from '../messages'
import styles from './Pagination.module.css'

interface PaginationProps {
  /** Trang đang xem, đếm từ 0. */
  page: number
  totalPages: number
  disabled?: boolean
  onChange: (page: number) => void
}

/** Nút "Trước"/"Sau" và nhãn "Trang x / y" (spec result-review). */
export function Pagination({ page, totalPages, disabled = false, onChange }: PaginationProps) {
  const previousRef = useRef<HTMLButtonElement>(null)
  const nextRef = useRef<HTMLButtonElement>(null)
  // Lần bấm gần nhất và trang đích của nó: chỉ dời focus khi đã tới đúng trang đó (tải lỗi thì trang không đổi).
  const pending = useRef<{ target: number; from: 'previous' | 'next' } | null>(null)

  useEffect(() => {
    const moved = pending.current
    if (!moved || moved.target !== page) return
    pending.current = null
    const clicked = moved.from === 'next' ? nextRef.current : previousRef.current
    const other = moved.from === 'next' ? previousRef.current : nextRef.current
    // Nút vừa bấm bị khoá ở trang đầu/cuối: focus sang nút chiều ngược lại (design D14). Chỉ khi focus vẫn ở nút đó
    // hoặc đã rơi về đầu trang: trang tới nơi sau "Thử lại" thì focus đang ở tiêu đề bước, không giật đi.
    const active = document.activeElement
    const focusLost = active === null || active === document.body || active === clicked
    if (clicked?.disabled && focusLost) other?.focus()
  }, [page, totalPages])

  function go(from: 'previous' | 'next') {
    const target = from === 'next' ? page + 1 : page - 1
    pending.current = { target, from }
    onChange(target)
  }

  return (
    <nav aria-label={messages.pagination.label} className={styles.pagination}>
      <button
        ref={previousRef}
        type="button"
        className={styles.button}
        disabled={disabled || page <= 0}
        onClick={() => go('previous')}
      >
        {messages.pagination.previous}
      </button>
      <span className={styles.label}>{messages.pagination.page(page + 1, totalPages)}</span>
      <button
        ref={nextRef}
        type="button"
        className={styles.button}
        disabled={disabled || page + 1 >= totalPages}
        onClick={() => go('next')}
      >
        {messages.pagination.next}
      </button>
    </nav>
  )
}
