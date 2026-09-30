import styles from './EmptyState.module.css'

/** Trạng thái rỗng: khung nét đứt, một dòng chính và một dòng gợi ý tuỳ chọn. */
export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className={styles.empty}>
      <p className={styles.title}>{title}</p>
      {hint && <p className={styles.hint}>{hint}</p>}
    </div>
  )
}
