import { useId, useRef, useState } from 'react'
import { saveBlob } from '../../api/download'
import { FIELD_TYPES, type FieldType } from '../../domain/types'
import buttons from '../../shared/ui/Button.module.css'
import { ConfirmPanel } from '../../shared/ui/ConfirmPanel'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { ArrowDownIcon, ArrowUpIcon, PlusIcon, TrashIcon } from '../../shared/ui/icons'
import { SpinnerMark } from '../../shared/ui/Spinner'
import { useValidator } from './context'
import { validatorMessages } from './messages'
import {
  checkSchema,
  CONSTRAINTS_BY_TYPE,
  hasProblems,
  matchColumns,
  mergeProblems,
  readSchemaFile,
  schemaFileContent,
  schemaFileName,
  type ConstraintKey,
  type FieldDraft,
  type FieldInput,
} from './schema'
import type { FieldPatch } from './state'
import { useRunValidation } from './useRunValidation'
import styles from './Validator.module.css'

const text = validatorMessages.schema

export function SchemaStep() {
  const { state, dispatch } = useValidator()
  const run = useRunValidation()
  const [confirmRegenerate, setConfirmRegenerate] = useState(false)
  const [badFile, setBadFile] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)
  const nameId = useId()
  const reasonId = useId()

  const local = checkSchema(state.schema)
  // Lỗi chung của BE đã nằm trong banner; ở đây chỉ gắn lỗi theo ô.
  const serverProblems = state.runFailure?.problems
  const problems = mergeProblems(local, serverProblems ? { ...serverProblems, general: [] } : null)
  const columns = state.preview?.columns.map((column) => column.name) ?? []
  const match = matchColumns(state.schema, columns)
  const missingRequired = state.schema.fields
    .filter((field) => field.required && match.byField[field.key] === null)
    .map((field) => field.name.trim())

  const blockedReason = !state.preview
    ? text.blocked.noPreview
    : hasProblems(local)
      ? text.blocked.invalid
      : missingRequired.length > 0
        ? text.blocked.missingRequired(missingRequired)
        : null

  async function openFile(file: File | undefined) {
    if (!file) return
    const read = readSchemaFile(await file.text(), state.nextFieldSeq)
    setBadFile(!read.ok)
    if (read.ok) dispatch({ type: 'schemaLoaded', name: read.name, fields: read.fields, nextSeq: read.nextSeq })
  }

  function saveFile() {
    const blob = new Blob([schemaFileContent(state.schema)], { type: 'application/json' })
    saveBlob(blob, schemaFileName(state.schema.name))
  }

  const update = (key: string, patch: FieldPatch) => dispatch({ type: 'fieldUpdated', key, patch })

  return (
    <section className={styles.step} aria-labelledby="validator-schema-title">
      <h2 id="validator-schema-title" className={styles.title} tabIndex={-1}>
        {text.title}
      </h2>
      <p className={styles.intro}>{text.intro}</p>

      <div className={styles.input}>
        <label htmlFor={nameId}>{text.nameLabel}</label>
        <input
          id={nameId}
          value={state.schema.name}
          aria-invalid={problems.name ? true : undefined}
          aria-describedby={problems.name ? `${nameId}-error` : undefined}
          onChange={(event) => dispatch({ type: 'schemaRenamed', name: event.target.value })}
        />
        {problems.name && (
          <p id={`${nameId}-error`} className={styles.error}>
            {problems.name}
          </p>
        )}
      </div>

      <div className={styles.toolbar}>
        <button type="button" className={buttons.small} onClick={() => dispatch({ type: 'fieldAdded' })}>
          <PlusIcon />
          {text.add}
        </button>
        <button
          type="button"
          className={buttons.small}
          disabled={!state.preview}
          onClick={() => (state.schema.fields.length > 0 ? setConfirmRegenerate(true) : dispatch({ type: 'schemaGenerated' }))}
        >
          {text.regenerate}
        </button>
        <button type="button" className={buttons.small} onClick={() => fileRef.current?.click()}>
          {text.openFile}
        </button>
        <input
          ref={fileRef}
          type="file"
          accept=".json,application/json"
          className="sr-only"
          aria-label={text.openFileLabel}
          tabIndex={-1}
          onChange={(event) => {
            const file = event.target.files?.[0]
            event.target.value = ''
            void openFile(file)
          }}
        />
        <button type="button" className={buttons.small} disabled={hasProblems(local)} onClick={saveFile}>
          {text.saveFile}
        </button>
      </div>

      {confirmRegenerate && state.preview && (
        <ConfirmPanel
          message={text.regenerateConfirm(state.schema.fields.length, state.preview.columns.length)}
          confirmLabel={text.regenerateAction}
          cancelLabel={text.cancel}
          onConfirm={() => {
            setConfirmRegenerate(false)
            dispatch({ type: 'schemaGenerated' })
          }}
          onCancel={() => setConfirmRegenerate(false)}
        />
      )}
      {badFile && <ErrorBanner text={{ headline: text.badFile }} />}

      {state.schema.fields.length === 0 ? (
        <p className={styles.muted}>{text.empty}</p>
      ) : (
        <ol className={styles.fields} aria-label={text.fieldsLabel}>
          {state.schema.fields.map((field, index) => (
            <FieldEditor
              key={field.key}
              field={field}
              position={index + 1}
              isFirst={index === 0}
              isLast={index === state.schema.fields.length - 1}
              errors={problems.fields[field.key] ?? {}}
              column={match.byField[field.key]}
              onChange={(patch) => update(field.key, patch)}
              onMove={(offset) => dispatch({ type: 'fieldMoved', key: field.key, offset })}
              onRemove={() => dispatch({ type: 'fieldRemoved', key: field.key })}
            />
          ))}
        </ol>
      )}
      {problems.general.length > 0 && <ErrorBanner text={{ headline: problems.general.join(' · ') }} />}
      {match.extraColumns.length > 0 && <p className={styles.muted}>{text.extraColumns(match.extraColumns)}</p>}

      {state.runFailure && <ErrorBanner text={state.runFailure.text} items={state.runFailure.items} />}

      <div className={styles.actions}>
        <button
          type="button"
          className={buttons.secondary}
          disabled={state.running}
          onClick={() => dispatch({ type: 'navigate', step: 'source' })}
        >
          {validatorMessages.back}
        </button>
        <div className={styles.runGroup}>
          <p role="status" className={styles.status}>
            {state.running && (
              <>
                <SpinnerMark /> {text.running}
              </>
            )}
          </p>
          {blockedReason && !state.running && (
            <p id={reasonId} className={styles.reason}>
              {blockedReason}
            </p>
          )}
          <button
            type="button"
            className={buttons.primary}
            disabled={blockedReason !== null || state.running}
            aria-describedby={blockedReason ? reasonId : undefined}
            onClick={() => void run()}
          >
            {text.run}
          </button>
        </div>
      </div>
    </section>
  )
}

