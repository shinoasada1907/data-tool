import type { Ref } from 'react'
import styles from './StepHeader.module.css'

interface StepHeaderProps {
  id: string
  title: string
  intro?: string
  /** Tiêu đề nhận focus có chủ đích (lưu lỗi mà không gắn được vào field nào; đổi bước), nên có `tabIndex={-1}`. */
  headingRef?: Ref<HTMLHeadingElement>
}

/** Tiêu đề h2 và dòng giới thiệu của một bước, dùng chung cho các bước cấu hình (review FE-F06/F07). */
export function StepHeader({ id, title, intro, headingRef }: StepHeaderProps) {
  return (
    <div className={styles.header}>
      <h2 id={id} ref={headingRef} className={styles.title} tabIndex={-1}>
        {title}
      </h2>
      {intro && <p className={styles.intro}>{intro}</p>}
    </div>
  )
}
