import { useEffect, useId, useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import { ApiError, isSessionUnusable } from '../../api/apiError'
import { putSchema } from '../../api/endpoints'
import { toTargetSchemaDto } from '../../api/mappers'
import { checkSchema, matchServerErrors } from '../../domain/schemaRules'
import { FIELD_TYPES, type FieldKey, type FieldType, type TargetField } from '../../domain/types'
import { describeApiError, type ErrorText } from '../../shared/describeError'
import { messages, stepLabels } from '../../shared/messages'
import { ConfirmPanel } from '../../shared/ui/ConfirmPanel'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { ArrowDownIcon, ArrowUpIcon, PlusIcon, TrashIcon } from '../../shared/ui/icons'
import { useWizard } from '../../wizard/context'
import { isBusy, type SchemaEdit } from '../../wizard/state'
import { StepActions } from '../../wizard/StepActions'
import { useBusyRequest } from '../../wizard/useBusyRequest'
import styles from './SchemaStep.module.css'

interface Failure {
  text: ErrorText
  /** `errors[]` của BE không gắn được vào field nào. */
  items: string[]
  /** Session hết hạn hoặc hỏng: chỉ còn cách upload lại (design D12). */
  reupload: boolean
}

/** Chỗ focus sau khi danh sách field đổi, vì control đang giữ focus có thể vừa biến mất hoặc bị khoá (design D14). */
type PendingFocus =
  | { kind: 'firstName' }
  | { kind: 'lastName' }
  | { kind: 'name'; key: FieldKey }
  | { kind: 'move'; key: FieldKey; direction: 'up' | 'down' }
  | { kind: 'addButton' }

type FieldPatch = Partial<Pick<TargetField, 'name' | 'type' | 'required'>>

export function SchemaStep() {
  const { state, dispatch } = useWizard()
  const runBusy = useBusyRequest()
  const fields = state.schema.draft
  const check = checkSchema(fields)
  // Trong lúc PUT, khoá cả phần sửa: sửa lúc đó thì bản vừa lưu không còn là bản đang hiển thị (review FE-F04).
  const saving = isBusy(state)
  // Field đã có lúc mở bước đều đã từng được focus (lúc thêm), nên coi như đã rời ô: quay lại bước vẫn thấy lỗi.
  const [touched, setTouched] = useState<ReadonlySet<FieldKey>>(() => new Set(fields.map((field) => field.key)))
  const [serverErrors, setServerErrors] = useState<Record<FieldKey, string>>({})
  const [failure, setFailure] = useState<Failure | null>(null)
  const [confirmingRegenerate, setConfirmingRegenerate] = useState(false)
  const pendingFocus = useRef<PendingFocus | null>(null)
  const regenerateButtonRef = useRef<HTMLButtonElement>(null)
  const rows = useRef(new Map<FieldKey, HTMLLIElement>())
  const addButtonRef = useRef<HTMLButtonElement>(null)
  const titleRef = useRef<HTMLHeadingElement>(null)
  const titleId = useId()

  useEffect(() => {
    const target = pendingFocus.current
    pendingFocus.current = null
    if (target) focusPending(target, fields, rows.current, addButtonRef.current)
  }, [fields])

  function edit(schemaEdit: SchemaEdit, focus?: PendingFocus) {
    pendingFocus.current = focus ?? null
    // Lỗi BE và khối lỗi nói về bản đã gửi; sửa bất kỳ field nào (ví dụ đổi tên field kia cho hết trùng) là bản đó không
    // còn nữa. Riêng lỗi session hỏng thì sửa gì cũng không hết, nên giữ nút "Upload lại".
    setServerErrors({})
    setFailure((current) => (current?.reupload ? current : null))
    dispatch({ type: 'schemaEdited', edit: schemaEdit })
  }

  function removeField(index: number) {
    const neighbour = fields[index + 1] ?? fields[index - 1]
    edit(
      { kind: 'remove', key: fields[index].key },
      neighbour ? { kind: 'name', key: neighbour.key } : { kind: 'addButton' },
    )
  }

  function regenerate() {
    setConfirmingRegenerate(false)
    edit({ kind: 'regenerate' }, { kind: 'firstName' })
  }

  // Đang có field thì hỏi trước khi thay; chưa có thì không có gì để mất.
  function requestRegenerate() {
    if (fields.length === 0) regenerate()
    else setConfirmingRegenerate(true)
  }

  function cancelRegenerate() {
    // Hộp xác nhận (đang giữ focus) biến mất: trả focus về nút đã mở nó.
    flushSync(() => setConfirmingRegenerate(false))
    regenerateButtonRef.current?.focus()
  }

  async function saveAndContinue() {
    // Đã lưu và chưa sửa gì từ đó: không PUT lại (spec target-schema).
    if (state.schema.saved) {
      dispatch({ type: 'navigate', step: 'mapping' })
      return
    }
    // Không cần khoá riêng chống bấm đúp: requestStarted khoá nút "Tiếp" ngay trong sự kiện click đầu tiên.
    if (!state.session) return
    const draft = fields
    const sessionId = state.session.id
    setFailure(null)
    setServerErrors({})
    try {
      await runBusy(() => putSchema(sessionId, toTargetSchemaDto(draft)))
      dispatch({ type: 'sectionSaved', section: 'schema', draft })
      dispatch({ type: 'navigate', step: 'mapping' })
    } catch (error) {
      showSaveError(error, draft)
    }
  }

  function showSaveError(error: unknown, draft: TargetField[]) {
    if (!(error instanceof ApiError)) {
      // Lỗi lập trình: user chỉ thấy câu chung, nên stack phải nằm ở console.
      console.error(error)
      setFailure({ text: { headline: messages.unexpected }, items: [], reupload: false })
      titleRef.current?.focus()
      return
    }
    const matched = matchServerErrors(draft, error.fieldErrors)
    // Gắn lỗi vào DOM trước rồi mới focus: screen reader đọc ô lúc nó nhận focus, thuộc tính gắn sau không được đọc lại.
    flushSync(() => {
      setServerErrors(matched.byKey)
      setFailure({
        text: describeApiError(error) ?? { headline: messages.unexpected },
        items: matched.general,
        reupload: isSessionUnusable(error),
      })
    })
    // Nút "Tiếp" bị khoá trong lúc lưu nên đã mất focus: đưa tới field lỗi đầu tiên, hoặc tiêu đề bước.
    const firstInvalid = draft.find((field) => matched.byKey[field.key])
    if (firstInvalid) focusPending({ kind: 'name', key: firstInvalid.key }, draft, rows.current, addButtonRef.current)
    else titleRef.current?.focus()
  }

  return (
    <section aria-labelledby={titleId} className={styles.step}>
      <div className={styles.header}>
        <h2 id={titleId} ref={titleRef} className={styles.title} tabIndex={-1}>
          {stepLabels.schema}
        </h2>
        <p className={styles.intro}>{messages.schema.intro}</p>
      </div>

      {failure && (
        <ErrorBanner
          text={failure.text}
          items={failure.items}
          action={
            failure.reupload
              ? { label: messages.sessionUnusableAction, onClick: () => dispatch({ type: 'reset' }) }
              : undefined
          }
        />
      )}

      <fieldset className={styles.editor} disabled={saving}>
        <legend className="sr-only">{messages.schema.editorLabel}</legend>
        {fields.length === 0 ? (
          <EmptyState title={messages.schema.empty} hint={messages.schema.emptyHint} />
        ) : (
          <div className={styles.table}>
            <div className={styles.head} aria-hidden="true">
              <span>#</span>
              <span>{messages.schema.columns.name}</span>
              <span>{messages.schema.columns.type}</span>
              <span>{messages.schema.columns.required}</span>
              <span />
            </div>
            <ol className={styles.fields}>
              {fields.map((field, index) => (
                <FieldRow
                  key={field.key}
                  field={field}
                  position={index + 1}
                  isFirst={index === 0}
                  isLast={index === fields.length - 1}
                  error={serverErrors[field.key] ?? (touched.has(field.key) ? check.errors[field.key] : undefined)}
                  rowRef={(element) => {
                    rows.current.set(field.key, element)
                    return () => {
                      rows.current.delete(field.key)
                    }
                  }}
                  onNameBlur={() =>
                    setTouched((current) => (current.has(field.key) ? current : new Set(current).add(field.key)))
                  }
                  onChange={(patch) => edit({ kind: 'update', key: field.key, patch })}
                  onMove={(direction) =>
                    edit(
                      { kind: 'move', key: field.key, offset: direction === 'up' ? -1 : 1 },
                      { kind: 'move', key: field.key, direction },
                    )
                  }
                  onRemove={() => removeField(index)}
                />
              ))}
            </ol>
          </div>
        )}

        <div className={styles.tools}>
          <button
            ref={addButtonRef}
            type="button"
            className={styles.add}
            onClick={() => edit({ kind: 'add' }, { kind: 'lastName' })}
          >
            <PlusIcon />
            {messages.schema.add}
          </button>
          <button ref={regenerateButtonRef} type="button" className={styles.regenerate} onClick={requestRegenerate}>
            {messages.schema.regenerate}
          </button>
        </div>

        {confirmingRegenerate && (
          <ConfirmPanel
            message={messages.schema.regenerateConfirm(fields.length, state.preview?.columns.length ?? 0)}
            confirmLabel={messages.schema.regenerateAction}
            cancelLabel={messages.schema.cancel}
            onConfirm={regenerate}
            onCancel={cancelRegenerate}
          />
        )}
      </fieldset>

      <StepActions
        onBack={() => dispatch({ type: 'navigate', step: 'preview' })}
        onNext={() => void saveAndContinue()}
        nextBlockedReason={check.blockedReason ?? undefined}
      />
    </section>
  )
}

interface FieldRowProps {
  field: TargetField
  position: number
  isFirst: boolean
  isLast: boolean
  error: string | undefined
  rowRef: (element: HTMLLIElement) => () => void
  onNameBlur: () => void
  onChange: (patch: FieldPatch) => void
  onMove: (direction: 'up' | 'down') => void
  onRemove: () => void
}

function FieldRow({ field, position, isFirst, isLast, error, rowRef, onNameBlur, onChange, onMove, onRemove }: FieldRowProps) {
  const id = useId()
  const errorId = `${id}-error`

  return (
    <li ref={rowRef} className={styles.row} data-invalid={error ? true : undefined}>
      <fieldset className={styles.fieldset}>
        <legend className="sr-only">{messages.schema.group(position)}</legend>
        <span className={styles.position} aria-hidden="true">
          {position}
        </span>

        <div className={styles.nameCell}>
          <label htmlFor={`${id}-name`} className="sr-only">
            {messages.schema.columns.name}
          </label>
          <input
            id={`${id}-name`}
            data-action="name"
            type="text"
            className={styles.name}
            value={field.name}
            autoComplete="off"
            spellCheck={false}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            onChange={(event) => onChange({ name: event.target.value })}
            onBlur={onNameBlur}
          />
          {error && (
            <p id={errorId} className={styles.error}>
              {error}
            </p>
          )}
        </div>

        <label htmlFor={`${id}-type`} className="sr-only">
          {messages.schema.typeLabel}
        </label>
        <select
          id={`${id}-type`}
          className={styles.type}
          value={field.type}
          onChange={(event) => onChange({ type: event.target.value as FieldType })}
        >
          {FIELD_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>

        <label className={styles.required}>
          <input
            type="checkbox"
            checked={field.required}
            onChange={(event) => onChange({ required: event.target.checked })}
          />
          <span className="sr-only">{messages.schema.requiredLabel}</span>
        </label>

        <div className={styles.actions}>
          <button
            type="button"
            data-action="up"
            className={styles.iconButton}
            aria-label={messages.schema.moveUp}
            title={messages.schema.moveUp}
            disabled={isFirst}
            onClick={() => onMove('up')}
          >
            <ArrowUpIcon />
          </button>
          <button
            type="button"
            data-action="down"
            className={styles.iconButton}
            aria-label={messages.schema.moveDown}
            title={messages.schema.moveDown}
            disabled={isLast}
            onClick={() => onMove('down')}
          >
            <ArrowDownIcon />
          </button>
          <button
            type="button"
            data-action="remove"
            className={styles.iconButton}
            aria-label={messages.schema.remove}
            title={messages.schema.remove}
            onClick={onRemove}
          >
            <TrashIcon />
          </button>
        </div>
      </fieldset>
    </li>
  )
}

function focusPending(
  target: PendingFocus,
  fields: TargetField[],
  rows: ReadonlyMap<FieldKey, HTMLLIElement>,
  addButton: HTMLButtonElement | null,
) {
  if (target.kind === 'addButton') {
    addButton?.focus()
    return
  }
  const key = target.kind === 'firstName' ? fields[0]?.key : target.kind === 'lastName' ? fields.at(-1)?.key : target.key
  const row = key === undefined ? undefined : rows.get(key)
  if (!row) return
  if (target.kind === 'move') {
    // Tới biên thì nút vừa bấm bị khoá; chuyển sang nút chiều ngược lại của cùng field.
    const same = row.querySelector<HTMLButtonElement>(`[data-action="${target.direction}"]`)
    const other = row.querySelector<HTMLButtonElement>(`[data-action="${target.direction === 'up' ? 'down' : 'up'}"]`)
    ;(same && !same.disabled ? same : other)?.focus()
  } else {
    row.querySelector<HTMLInputElement>('[data-action="name"]')?.focus()
  }
}

