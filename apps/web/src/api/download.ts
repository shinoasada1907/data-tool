import { errorFromHttpResponse } from './apiError'
import { fetchWithin } from './client'
import { fileNameFromContentDisposition } from './contentDisposition'

/** BE stream file export và cho tối đa 5 phút (be-f10 F10-D8); FE chờ bằng đó thay vì 30 giây mặc định. */
export const DOWNLOAD_TIMEOUT_MS = 300_000

/** Firefox huỷ lượt tải nếu object URL bị thu hồi ngay trong lúc click, nên thu hồi sau một nhịp. */
const REVOKE_DELAY_MS = 1_000

export interface Download {
  blob: Blob
  /** Từ `Content-Disposition`; null thì bên gọi dùng tên dự phòng. */
  fileName: string | null
}

/**
 * Tải file bằng `fetch` + blob (design D10): kiểm `response.ok` trước; lỗi thì đọc ProblemDetail và ném `ApiError`,
 * không bao giờ trả body lỗi như một file. Kết nối đứt khi BE đang stream (BE không đổi được status nữa) là lỗi mạng.
 */
export async function download(path: string, { signal }: { signal?: AbortSignal } = {}): Promise<Download> {
  const result = await fetchWithin(path, {}, { signal, timeoutMs: DOWNLOAD_TIMEOUT_MS }, async (response) =>
    response.ok
      ? { ok: true as const, blob: await response.blob(), disposition: response.headers.get('Content-Disposition') }
      : {
          ok: false as const,
          status: response.status,
          contentType: response.headers.get('Content-Type'),
          text: await response.text(),
        },
  )
  if (!result.ok) throw errorFromHttpResponse(result.status, result.contentType, result.text)
  return { blob: result.blob, fileName: fileNameFromContentDisposition(result.disposition) }
}

/** Lưu blob về máy: object URL, bấm một thẻ `<a download>` tạm, rồi thu hồi URL. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.hidden = true
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), REVOKE_DELAY_MS)
}
