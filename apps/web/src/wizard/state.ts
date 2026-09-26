import type { SessionInfo, SourcePreview } from '../domain/types'

export const STEPS = ['upload', 'preview', 'schema', 'mapping', 'rules', 'result'] as const
export type StepId = (typeof STEPS)[number]

// Lát FE-F02. Các feature sau thêm schema, mapping, rules, result (design D2).
export interface WizardState {
  step: StepId
  /** Số request làm đổi state đang chạy. Lớn hơn 0 thì khoá điều hướng (xem isBusy). */
  pendingRequests: number
  session: SessionInfo | null
  /** Tải một lần cho mỗi session; null khi chưa tải xong (spec source-preview). */
  preview: SourcePreview | null
}

export const initialWizardState: WizardState = {
  step: 'upload',
  pendingRequests: 0,
  session: null,
  preview: null,
}

export type WizardAction =
  | { type: 'sessionCreated'; session: SessionInfo }
  /** `sessionId`: id FE đã dùng để gửi request (không phải field BE gửi lại). */
  | { type: 'previewLoaded'; sessionId: string; preview: SourcePreview }
  | { type: 'navigate'; step: StepId }
  | { type: 'requestStarted' }
  | { type: 'requestSettled' }
  | { type: 'reset' }

export function isBusy(state: WizardState): boolean {
  return state.pendingRequests > 0
}
