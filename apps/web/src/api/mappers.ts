import { normalizeFieldName } from '../domain/schemaRules'
import type { MappingDraft, SessionInfo, SourcePreview, TargetField } from '../domain/types'
import type { ImportSessionDto, MappingConfigDto, SourcePreviewDto, TargetSchemaDto } from './dto'

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
