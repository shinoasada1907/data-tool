import { messages } from '../shared/messages'
import { normalizeFieldName } from './schemaRules'
import type {
  FieldKey,
  MappingDraft,
  TargetField,
  TransformationId,
  TransformationsDraft,
  UserRule,
} from './types'

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

// BE có hai luật "rỗng" (review FE-F06/F07), FE dùng đúng luật của từng chỗ để chặn đúng những gì BE sẽ từ chối:
// - giá trị cố định ở mapping: `String.isBlank()` (`MappingConfig`), chỉ ký tự `Character.isWhitespace` nhận;
// - tham số transformation: `TextValues.isEmpty`, thêm cả NBSP (U+00A0, U+2007, U+202F).
// Khác `trim()` của JS: U+001C–U+001F là khoảng trắng theo Java, còn NBSP thì tuỳ luật.

/** `String.isBlank()` của Java: luật BE dùng cho giá trị cố định ở mapping. */
function isBlankLikeJava(value: string): boolean {
  // Cố ý khớp ký tự điều khiển: U+001C–U+001F là khoảng trắng theo Java.
  // oxlint-disable-next-line no-control-regex
  return /^[\t\n\u000B\f\r\u001C-\u001F \u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]*$/.test(value)
}

/** `TextValues.isEmpty` của BE: `isBlank()` cộng NBSP. Luật BE dùng cho tham số transformation. */
function isEmptyLikeBe(value: string): boolean {
  return isBlankLikeJava(value.replace(/[\u00A0\u2007\u202F]/g, ''))
}

/** Lỗi theo từng ô tham số của một bước biến đổi. */
export type TransformationIssue = Partial<Record<'value' | 'inputFormat' | 'outputFormat', string>>

export interface TransformationsCheck {
  issues: Record<TransformationId, TransformationIssue>
  /** Lý do khoá "Chạy xử lý", liệt kê tên field có bước thiếu tham số; null khi chạy được. */
  blockedReason: string | null
}

/**
 * Tham số bắt buộc của transformation (spec rule-config): `defaultValue` cần `value`, `dateFormat` cần cả hai định dạng.
 * "Rỗng" theo luật BE-F06 dùng cho tham số (`TextValues.isEmpty`), khác luật của giá trị cố định ở mapping.
 */
export function checkTransformations(
  fields: readonly TargetField[],
  transformations: TransformationsDraft,
): TransformationsCheck {
  const issues: Record<TransformationId, TransformationIssue> = {}
  const blockedFields: string[] = []

  for (const field of fields) {
    let fieldBlocked = false
    for (const step of transformations[field.key] ?? []) {
      const issue: TransformationIssue = {}
      if (step.type === 'defaultValue' && isEmptyLikeBe(step.value)) issue.value = messages.rules.missingValue
      if (step.type === 'dateFormat') {
        if (isEmptyLikeBe(step.inputFormat)) issue.inputFormat = messages.rules.missingInputFormat
        if (isEmptyLikeBe(step.outputFormat)) issue.outputFormat = messages.rules.missingOutputFormat
      }
      if (Object.keys(issue).length > 0) {
        issues[step.id] = issue
        fieldBlocked = true
      }
    }
    if (fieldBlocked) blockedFields.push(normalizeFieldName(field.name))
  }

  return {
    issues,
    blockedReason: blockedFields.length > 0 ? messages.rules.blockedMissingParams(blockedFields) : null,
  }
}

/** Rule BE tự áp theo schema, hiện dạng chỉ đọc (design D7): `required` khi field bắt buộc, và `type:<kiểu>`. */
export function impliedRules(field: TargetField): string[] {
  return [...(field.required ? ['required'] : []), `type:${field.type}`]
}

/** `email` chỉ có nghĩa ở field kiểu `string` (field kiểu `email` đã kiểm sẵn); `unique` dùng được ở mọi kiểu. */
export function canUseRule(field: TargetField, rule: UserRule): boolean {
  return rule === 'unique' || field.type === 'string'
}
