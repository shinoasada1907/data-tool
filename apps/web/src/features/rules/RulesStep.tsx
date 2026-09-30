import { useCallback, useId, useMemo, useRef, type ReactNode } from 'react'
import { checkTransformations, type TransformationIssue } from '../../domain/configRules'
import { normalizeFieldName } from '../../domain/schemaRules'
import type { FieldKey, Transformation, TransformationId, UserRule } from '../../domain/types'
import { messages, stepLabels } from '../../shared/messages'
import { useWizard } from '../../wizard/context'
import { isBusy, type TransformationEdit } from '../../wizard/state'
import { StepActions } from '../../wizard/StepActions'
import { SaveFailureBanner } from '../../wizard/SaveFailureBanner'
import { StepHeader } from '../../wizard/StepHeader'
import { useRunPipeline } from '../run/useRunPipeline'
import styles from './RulesStep.module.css'
import { TransformationEditor } from './TransformationEditor'
import { ValidationEditor } from './ValidationEditor'

/** Mẫu ngày gợi ý cho cả hai ô định dạng (spec rule-config); vẫn cho nhập tự do. */
const DATE_PATTERNS = ['yyyy-MM-dd', 'dd/MM/yyyy', 'MM/dd/yyyy', 'dd-MM-yyyy', 'yyyy/MM/dd', 'dd.MM.yyyy']
// Tham chiếu cố định cho field chưa có gì, để editor `memo` không render lại vì một mảng/đối tượng rỗng mới.
const NO_STEPS: readonly Transformation[] = []
const NO_RULES: readonly UserRule[] = []
const NO_ISSUES: Readonly<Record<TransformationId, TransformationIssue>> = {}

/** Bước 5: biến đổi và kiểm tra theo từng field, theo thứ tự schema (spec rule-config). */
export function RulesStep() {
  const { state, dispatch } = useWizard()
  const fields = state.schema.draft
  const transformations = state.transformations.draft
  const check = useMemo(() => checkTransformations(fields, transformations), [fields, transformations])
  // Vấn đề tách theo field, giữ tham chiếu ổn định cho field không đổi.
  const issuesByField = useMemo(() => {
    const byField: Record<FieldKey, Record<TransformationId, TransformationIssue>> = {}
    for (const field of fields) {
      for (const step of transformations[field.key] ?? NO_STEPS) {
        const issue = check.issues[step.id]
        if (issue) (byField[field.key] ??= {})[step.id] = issue
      }
    }
    return byField
  }, [fields, transformations, check])
  const saving = isBusy(state)
  const titleId = useId()
  const titleRef = useRef<HTMLHeadingElement>(null)
  const editorRef = useRef<HTMLFieldSetElement>(null)
  const datePatternsId = useId()
  const { run, running, blockedReason, serverErrors, failure, clearOnEdit } = useRunPipeline({
    headingRef: titleRef,
    focusField: (key) => {
      const target = editorRef.current?.querySelector<HTMLElement>(`[data-field-key="${key}"] [data-issue-target]`)
      target?.focus()
      return Boolean(target)
    },
  })

  const editTransformations = useCallback(
    (key: FieldKey, edit: TransformationEdit) => {
      clearOnEdit()
      dispatch({ type: 'transformationsEdited', key, edit })
    },
    [dispatch, clearOnEdit],
  )
  const toggleRule = useCallback(
    (key: FieldKey, rule: UserRule, enabled: boolean) => {
      clearOnEdit()
      dispatch({ type: 'validationToggled', key, rule, enabled })
    },
    [dispatch, clearOnEdit],
  )

  return (
    <section aria-labelledby={titleId} className={styles.stepPage}>
      <StepHeader id={titleId} title={stepLabels.rules} intro={messages.rules.intro} headingRef={titleRef} />

      {failure && <SaveFailureBanner failure={failure} />}

      <datalist id={datePatternsId}>
        {DATE_PATTERNS.map((pattern) => (
          <option key={pattern} value={pattern} />
        ))}
      </datalist>

      <fieldset ref={editorRef} className={styles.editor} disabled={saving}>
        <legend className="sr-only">{stepLabels.rules}</legend>
        {fields.map((field) => (
          <FieldRules
            key={field.key}
            fieldKey={field.key}
            name={normalizeFieldName(field.name)}
            type={field.type}
            required={field.required}
            error={serverErrors[field.key]}
          >
            <TransformationEditor
              field={field}
              steps={transformations[field.key] ?? NO_STEPS}
              issues={issuesByField[field.key] ?? NO_ISSUES}
              datePatternsId={datePatternsId}
              onEdit={editTransformations}
            />
            <ValidationEditor field={field} enabled={state.validations.draft[field.key] ?? NO_RULES} onToggle={toggleRule} />
          </FieldRules>
        ))}
      </fieldset>

      <StepActions
        onBack={() => dispatch({ type: 'navigate', step: 'mapping' })}
        onNext={run}
        nextLabel={messages.rules.run}
        nextBlockedReason={blockedReason}
        progressLabel={running ? messages.run.running : null}
      />
    </section>
  )
}

/**
 * Một field: nhóm (không phải landmark) có tên là tên field, để file nhiều cột không tạo hàng trăm landmark cho screen
 * reader; tiêu đề h3 vẫn dùng để điều hướng (review FE-F06/F07). Lỗi BE của field (422 khi lưu transformations hoặc
 * validations) nằm ngay dưới đầu thẻ, là mô tả của nhóm và là chỗ nhận focus sau khi chạy lỗi.
 */
function FieldRules({
  fieldKey,
  name,
  type,
  required,
  error,
  children,
}: {
  fieldKey: FieldKey
  name: string
  type: string
  required: boolean
  error: string | undefined
  children: ReactNode
}) {
  const headingId = useId()
  const errorId = useId()

  return (
    <div
      role="group"
      aria-labelledby={headingId}
      aria-describedby={error ? errorId : undefined}
      data-field-key={fieldKey}
      className={styles.fieldCard}
    >
      <div className={styles.fieldHeader}>
        <h3 id={headingId} className={styles.fieldName}>
          {name}
        </h3>
        <span className={styles.meta}>
          <span>{type}</span>
          {required && <span className={styles.required}>{messages.mapping.requiredBadge}</span>}
        </span>
      </div>
      {error && (
        <p id={errorId} className={styles.fieldError} tabIndex={-1} data-issue-target>
          {error}
        </p>
      )}
      <div className={styles.fieldBody}>{children}</div>
    </div>
  )
}
