import { useEffect, useId, useRef, useState } from 'react'
import { ApiError, isRetryable, isSessionUnusable } from '../../api/apiError'
import { getPreview } from '../../api/endpoints'
import { toSourcePreview } from '../../api/mappers'
import type { PreviewRow, SessionInfo, SourcePreview } from '../../domain/types'
import { describeApiError, type ErrorText } from '../../shared/describeError'
import { messages, stepLabels } from '../../shared/messages'
import { DataTable, EmptyCell, type DataTableColumn } from '../../shared/ui/DataTable'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { Spinner } from '../../shared/ui/Spinner'
import { useWizard } from '../../wizard/context'
import { canEnter } from '../../wizard/guards'
import { StepActions } from '../../wizard/StepActions'
import styles from './PreviewStep.module.css'

interface Failure {
  text: ErrorText
  /** Lỗi mạng/5xx thì thử lại được; session hết hạn hoặc hỏng thì chỉ còn cách upload lại (design D12). */
  action: 'retry' | 'reupload' | null
}

function toFailure(error: unknown): Failure {
  if (!(error instanceof ApiError)) {
    // Lỗi lập trình (mapper, reducer…): user chỉ thấy câu chung, nên stack phải nằm ở console.
    console.error(error)
    return { text: { headline: messages.unexpected }, action: null }
  }

  const text = describeApiError(error) ?? { headline: messages.unexpected }
  if (isSessionUnusable(error)) return { text, action: 'reupload' }
  return { text, action: isRetryable(error) ? 'retry' : null }
}

export function PreviewStep() {
  const { state, dispatch } = useWizard()
  const { session, preview } = state
  const [failure, setFailure] = useState<Failure | null>(null)
  const [attempt, setAttempt] = useState(0)
  const titleId = useId()
  const titleRef = useRef<HTMLHeadingElement>(null)

  // Tải một lần cho mỗi session; preview đã có trong state thì không gọi lại (spec source-preview).
  // Không khoá điều hướng: đây là request đọc; rời bước thì huỷ, và reducer bỏ response của session cũ.
  useEffect(() => {
    if (!session || preview) return
    const controller = new AbortController()
    getPreview(session.id, { signal: controller.signal })
      .then((dto) => dispatch({ type: 'previewLoaded', sessionId: session.id, preview: toSourcePreview(dto) }))
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.kind === 'aborted') return
        setFailure(toFailure(error))
      })
    return () => controller.abort()
  }, [session, preview, attempt, dispatch])

  if (!session) return null

  const failureAction =
    failure?.action === 'retry'
      ? {
          label: messages.retry,
          onClick: () => {
            // Nút này biến mất cùng khối lỗi; giữ focus trong bước thay vì để rơi về đầu trang (design D14).
            titleRef.current?.focus()
            setFailure(null)
            setAttempt((count) => count + 1)
          },
        }
      : failure?.action === 'reupload'
        ? { label: messages.sessionUnusableAction, onClick: () => dispatch({ type: 'reset' }) }
        : undefined

  const nextGuard = canEnter('schema', state)

  return (
    <section aria-labelledby={titleId} className={styles.step}>
      <h2 id={titleId} ref={titleRef} className={styles.title} tabIndex={-1}>
        {stepLabels.preview}
      </h2>

      <Summary session={session} preview={preview} />

      {/* Luôn có trong DOM, chỉ đổi nội dung: báo lúc đang tải và lúc tải xong. Lỗi đã có role="alert". */}
      <p role="status" className="sr-only">
        {preview
          ? messages.preview.rowsShown(preview.rows.length, preview.totalRows)
          : !failure && messages.preview.loading}
      </p>

      {failure && <ErrorBanner text={failure.text} action={failureAction} />}
      {!preview && !failure && <Spinner label={messages.preview.loading} />}

      {preview && (
        <>
          <DataTable
            label={messages.preview.tableLabel}
            columns={previewColumns(preview.columns)}
            rows={preview.rows}
            rowKey={(row) => row.rowNumber}
          />
          {preview.rows.length === 0 && <EmptyState title={messages.preview.noRows} hint={messages.preview.noRowsHint} />}
        </>
      )}

      <StepActions
        onBack={() => dispatch({ type: 'navigate', step: 'upload' })}
        onNext={() => dispatch({ type: 'navigate', step: 'schema' })}
        nextBlockedReason={nextGuard.allowed ? undefined : nextGuard.reason}
      />
    </section>
  )
}

/** Cột "Dòng" rồi tới các cột nguồn theo đúng thứ tự `columns[]`; ô lấy theo vị trí, không theo tên. */
function previewColumns(names: string[]): DataTableColumn<PreviewRow>[] {
  return [
    { key: 'row-number', header: messages.preview.rowNumber, cell: (row) => row.rowNumber, rowNumber: true },
    ...names.map((name, index) => ({
      key: index,
      header: name,
      cell: (row: PreviewRow) => row.values[index] ?? <EmptyCell />,
    })),
  ]
}

function Summary({ session, preview }: { session: SessionInfo; preview: SourcePreview | null }) {
  return (
    <ul aria-label={messages.preview.summaryLabel} className={styles.summary}>
      <li className={styles.fileName}>{session.fileName}</li>
      <li className={styles.chip}>{session.fileType}</li>
      {preview && <li>{messages.preview.rowsShown(preview.rows.length, preview.totalRows)}</li>}
      {preview?.sheetName && <li>{messages.preview.sheet(preview.sheetName)}</li>}
    </ul>
  )
}
