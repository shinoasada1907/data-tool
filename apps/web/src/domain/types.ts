// Model nội bộ của UI; tách khỏi DTO của BE (design D5).

export type SourceFileType = 'CSV' | 'XLSX'

export interface SessionInfo {
  id: string
  fileName: string
  fileType: SourceFileType
  sizeBytes: number
}
