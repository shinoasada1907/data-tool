import { normalizeFieldName } from '../domain/schemaRules'
import {
  ISO_DATE_FORMAT,
  USER_RULES,
  type MappingDraft,
  type SessionInfo,
  type SourcePreview,
  type TargetField,
  type Transformation,
  type TransformationsDraft,
  type ValidationsDraft,
} from '../domain/types'
import type {
  ImportSessionDto,
  MappingConfigDto,
  SourcePreviewDto,
  TargetSchemaDto,
  TransformationConfigDto,
  ValidationConfigDto,
} from './dto'

// Chuyển DTO ↔ model nội bộ (design D5). Mỗi feature thêm phần của mình.

export function toSessionInfo(dto: ImportSessionDto): SessionInfo {
  return {
    id: dto.id,
    fileName: dto.originalFileName,
    fileType: dto.fileType,
    sizeBytes: dto.sizeBytes,
  }
}

/** Cột theo vị trí trong mảng, không qua object theo tên: key dạng số sẽ bị JS đưa lên đầu. */
export function toSourcePreview(dto: SourcePreviewDto): SourcePreview {
  return {
    sheetName: dto.sheetName,
    columns: dto.columns.map((column) => column.name),
    rows: dto.rows.map((row) => ({ rowNumber: row.rowNumber, values: row.values })),
    totalRows: dto.totalRows,
  }
}

/** Tên gửi lên đã chuẩn hoá NFC và trim; `order` là vị trí hiển thị, bắt đầu từ 0. Key nội bộ không rời khỏi FE (D3). */
export function toTargetSchemaDto(fields: TargetField[]): TargetSchemaDto {
  return {
    fields: fields.map((field, order) => ({
      name: normalizeFieldName(field.name),
      type: field.type,
      required: field.required,
      order,
    })),
  }
}

/**
 * Chỉ gồm field đã map, theo thứ tự schema; `targetField` là tên hiện tại của field (đã chuẩn hoá như khi PUT schema),
 * nên đổi tên field thì lần PUT mapping kế tiếp gửi tên mới (spec field-mapping).
 */
export function toMappingConfigDto(fields: readonly TargetField[], mapping: MappingDraft): MappingConfigDto {
  return {
    mappings: fields.flatMap((field): MappingConfigDto['mappings'] => {
      const current = mapping[field.key]
      if (!current) return []
      const targetField = normalizeFieldName(field.name)
      return current.kind === 'column'
        ? [{ targetField, mappingType: 'SOURCE_COLUMN', sourceColumn: current.column, constantValue: null }]
        : [{ targetField, mappingType: 'CONSTANT', sourceColumn: null, constantValue: current.value }]
    }),
  }
}

/**
 * Theo thứ tự schema rồi thứ tự bước; `order` bắt đầu từ 0 trong từng field. Chỉ `defaultValue` và `dateFormat` có
 * `params` (spec rule-config). Field kiểu `date` luôn xuất `yyyy-MM-dd` (design D19).
 */
export function toTransformationConfigDto(
  fields: readonly TargetField[],
  transformations: TransformationsDraft,
): TransformationConfigDto {
  return {
    transformations: fields.flatMap((field) =>
      (transformations[field.key] ?? []).map((step, order) => ({
        targetField: normalizeFieldName(field.name),
        order,
        type: step.type,
        ...paramsOf(step, field),
      })),
    ),
  }
}

function paramsOf(step: Transformation, field: TargetField): { params?: Record<string, string> } {
  switch (step.type) {
    case 'defaultValue':
      return { params: { value: step.value } }
    case 'dateFormat':
      return {
        params: {
          inputFormat: step.inputFormat,
          outputFormat: field.type === 'date' ? ISO_DATE_FORMAT : step.outputFormat,
        },
      }
    default:
      return {}
  }
}

/**
 * Chỉ rule do user bật, theo thứ tự schema rồi `email` trước `unique`, không có `params`. `required` và `type` không
 * bao giờ nằm ở đây vì BE suy ra từ schema (design D7). `email` ở field không phải `string` bị bỏ: với kiểu
 * `number`/`boolean`/`date` BE trả 422, với kiểu `email` BE bỏ qua kèm warning `RULE_IMPLIED_BY_SCHEMA`.
 */
export function toValidationConfigDto(
  fields: readonly TargetField[],
  validations: ValidationsDraft,
): ValidationConfigDto {
  return {
    validations: fields.flatMap((field) => {
      const enabled = validations[field.key] ?? []
      return USER_RULES.filter((rule) => enabled.includes(rule) && (rule !== 'email' || field.type === 'string')).map(
        (rule) => ({ targetField: normalizeFieldName(field.name), type: rule }),
      )
    }),
  }
}
