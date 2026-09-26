import { messages } from '../shared/messages'
import type { StepId, WizardState } from './state'

export type Guard = { allowed: true } | { allowed: false; reason: string }

const ALLOWED: Guard = { allowed: true }

function locked(reason: string): Guard {
  return { allowed: false, reason }
}

/** Bước đã hoàn tất, để stepper hiện "đã xong". Lát FE-F02 mới có Upload và Xem trước. */
export function isStepDone(step: StepId, state: WizardState): boolean {
  switch (step) {
    case 'upload':
      return state.session !== null
    case 'preview':
      return state.preview !== null
    default:
      return false
  }
}

/**
 * Điều kiện vào từng bước (spec import-wizard, "Chặn tiến tới bước khi thiếu phụ thuộc").
 * Lát FE-F02 mới có session và preview; các bước sau được mở dần theo từng feature.
 */
export function canEnter(step: StepId, state: WizardState): Guard {
  switch (step) {
    case 'upload':
      return ALLOWED
    case 'preview':
      return state.session ? ALLOWED : locked(messages.guard.needUpload)
    case 'schema':
      return state.preview ? ALLOWED : locked(messages.guard.needPreview)
    case 'mapping':
      return locked(messages.guard.needSchema)
    case 'rules':
      return locked(messages.guard.needMapping)
    case 'result':
      return locked(messages.guard.needResult)
  }
}
