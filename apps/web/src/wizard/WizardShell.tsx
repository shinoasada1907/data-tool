import { useEffect, useRef, type RefObject } from 'react'
import { MappingStep } from '../features/mapping/MappingStep'
import { PreviewStep } from '../features/preview/PreviewStep'
import { SchemaStep } from '../features/schema/SchemaStep'
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
  const cardRef = useRef<HTMLDivElement>(null)
  useBeforeUnload(state.session !== null)
  useFocusNewStep(state.step, cardRef)

  return (
    <div className={styles.wizard}>
      <Stepper
        label={messages.stepperLabel}
        doneLabel={messages.stepDone}
        items={stepperItems(state)}
        disabled={isBusy(state)}
        onSelect={(step) => dispatch({ type: 'navigate', step })}
      />
      <div ref={cardRef} className={styles.card}>
        {renderStep(state.step)}
      </div>
    </div>
  )
}

/**
 * Nút vừa bấm (Tiếp, Quay lại, Upload lại…) hoặc ô chọn file biến mất cùng bước cũ, và focus rơi về đầu trang.
 * Khi đó đưa focus tới tiêu đề của bước mới (design D14). Đổi bước bằng stepper thì nút stepper vẫn giữ focus,
 * nên không đụng tới.
 */
function useFocusNewStep(step: StepId, cardRef: RefObject<HTMLDivElement | null>) {
  const previousStep = useRef(step)

  useEffect(() => {
    // So với bước trước đó chứ không dùng cờ "lần đầu", vì StrictMode chạy effect hai lần lúc mount.
    if (previousStep.current === step) return
    previousStep.current = step
    if (document.activeElement && document.activeElement !== document.body) return
    cardRef.current?.querySelector<HTMLElement>('h2')?.focus()
  }, [step, cardRef])
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
    case 'preview':
      return <PreviewStep />
    case 'schema':
      return <SchemaStep />
    case 'mapping':
      return <MappingStep />
    default:
      return <StepPlaceholder step={step} />
  }
}

/** Chỗ giữ cho các bước chưa làm; mỗi feature thay bằng màn thật (task 4.3). */
function StepPlaceholder({ step }: { step: StepId }) {
  return (
    <section className={styles.placeholder}>
      <h2 tabIndex={-1}>{stepLabels[step]}</h2>
      <p className={styles.placeholderBox}>{messages.stepInProgress}</p>
    </section>
  )
}
