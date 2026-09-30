/**
 * Sau khi bấm "Lên"/"Xuống" trong một danh sách sắp xếp được: giữ focus ở nút vừa bấm của cùng dòng; tới biên thì nút
 * đó bị khoá, nên chuyển sang nút chiều ngược lại (design D14). Nút được đánh dấu `data-action="up"` / `"down"`.
 */
export function focusMoveButton(row: Element | null | undefined, direction: 'up' | 'down') {
  if (!row) return
  const same = row.querySelector<HTMLButtonElement>(`[data-action="${direction}"]`)
  const other = row.querySelector<HTMLButtonElement>(`[data-action="${direction === 'up' ? 'down' : 'up'}"]`)
  ;(same && !same.disabled ? same : other)?.focus()
}

/** Control đầu tiên còn bấm/nhập được trong một dòng (bỏ qua control đang khoá). */
export function focusFirstControl(row: Element | null | undefined): boolean {
  const control = row?.querySelector<HTMLElement>(
    'input:not([disabled]):not([readonly]), select:not([disabled]), button:not([disabled])',
  )
  control?.focus()
  return Boolean(control)
}
