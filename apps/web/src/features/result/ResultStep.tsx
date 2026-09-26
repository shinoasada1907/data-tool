import { useEffect, useId, useRef, useState, type KeyboardEvent, type RefObject } from 'react'
import { flushSync } from 'react-dom'
import { ApiError, isRetryable, isSessionUnusable } from '../../api/apiError'
import { getResult } from '../../api/endpoints'
import { toResultPage } from '../../api/mappers'
import type { PipelineSummary, ResultQuery, ResultRow, ResultView, RowError } from '../../domain/types'
import { codeMessage, describeApiError, type ErrorText } from '../../shared/describeError'
import { formatNumber } from '../../shared/format'
import { messages, stepLabels } from '../../shared/messages'
import { DataTable, EmptyCell, type DataTableColumn } from '../../shared/ui/DataTable'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { Pagination } from '../../shared/ui/Pagination'
import { SpinnerMark } from '../../shared/ui/Spinner'
import { useWizard } from '../../wizard/context'
import { SaveFailureBanner } from '../../wizard/SaveFailureBanner'
import { isBusy } from '../../wizard/state'
import { StepActions } from '../../wizard/StepActions'
import { StepHeader } from '../../wizard/StepHeader'
import { useBusyRequest } from '../../wizard/useBusyRequest'
import { useRunPipeline } from '../run/useRunPipeline'
import styles from './ResultStep.module.css'

/** Thứ tự tab trên màn hình. */
const VIEWS: readonly ResultView[] = ['valid', 'invalid']

interface LoadFailure {
  text: ErrorText
  /** Truy vấn bị lỗi, để "Thử lại" gửi lại đúng nó. */
  query: ResultQuery
  action: 'retry' | 'reupload' | null
}

