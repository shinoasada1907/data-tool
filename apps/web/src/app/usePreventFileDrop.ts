import { useEffect } from 'react'

/**
 * Thả file ra ngoài vùng upload thì trình duyệt mặc định mở hoặc tải file, tức là rời khỏi app.
 * Chặn ở window; vùng upload tự xử lý sự kiện của nó trước (React bắt ở root, bên dưới window).
 */
export function usePreventFileDrop() {
  useEffect(() => {
    const prevent = (event: DragEvent) => {
      if (event.defaultPrevented || !event.dataTransfer?.types.includes('Files')) return
      event.preventDefault()
      event.dataTransfer.dropEffect = 'none'
    }

    window.addEventListener('dragover', prevent)
    window.addEventListener('drop', prevent)
    return () => {
      window.removeEventListener('dragover', prevent)
      window.removeEventListener('drop', prevent)
    }
  }, [])
}
