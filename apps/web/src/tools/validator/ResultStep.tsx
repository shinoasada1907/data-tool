import { useEffect, useId, useState } from 'react'
import { ApiError } from '../../api/apiError'
import { saveBlob } from '../../api/download'
import type { OutputFormat } from '../../shared/output/formats'
import { OutputFormatPicker } from '../../shared/output/OutputFormatPicker'
import { codeMessage, describeApiError, type ErrorText } from '../../shared/describeError'
import { formatNumber } from '../../shared/format'
import { messages } from '../../shared/messages'
import buttons from '../../shared/ui/Button.module.css'
import { DataTable, EmptyCell, type DataTableColumn } from '../../shared/ui/DataTable'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { Pagination } from '../../shared/ui/Pagination'
import { Spinner } from '../../shared/ui/Spinner'
import { useValidator } from './context'
import { validatorMessages } from './messages'
import { checkSchema, hasProblems } from './schema'
import type { ResultState } from './state'
import { useRunValidation } from './useRunValidation'
import styles from './Validator.module.css'
import { exportRun, getRows, type ExportContent, type RowsQuery, type RowsView, type ValidatorRow } from './validatorApi'

const text = validatorMessages.result

function describe(error: unknown): ErrorText | null {
  return error instanceof ApiError ? describeApiError(error) : { headline: messages.unexpected }
}

export function ResultStep() {
  const { state, dispatch } = useValidator()
  const run = useRunValidation()
  const result = state.result
  const loadError = useRowsLoader(result)
  if (!result) return null

  const { run: runDto, query, page } = result
  const { summary, compatibility } = runDto
  const setQuery = (next: Partial<RowsQuery>) => dispatch({ type: 'queryChanged', query: { ...query, page: 0, ...next } })

  // Chạy lại được ngay khi nguồn đã đọc xong và schema không có lỗi; không thì về bước cần sửa.
  function rerun() {
    if (!state.preview) dispatch({ type: 'navigate', step: 'source' })
    else if (hasProblems(checkSchema(state.schema))) dispatch({ type: 'navigate', step: 'schema' })
    else void run()
  }

  return (
    <section className={styles.step} aria-labelledby="validator-result-title">
      <h2 id="validator-result-title" className={styles.title} tabIndex={-1}>
        {text.title}
      </h2>

      {result.expired ? (
        <ErrorBanner text={{ headline: text.expired }} action={{ label: text.rerun, onClick: rerun }} />
      ) : (
        result.stale && (
          <div className={styles.notice} role="status">
            <p>{text.stale}</p>
            <button type="button" className={buttons.small} disabled={state.running} onClick={rerun}>
              {state.running ? validatorMessages.schema.running : text.rerun}
            </button>
          </div>
        )
      )}

      <dl className={styles.tiles} aria-label={text.summaryLabel}>
        <Tile label={text.total} value={summary.totalRows} />
        <Tile label={text.valid} value={summary.validRows} tone="valid" />
        <Tile label={text.invalid} value={summary.invalidRows} tone="invalid" />
        <Tile label={text.errors} value={summary.errorCount} tone="invalid" />
      </dl>
      <ul className={styles.compat}>
        <li>{text.matched(compatibility.matched.length, runDto.fields.length)}</li>
        {compatibility.missingOptional.length > 0 && <li>{text.missingOptional(compatibility.missingOptional)}</li>}
        {compatibility.extraColumns.length > 0 && <li>{text.extraColumns(compatibility.extraColumns)}</li>}
      </ul>

      <div className={styles.tabs} role="group" aria-label={text.tabsLabel}>
        {(['INVALID', 'VALID'] as const).map((view) => (
          <button
            key={view}
            type="button"
            className={styles.tab}
            aria-pressed={query.view === view}
            onClick={() => query.view !== view && setQuery({ view, field: null, code: null })}
          >
            {text.tab(view === 'VALID' ? text.valid : text.invalid, view === 'VALID' ? summary.validRows : summary.invalidRows)}
          </button>
        ))}
      </div>

      {query.view === 'INVALID' && summary.invalidRows > 0 && <Filters result={result} onChange={setQuery} />}

      {loadError ? (
        <ErrorBanner text={loadError.text} action={{ label: text.retry, onClick: loadError.retry }} />
      ) : !page ? (
        result.expired ? null : <Spinner label={text.loading} />
      ) : page.rows.length === 0 ? (
        <p className={styles.muted}>{emptyText(query)}</p>
      ) : (
        <>
          <ResultTable fields={runDto.fields} view={query.view} rows={page.rows} />
          {page.page.totalPages > 1 && (
            <Pagination page={query.page} totalPages={page.page.totalPages} onChange={(next) => setQuery({ ...query, page: next })} />
          )}
        </>
      )}

      {/* Run mới thì khung tải làm lại từ đầu: không giữ "Đã tải …" hay lỗi của run cũ. */}
      <ExportPanel key={runDto.id} result={result} />
    </section>
  )
}

