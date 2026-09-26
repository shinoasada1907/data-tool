import { messages } from '../shared/messages'
import type { FieldKey, TargetField } from './types'

/** Trùng luật của BE: tên sau khi trim dài 1–100 ký tự (đếm như `String.length` của Java). */
export const MAX_FIELD_NAME_LENGTH = 100

/**
 * Tên field như FE kiểm và gửi lên BE: chuẩn hoá NFC rồi trim. Chữ Việt gõ dạng tổ hợp (NFD) và dựng sẵn (NFC) nhìn
 * giống hệt nhau nhưng khác chuỗi; không chuẩn hoá thì file xuất ra có hai cột trùng tiêu đề.
 */
export function normalizeFieldName(name: string): string {
  return name.normalize('NFC').trim()
}

export interface SchemaCheck {
  /** Lỗi tên theo key của field; field không có lỗi thì không có mặt. */
  errors: Record<FieldKey, string>
  /** Lý do khoá nút "Tiếp"; null khi schema lưu được. */
  blockedReason: string | null
}

/**
 * Kiểm schema phía FE với cùng luật như BE (spec target-schema), để lỗi hiện ngay tại field thay vì chờ 422.
 * Tên trùng được so sau khi chuẩn hoá và không phân biệt hoa thường; mọi field mang tên trùng đều bị đánh dấu.
 * `toLowerCase` của JS lệch `equalsIgnoreCase` của Java ở vài chữ hiếm (ς/σ, İ/i); các ca đó BE vẫn chặn bằng 422.
 */
export function checkSchema(fields: TargetField[]): SchemaCheck {
  if (fields.length === 0) return { errors: {}, blockedReason: messages.schema.blocked.empty }

  const errors: Record<FieldKey, string> = {}
  const keysByName = new Map<string, FieldKey[]>()

  for (const field of fields) {
    const name = normalizeFieldName(field.name)
    if (name.length === 0) {
      errors[field.key] = messages.schema.nameErrors.blank
    } else if (name.length > MAX_FIELD_NAME_LENGTH) {
      errors[field.key] = messages.schema.nameErrors.tooLong(MAX_FIELD_NAME_LENGTH)
    } else {
      const normalized = name.toLowerCase()
      keysByName.set(normalized, [...(keysByName.get(normalized) ?? []), field.key])
    }
  }

  for (const keys of keysByName.values()) {
    if (keys.length > 1) for (const key of keys) errors[key] = messages.schema.nameErrors.duplicate
  }

  return { errors, blockedReason: blockedReason(fields, errors) }
}

function blockedReason(fields: TargetField[], errors: Record<FieldKey, string>): string | null {
  if (fields.some((field) => normalizeFieldName(field.name).length === 0)) return messages.schema.blocked.unnamed
  return Object.keys(errors).length > 0 ? messages.schema.blocked.invalid : null
}

export interface ServerErrors {
  /** Lỗi của BE gắn được vào một field (so tên đã trim). */
  byKey: Record<FieldKey, string>
  /** Lỗi không gắn được vào field nào: hiện ở đầu form. */
  general: string[]
}

/**
 * Chia `errors[]` của `422 SCHEMA_INVALID` theo field. BE trả lại đúng tên FE đã gửi, nên so với tên đã chuẩn hoá của
 * từng field; lỗi không có `field`, hoặc tên không khớp field nào, đưa lên đầu form (spec target-schema).
 */
export function matchServerErrors(
  fields: readonly TargetField[],
  items: readonly { field: string | null; message: string }[],
): ServerErrors {
  const byKey: Record<FieldKey, string> = {}
  const general: string[] = []

  for (const item of items) {
    const field =
      item.field === null ? undefined : fields.find((candidate) => normalizeFieldName(candidate.name) === item.field)
    if (field) {
      byKey[field.key] = byKey[field.key] ? `${byKey[field.key]} ${item.message}` : item.message
    } else {
      general.push(item.field === null ? item.message : `${item.field}: ${item.message}`)
    }
  }

  return { byKey, general }
}
