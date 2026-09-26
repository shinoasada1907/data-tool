import { onTestFinished, vi } from 'vitest'

export interface SavedFile {
  fileName: string
  blob: Blob
}

/**
 * Ghi lại các file mà app "lưu về máy" qua `saveBlob`: jsdom không có `URL.createObjectURL` và không tải file thật.
 * Mọi thứ được trả lại như cũ khi test kết thúc.
 */
export function captureDownloads(): SavedFile[] {
  const saved: SavedFile[] = []
  const blobs = new Map<string, Blob>()
  // Bộ đếm tăng dần: dùng `blobs.size` thì URL mới có thể trùng URL còn sống sau một lần revoke.
  let created = 0
  const original = { create: URL.createObjectURL, revoke: URL.revokeObjectURL }
  URL.createObjectURL = (blob: Blob | MediaSource) => {
    created += 1
    const url = `blob:test/${created}`
    blobs.set(url, blob as Blob)
    return url
  }
  URL.revokeObjectURL = (url: string) => {
    blobs.delete(url)
  }
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    const blob = blobs.get(this.href)
    if (blob && this.download) saved.push({ fileName: this.download, blob })
  })
  onTestFinished(() => {
    URL.createObjectURL = original.create
    URL.revokeObjectURL = original.revoke
    click.mockRestore()
  })
  return saved
}
