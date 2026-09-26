import type { SourceFileType } from '../../domain/types'

export type FileRejection = 'multiple' | 'extension' | 'empty' | 'tooLarge'

export type FileCheck =
  | { ok: true; file: File; fileType: SourceFileType }
  | { ok: false; reason: FileRejection }

const TYPE_BY_EXTENSION: Readonly<Record<string, SourceFileType>> = { csv: 'CSV', xlsx: 'XLSX' }

/**
 * Kiểm file phía client, chỉ là lớp UX; BE vẫn kiểm lại bằng magic bytes (design D11).
 * Chỉ xét đuôi file: MIME không đáng tin (Windows có Excel gửi .csv là application/vnd.ms-excel).
 * Trả null khi không có file nào, ví dụ user đóng hộp chọn.
 */
export function checkSelectedFiles(files: readonly File[], maxUploadMb: number): FileCheck | null {
  if (files.length === 0) return null
  if (files.length > 1) return { ok: false, reason: 'multiple' }

  const file = files[0]
  const dot = file.name.lastIndexOf('.')
  const fileType = dot === -1 ? undefined : TYPE_BY_EXTENSION[file.name.slice(dot + 1).toLowerCase()]
  if (!fileType) return { ok: false, reason: 'extension' }
  if (file.size === 0) return { ok: false, reason: 'empty' }
  // Cùng cơ số 1024 với DataSize của Spring ("20MB") mà BE dùng.
  if (file.size > maxUploadMb * 1024 * 1024) return { ok: false, reason: 'tooLarge' }

  return { ok: true, file, fileType }
}
