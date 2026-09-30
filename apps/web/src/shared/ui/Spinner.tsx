import styles from './Spinner.module.css'

/**
 * Chỉ báo đang tải, chỉ để nhìn. Vùng live phải nằm sẵn trong DOM trước khi đổi nội dung (design D14), nên
 * việc báo cho screen reader do vùng `role="status"` của bước đảm nhận, không phải component bị gắn/gỡ này.
 */
export function Spinner({ label }: { label: string }) {
  return (
    <div aria-hidden="true" className={styles.spinner}>
      <span className={styles.mark} aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

/** Chỉ ô vuông xoay, đặt cạnh chữ của một vùng `role="status"` có sẵn (ví dụ "Đang xử lý…" cạnh nút chạy). */
export function SpinnerMark() {
  return <span className={styles.mark} aria-hidden="true" />
}
