import { memo, useCallback, useId, useMemo, useRef } from 'react'
import { putMapping } from '../../api/endpoints'
import { toMappingConfigDto } from '../../api/mappers'
import { checkMapping } from '../../domain/configRules'
import { normalizeFieldName } from '../../domain/schemaRules'
import type { FieldKey, FieldMapping, SourcePreview, TargetField } from '../../domain/types'
import { messages, stepLabels } from '../../shared/messages'
import { useWizard } from '../../wizard/context'
import { SaveFailureBanner } from '../../wizard/SaveFailureBanner'
import { isBusy } from '../../wizard/state'
import { StepActions } from '../../wizard/StepActions'
import { StepHeader } from '../../wizard/StepHeader'
import { useBusyRequest } from '../../wizard/useBusyRequest'
import { useSaveFeedback } from '../../wizard/useSaveFeedback'
import styles from './MappingStep.module.css'

const MAX_SAMPLES = 3
const UNMAPPED = ''
const CONSTANT = 'constant'
const NO_SAMPLES: readonly string[] = []

/** Lựa chọn ở ô "Nguồn": bỏ map, một cột, hoặc chuyển sang giá trị cố định. */
type SourceChoice = { kind: 'unmapped' } | { kind: 'column'; column: string } | { kind: 'constant' }

export function MappingStep() {
  const { state, dispatch } = useWizard()
  const runBusy = useBusyRequest()
  const fields = state.schema.draft
  const mapping = state.mapping.draft
  const { preview } = state
  const columns = preview?.columns ?? NO_SAMPLES
  const check = checkMapping(fields, mapping)
  // Trong lúc PUT, khoá cả phần sửa: sửa lúc đó thì bản vừa lưu không còn là bản đang hiển thị (design D2).
  const saving = isBusy(state)
  const { serverErrors, failure, clearBeforeSave, clearOnEdit, report } = useSaveFeedback()
  const rows = useRef(new Map<FieldKey, HTMLLIElement>())
  // Giá trị cố định cuối cùng của từng field: lướt qua lựa chọn khác rồi quay lại thì không mất (review FE-F05).
  const lastConstants = useRef(new Map<FieldKey, string>())
  const titleRef = useRef<HTMLHeadingElement>(null)
  const titleId = useId()

  // Giá trị mẫu tính một lần cho mỗi cột, không quét lại preview ở mỗi lần gõ.
  const samplesByColumn = useMemo(() => columns.map((_, index) => samplesOf(preview, index)), [columns, preview])

  const edit = useCallback(
    (key: FieldKey, next: FieldMapping | null) => {
      clearOnEdit()
      dispatch({ type: 'mappingEdited', key, mapping: next })
    },
    [clearOnEdit, dispatch],
  )

  const choose = useCallback(
    (key: FieldKey, choice: SourceChoice, current: FieldMapping | undefined) => {
      if (current?.kind === 'constant') lastConstants.current.set(key, current.value)
      if (choice.kind === 'unmapped') edit(key, null)
      else if (choice.kind === 'column') edit(key, { kind: 'column', column: choice.column })
      else edit(key, { kind: 'constant', value: lastConstants.current.get(key) ?? '' })
    },
    [edit],
  )

  const registerRow = useCallback((key: FieldKey, element: HTMLLIElement | null) => {
    if (element) rows.current.set(key, element)
    else rows.current.delete(key)
  }, [])

  async function saveAndContinue() {
    // Đã lưu và chưa sửa gì từ đó: không PUT lại (spec field-mapping).
    if (state.mapping.saved) {
      dispatch({ type: 'navigate', step: 'rules' })
      return
    }
    if (!state.session) return
    const draft = mapping
    const sentFields = fields
    const sessionId = state.session.id
    clearBeforeSave()
    try {
      await runBusy(() => putMapping(sessionId, toMappingConfigDto(sentFields, draft)))
      dispatch({ type: 'sectionSaved', section: 'mapping', draft })
      dispatch({ type: 'navigate', step: 'rules' })
    } catch (error) {
      // `errors[].field` là `targetField` đã gửi. Focus đúng control đang mang lỗi của dòng đó: ô nhập giá trị cố định
      // hoặc ô chọn nguồn (cùng luật với chỗ gắn `aria-describedby`).
      report(
        error,
        sentFields,
        (key) => {
          const target = rows.current.get(key)?.querySelector<HTMLElement>('[data-issue-target]')
          target?.focus()
          return Boolean(target)
        },
        titleRef.current,
      )
    }
  }

  return (
    <section aria-labelledby={titleId} className={styles.step}>
      <StepHeader id={titleId} title={stepLabels.mapping} intro={messages.mapping.intro} headingRef={titleRef} />

      {failure && <SaveFailureBanner failure={failure} />}

      <fieldset className={styles.editor} disabled={saving}>
        <legend className="sr-only">{messages.mapping.editorLabel}</legend>
        <div className={styles.table}>
          <div className={styles.head} aria-hidden="true">
            <span>{messages.mapping.columns.field}</span>
            <span>{messages.mapping.columns.source}</span>
            <span>{messages.mapping.columns.value}</span>
          </div>
          <ol className={styles.fields}>
            {fields.map((field) => {
              const current = mapping[field.key]
              const columnIndex = current?.kind === 'column' ? columns.indexOf(current.column) : -1
              const serverError = serverErrors[field.key]
              const issue = serverError ? { level: 'error' as const, message: serverError } : check.issues[field.key]
              return (
                <MappingRow
                  key={field.key}
                  field={field}
                  columns={columns}
                  mapping={current}
                  columnIndex={columnIndex}
                  samples={columnIndex >= 0 ? samplesByColumn[columnIndex] : NO_SAMPLES}
                  issueLevel={issue?.level}
                  issueMessage={issue?.message}
                  registerRow={registerRow}
                  onChoose={choose}
                  onEdit={edit}
                />
              )
            })}
          </ol>
        </div>
      </fieldset>

      <StepActions
        onBack={() => dispatch({ type: 'navigate', step: 'schema' })}
        onNext={() => void saveAndContinue()}
        nextBlockedReason={check.blockedReason ?? undefined}
      />
    </section>
  )
}

