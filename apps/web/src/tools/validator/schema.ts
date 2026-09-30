import type { ProblemItemDto } from '../../api/dto'
import { FIELD_TYPES, type FieldKey, type FieldType } from '../../domain/types'
import { codeMessage } from '../../shared/describeError'
import type { DatasetColumn } from '../../shared/dataset/datasetApi'
import { validatorMessages } from './messages'
import type { SchemaConstraintsDto, SchemaDto, SchemaFieldDto } from './validatorApi'

const text = validatorMessages.schema.errors

export const CONSTRAINT_KEYS = ['min', 'max', 'minLength', 'maxLength', 'pattern', 'format'] as const
export type ConstraintKey = (typeof CONSTRAINT_KEYS)[number]

/** Ràng buộc có ô nhập theo từng kiểu (luật TD9 của BE). */
export const CONSTRAINTS_BY_TYPE: Readonly<Record<FieldType, readonly ConstraintKey[]>> = {
  string: ['minLength', 'maxLength', 'pattern'],
  email: ['minLength', 'maxLength', 'pattern'],
  number: ['min', 'max'],
  date: ['format'],
  boolean: [],
}

/** Field đang soạn. Ràng buộc giữ nguyên chữ đang gõ; đổi kiểu không xoá, chỉ ẩn (design V4). */
export interface FieldDraft {
  key: FieldKey
  name: string
  type: FieldType
  required: boolean
  unique: boolean
  constraints: Record<ConstraintKey, string>
  /** Chỉ là metadata: giữ qua mở/lưu file, không có ô nhập vì Validator không dùng (design V7). */
  defaultValue: string | null
}

export interface SchemaDraft {
  name: string
  fields: FieldDraft[]
}

export type FieldInput = 'name' | 'type' | ConstraintKey

export interface SchemaProblems {
  name: string | null
  fields: Readonly<Record<FieldKey, Partial<Record<FieldInput, string>>>>
  /** Lỗi không gắn được vào ô nào. */
  general: string[]
}

const MAX_SCHEMA_NAME = 200
const MAX_FIELD_NAME = 100
const MAX_LENGTH = 32_767
const MAX_PATTERN = 500
const NUMBER = /^-?\d+(\.\d+)?$/
const LENGTH = /^\d+$/

export function newField(key: FieldKey): FieldDraft {
  return {
    key,
    name: '',
    type: 'string',
    required: false,
    unique: false,
    constraints: { min: '', max: '', minLength: '', maxLength: '', pattern: '', format: '' },
    defaultValue: null,
  }
}

/** Mỗi cột một field, kiểu theo `inferredType` của BE (`empty` thành string), không bắt buộc (design V4). */
export function fieldsFromColumns(columns: readonly DatasetColumn[], nextSeq: number) {
  const fields = columns.map((column, index) => ({
    ...newField(`f${nextSeq + index}`),
    name: column.name,
    type: column.inferredType === 'empty' ? ('string' as const) : column.inferredType,
  }))
  return { fields, nextSeq: nextSeq + columns.length }
}

const normalize = (name: string) => name.trim().toLowerCase()

