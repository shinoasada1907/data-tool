import { useEffect, useId, useRef, useState } from 'react'
import { ApiError, isSessionUnusable } from '../../api/apiError'
import { downloadErrorReport, downloadValidRows } from '../../api/endpoints'
import { saveBlob } from '../../api/download'
import type { PipelineSummary, SessionInfo } from '../../domain/types'
import type { StaleReason } from '../../wizard/state'
import { describeApiError, type ErrorText } from '../../shared/describeError'
import { messages } from '../../shared/messages'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { SpinnerMark } from '../../shared/ui/Spinner'
import { useWizard } from '../../wizard/context'
import { fallbackFileName, type ExportSuffix } from './fileNames'
import styles from './ExportActions.module.css'

type ExportKind = 'json' | 'csv' | 'errors'

const KINDS: readonly ExportKind[] = ['json', 'csv', 'errors']
const SUFFIXES: Record<ExportKind, ExportSuffix> = { json: '-valid.json', csv: '-valid.csv', errors: '-errors.csv' }

interface ExportActionsProps {
  session: SessionInfo
  summary: PipelineSummary
  /** Kết quả đã cũ thì khoá cả ba nút, lý do khoá theo lý do cũ (design D18). */
  staleReason: StaleReason | null
  /** BE trả `409 RESULT_NOT_AVAILABLE`: đánh dấu kết quả là cũ và đưa focus tới "Chạy lại" (design D18). */
  onResultUnavailable: () => void
}

interface Failure {
  text: ErrorText
  reupload: boolean
}

/**
 * Ba nút tải file (spec result-export). Tải bằng `fetch` + blob (design D10), nên lỗi của BE hiện ra thay vì bị lưu
 * thành file. Không khoá điều hướng: rời bước thì mọi lượt tải đang chạy bị huỷ và không lưu gì (như GET preview, D2).
 */
export function ExportActions({ session, summary, staleReason, onResultUnavailable }: ExportActionsProps) {
  const { dispatch } = useWizard()
  const headingId = useId()
  const reasonIdBase = useId()
  const [downloading, setDownloading] = useState<ReadonlySet<ExportKind>>(new Set())
  const [failure, setFailure] = useState<Failure | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const controllers = useRef(new Set<AbortController>())

  useEffect(() => {
    const active = controllers.current
    return () => {
      for (const controller of active) controller.abort()
    }
  }, [])

  function blockedReason(kind: ExportKind): string | undefined {
    if (staleReason === 'sessionUnusable') return messages.export.sessionUnusableReason
    if (staleReason !== null) return messages.export.staleReason
    if (kind !== 'errors' && summary.valid === 0) return messages.export.noValid
    if (kind === 'errors' && summary.invalid === 0) return messages.export.noInvalid
    return undefined
  }

  async function start(kind: ExportKind) {
    // Nút đang tải chỉ `aria-disabled` để giữ focus, nên cú bấm thứ hai vẫn tới đây và phải bị bỏ qua.
    if (downloading.has(kind) || blockedReason(kind) !== undefined) return
    const controller = new AbortController()
    controllers.current.add(controller)
    setDownloading((current) => new Set(current).add(kind))
    setFailure(null)
    setAnnouncement(messages.export.downloading[kind])
    try {
      const options = { signal: controller.signal }
      const file =
        kind === 'errors'
          ? await downloadErrorReport(session.id, options)
          : await downloadValidRows(session.id, kind, options)
      const fileName = file.fileName ?? fallbackFileName(session.fileName, SUFFIXES[kind])
      saveBlob(file.blob, fileName)
      setAnnouncement(messages.export.saved(fileName))
    } catch (error) {
      if (error instanceof ApiError && error.kind === 'aborted') return
      setAnnouncement('')
      if (error instanceof ApiError && error.code === 'RESULT_NOT_AVAILABLE') {
        onResultUnavailable()
        return
      }
      setFailure(toFailure(error))
    } finally {
      controllers.current.delete(controller)
      setDownloading((current) => {
        const next = new Set(current)
        next.delete(kind)
        return next
      })
    }
  }

  const labels: Record<ExportKind, string> = {
    json: messages.export.json,
    csv: messages.export.csv,
    errors: messages.export.errors,
  }
  // Kết quả cũ: một lý do chung cho cả ba nút; còn lại mỗi nút bị khoá có lý do riêng.
  const reasons = new Map(KINDS.map((kind) => [kind, blockedReason(kind)]))
  const shownReasons = [...new Set([...reasons.values()].filter((reason): reason is string => reason !== undefined))]
  const reasonId = (reason: string) => `${reasonIdBase}-${shownReasons.indexOf(reason)}`

  return (
    <div role="group" aria-labelledby={headingId} className={styles.export}>
      <h3 id={headingId} className={styles.heading}>
        {messages.export.heading}
      </h3>
      <div className={styles.buttons}>
        {KINDS.map((kind) => {
          const reason = reasons.get(kind)
          const busy = downloading.has(kind)
          return (
            <button
              key={kind}
              type="button"
              className={styles.button}
              disabled={reason !== undefined}
              aria-disabled={busy || undefined}
              aria-describedby={reason ? reasonId(reason) : undefined}
              onClick={() => void start(kind)}
            >
              {busy && <SpinnerMark />}
              {labels[kind]}
            </button>
          )
        })}
      </div>
      {shownReasons.map((reason) => (
        <p key={reason} id={reasonId(reason)} className={styles.reason}>
          {reason}
        </p>
      ))}
      {/* Luôn có trong DOM (design D14): báo đang tải và tên file vừa lưu. */}
      <p role="status" className="sr-only">
        {announcement}
      </p>
      {failure && (
        <ErrorBanner
          text={failure.text}
          action={
            failure.reupload
              ? { label: messages.sessionUnusableAction, onClick: () => dispatch({ type: 'reset' }) }
              : undefined
          }
        />
      )}
    </div>
  )
}

function toFailure(error: unknown): Failure {
  if (!(error instanceof ApiError)) {
    // Lỗi lập trình: user chỉ thấy câu chung, nên stack phải nằm ở console.
    console.error(error)
    return { text: { headline: messages.unexpected }, reupload: false }
  }
  return { text: describeApiError(error) ?? { headline: messages.unexpected }, reupload: isSessionUnusable(error) }
}
