import { messages } from '../shared/messages'
import type { StepId, WizardState } from './state'

export type Guard = { allowed: true } | { allowed: false; reason: string }

const ALLOWED: Guard = { allowed: true }

function locked(reason: string): Guard {
  return { allowed: false, reason }
}

/** Bước đã hoàn tất, để stepper hiện "đã xong". Lát FE-F01 mới có bước Upload. */
export function isStepDone(step: StepId, state: WizardState): boolean {
  return step === 'upload' && state.session !== null
}

/**
 * Điều kiện vào từng bước (spec import-wizard, "Chặn tiến tới bước khi thiếu phụ thuộc").
 * Lát FE-F01 mới có session; các bước sau được mở dần theo từng feature.
 */
export function canEnter(step: StepId, state: WizardState): Guard {
  switch (step) {
    case 'upload':
      return ALLOWED
    case 'preview':
      return state.session ? ALLOWED : locked(messages.guard.needUpload)
    case 'schema':
      return locked(messages.guard.needPreview)
    case 'mapping':
      return locked(messages.guard.needSchema)
    case 'rules':
      return locked(messages.guard.needMapping)
    case 'result':
      return locked(messages.guard.needResult)
  }
}