/** Kiểm phía FE, báo ngay khi gõ (design V5). RE2, format ngày và defaultValue để BE kiểm lúc chạy. */
export function checkSchema(draft: SchemaDraft): SchemaProblems {
  const fields: Record<FieldKey, Partial<Record<FieldInput, string>>> = {}
  const add = (key: FieldKey, input: FieldInput, message: string) => {
    fields[key] = { ...fields[key], [input]: fields[key]?.[input] ?? message }
  }

  const counts = new Map<string, number>()
  for (const field of draft.fields) {
    const name = normalize(field.name)
    if (name) counts.set(name, (counts.get(name) ?? 0) + 1)
  }

  for (const field of draft.fields) {
    const name = field.name.trim()
    if (!name) add(field.key, 'name', text.fieldBlank)
    else if (name.length > MAX_FIELD_NAME) add(field.key, 'name', text.fieldTooLong)
    else if ((counts.get(normalize(name)) ?? 0) > 1) add(field.key, 'name', text.fieldDuplicate)

    const { min, max, minLength, maxLength, pattern } = trimmedConstraints(field)
    if (field.type === 'number') {
      if (min && !NUMBER.test(min)) add(field.key, 'min', text.notNumber)
      if (max && !NUMBER.test(max)) add(field.key, 'max', text.notNumber)
      if (NUMBER.test(min) && NUMBER.test(max) && Number(min) > Number(max)) add(field.key, 'max', text.maxBelowMin)
    }
    if (field.type === 'string' || field.type === 'email') {
      const validLength = (value: string) => LENGTH.test(value) && Number(value) <= MAX_LENGTH
      if (minLength && !validLength(minLength)) add(field.key, 'minLength', text.notLength)
      if (maxLength && !validLength(maxLength)) add(field.key, 'maxLength', text.notLength)
      if (validLength(minLength) && validLength(maxLength) && Number(minLength) > Number(maxLength)) {
        add(field.key, 'maxLength', text.maxLengthBelowMin)
      }
      if (pattern.length > MAX_PATTERN) add(field.key, 'pattern', text.patternTooLong)
    }
  }

  const name = draft.name.trim()
  return {
    name: !name ? text.nameBlank : name.length > MAX_SCHEMA_NAME ? text.nameTooLong : null,
    fields,
    general: draft.fields.length === 0 ? [text.noFields] : [],
  }
}

export function hasProblems(problems: SchemaProblems): boolean {
  return problems.name !== null || problems.general.length > 0 || Object.keys(problems.fields).length > 0
}

/** Gộp lỗi FE với lỗi BE trả về: ô nào đã có lỗi FE thì giữ lỗi FE. */
export function mergeProblems(local: SchemaProblems, server: SchemaProblems | null): SchemaProblems {
  if (!server) return local
  const fields: Record<FieldKey, Partial<Record<FieldInput, string>>> = { ...server.fields }
  for (const [key, inputs] of Object.entries(local.fields)) fields[key] = { ...fields[key], ...inputs }
  return { name: local.name ?? server.name, fields, general: [...local.general, ...server.general] }
}

function trimmedConstraints(field: FieldDraft): Record<ConstraintKey, string> {
  const result = { ...field.constraints }
  for (const key of CONSTRAINT_KEYS) result[key] = result[key].trim()
  return result
}

/** Body `schema` gửi BE: tên đã trim, chỉ ràng buộc hợp kiểu và không rỗng; `unique` chỉ khi bật (design V4). */
export function toSchemaDto(draft: SchemaDraft): SchemaDto {
  return { name: draft.name.trim(), fields: draft.fields.map(toFieldDto) }
}

function toFieldDto(field: FieldDraft): SchemaFieldDto {
  const values = trimmedConstraints(field)
  const constraints: SchemaConstraintsDto = {}
  if (field.unique) constraints.unique = true
  for (const key of CONSTRAINTS_BY_TYPE[field.type]) {
    const value = values[key]
    if (!value) continue
    if (key === 'pattern' || key === 'format') constraints[key] = value
    else constraints[key] = Number(value)
  }
  if (field.defaultValue !== null && field.defaultValue !== '') constraints.defaultValue = field.defaultValue
  const dto: SchemaFieldDto = { name: field.name.trim(), type: field.type, required: field.required }
  return Object.keys(constraints).length > 0 ? { ...dto, constraints } : dto
}

const FIELD_POINTER = /^\/schema\/fields\/(\d+)\/(name|type|constraints\/(min|max|minLength|maxLength|pattern|format))$/

/**
 * Lỗi `SCHEMA_INVALID` của BE theo `pointer` (tính từ gốc body): gắn vào đúng ô của field thứ i trong bản đã gửi;
 * không gắn được thì vào danh sách chung (design V5).
 */
export function problemsFromPointers(draft: SchemaDraft, items: readonly ProblemItemDto[]): SchemaProblems {
  const fields: Record<FieldKey, Partial<Record<FieldInput, string>>> = {}
  const general: string[] = []
  let name: string | null = null

  for (const item of items) {
    const message = describeItem(item)
    const match = item.pointer ? FIELD_POINTER.exec(item.pointer) : null
    const field = match ? draft.fields[Number(match[1])] : undefined
    if (match && field) {
      const input = (match[3] ?? match[2]) as FieldInput
      fields[field.key] = { ...fields[field.key], [input]: fields[field.key]?.[input] ?? message }
    } else if (item.pointer === '/schema/name') {
      name = name ?? message
    } else {
      general.push(message)
    }
  }
  return { name, fields, general }
}