/** Bước 6: tóm tắt và các dòng kết quả của lần chạy gần nhất (spec result-review). */
export function ResultStep() {
  const { state, dispatch } = useWizard()
  const runBusy = useBusyRequest()
  const titleId = useId()
  const titleRef = useRef<HTMLHeadingElement>(null)
  const rerunRef = useRef<HTMLButtonElement>(null)
  const fieldFilterRef = useRef<HTMLSelectElement>(null)
  const pipeline = useRunPipeline({ headingRef: titleRef })
  const [loadFailure, setLoadFailure] = useState<LoadFailure | null>(null)
  // Truy vấn đang tải: tab và bộ lọc hiện ngay lựa chọn mới trong lúc chờ, bảng vẫn là trang cũ.
  const [pending, setPending] = useState<ResultQuery | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const loadingRef = useRef(false)
  const { session, result } = state
  const summary = result?.summary

  // Chạy lại thành công: nút "Chạy lại" biến mất cùng cảnh báo và focus rơi về đầu trang; đưa về tiêu đề (design D14).
  useEffect(() => {
    if (document.activeElement && document.activeElement !== document.body) return
    titleRef.current?.focus()
  }, [summary])

  if (!session || !result) return null
  const { query, page, stale, columns } = result
  const busy = isBusy(state)
  const shown = pending ?? query

  async function load(next: ResultQuery) {
    // Kết quả cũ: BE đã xoá kết quả, chỉ còn "Chạy lại" (design D18).
    if (!session || loadingRef.current || stale) return
    loadingRef.current = true
    setPending(next)
    setLoadFailure(null)
    try {
      const loaded = toResultPage(await runBusy(() => getResult(session.id, next)))
      dispatch({ type: 'resultPageLoaded', query: next, page: loaded })
      const label = messages.result.tableLabel[next.view]
      setAnnouncement(
        loaded.totalElements === 0 ? emptyMessage(next) : messages.result.pageStatus(label, loaded.number + 1, loaded.totalPages),
      )
    } catch (error) {
      if (error instanceof ApiError && error.code === 'RESULT_NOT_AVAILABLE') {
        // Mọi nút đổi trang, tab, lọc bị khoá ngay: đưa focus tới việc duy nhất còn làm được.
        flushSync(() => dispatch({ type: 'resultUnavailable' }))
        rerunRef.current?.focus()
      } else {
        setLoadFailure(toLoadFailure(error, next))
      }
    } finally {
      loadingRef.current = false
      setPending(null)
    }
  }

  const failureAction =
    loadFailure?.action === 'retry'
      ? { label: messages.retry, onClick: () => void load(loadFailure.query) }
      : loadFailure?.action === 'reupload'
        ? { label: messages.sessionUnusableAction, onClick: () => dispatch({ type: 'reset' }) }
        : undefined
  const panelId = `${titleId}-panel`
  const filtered = query.field !== null || query.code !== null

  return (
    <section aria-labelledby={titleId} className={styles.step}>
      <StepHeader id={titleId} title={stepLabels.result} intro={messages.result.intro} headingRef={titleRef} />

      {pipeline.failure && <SaveFailureBanner failure={pipeline.failure} />}
      {loadFailure && <ErrorBanner text={loadFailure.text} action={failureAction} />}

      <StaleNotice
        stale={stale}
        busy={busy}
        running={pipeline.running}
        blockedReason={pipeline.blockedReason}
        rerunRef={rerunRef}
        onRerun={pipeline.run}
      />

      <SummaryCards summary={result.summary} />

      {/* Luôn có trong DOM (design D14): báo lúc đang tải và trang vừa tải xong. */}
      <p role="status" className="sr-only">
        {pending ? messages.result.loading : announcement}
      </p>

      <ResultTabs
        summary={result.summary}
        view={shown.view}
        panelId={panelId}
        disabled={stale}
        onSelect={(view) => {
          if (view !== shown.view) void load({ view, page: 0, field: null, code: null })
        }}
      />

      <div
        id={panelId}
        role="tabpanel"
        aria-labelledby={`${panelId}-${query.view}`}
        aria-busy={pending !== null}
        className={styles.panel}
      >
        {shown.view === 'invalid' && result.summary.invalid > 0 && (
          <ErrorFilters
            columns={columns}
            summary={result.summary}
            query={shown}
            disabled={stale}
            fieldFilterRef={fieldFilterRef}
            onChange={(filter) => void load({ ...shown, ...filter, page: 0 })}
            onClear={() => {
              // Nút "Xoá lọc" bị khoá ngay khi hết bộ lọc: giữ focus trong cụm lọc (design D14).
              fieldFilterRef.current?.focus()
              void load({ ...shown, page: 0, field: null, code: null })
            }}
          />
        )}

        {page.totalElements === 0 ? (
          <EmptyState title={query.view === 'invalid' && filtered ? messages.result.emptyFiltered : emptyMessage(query)} />
        ) : (
          <>
            <DataTable
              label={messages.result.tableLabel[query.view]}
              columns={resultColumns(columns)}
              rows={page.rows}
              rowKey={(row) => row.rowNumber}
              flaggedLabel={messages.result.flagged}
              detail={(row) => (row.errors.length > 0 ? <ErrorDetails row={row} /> : null)}
            />
            <Pagination
              page={page.number}
              totalPages={page.totalPages}
              disabled={stale}
              onChange={(number) => void load({ ...query, page: number })}
            />
          </>
        )}
      </div>

      <StepActions onBack={() => dispatch({ type: 'navigate', step: 'rules' })} />
    </section>
  )
}

function emptyMessage(query: ResultQuery): string {
  return messages.result.empty[query.view]
}

function toLoadFailure(error: unknown, query: ResultQuery): LoadFailure {
  if (!(error instanceof ApiError)) {
    // Lỗi lập trình: user chỉ thấy câu chung, nên stack phải nằm ở console.
    console.error(error)
    return { text: { headline: messages.unexpected }, query, action: null }
  }
  const text = describeApiError(error) ?? { headline: messages.unexpected }
  if (isSessionUnusable(error)) return { text, query, action: 'reupload' }
  return { text, query, action: isRetryable(error) ? 'retry' : null }
}

