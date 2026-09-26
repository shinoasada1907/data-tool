import type { FieldKey, FieldType, SessionInfo, SourcePreview, TargetField } from '../domain/types'

export const STEPS = ['upload', 'preview', 'schema', 'mapping', 'rules', 'result'] as const
export type StepId = (typeof STEPS)[number]

/** Một phần cấu hình: bản đang sửa, và bản đó đã được PUT thành công và chưa sửa gì từ đó hay chưa (design D2). */
export interface Section<T> {
  draft: T
  saved: boolean
}

// Lát FE-F04. Các feature sau thêm mapping, rules, result (design D2).
export interface WizardState {
  step: StepId
  /** Số request làm đổi state đang chạy. Lớn hơn 0 thì khoá điều hướng (xem isBusy). */
  pendingRequests: number
  /** Số thứ tự cho key của field kế tiếp (`f1`, `f2`, …); không bao giờ dùng lại key (design D3). */
  nextFieldSeq: number
  session: SessionInfo | null
  /** Tải một lần cho mỗi session; null khi chưa tải xong (spec source-preview). */
  preview: SourcePreview | null
  schema: Section<TargetField[]>
}

export const initialWizardState: WizardState = {
  step: 'upload',
  pendingRequests: 0,
  nextFieldSeq: 1,
  session: null,
  preview: null,
  schema: { draft: [], saved: false },
}

export type SchemaEdit =
  | { kind: 'add' }
  /** Thay toàn bộ field bằng field sinh từ cột nguồn của preview ("Tạo lại từ file"). */
  | { kind: 'regenerate' }
  | { kind: 'update'; key: FieldKey; patch: Partial<{ name: string; type: FieldType; required: boolean }> }
  | { kind: 'remove'; key: FieldKey }
  | { kind: 'move'; key: FieldKey; offset: -1 | 1 }

export type WizardAction =
  | { type: 'sessionCreated'; session: SessionInfo }
  /** `sessionId`: id FE đã dùng để gửi request (không phải field BE gửi lại). */
  | { type: 'previewLoaded'; sessionId: string; preview: SourcePreview }
  | { type: 'schemaEdited'; edit: SchemaEdit }
  /** `draft`: đúng bản đã gửi đi; nếu user đã sửa tiếp trong lúc chờ thì bản hiện tại vẫn là chưa lưu. */
  | { type: 'sectionSaved'; section: 'schema'; draft: TargetField[] }
  | { type: 'navigate'; step: StepId }
  | { type: 'requestStarted' }
  | { type: 'requestSettled' }
  | { type: 'reset' }

export function isBusy(state: WizardState): boolean {
  return state.pendingRequests > 0
}
