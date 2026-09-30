import { memo, useEffect, useId, useRef, useState } from 'react'
import type { TransformationIssue } from '../../domain/configRules'
import {
  TRANSFORMATION_TYPES,
  type FieldKey,
  type TargetField,
  type Transformation,
  type TransformationId,
  type TransformationType,
} from '../../domain/types'
import { messages } from '../../shared/messages'
import { ArrowDownIcon, ArrowUpIcon, TrashIcon } from '../../shared/ui/icons'
import { focusFirstControl, focusMoveButton } from '../../shared/ui/listFocus'
import type { TransformationEdit, TransformationPatch } from '../../wizard/state'
import styles from './RulesStep.module.css'

/** Chỗ focus sau khi danh sách bước đổi, vì control đang giữ focus có thể vừa biến mất hoặc bị khoá (design D14). */
type PendingFocus =
  | { kind: 'lastParam' }
  | { kind: 'firstControl'; id: TransformationId }
  | { kind: 'move'; id: TransformationId; direction: 'up' | 'down' }
  | { kind: 'addSelect' }

interface TransformationEditorProps {
  field: TargetField
  steps: readonly Transformation[]
  /** Chỉ vấn đề của các bước thuộc field này. */
  issues: Readonly<Record<TransformationId, TransformationIssue>>
  /** Id của `<datalist>` gợi ý mẫu ngày, dùng chung cho mọi ô định dạng. */
  datePatternsId: string
  onEdit: (key: FieldKey, edit: TransformationEdit) => void
}

/**
 * Các bước biến đổi của một field: thêm, xoá, Lên/Xuống, ô tham số, dòng tóm tắt (spec rule-config). `memo` với props
 * ổn định: gõ vào ô tham số của một field không render lại các field khác (review FE-F06/F07).
 */
export const TransformationEditor = memo(function TransformationEditor({
  field,
  steps,
  issues,
  datePatternsId,
  onEdit,
}: TransformationEditorProps) {
  const [newType, setNewType] = useState<TransformationType>('trim')
  const pendingFocus = useRef<PendingFocus | null>(null)
  const listRef = useRef<HTMLOListElement>(null)
  const addSelectRef = useRef<HTMLSelectElement>(null)
  const addSelectId = useId()

  useEffect(() => {
    const target = pendingFocus.current
    pendingFocus.current = null
    if (target) focusPending(target, listRef.current, addSelectRef.current)
  }, [steps])

  function edit(transformationEdit: TransformationEdit, focus?: PendingFocus) {
    pendingFocus.current = focus ?? null
    onEdit(field.key, transformationEdit)
  }

  function add() {
    // Bước có tham số thì đưa con trỏ vào ô tham số đầu; bước không có thì focus ở lại nút "Thêm biến đổi" (dòng tóm
    // tắt là vùng live nên vẫn báo bước mới cho screen reader).
    const hasParams = newType === 'defaultValue' || newType === 'dateFormat'
    edit({ kind: 'add', type: newType }, hasParams ? { kind: 'lastParam' } : undefined)
  }

  function remove(index: number) {
    // Như bước Schema: sang control đầu của bước kề bên, không sang nút "Xoá" của nó, để bấm đúp không xoá liên tiếp.
    const neighbour = steps[index + 1] ?? steps[index - 1]
    edit(
      { kind: 'remove', id: steps[index].id },
      neighbour ? { kind: 'firstControl', id: neighbour.id } : { kind: 'addSelect' },
    )
  }

  return (
    <fieldset className={styles.section}>
      <legend className={styles.sectionTitle}>{messages.rules.transformationsLegend}</legend>
      {/* Vùng live: chỉ đổi khi thêm, xoá, đổi thứ tự bước (không đổi khi gõ tham số). */}
      <p className={styles.summary} data-testid="transformation-summary" aria-live="polite">
        {steps.length > 0 ? steps.map((step) => step.type).join(' → ') : messages.rules.noTransformation}
      </p>

      <ol ref={listRef} aria-label={messages.rules.stepsLabel} className={styles.steps}>
        {steps.map((step, index) => (
          <li key={step.id} data-step-id={step.id} className={styles.step}>
            <StepRow
              field={field}
              step={step}
              position={index + 1}
              isFirst={index === 0}
              isLast={index === steps.length - 1}
              trimBefore={steps.slice(0, index).some((previous) => previous.type === 'trim')}
              issue={issues[step.id]}
              datePatternsId={datePatternsId}
              onPatch={(patch) => edit({ kind: 'update', id: step.id, patch })}
              onMove={(direction) =>
                edit(
                  { kind: 'move', id: step.id, offset: direction === 'up' ? -1 : 1 },
                  { kind: 'move', id: step.id, direction },
                )
              }
              onRemove={() => remove(index)}
            />
          </li>
        ))}
      </ol>

      <div className={styles.add}>
        <label htmlFor={addSelectId} className="sr-only">
          {messages.rules.addTypeLabel}
        </label>
        <select
          id={addSelectId}
          ref={addSelectRef}
          className={styles.addType}
          value={newType}
          onChange={(event) => setNewType(event.target.value as TransformationType)}
        >
          {TRANSFORMATION_TYPES.map((type) => (
            <option key={type} value={type}>
              {messages.rules.typeOptions[type]}
            </option>
          ))}
        </select>
        <button type="button" className={styles.addButton} onClick={add}>
          {messages.rules.add}
        </button>
      </div>
    </fieldset>
  )
})

