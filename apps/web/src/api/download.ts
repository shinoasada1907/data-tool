import { errorFromHttpResponse } from './apiError'
import { fetchWithin } from './client'
import { fileNameFromContentDisposition } from './contentDisposition'

/**
 * Thời gian im lặng tối đa khi tải file: chưa có header, hoặc giữa hai lần nhận dữ liệu. Không giới hạn tổng thời gian:
 * BE stream file và không tự cắt (be-f10 bỏ F10-D8, vì giới hạn tổng làm file lớn bị cắt trên mạng chậm). Nhờ nhận biết
 * im lặng, lượt tải cũng kết thúc khi BE cắt kết nối giữa chừng mà proxy của Vite không chuyển lỗi về (review FE-F10).
 */
export const DOWNLOAD_IDLE_TIMEOUT_MS = 30_000

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
export async function download(
  path: string,
  { signal, method = 'GET', body }: { signal?: AbortSignal; method?: 'GET' | 'POST'; body?: unknown } = {},
): Promise<Download> {
  // Export của toolbox là POST với body JSON chọn nội dung và định dạng (design V9).
  const init: RequestInit =
    body === undefined
      ? { method }
      : { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
  const result = await fetchWithin(path, init, { signal, timeoutMs: DOWNLOAD_IDLE_TIMEOUT_MS }, async (response, progress) =>
    response.ok
      ? {
          ok: true as const,
          blob: await readBody(response, progress),
          disposition: response.headers.get('Content-Disposition'),
        }
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

/**
 * Đọc body theo từng chunk, báo `stillReceiving` sau mỗi chunk để thời gian im lặng tính lại từ đầu. Bị huỷ (hết giờ,
 * rời bước) thì luồng có thể kết thúc êm như đã đọc xong; khi đó phải ném lỗi, không bao giờ trả một file bị cắt cụt.
 */
async function readBody(
  response: Response,
  { stillReceiving, signal }: { stillReceiving: () => void; signal: AbortSignal },
): Promise<Blob> {
  const type = response.headers.get('Content-Type') ?? ''
  if (!response.body) return new Blob([], { type })
  const reader = response.body.getReader()
  const cancel = () => void reader.cancel()
  signal.addEventListener('abort', cancel, { once: true })
  try {
    const parts: Uint8Array[] = []
    for (;;) {
      const { done, value } = await reader.read()
      if (signal.aborted) throw new DOMException('Download aborted', 'AbortError')
      if (done) return new Blob(parts as BlobPart[], { type })
      parts.push(value)
      stillReceiving()
    }
  } finally {
    signal.removeEventListener('abort', cancel)
  }
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
