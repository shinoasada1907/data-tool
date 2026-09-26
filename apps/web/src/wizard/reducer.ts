import { canEnter } from './guards'
import { initialWizardState, isBusy, type WizardAction, type WizardState } from './state'

export function wizardReducer(state: WizardState, action: WizardAction): WizardState {
  switch (action.type) {
    case 'sessionCreated':
      // Session mới thay nguyên khối state của session cũ (design D13). Bộ đếm request giữ nguyên:
      // request đang chạy vẫn sẽ báo kết thúc sau đó.
      return { ...initialWizardState, pendingRequests: state.pendingRequests, session: action.session, step: 'preview' }
    case 'previewLoaded':
      // Response về muộn của session cũ không được ghi đè lên session hiện tại.
      if (action.sessionId !== state.session?.id) return state
      return { ...state, preview: action.preview }
    case 'navigate':
      if (isBusy(state) || !canEnter(action.step, state).allowed) return state
      return { ...state, step: action.step }
    case 'requestStarted':
      return { ...state, pendingRequests: state.pendingRequests + 1 }
    case 'requestSettled':
      return { ...state, pendingRequests: Math.max(0, state.pendingRequests - 1) }
    case 'reset':
      return { ...initialWizardState, pendingRequests: state.pendingRequests }
  }
}