function SummaryCards({ summary }: { summary: PipelineSummary }) {
  const cards = [
    { label: messages.result.total, value: summary.total, tone: 'total' },
    { label: messages.result.valid, value: summary.valid, tone: 'valid' },
    { label: messages.result.invalid, value: summary.invalid, tone: 'invalid' },
  ] as const

  return (
    <div role="group" aria-label={messages.result.summaryLabel}>
      <dl className={styles.cards}>
        {cards.map((card) => (
          <div key={card.tone} className={styles.card} data-tone={card.tone}>
            <dt className={styles.cardLabel}>{card.label}</dt>
            <dd className={styles.cardValue}>{formatNumber(card.value)}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

interface StaleNoticeProps {
  stale: boolean
  busy: boolean
  running: boolean
  blockedReason: string | undefined
  rerunRef: RefObject<HTMLButtonElement | null>
  onRerun: () => void
}

/** Cảnh báo kết quả cũ và nút "Chạy lại" (design D18). Vùng status có sẵn, nên cảnh báo xuất hiện sau 409 được đọc. */
function StaleNotice({ stale, busy, running, blockedReason, rerunRef, onRerun }: StaleNoticeProps) {
  const reasonId = useId()

  return (
    <div role="status" className={styles.staleRegion}>
      {stale && (
        <div className={styles.stale}>
          <p className={styles.staleText}>{messages.result.stale}</p>
          <div className={styles.staleActions}>
            <button
              ref={rerunRef}
              type="button"
              className={styles.rerun}
              disabled={busy || blockedReason !== undefined}
              aria-describedby={blockedReason ? reasonId : undefined}
              onClick={onRerun}
            >
              {messages.result.rerun}
            </button>
            {running && (
              <span className={styles.progress}>
                <SpinnerMark />
                {messages.run.running}
              </span>
            )}
            {blockedReason && (
              <p id={reasonId} className={styles.reason}>
                {blockedReason}
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

interface ResultTabsProps {
  summary: PipelineSummary
  view: ResultView
  panelId: string
  disabled: boolean
  onSelect: (view: ResultView) => void
}

/**
 * Tab "Hợp lệ (n)" / "Lỗi (n)". Mũi tên, Home, End chỉ dời focus; Enter hoặc Space mới tải tab đó, vì mỗi lần đổi tab
 * là một request (mẫu tab kích hoạt thủ công của WAI-ARIA).
 */
function ResultTabs({ summary, view, panelId, disabled, onSelect }: ResultTabsProps) {
  const tabs = useRef<(HTMLButtonElement | null)[]>([])
  const counts: Record<ResultView, number> = { valid: summary.valid, invalid: summary.invalid }
  const labels: Record<ResultView, string> = { valid: messages.result.valid, invalid: messages.result.invalid }

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const last = VIEWS.length - 1
    const target =
      event.key === 'ArrowRight'
        ? (index + 1) % VIEWS.length
        : event.key === 'ArrowLeft'
          ? (index + last) % VIEWS.length
          : event.key === 'Home'
            ? 0
            : event.key === 'End'
              ? last
              : null
    if (target === null) return
    event.preventDefault()
    tabs.current[target]?.focus()
  }

  return (
    <div role="tablist" aria-label={messages.result.tabsLabel} className={styles.tabs}>
      {VIEWS.map((candidate, index) => {
        const selected = candidate === view
        return (
          <button
            key={candidate}
            ref={(element) => {
              tabs.current[index] = element
            }}
            id={`${panelId}-${candidate}`}
            type="button"
            role="tab"
            className={styles.tab}
            aria-selected={selected}
            aria-controls={panelId}
            tabIndex={selected ? 0 : -1}
            disabled={disabled}
            onKeyDown={(event) => onKeyDown(event, index)}
            onClick={() => onSelect(candidate)}
          >
            {messages.result.tab(labels[candidate], counts[candidate])}
          </button>
        )
      })}
    </div>
  )
}

interface ErrorFiltersProps {
  columns: readonly string[]
  summary: PipelineSummary
  query: ResultQuery
  disabled: boolean
  fieldFilterRef: RefObject<HTMLSelectElement | null>
  onChange: (filter: Pick<ResultQuery, 'field' | 'code'>) => void
  onClear: () => void
}

/**
 * Lọc tab Lỗi theo field và mã lỗi (spec result-review). Field theo thứ tự schema chứ không theo key của
 * `errorCountsByField`: JS đưa key dạng số ("1", "2024") lên đầu object.
 */
function ErrorFilters({ columns, summary, query, disabled, fieldFilterRef, onChange, onClear }: ErrorFiltersProps) {
  const fieldId = useId()
  const codeId = useId()
  const fields = columns.flatMap((name) => {
    const count = ownCount(summary.errorCountsByField, name)
    return count > 0 ? [{ name, count }] : []
  })
  const codes = Object.entries(summary.errorCountsByCode).filter(([, count]) => count > 0)

  return (
    <div role="group" aria-label={messages.result.filtersLabel} className={styles.filters}>
      <div className={styles.filter}>
        <label htmlFor={fieldId} className={styles.filterLabel}>
          {messages.result.fieldFilter}
        </label>
        <select
          id={fieldId}
          ref={fieldFilterRef}
          className={styles.select}
          value={query.field ?? ''}
          disabled={disabled}
          onChange={(event) => onChange({ field: event.target.value || null, code: query.code })}
        >
          <option value="">{messages.result.allFields}</option>
          {fields.map(({ name, count }) => (
            <option key={name} value={name}>
              {messages.result.filterOption(name, count)}
            </option>
          ))}
        </select>
      </div>
      <div className={styles.filter}>
        <label htmlFor={codeId} className={styles.filterLabel}>
          {messages.result.codeFilter}
        </label>
        <select
          id={codeId}
          className={styles.select}
          value={query.code ?? ''}
          disabled={disabled}
          onChange={(event) => onChange({ field: query.field, code: event.target.value || null })}
        >
          <option value="">{messages.result.allCodes}</option>
          {codes.map(([code, count]) => (
            <option key={code} value={code}>
              {messages.result.filterOption(codeMessage(code) ?? code, count)}
            </option>
          ))}
        </select>
      </div>
      <button
        type="button"
        className={styles.clear}
        disabled={disabled || (query.field === null && query.code === null)}
        onClick={onClear}
      >
        {messages.result.clearFilters}
      </button>
    </div>
  )
}

/** hasOwn: tên field như "constructor" không được lấy nhầm thuộc tính kế thừa của object. */
function ownCount(counts: Readonly<Record<string, number>>, key: string): number {
  return Object.hasOwn(counts, key) ? counts[key] : 0
}

/** Cột "Dòng" rồi các field theo thứ tự schema lúc chạy; ô lấy theo tên field, không theo thứ tự key của `values`. */
function resultColumns(columns: readonly string[]): DataTableColumn<ResultRow>[] {
  return [
    { key: 'row-number', header: messages.result.rowNumber, cell: (row) => row.rowNumber, rowNumber: true },
    ...columns.map((name, index) => ({
      key: index,
      header: name,
      cell: (row: ResultRow) => formatValue(Object.hasOwn(row.values, name) ? row.values[name] : null),
      flagged: (row: ResultRow) => row.errors.some((error) => error.fieldName === name),
    })),
  ]
}

/** Giá trị như BE trả: dòng hợp lệ đã ép kiểu (số, boolean), dòng lỗi là chuỗi; null hiện placeholder. */
function formatValue(value: unknown) {
  if (value === null || value === undefined) return <EmptyCell />
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value)
}

/** Danh sách lỗi dưới một dòng: field, nhãn mã lỗi, mã, rule (hoặc bước biến đổi), giá trị nguồn, message của BE. */
function ErrorDetails({ row }: { row: ResultRow }) {
  return (
    <ul aria-label={messages.result.rowErrors(row.rowNumber)} className={styles.errors}>
      {row.errors.map((error, index) => (
        <li key={index} className={styles.error}>
          <span className={styles.errorField}>{error.fieldName}</span>: {codeMessage(error.code) ?? error.code}{' '}
          <code className={styles.errorCode}>({error.code})</code> — {ruleText(error)}. {messages.result.sourceValue}:{' '}
          {error.sourceValue === null ? (
            messages.emptyCell
          ) : (
            <span className={styles.sourceValue}>“{error.sourceValue}”</span>
          )}
          .{' '}
          <span lang="en" className={styles.errorMessage}>
            {error.message}
          </span>
        </li>
      ))}
    </ul>
  )
}

/** `step` đếm từ 0; hiển thị `step + 1` cho khớp số thứ tự bước ở màn Biến đổi & kiểm tra (spec result-review). */
function ruleText(error: RowError): string {
  return error.stage === 'TRANSFORMATION'
    ? messages.result.transformationRule(error.rule, error.step === null ? null : error.step + 1)
    : messages.result.validationRule(error.rule)
}