interface MappingRowProps {
  field: TargetField
  columns: readonly string[]
  mapping: FieldMapping | undefined
  columnIndex: number
  samples: readonly string[]
  issueLevel: 'error' | 'warning' | undefined
  issueMessage: string | undefined
  registerRow: (key: FieldKey, element: HTMLLIElement | null) => void
  onChoose: (key: FieldKey, choice: SourceChoice, current: FieldMapping | undefined) => void
  onEdit: (key: FieldKey, next: FieldMapping | null) => void
}

/**
 * Một dòng mapping. `memo` với props ổn định: gõ vào ô giá trị cố định của một field không render lại cả bảng (file
 * nhiều cột có hàng nghìn `<option>`).
 */
const MappingRow = memo(function MappingRow({
  field,
  columns,
  mapping,
  columnIndex,
  samples,
  issueLevel,
  issueMessage,
  registerRow,
  onChoose,
  onEdit,
}: MappingRowProps) {
  const id = useId()
  const metaId = `${id}-meta`
  const samplesId = `${id}-samples`
  const issueId = `${id}-issue`
  const name = normalizeFieldName(field.name)
  const selectValue = !mapping ? UNMAPPED : mapping.kind === 'constant' ? CONSTANT : `column:${columnIndex}`
  // Lỗi hằng rỗng (và lỗi BE của dòng hằng) gắn vào ô nhập; các vấn đề khác gắn vào ô chọn nguồn.
  const issueOnInput = mapping?.kind === 'constant'
  const invalid = issueLevel === 'error'
  const showSamples = mapping?.kind === 'column'
  // Ô chọn nguồn được mô tả bằng kiểu, bắt buộc và giá trị mẫu, để user screen reader biết field cần gì (review FE-F05).
  const selectDescription = [
    metaId,
    showSamples && samples.length > 0 ? samplesId : null,
    issueMessage && !issueOnInput ? issueId : null,
  ]
    .filter(Boolean)
    .join(' ')

  function select(value: string) {
    if (value === UNMAPPED) onChoose(field.key, { kind: 'unmapped' }, mapping)
    else if (value === CONSTANT) onChoose(field.key, { kind: 'constant' }, mapping)
    else onChoose(field.key, { kind: 'column', column: columns[Number(value.slice('column:'.length))] }, mapping)
  }

  return (
    <li ref={(element) => registerRow(field.key, element)} className={styles.row} data-issue={issueLevel}>
      <fieldset className={styles.fieldset}>
        <legend className="sr-only">{name}</legend>

        <div className={styles.field}>
          {/* Tên đã có trong legend; phần kiểu và bắt buộc là mô tả của ô chọn nguồn. */}
          <span className={styles.fieldName} aria-hidden="true">
            {name}
          </span>
          <span id={metaId} className={styles.meta}>
            <span className="sr-only">{messages.mapping.typeLabel} </span>
            <span className={styles.type}>{field.type}</span>
            {field.required && <span className={styles.required}>{messages.mapping.requiredBadge}</span>}
          </span>
        </div>

        <div className={styles.sourceCell}>
          <label htmlFor={`${id}-source`} className="sr-only">
            {messages.mapping.sourceLabel}
          </label>
          <select
            id={`${id}-source`}
            className={styles.source}
            value={selectValue}
            data-issue-target={!issueOnInput || undefined}
            aria-invalid={invalid && !issueOnInput ? true : undefined}
            aria-describedby={selectDescription}
            onChange={(event) => select(event.target.value)}
          >
            <option value={UNMAPPED}>{messages.mapping.unmapped}</option>
            <optgroup label={messages.mapping.columnsGroup}>
              {columns.map((column, index) => (
                <option key={index} value={`column:${index}`}>
                  {column}
                </option>
              ))}
            </optgroup>
            <option value={CONSTANT}>{messages.mapping.constantOption}</option>
          </select>
        </div>

        <div className={styles.valueCell}>
          {mapping?.kind === 'constant' && (
            <>
              <label htmlFor={`${id}-constant`} className="sr-only">
                {messages.mapping.constantLabel}
              </label>
              <input
                id={`${id}-constant`}
                type="text"
                className={styles.constant}
                value={mapping.value}
                autoComplete="off"
                data-issue-target
                aria-invalid={invalid ? true : undefined}
                aria-describedby={issueMessage ? issueId : undefined}
                onChange={(event) => onEdit(field.key, { kind: 'constant', value: event.target.value })}
              />
            </>
          )}
          {showSamples &&
            (samples.length > 0 ? (
              <ul id={samplesId} aria-label={messages.mapping.samplesLabel} className={styles.samples}>
                {samples.map((value, index) => (
                  <li key={index}>{value}</li>
                ))}
              </ul>
            ) : (
              <p className={styles.noSamples}>{messages.mapping.noSamples}</p>
            ))}
        </div>

        {issueMessage && (
          <p id={issueId} className={styles.issue} data-level={issueLevel}>
            {issueMessage}
          </p>
        )}
      </fieldset>
    </li>
  )
})

/** Tối đa 3 giá trị không rỗng đầu tiên của cột, lấy từ preview đang có; không gọi thêm API (spec field-mapping). */
function samplesOf(preview: SourcePreview | null, columnIndex: number): readonly string[] {
  if (!preview) return NO_SAMPLES
  const values: string[] = []
  for (const row of preview.rows) {
    const value = row.values[columnIndex]
    if (value !== null && value !== undefined && value.trim() !== '') values.push(value)
    if (values.length === MAX_SAMPLES) break
  }
  return values
}
