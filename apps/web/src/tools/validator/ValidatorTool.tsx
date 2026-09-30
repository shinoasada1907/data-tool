import { useEffect, useMemo, useReducer, useRef } from 'react'
import { Stepper, type StepperItem } from '../../shared/ui/Stepper'
import { useBeforeUnload } from '../../wizard/useBeforeUnload'
import { ValidatorContext } from './context'
import { stepLabels, validatorMessages as text } from './messages'
import { ResultStep } from './ResultStep'
import { SchemaStep } from './SchemaStep'
import { SourceStep } from './SourceStep'
import { initialValidatorState, lockReason, STEPS, validatorReducer, type StepId, type ValidatorState } from './state'
import styles from './Validator.module.css'

/** Công cụ "Kiểm tra dữ liệu" (spec data-validator): Dữ liệu → Schema → Kết quả. */
export function ValidatorTool() {
  const [state, dispatch] = useReducer(validatorReducer, initialValidatorState)
  const value = useMemo(() => ({ state, dispatch }), [state])
  const cardRef = useRef<HTMLDivElement>(null)
  useBeforeUnload(state.dataset !== null)
  useFocusNewStep(state.step, cardRef)

  return (
    <ValidatorContext value={value}>
      <div className={styles.tool}>
        <Stepper
          label={text.stepperLabel}
          doneLabel={text.stepDone}
          items={stepperItems(state)}
          disabled={state.running}
          onSelect={(step) => dispatch({ type: 'navigate', step })}
        />
        <div ref={cardRef} className={styles.card}>
          {state.step === 'source' && <SourceStep />}
          {state.step === 'schema' && <SchemaStep />}
          {state.step === 'result' && <ResultStep />}
        </div>
      </div>
    </ValidatorContext>
  )
}

function stepperItems(state: ValidatorState): StepperItem<StepId>[] {
  return STEPS.map((id) => {
    const label = stepLabels[id]
    if (id === state.step) return { id, label, state: 'current' }
    const reason = lockReason(id, state)
    if (reason) return { id, label, state: 'locked', reason: text.guard[reason] }
    const done = id === 'source' ? state.preview !== null : id === 'schema' ? state.result !== null : false
    return { id, label, state: done ? 'done' : 'available' }
  })
}

/** Nút vừa bấm biến mất cùng bước cũ thì đưa focus tới tiêu đề bước mới (như wizard Import, design D14). */
function useFocusNewStep(step: StepId, cardRef: React.RefObject<HTMLDivElement | null>) {
  const previous = useRef(step)
  useEffect(() => {
    if (previous.current === step) return
    previous.current = step
    if (document.activeElement && document.activeElement !== document.body) return
    cardRef.current?.querySelector<HTMLElement>('h2')?.focus()
  }, [step, cardRef])
}
