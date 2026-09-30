import { isBlankLikeJava } from '../../domain/configRules'

export type ExportSuffix = '-valid.json' | '-valid.csv' | '-errors.csv'

/**
 * Tên file khi response không có `Content-Disposition` (spec result-export), theo đúng luật đặt tên của BE
 * (`ExportFileName`, be-f10 F10-D6): bỏ đuôi cuối cùng của tên file gốc; thay `"`, `\`, `/`, ký tự điều khiển (`Cc`) và
 * ký tự định dạng vô hình (`Cf`, ví dụ U+202E đảo chiều chữ) bằng `_`; phần tên trống (`isBlank()` của Java) thì dùng
 * `export`; rồi nối hậu tố.
 */
export function fallbackFileName(originalFileName: string, suffix: ExportSuffix): string {
  const dot = originalFileName.lastIndexOf('.')
  const base = dot === -1 ? originalFileName : originalFileName.slice(0, dot)
  const safe = base.replace(/["\\/\p{Cc}\p{Cf}]/gu, '_')
  return `${isBlankLikeJava(safe) ? 'export' : safe}${suffix}`
}
