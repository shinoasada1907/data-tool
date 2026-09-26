import { UploadStep } from '../features/upload/UploadStep'
import { messages, stepLabels } from '../shared/messages'
import { Stepper, type StepperItem } from '../shared/ui/Stepper'
import { useWizard } from './context'
import { canEnter, isStepDone } from './guards'
import { isBusy, STEPS, type StepId, type WizardState } from './state'
import { useBeforeUnload } from './useBeforeUnload'
import styles from './WizardShell.module.css'

/** Nội dung của công cụ Import: stepper và bước hiện tại. Header và sidebar thuộc AppShell. */
export function WizardShell() {
  const { state, dispatch } = useWizard()
  useBeforeUnload(state.session !== null)

  return (
    <div className={styles.wizard}>
      <Stepper
        label={messages.stepperLabel}
        doneLabel={messages.stepDone}
        items={stepperItems(state)}
        disabled={isBusy(state)}
        onSelect={(step) => dispatch({ type: 'navigate', step })}
      />
      <div className={styles.card}>{renderStep(state.step)}</div>
    </div>
  )
}

function stepperItems(state: WizardState): StepperItem<StepId>[] {
  return STEPS.map((id) => {
    const label = stepLabels[id]
    if (id === state.step) return { id, label, state: 'current' }

    const guard = canEnter(id, state)
    if (!guard.allowed) return { id, label, state: 'locked', reason: guard.reason }
    return { id, label, state: isStepDone(id, state) ? 'done' : 'available' }
  })
}

function renderStep(step: StepId) {
  switch (step) {
    case 'upload':
      return <UploadStep />
    default:
      return <StepPlaceholder step={step} />
  }
}

/** Chỗ giữ cho các bước chưa làm; mỗi feature thay bằng màn thật (task 4.3). */
function StepPlaceholder({ step }: { step: StepId }) {
  return (
    <section className={styles.placeholder}>
      <h2>{stepLabels[step]}</h2>
      <p className={styles.placeholderBox}>{messages.stepInProgress}</p>
    </section>
  )
}
