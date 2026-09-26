import { useEffect, useEffectEvent, useId, useRef, useState, type KeyboardEvent, type RefObject } from 'react'
import { flushSync } from 'react-dom'
import { ApiError } from '../../api/apiError'
import { getResult } from '../../api/endpoints'
import { toResultPage } from '../../api/mappers'
import type { PipelineSummary, ResultQuery, ResultRow, ResultView, RowError, SessionInfo } from '../../domain/types'
import { codeMessage } from '../../shared/describeError'
import { formatNumber } from '../../shared/format'
import { messages, stepLabels } from '../../shared/messages'
import { DataTable, EmptyCell, type DataTableColumn } from '../../shared/ui/DataTable'
import { EmptyState } from '../../shared/ui/EmptyState'
import { Pagination } from '../../shared/ui/Pagination'
import { Spinner, SpinnerMark } from '../../shared/ui/Spinner'
import { useWizard } from '../../wizard/context'
import { toLoadFailure, type LoadFailure } from '../../wizard/loadFailure'
import { LoadFailureBanner } from '../../wizard/LoadFailureBanner'
import { SaveFailureBanner } from '../../wizard/SaveFailureBanner'
import { isBusy, type ResultState, type StaleReason } from '../../wizard/state'
import { StepActions } from '../../wizard/StepActions'
import { StepHeader } from '../../wizard/StepHeader'
import { useBusyRequest } from '../../wizard/useBusyRequest'
import { useRunPipeline } from '../run/useRunPipeline'
import styles from './ResultStep.module.css'

/** Thứ tự tab trên màn hình. */
const VIEWS: readonly ResultView[] = ['valid', 'invalid']

/** Bước 6: tóm tắt và các dòng kết quả của lần chạy gần nhất (spec result-review). */
export function ResultStep() {
  const { state } = useWizard()
  const { session, result } = state
  if (!session || !result) return null
  return <ResultContent session={session} result={result} />
}

