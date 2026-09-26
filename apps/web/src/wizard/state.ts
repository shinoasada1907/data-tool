import type { SessionInfo } from '../domain/types'

export const STEPS = ['upload', 'preview', 'schema', 'mapping', 'rules', 'result'] as const
export type StepId = (typeof STEPS)[number]

// Lát FE-F01. Các feature sau thêm preview, schema, mapping, rules, result (design D2).
export interface WizardState {
  step: StepId
  /** Số request làm đổi state đang chạy. Lớn hơn 0 thì khoá điều hướng (xem isBusy). */
  pendingRequests: number
  session: SessionInfo | null
}

export const initialWizardState: WizardState = {
  step: 'upload',
  pendingRequests: 0,
  session: null,
}

export type WizardAction =
  | { type: 'sessionCreated'; session: SessionInfo }
  | { type: 'navigate'; step: StepId }
  | { type: 'requestStarted' }
  | { type: 'requestSettled' }
  | { type: 'reset' }

export function isBusy(state: WizardState): boolean {
  return state.pendingRequests > 0
}
