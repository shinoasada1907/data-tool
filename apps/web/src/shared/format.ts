const BYTE_UNITS = ['B', 'KB', 'MB', 'GB'] as const

/** Dung lượng dễ đọc theo vi-VN, cơ số 1024 (giống cách BE tính MB): `1,2 MB`. */
export function formatBytes(bytes: number): string {
  let value = bytes
  let unit = 0
  const digits = () => (unit === 0 ? 0 : 1)
  const rounded = () => Number(value.toFixed(digits()))

  // So trên giá trị đã làm tròn, để 1023,99 KB thành "1 MB" chứ không ra "1.024 KB".
  while (rounded() >= 1024 && unit < BYTE_UNITS.length - 1) {
    value /= 1024
    unit += 1
  }

  const number = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: digits() }).format(value)
  return `${number} ${BYTE_UNITS[unit]}`
}