function ResultContent({ session, result }: { session: SessionInfo; result: ResultState }) {
  const { state, dispatch } = useWizard()
  const runBusy = useBusyRequest()
  const titleId = useId()
  const titleRef = useRef<HTMLHeadingElement>(null)
  const rerunRef = useRef<HTMLButtonElement>(null)
  const fieldFilterRef = useRef<HTMLSelectElement>(null)
  const pipeline = useRunPipeline({ headingRef: titleRef })
  // Lỗi tải và câu của vùng status gắn với lần chạy (`summary`): chạy lại thì chúng tự hết hiệu lực.
  const [loadFailure, setLoadFailure] = useState<LoadFailureOf | null>(null)
  const [announcement, setAnnouncement] = useState<{ summary: PipelineSummary; text: string } | null>(null)
  // Truy vấn user chọn gần nhất mà chưa tải xong: tab và bộ lọc hiện ngay lựa chọn đó, bảng vẫn là trang cũ.
  const [pending, setPending] = useState<ResultQuery | null>(null)
  const inFlight = useRef<ResultQuery | null>(null)
  const queued = useRef<ResultQuery | null>(null)
  const { query, page, columns, summary } = result
  const stale = result.stale !== null
  const busy = isBusy(state)
  const shown = pending ?? query
  const failure = loadFailure?.summary === summary ? loadFailure : null
  // Trang đầu của lần chạy mới chưa tải (hoặc đang tải).
  const loadingFirstPage = page === null && !stale && failure === null

  /**
   * Tải một trang. Lựa chọn mới nhất thắng: chọn tiếp trong lúc trang trước còn đang tải thì lựa chọn đó được xếp hàng
   * và tải ngay sau, trang trước không được vẽ ra (review FE-F08/F09; Chrome trên Windows phát `change` ở mỗi lần bấm
   * mũi tên trên select đang đóng, tasks 8.2). Chọn lại đúng trang đang tải (bấm đúp "Sau") thì chỉ chờ nó.
   */
  function load(next: ResultQuery) {
    // Kết quả cũ: BE đã xoá kết quả, chỉ còn "Chạy lại" (design D18).
    if (stale) return
    setLoadFailure(null)
    setPending(next)
    void fetchPages(next)
  }

  async function fetchPages(next: ResultQuery) {
    if (inFlight.current) {
      queued.current = sameQuery(inFlight.current, next) ? null : next
      return
    }
    let current: ResultQuery | null = next
    while (current) {
      const target: ResultQuery = current
      inFlight.current = target
      try {
        const loaded = toResultPage(await runBusy(() => getResult(session.id, target)))
        if (!queued.current) {
          dispatch({ type: 'resultPageLoaded', query: target, page: loaded })
          setAnnouncement({ summary, text: pageAnnouncement(target, loaded.number, loaded.totalPages, loaded.totalElements) })
        }
      } catch (error) {
        if (!queued.current) {
          if (error instanceof ApiError && error.code === 'RESULT_NOT_AVAILABLE') {
            // Mọi nút đổi trang, tab, lọc bị khoá ngay: đưa focus tới việc duy nhất còn làm được.
            inFlight.current = null
            flushSync(() => {
              setPending(null)
              dispatch({ type: 'resultUnavailable', reason: 'unavailable' })
            })
            rerunRef.current?.focus()
            return
          }
          setLoadFailure({ failure: toLoadFailure(error), query: target, summary })
          setAnnouncement(null)
        }
      }
      current = queued.current
      queued.current = null
    }
    inFlight.current = null
    setPending(null)
  }

  // Vừa chạy xong (vào bước, hoặc "Chạy lại" ngay tại bước): tải trang đầu. Nút "Chạy lại" biến mất cùng cảnh báo nên
  // focus có thể đã rơi về đầu trang; khi đó đưa về tiêu đề bước (design D14). Là effect event: chỉ chạy khi có lần
  // chạy mới (`summary` mới), còn `result` và `fetchPages` luôn đọc bản mới nhất.
  const onNewRun = useEffectEvent(() => {
    if (result.page === null && result.stale === null) void fetchPages(result.query)
    if (document.activeElement && document.activeElement !== document.body) return
    titleRef.current?.focus()
  })
  useEffect(() => {
    onNewRun()
  }, [summary])

  const panelId = `${titleId}-panel`
  const filtered = query.field !== null || query.code !== null

  return (
    <section aria-labelledby={titleId} className={styles.step}>
      <StepHeader id={titleId} title={stepLabels.result} intro={messages.result.intro} headingRef={titleRef} />

      {pipeline.failure && <SaveFailureBanner failure={pipeline.failure} />}
      {failure && (
        <LoadFailureBanner failure={failure.failure} retryFocusRef={titleRef} onRetry={() => load(failure.query)} />
      )}

      <StaleNotice
        reason={result.stale}
        busy={busy}
        running={pipeline.running}
        blockedReason={pipeline.blockedReason}
        rerunRef={rerunRef}
        onRerun={pipeline.run}
      />

      <SummaryCards summary={summary} />

      {/* Luôn có trong DOM (design D14): báo lúc đang tải và trang vừa tải xong. */}
      <p role="status" className="sr-only">
        {pending || loadingFirstPage ? messages.result.loading : announcement?.summary === summary ? announcement.text : ''}
      </p>

      <ResultTabs
        summary={summary}
        view={shown.view}
        panelId={panelId}
        disabled={stale}
        onSelect={(view) => {
          if (view !== shown.view) load({ view, page: 0, field: null, code: null })
        }}
      />

      <div
        id={panelId}
        role="tabpanel"
        aria-labelledby={`${panelId}-${shown.view}`}
        aria-busy={pending !== null || loadingFirstPage}
        className={styles.panel}
      >
        {shown.view === 'invalid' && summary.invalid > 0 && (
          <ErrorFilters
            columns={columns}
            summary={summary}
            query={shown}
            disabled={stale}
            fieldFilterRef={fieldFilterRef}
            onChange={(filter) => load({ ...shown, ...filter, page: 0 })}
            onClear={() => {
              // Nút "Xoá lọc" bị khoá ngay khi hết bộ lọc: giữ focus trong cụm lọc (design D14).
              fieldFilterRef.current?.focus()
              load({ ...shown, page: 0, field: null, code: null })
            }}
          />
        )}

        {page === null ? (
          loadingFirstPage && <Spinner label={messages.result.loading} />
        ) : (
          <div className={styles.rows}>
            {page.totalElements === 0 ? (
              <EmptyState
                title={query.view === 'invalid' && filtered ? messages.result.emptyFiltered : messages.result.empty[query.view]}
              />
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
                  onChange={(number) => load({ ...query, page: number })}
                />
              </>
            )}
          </div>
        )}
      </div>

      <StepActions onBack={() => dispatch({ type: 'navigate', step: 'rules' })} />
    </section>
  )
}