function emptyText(query: RowsQuery): string {
  if (query.view === 'VALID') return text.emptyValid
  return query.field !== null || query.code !== null ? text.emptyFiltered : text.emptyInvalid
}

function Tile({ label, value, tone }: { label: string; value: number; tone?: 'valid' | 'invalid' }) {
  return (
    <div className={styles.tile} data-tone={tone}>
      <dt>{label}</dt>
      <dd>{formatNumber(value)}</dd>
    </div>
  )
}

/** Tải trang của `query` khi chưa có; lỗi gắn với đúng lượt tải, `RUN_NOT_FOUND` thì đánh dấu kết quả đã hết hạn. */
function useRowsLoader(result: ResultState | null) {
  const { dispatch } = useValidator()
  const [attempt, setAttempt] = useState(0)
  const [failure, setFailure] = useState<{ key: string; text: ErrorText } | null>(null)
  const runId = result?.run.id ?? null
  const queryKey = result ? JSON.stringify(result.query) : ''
  const key = `${runId}|${queryKey}|${attempt}`
  const needed = result !== null && result.page === null && !result.expired

  useEffect(() => {
    if (!needed || runId === null) return
    const controller = new AbortController()
    const query = JSON.parse(queryKey) as RowsQuery
    getRows(runId, query, { signal: controller.signal }).then(
      (page) => dispatch({ type: 'rowsLoaded', runId, query, page }),
      (error: unknown) => {
        if (error instanceof ApiError && error.code === 'RUN_NOT_FOUND') {
          dispatch({ type: 'resultExpired', runId })
          return
        }
        const described = describe(error)
        if (described) setFailure({ key, text: described })
      },
    )
    return () => controller.abort()
  }, [needed, runId, queryKey, key, dispatch])

  if (!needed || failure?.key !== key) return null
  return { text: failure.text, retry: () => setAttempt((count) => count + 1) }
}

function Filters({ result, onChange }: { result: ResultState; onChange: (next: Partial<RowsQuery>) => void }) {
  const { run, query } = result
  const fieldId = useId()
  const codeId = useId()
  const byField = run.summary.errorCountsByField
  // Theo thứ tự schema, không theo key của object (JS đưa key dạng số lên đầu).
  const fields = run.fields.filter((field) => Object.hasOwn(byField, field))
  const codes = Object.entries(run.summary.errorCountsByCode)

  return (
    <div className={styles.filters} role="group" aria-label={text.filtersLabel}>
      <div className={styles.input}>
        <label htmlFor={fieldId}>{text.fieldFilter}</label>
        <select id={fieldId} value={query.field ?? ''} onChange={(event) => onChange({ field: event.target.value || null })}>
          <option value="">{text.allFields}</option>
          {fields.map((field) => (
            <option key={field} value={field}>
              {text.filterOption(field, byField[field])}
            </option>
          ))}
        </select>
      </div>
      <div className={styles.input}>
        <label htmlFor={codeId}>{text.codeFilter}</label>
        <select id={codeId} value={query.code ?? ''} onChange={(event) => onChange({ code: event.target.value || null })}>
          <option value="">{text.allCodes}</option>
          {codes.map(([code, count]) => (
            <option key={code} value={code}>
              {text.filterOption(codeMessage(code) ?? code, count)}
            </option>
          ))}
        </select>
      </div>
      {(query.field !== null || query.code !== null) && (
        <button type="button" className={buttons.small} onClick={() => onChange({ field: null, code: null })}>
          {text.clearFilters}
        </button>
      )}
    </div>
  )
}