/** Thông điệp tiếng Việt theo mã, kèm message của BE (có chi tiết như tên field, giới hạn…). */
export function describeItem(item: Pick<ProblemItemDto, 'code' | 'message'>): string {
  const known = codeMessage(item.code)
  return known ? `${known}: ${item.message}` : item.message
}

/**
 * Cột khớp với từng field, theo đúng luật BE (design V6): tên giống hệt trước, rồi cột đầu tiên còn trống giống khi
 * trim + không phân biệt hoa thường; mỗi cột một field.
 */
export function matchColumns(draft: SchemaDraft, columns: readonly string[]) {
  const byField: Record<FieldKey, string | null> = {}
  const used = new Set<number>()
  for (const field of draft.fields) {
    const index = columns.findIndex((column, i) => !used.has(i) && column === field.name)
    if (index !== -1) {
      used.add(index)
      byField[field.key] = columns[index]
    }
  }
  for (const field of draft.fields) {
    if (field.key in byField) continue
    const wanted = normalize(field.name)
    const index = wanted ? columns.findIndex((column, i) => !used.has(i) && normalize(column) === wanted) : -1
    if (index !== -1) used.add(index)
    byField[field.key] = index === -1 ? null : columns[index]
  }
  return { byField, extraColumns: columns.filter((_, i) => !used.has(i)) }
}

export const SCHEMA_FILE_FORMAT = 'udt.schema'
export const SCHEMA_FILE_VERSION = 1

/** Nội dung file schema (design V7): đúng `SchemaDto` kèm định dạng và phiên bản. */
export function schemaFileContent(draft: SchemaDraft): string {
  return `${JSON.stringify({ format: SCHEMA_FILE_FORMAT, version: SCHEMA_FILE_VERSION, ...toSchemaDto(draft) }, null, 2)}\n`
}

export function schemaFileName(name: string): string {
  const safe = name.trim().replace(/[\\/:*?"<>|]/g, '_')
  return `${safe || 'schema'}.schema.json`
}

export type SchemaFileRead = { ok: true; name: string; fields: FieldDraft[]; nextSeq: number } | { ok: false }

/** Đọc file schema; sai cấu trúc thì `{ ok: false }` và schema đang có giữ nguyên. Key field cấp mới từ `nextSeq`. */
export function readSchemaFile(content: string, nextSeq: number): SchemaFileRead {
  let value: unknown
  try {
    value = JSON.parse(content)
  } catch {
    return { ok: false }
  }
  if (
    !isRecord(value) ||
    value.format !== SCHEMA_FILE_FORMAT ||
    value.version !== SCHEMA_FILE_VERSION ||
    typeof value.name !== 'string' ||
    !Array.isArray(value.fields)
  ) {
    return { ok: false }
  }

  const fields: FieldDraft[] = []
  for (const [index, raw] of value.fields.entries()) {
    const field = readField(raw, `f${nextSeq + index}`)
    if (!field) return { ok: false }
    fields.push(field)
  }
  return { ok: true, name: value.name, fields, nextSeq: nextSeq + fields.length }
}

function readField(raw: unknown, key: FieldKey): FieldDraft | null {
  if (!isRecord(raw) || typeof raw.name !== 'string' || !FIELD_TYPES.includes(raw.type as FieldType)) return null
  if (raw.required !== undefined && typeof raw.required !== 'boolean') return null
  const constraints = raw.constraints ?? {}
  if (!isRecord(constraints)) return null

  const field: FieldDraft = { ...newField(key), name: raw.name, type: raw.type as FieldType, required: raw.required === true }
  field.unique = constraints.unique === true
  for (const constraint of CONSTRAINT_KEYS) {
    const current = constraints[constraint]
    if (typeof current === 'number' || typeof current === 'string') field.constraints[constraint] = String(current)
  }
  if (typeof constraints.defaultValue === 'string') field.defaultValue = constraints.defaultValue
  return field
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