interface LoadFailureOf {
  failure: LoadFailure
  /** Truy vấn bị lỗi, để "Thử lại" gửi lại đúng nó. */
  query: ResultQuery
  /** Lần chạy mà lỗi này thuộc về. */
  summary: PipelineSummary
}

function sameQuery(a: ResultQuery, b: ResultQuery): boolean {
  return a.view === b.view && a.page === b.page && a.field === b.field && a.code === b.code
}

/** Câu cho vùng status sau khi tải xong một trang (`page` đếm từ 0). */
function pageAnnouncement(query: ResultQuery, page: number, totalPages: number, totalElements: number): string {
  if (totalElements === 0) return messages.result.empty[query.view]
  return messages.result.pageStatus(messages.result.tableLabel[query.view], page + 1, totalPages)
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
  reason: StaleReason | null
  busy: boolean
  running: boolean
  blockedReason: string | undefined
  rerunRef: RefObject<HTMLButtonElement | null>
  onRerun: () => void
}

/**
 * Cảnh báo kết quả cũ, câu và nút theo lý do (design D18): "Chạy lại", hoặc "Upload lại" khi session không dùng được
 * nữa. Vùng status có sẵn, nên cảnh báo xuất hiện sau 409 được đọc. `rerunRef` trỏ tới nút đang có.
 */
function StaleNotice({ reason, busy, running, blockedReason, rerunRef, onRerun }: StaleNoticeProps) {
  const { dispatch } = useWizard()
  const reasonId = useId()

  return (
    <div role="status" className={styles.staleRegion}>
      {reason === 'sessionUnusable' && (
        <div className={styles.stale}>
          <p className={styles.staleText}>{messages.result.stale.sessionUnusable}</p>
          <div className={styles.staleActions}>
            <button ref={rerunRef} type="button" className={styles.rerun} onClick={() => dispatch({ type: 'reset' })}>
              {messages.sessionUnusableAction}
            </button>
          </div>
        </div>
      )}
      {(reason === 'configChanged' || reason === 'unavailable') && (
        <div className={styles.stale}>
          <p className={styles.staleText}>{messages.result.stale[reason]}</p>
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
            // Kết quả cũ: chỉ khoá tab kia; tab đang chọn vẫn nhận focus để tablist không mất điểm dừng Tab.
            disabled={disabled && !selected}
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

/**
 * `step` đếm từ 0; hiển thị `step + 1` cho khớp số thứ tự bước ở màn Biến đổi & kiểm tra (spec result-review).
 * Lỗi bất ngờ khi map có `rule = "mapping"`, không có bước (BE-F08): mapping không phải một bước biến đổi.
 */
function ruleText(error: RowError): string {
  if (error.stage === 'VALIDATION') return messages.result.validationRule(error.rule)
  if (error.rule === 'mapping' && error.step === null) return messages.result.mappingRule
  return messages.result.transformationRule(error.rule, error.step === null ? null : error.step + 1)
}
