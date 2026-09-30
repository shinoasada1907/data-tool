import type { SourceFileType } from '../../domain/types'

export type FileRejection = 'multiple' | 'extension' | 'empty' | 'tooLarge'

export type FileCheck<T extends string = SourceFileType> =
  | { ok: true; file: File; fileType: T }
  | { ok: false; reason: FileRejection }

/** Đuôi file mà Importer nhận. Công cụ khác truyền bảng của mình (toolbox nhận thêm JSON). */
const IMPORT_TYPES: Readonly<Record<string, SourceFileType>> = { csv: 'CSV', xlsx: 'XLSX' }

/**
 * Kiểm file phía client, chỉ là lớp UX; BE vẫn kiểm lại bằng magic bytes (design D11).
 * Chỉ xét đuôi file: MIME không đáng tin (Windows có Excel gửi .csv là application/vnd.ms-excel).
 * Trả null khi không có file nào, ví dụ user đóng hộp chọn.
 */
export function checkSelectedFiles<T extends string = SourceFileType>(
  files: readonly File[],
  maxUploadMb: number,
  typesByExtension: Readonly<Record<string, T>> = IMPORT_TYPES as Readonly<Record<string, T>>,
): FileCheck<T> | null {
  if (files.length === 0) return null
  if (files.length > 1) return { ok: false, reason: 'multiple' }

  const file = files[0]
  const dot = file.name.lastIndexOf('.')
  const extension = dot === -1 ? '' : file.name.slice(dot + 1).toLowerCase()
  // hasOwn: đuôi như "constructor" không được lấy nhầm thuộc tính kế thừa của object.
  const fileType = Object.hasOwn(typesByExtension, extension) ? typesByExtension[extension] : undefined
  if (!fileType) return { ok: false, reason: 'extension' }
  if (file.size === 0) return { ok: false, reason: 'empty' }
  // Cùng cơ số 1024 với DataSize của Spring ("20MB") mà BE dùng.
  if (file.size > maxUploadMb * 1024 * 1024) return { ok: false, reason: 'tooLarge' }

  return { ok: true, file, fileType }
}
