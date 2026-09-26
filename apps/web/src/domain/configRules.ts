import { messages } from '../shared/messages'
import { normalizeFieldName } from './schemaRules'
import type { FieldKey, MappingDraft, TargetField } from './types'

export interface ConfigIssue {
  /** `warning` chỉ nhắc, không chặn; `error` khoá nút "Tiếp". */
  level: 'error' | 'warning'
  message: string
}

export interface MappingCheck {
  /** Vấn đề theo key của field; field ổn thì không có mặt. */
  issues: Record<FieldKey, ConfigIssue>
  /** Lý do khoá nút "Tiếp", liệt kê tên các field gây lỗi; null khi lưu được. */
  blockedReason: string | null
}

/**
 * Kiểm mapping theo spec field-mapping: field bắt buộc chưa map và giá trị cố định rỗng là lỗi; field không bắt buộc
 * chưa map chỉ cảnh báo. Một cột nguồn dùng cho nhiều field là hợp lệ.
 */
export function checkMapping(fields: readonly TargetField[], mapping: MappingDraft): MappingCheck {
  const issues: Record<FieldKey, ConfigIssue> = {}
  const unmappedRequired: string[] = []
  const blankConstants: string[] = []

  for (const field of fields) {
    const current = mapping[field.key]
    if (!current) {
      if (field.required) {
        issues[field.key] = { level: 'error', message: messages.mapping.requiredUnmapped }
        unmappedRequired.push(normalizeFieldName(field.name))
      } else {
        issues[field.key] = { level: 'warning', message: messages.mapping.optionalUnmapped }
      }
    } else if (current.kind === 'constant' && isBlankLikeJava(current.value)) {
      issues[field.key] = { level: 'error', message: messages.mapping.blankConstant }
      blankConstants.push(normalizeFieldName(field.name))
    }
  }

  const reasons = [
    unmappedRequired.length > 0 ? messages.mapping.blockedRequired(unmappedRequired) : null,
    blankConstants.length > 0 ? messages.mapping.blockedConstant(blankConstants) : null,
  ].filter((reason) => reason !== null)

  return { issues, blockedReason: reasons.length > 0 ? reasons.join('; ') : null }
}

/**
 * `String.isBlank()` của Java, luật BE dùng cho giá trị cố định: chỉ gồm ký tự mà `Character.isWhitespace` nhận.
 * Khác `trim()` của JS ở hai chỗ: NBSP (U+00A0, U+2007, U+202F) không phải khoảng trắng, còn U+001C–U+001F thì có.
 * Dùng đúng luật này để FE chặn đúng những gì BE sẽ từ chối, không hơn không kém (review FE-F05).
 */
function isBlankLikeJava(value: string): boolean {
  // Cố ý khớp ký tự điều khiển: U+001C–U+001F là khoảng trắng theo Java.
  // oxlint-disable-next-line no-control-regex
  return /^[\t\n\u000B\f\r\u001C-\u001F \u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]*$/.test(value)
}
