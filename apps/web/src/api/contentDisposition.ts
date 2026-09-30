/**
 * Tên file từ header `Content-Disposition` (design D10): ưu tiên `filename*` (RFC 5987; BE luôn gửi dạng này), sau đó
 * `filename`. Không có tên dùng được thì trả null để bên gọi dùng tên dự phòng. Ký tự đường dẫn (`/`, `\`) và ký tự
 * điều khiển luôn được thay bằng `_`, như BE làm khi đặt tên (be-f10 F10-D6).
 */
export function fileNameFromContentDisposition(header: string | null): string | null {
  if (!header) return null
  const params = parseParams(header)
  const extended = params.get('filename*')
  // `filename*` rỗng hay hỏng thì rơi về `filename`.
  const name = (extended === undefined ? null : decodeExtValue(extended)) || params.get('filename') || null
  if (name === null) return null
  // Ký tự đường dẫn, điều khiển (Cc) và định dạng vô hình (Cf, ví dụ U+202E đảo chiều chữ), như BE (be-f10 F10-D6).
  const safe = name.replace(/[/\\\p{Cc}\p{Cf}]/gu, '_')
  return safe.trim() === '' ? null : safe
}

/** Tham số của header (tên viết thường → giá trị đã bỏ ngoặc kép và ký tự thoát). Tham số lặp lại thì lấy cái đầu. */
function parseParams(header: string): Map<string, string> {
  const params = new Map<string, string>()
  const pattern = /;\s*([^\s=;]+)\s*=\s*("(?:[^"\\]|\\.)*"|[^;]*)/g
  for (const match of header.matchAll(pattern)) {
    const name = match[1].toLowerCase()
    const raw = match[2].trim()
    // Chỉ bỏ ngoặc kép khi có cả hai đầu; thiếu ngoặc đóng thì giữ nguyên phần sau ngoặc mở, không cắt mất ký tự cuối.
    const quoted = raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')
    const value = quoted ? raw.slice(1, -1).replace(/\\(.)/g, '$1') : raw.replace(/^"/, '')
    if (!params.has(name)) params.set(name, value)
  }
  return params
}

/** `charset'lang'percent-encoded` (RFC 5987). Chỉ nhận UTF-8 và ISO-8859-1; hỏng thì null. */
function decodeExtValue(value: string): string | null {
  const match = /^([^']*)'[^']*'(.*)$/.exec(value)
  if (!match) return null
  const charset = match[1].toLowerCase()
  const encoded = match[2]
  try {
    if (charset === 'utf-8') return decodeURIComponent(encoded)
    if (charset === 'iso-8859-1') {
      return encoded.replace(/%([0-9a-f]{2})/gi, (_, hex: string) => String.fromCharCode(parseInt(hex, 16)))
    }
  } catch {
    return null
  }
  return null
}
