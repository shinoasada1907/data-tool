import type { SessionInfo } from '../domain/types'
import type { ImportSessionDto } from './dto'

// Chuyển DTO ↔ model nội bộ (design D5). Mỗi feature thêm phần của mình.

export function toSessionInfo(dto: ImportSessionDto): SessionInfo {
  return {
    id: dto.id,
    fileName: dto.originalFileName,
    fileType: dto.fileType,
    sizeBytes: dto.sizeBytes,
  }
}
