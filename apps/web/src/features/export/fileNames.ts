export type ExportSuffix = '-valid.json' | '-valid.csv' | '-errors.csv'

/**
 * Tên file khi response không có `Content-Disposition` (spec result-export), theo đúng luật đặt tên của BE (be-f10
 * F10-D6): bỏ đuôi cuối cùng của tên file gốc, thay `"`, `\`, `/` và ký tự điều khiển bằng `_`, phần tên rỗng thì dùng
 * `export`, rồi nối hậu tố.
 */
export function fallbackFileName(originalFileName: string, suffix: ExportSuffix): string {
  const dot = originalFileName.lastIndexOf('.')
  const base = dot === -1 ? originalFileName : originalFileName.slice(0, dot)
  // eslint-disable-next-line no-control-regex -- chủ đích: loại ký tự điều khiển khỏi tên file.
  const safe = base.replace(/["\\/\u0000-\u001f\u007f]/g, '_')
  return `${safe === '' ? 'export' : safe}${suffix}`
}