interface FieldEditorProps {
  field: FieldDraft
  position: number
  isFirst: boolean
  isLast: boolean
  errors: Partial<Record<FieldInput, string>>
  /** Cột khớp với field, hoặc null khi không có cột nào. */
  column: string | null | undefined
  onChange: (patch: FieldPatch) => void
  onMove: (offset: -1 | 1) => void
  onRemove: () => void
}

function FieldEditor({ field, position, isFirst, isLast, errors, column, onChange, onMove, onRemove }: FieldEditorProps) {
  const id = useId()
  const group = text.group(position)

  return (
    <li className={styles.field} aria-label={group}>
      <div className={styles.fieldRow}>
        <TextInput id={`${id}-name`} label={text.fieldName} value={field.name} error={errors.name} onChange={(name) => onChange({ name })} />
        <div className={styles.input}>
          <label htmlFor={`${id}-type`}>{text.type}</label>
          <select id={`${id}-type`} value={field.type} onChange={(event) => onChange({ type: event.target.value as FieldType })}>
            {FIELD_TYPES.map((type) => (
              <option key={type} value={type}>
                {text.types[type]}
              </option>
            ))}
          </select>
        </div>
        <label className={styles.check}>
          <input type="checkbox" checked={field.required} onChange={(event) => onChange({ required: event.target.checked })} />
          {text.required}
        </label>
        <label className={styles.check}>
          <input type="checkbox" checked={field.unique} onChange={(event) => onChange({ unique: event.target.checked })} />
          {text.unique}
        </label>
        <div className={styles.fieldButtons}>
          <button type="button" className={buttons.small} aria-label={`${text.moveUp} ${group}`} title={text.moveUp} disabled={isFirst} onClick={() => onMove(-1)}>
            <ArrowUpIcon />
          </button>
          <button type="button" className={buttons.small} aria-label={`${text.moveDown} ${group}`} title={text.moveDown} disabled={isLast} onClick={() => onMove(1)}>
            <ArrowDownIcon />
          </button>
          <button type="button" className={buttons.small} aria-label={`${text.remove} ${group}`} title={text.remove} onClick={onRemove}>
            <TrashIcon />
          </button>
        </div>
      </div>

      {CONSTRAINTS_BY_TYPE[field.type].length > 0 && (
        <div className={styles.fieldRow}>
          {CONSTRAINTS_BY_TYPE[field.type].map((key: ConstraintKey) => (
            <TextInput
              key={key}
              id={`${id}-${key}`}
              label={text.constraints[key]}
              value={field.constraints[key]}
              error={errors[key]}
              placeholder={key === 'format' ? text.formatPlaceholder : undefined}
              wide={key === 'pattern'}
              onChange={(value) => onChange({ constraints: { [key]: value } })}
            />
          ))}
        </div>
      )}

      <p className={styles.match} data-missing={column === null ? (field.required ? 'required' : 'optional') : undefined}>
        {column ? text.column(column) : field.required ? text.noColumnRequired : text.noColumnOptional}
      </p>
    </li>
  )
}

interface TextInputProps {
  id: string
  label: string
  value: string
  error?: string
  placeholder?: string
  wide?: boolean
  onChange: (value: string) => void
}

function TextInput({ id, label, value, error, placeholder, wide = false, onChange }: TextInputProps) {
  return (
    <div className={styles.input} data-wide={wide || undefined}>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        value={value}
        placeholder={placeholder}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {error && (
        <p id={`${id}-error`} className={styles.error}>
          {error}
        </p>
      )}
    </div>
  )
}
