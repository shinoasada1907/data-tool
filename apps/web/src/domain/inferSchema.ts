import type { FieldType, SourcePreview, TargetField } from './types'

// Cùng luật với kiểm kiểu của BE-F07 (không trim giá trị), để kiểu đoán ra không tự sinh lỗi VALIDATION_TYPE.
const NUMBER = /^-?\d+(\.\d+)?$/
const BOOLEAN = /^(true|false|1|0)$/i
const BOOLEAN_WORD = /^(true|false)$/i
const DATE = /^(\d{4})-(\d{2})-(\d{2})$/
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

/**
 * Đoán kiểu của một cột từ các ô không rỗng của preview (spec target-schema, "Sinh schema từ cột nguồn").
 * Cột chỉ có `1`/`0` là `number`: hay gặp ở cột số lượng hơn cột đúng/sai. Không có ô nào có giá trị thì là `string`.
 */
export function inferFieldType(values: readonly (string | null)[]): FieldType {
  // Ô rỗng như BE hiểu: null, hoặc chỉ gồm khoảng trắng (kể cả NBSP).
  const filled = values.filter((value): value is string => value !== null && value.trim() !== '')
  if (filled.length === 0) return 'string'

  const all = (test: (value: string) => boolean) => filled.every(test)
  if (all((value) => BOOLEAN.test(value)) && filled.some((value) => BOOLEAN_WORD.test(value))) return 'boolean'
  if (all((value) => NUMBER.test(value))) return 'number'
  if (all(isCalendarDate)) return 'date'
  if (all((value) => EMAIL.test(value))) return 'email'
  return 'string'
}

/** `yyyy-MM-dd` và là ngày có thật (BE parse STRICT: `2024-02-30` là sai). */
function isCalendarDate(value: string): boolean {
  const match = DATE.exec(value)
  if (!match) return false
  const [year, month, day] = match.slice(1).map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  // setUTCFullYear để năm 0000–0099 không bị Date hiểu thành 1900–1999.
  date.setUTCFullYear(year)
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

/**
 * Mỗi cột nguồn thành một field cùng tên, theo đúng thứ tự `columns[]`, không bắt buộc. Key đánh tiếp từ `startSeq`,
 * để field sinh lại không dùng lại key của field cũ (design D3).
 */
export function fieldsFromPreview(
  preview: SourcePreview,
  startSeq: number,
): { fields: TargetField[]; nextFieldSeq: number } {
  const fields = preview.columns.map((name, index) => ({
    key: `f${startSeq + index}`,
    name,
    type: inferFieldType(preview.rows.map((row) => row.values[index] ?? null)),
    required: false,
  }))
  return { fields, nextFieldSeq: startSeq + fields.length }
}
