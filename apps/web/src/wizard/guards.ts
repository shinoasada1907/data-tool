import { messages } from '../shared/messages'
import type { StepId, WizardState } from './state'

export type Guard = { allowed: true } | { allowed: false; reason: string }

const ALLOWED: Guard = { allowed: true }

function locked(reason: string): Guard {
  return { allowed: false, reason }
}

/** Bước đã hoàn tất, để stepper hiện "đã xong". Bước Kết quả không bao giờ "xong": export là việc tuỳ chọn. */
export function isStepDone(step: StepId, state: WizardState): boolean {
  switch (step) {
    case 'upload':
      return state.session !== null
    case 'preview':
      return state.preview !== null
    case 'schema':
      return hasSavedSchema(state)
    case 'mapping':
      return hasSavedMapping(state)
    case 'rules':
      return state.result !== null && state.result.stale === null
    default:
      return false
  }
}

/** Điều kiện vào từng bước (spec import-wizard, "Chặn tiến tới bước khi thiếu phụ thuộc"). */
export function canEnter(step: StepId, state: WizardState): Guard {
  switch (step) {
    case 'upload':
      return ALLOWED
    case 'preview':
      return state.session ? ALLOWED : locked(messages.guard.needUpload)
    case 'schema':
      return state.preview ? ALLOWED : locked(messages.guard.needPreview)
    case 'mapping':
      return hasSavedSchema(state) ? ALLOWED : locked(messages.guard.needSchema)
    case 'rules':
      return hasSavedMapping(state) ? ALLOWED : locked(messages.guard.needMapping)
    case 'result': {
      // Điều kiện của bước trước đi trước, để lý do khoá chỉ đúng việc cần làm trước tiên.
      const rules = canEnter('rules', state)
      if (!rules.allowed) return rules
      return state.result ? ALLOWED : locked(messages.guard.needResult)
    }
  }
}

function hasSavedSchema(state: WizardState): boolean {
  return state.schema.saved && state.schema.draft.length > 0
}

/** Điều kiện của Mapping, và mapping đã lưu (spec import-wizard). */
function hasSavedMapping(state: WizardState): boolean {
  return hasSavedSchema(state) && state.mapping.saved
}