function ResultTable({ fields, view, rows }: { fields: readonly string[]; view: RowsView; rows: readonly ValidatorRow[] }) {
  const columns: DataTableColumn<ValidatorRow>[] = [
    { key: '#', header: text.rowNumber, rowNumber: true, cell: (row) => row.rowNumber },
    ...fields.map((field, index) => ({
      key: `f${index}`,
      header: field,
      cell: (row: ValidatorRow) => row.values[index] ?? <EmptyCell />,
      flagged: (row: ValidatorRow) => row.errors.some((error) => error.field === field),
    })),
  ]

  return (
    <DataTable
      label={view === 'VALID' ? text.valid : text.invalid}
      columns={columns}
      rows={rows}
      rowKey={(row) => row.rowNumber}
      flaggedLabel={text.flagged}
      detail={(row) =>
        row.errors.length === 0 ? null : (
          <ul className={styles.rowErrors} aria-label={text.rowErrors(row.rowNumber)}>
            {row.errors.map((error, index) => (
              <li key={index}>
                <strong>{error.field}</strong>: {codeMessage(error.code) ?? error.code}{' '}
                <span className={styles.muted}>
                  — {error.message} ({text.value}: {error.value ?? '∅'})
                </span>
              </li>
            ))}
          </ul>
        )
      }
    />
  )
}

function ExportPanel({ result }: { result: ResultState }) {
  const { dispatch } = useValidator()
  const { summary } = result.run
  const [content, setContent] = useState<ExportContent>(summary.errorCount > 0 ? 'ERRORS' : 'VALID')
  const [format, setFormat] = useState<OutputFormat>('CSV')
  const [status, setStatus] = useState<{ kind: 'idle' } | { kind: 'busy' } | { kind: 'saved'; fileName: string } | { kind: 'failed'; text: ErrorText }>({ kind: 'idle' })
  const contentId = useId()
  const t = validatorMessages.export
  const counts: Record<ExportContent, number> = { VALID: summary.validRows, INVALID: summary.invalidRows, ERRORS: summary.errorCount }
  const empty = counts[content] === 0

  async function exportFile() {
    setStatus({ kind: 'busy' })
    try {
      const file = await exportRun(result.run.id, content, format)
      const fileName = file.fileName ?? `${t.fallbackName}.${format.toLowerCase()}`
      saveBlob(file.blob, fileName)
      setStatus({ kind: 'saved', fileName })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'RUN_NOT_FOUND') dispatch({ type: 'resultExpired', runId: result.run.id })
      const described = describe(error)
      setStatus(described ? { kind: 'failed', text: described } : { kind: 'idle' })
    }
  }

  return (
    <section className={styles.export} aria-labelledby={`${contentId}-heading`}>
      <h3 id={`${contentId}-heading`}>{t.heading}</h3>
      <div className={styles.exportRow}>
        <div className={styles.input}>
          <label htmlFor={contentId}>{t.contentLabel}</label>
          <select id={contentId} value={content} onChange={(event) => setContent(event.target.value as ExportContent)}>
            {(Object.keys(t.contents) as ExportContent[]).map((key) => (
              <option key={key} value={key}>
                {`${t.contents[key]} (${formatNumber(counts[key])})`}
              </option>
            ))}
          </select>
        </div>
        <OutputFormatPicker label={t.formatLabel} value={format} onChange={setFormat} />
        <button
          type="button"
          className={buttons.primary}
          disabled={empty || result.expired || status.kind === 'busy'}
          onClick={() => void exportFile()}
        >
          {status.kind === 'busy' ? t.downloading : t.download}
        </button>
      </div>
      <p role="status" className={styles.status}>
        {empty ? t.nothing : status.kind === 'saved' ? t.saved(status.fileName) : ''}
      </p>
      {status.kind === 'failed' && <ErrorBanner text={status.text} />}
    </section>
  )
}
