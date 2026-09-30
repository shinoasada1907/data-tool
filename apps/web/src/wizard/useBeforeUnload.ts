import { useEffect } from 'react'

/** Hỏi xác nhận trước khi tải lại hoặc rời trang, vì V0.1 không khôi phục được state (design D12). */
export function useBeforeUnload(active: boolean) {
  useEffect(() => {
    if (!active) return

    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      // Chrome/Edge < 119 chỉ hiện hộp xác nhận khi returnValue khác chuỗi rỗng.
      event.returnValue = true
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [active])
}
