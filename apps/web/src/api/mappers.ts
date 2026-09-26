import type { SessionInfo, SourcePreview } from '../domain/types'
import type { ImportSessionDto, SourcePreviewDto } from './dto'

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
