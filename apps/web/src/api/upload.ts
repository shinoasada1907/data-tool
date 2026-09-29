import { ApiError, errorFromHttpResponse } from './apiError'
import type { ImportSessionDto } from './dto'

const UPLOAD_URL = '/api/import-sessions'

export interface UploadOptions {
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

/** Upload file nguồn, tạo import session. */
export function uploadSourceFile(file: File, options: UploadOptions = {}): Promise<ImportSessionDto> {
  return uploadFile(UPLOAD_URL, file, parseSession, options)
}

/**
 * Gửi một file dạng multipart (part `file`) tới `url`. Dùng XHR thay vì fetch vì fetch không báo được tiến độ upload
 * (design D6). Lỗi HTTP, lỗi mạng, huỷ và response 2xx mà `parse` trả null đều reject bằng ApiError.
 */
export function uploadFile<T>(
  url: string,
  file: File,
  parse: (body: string) => T | null,
  { onProgress, signal }: UploadOptions = {},
): Promise<T> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new ApiError({ kind: 'aborted' }))
      return
    }

    const xhr = new XMLHttpRequest()
    const abort = () => xhr.abort()

    xhr.open('POST', url)
    xhr.setRequestHeader('Accept', 'application/json, application/problem+json')

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress?.(Math.round((event.loaded / event.total) * 100))
      }
    }
    xhr.onload = () => {
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(errorFromHttpResponse(xhr.status, xhr.getResponseHeader('Content-Type'), xhr.responseText))
        return
      }
      const parsed = parse(xhr.responseText)
      if (parsed !== null) {
        resolve(parsed)
      } else {
        reject(new ApiError({ kind: 'http', status: xhr.status, code: 'INVALID_RESPONSE' }))
      }
    }
    xhr.onerror = () => reject(new ApiError({ kind: 'network' }))
    xhr.onabort = () => reject(new ApiError({ kind: 'aborted' }))
    xhr.onloadend = () => signal?.removeEventListener('abort', abort)

    signal?.addEventListener('abort', abort, { once: true })

    const form = new FormData()
    form.append('file', file)
    xhr.send(form)
  })
}

/** Kiểm tối thiểu những field FE dùng ngay; body lệch contract thì báo lỗi thay vì chạy tiếp với id rỗng. */
function parseSession(body: string): ImportSessionDto | null {
  let value: unknown
  try {
    value = JSON.parse(body)
  } catch {
    return null
  }
  if (typeof value !== 'object' || value === null) return null

  const dto = value as Partial<ImportSessionDto>
  const valid =
    typeof dto.id === 'string' &&
    typeof dto.originalFileName === 'string' &&
    (dto.fileType === 'CSV' || dto.fileType === 'XLSX') &&
    typeof dto.sizeBytes === 'number'
  return valid ? (value as ImportSessionDto) : null
}