interface StepRowProps {
  field: TargetField
  step: Transformation
  position: number
  isFirst: boolean
  isLast: boolean
  trimBefore: boolean
  issue: TransformationIssue | undefined
  datePatternsId: string
  onPatch: (patch: TransformationPatch) => void
  onMove: (direction: 'up' | 'down') => void
  onRemove: () => void
}

function StepRow({
  field,
  step,
  position,
  isFirst,
  isLast,
  trimBefore,
  issue,
  datePatternsId,
  onPatch,
  onMove,
  onRemove,
}: StepRowProps) {
  const id = useId()
  const dateField = field.type === 'date'
  const trimHintId = `${id}-trim-hint`

  return (
    <fieldset className={styles.stepFieldset}>
      <legend className="sr-only">{messages.rules.step(position, step.type)}</legend>
      <span className={styles.stepType} aria-hidden="true">
        {position}. {step.type}
      </span>

      <div className={styles.params}>
        {step.type === 'defaultValue' && (
          <ParamInput
            id={`${id}-value`}
            label={messages.rules.valueLabel}
            value={step.value}
            error={issue?.value}
            onChange={(value) => onPatch({ value })}
          />
        )}
        {step.type === 'dateFormat' && (
          <>
            <ParamInput
              id={`${id}-input`}
              label={messages.rules.inputFormatLabel}
              value={step.inputFormat}
              error={issue?.inputFormat}
              list={datePatternsId}
              extraDescription={trimBefore ? undefined : trimHintId}
              warnSurroundingSpaces
              onChange={(inputFormat) => onPatch({ inputFormat })}
            />
            <ParamInput
              id={`${id}-output`}
              label={messages.rules.outputFormatLabel}
              value={step.outputFormat}
              error={issue?.outputFormat}
              list={datePatternsId}
              readOnly={dateField}
              note={dateField ? messages.rules.dateOutputLocked : undefined}
              warnSurroundingSpaces
              onChange={(outputFormat) => onPatch({ outputFormat })}
            />
            {!trimBefore && (
              <p id={trimHintId} className={styles.hint}>
                {messages.rules.trimHint}
              </p>
            )}
          </>
        )}
      </div>

      <div className={styles.stepActions}>
        <button
          type="button"
          data-action="up"
          className={styles.iconButton}
          aria-label={messages.rules.moveUp}
          title={messages.rules.moveUp}
          disabled={isFirst}
          onClick={() => onMove('up')}
        >
          <ArrowUpIcon />
        </button>
        <button
          type="button"
          data-action="down"
          className={styles.iconButton}
          aria-label={messages.rules.moveDown}
          title={messages.rules.moveDown}
          disabled={isLast}
          onClick={() => onMove('down')}
        >
          <ArrowDownIcon />
        </button>
        <button
          type="button"
          data-action="remove"
          className={styles.iconButton}
          aria-label={messages.rules.remove}
          title={messages.rules.remove}
          onClick={onRemove}
        >
          <TrashIcon />
        </button>
      </div>
    </fieldset>
  )
}

interface ParamInputProps {
  id: string
  label: string
  value: string
  error: string | undefined
  list?: string
  readOnly?: boolean
  /** Ghi chú cố định (ví dụ vì sao ô bị khoá), là mô tả của ô. */
  note?: string
  /** Id của một đoạn mô tả khác nằm ngoài ô (ví dụ lời nhắc thêm trim). */
  extraDescription?: string
  /**
   * Mẫu ngày có khoảng trắng ở đầu/cuối: BE vẫn nhận (khoảng trắng là ký tự thường của mẫu) nhưng mọi dòng sẽ lỗi,
   * nên cảnh báo tại ô; không tự trim để khỏi lệch với BE (review FE-F06/F07).
   */
  warnSurroundingSpaces?: boolean
  onChange: (value: string) => void
}

function ParamInput({
  id,
  label,
  value,
  error,
  list,
  readOnly = false,
  note,
  extraDescription,
  warnSurroundingSpaces = false,
  onChange,
}: ParamInputProps) {
  const warning =
    warnSurroundingSpaces && !error && value !== '' && value !== value.trim() ? messages.rules.surroundingSpaces : null
  const describedBy = [
    error ? `${id}-error` : null,
    warning ? `${id}-warning` : null,
    note ? `${id}-note` : null,
    extraDescription ?? null,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={styles.param}>
      <label htmlFor={id} className={styles.paramLabel}>
        {label}
      </label>
      <input
        id={id}
        data-action="param"
        type="text"
        className={styles.paramInput}
        value={value}
        list={list}
        readOnly={readOnly}
        autoComplete="off"
        spellCheck={false}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {error && (
        <p id={`${id}-error`} className={styles.error}>
          {error}
        </p>
      )}
      {warning && (
        <p id={`${id}-warning`} className={styles.warning}>
          {warning}
        </p>
      )}
      {note && (
        <p id={`${id}-note`} className={styles.note}>
          {note}
        </p>
      )}
    </div>
  )
}

function focusPending(target: PendingFocus, list: HTMLOListElement | null, addSelect: HTMLSelectElement | null) {
  if (target.kind === 'addSelect') {
    addSelect?.focus()
    return
  }
  if (!list) return
  if (target.kind === 'lastParam') {
    const rows = list.querySelectorAll<HTMLLIElement>('[data-step-id]')
    rows[rows.length - 1]?.querySelector<HTMLInputElement>('[data-action="param"]')?.focus()
    return
  }
  const row = list.querySelector(`[data-step-id="${target.id}"]`)
  if (target.kind === 'firstControl') focusFirstControl(row)
  else focusMoveButton(row, target.direction)
}
