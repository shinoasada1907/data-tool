import { useEffect, useId, useRef, useState } from 'react'
import { ApiError, isRetryable } from '../../api/apiError'
import { toSessionInfo } from '../../api/mappers'
import { uploadSourceFile } from '../../api/upload'
import { config } from '../../config'
import type { SourceFileType } from '../../domain/types'
import { describeApiError, type ErrorText } from '../../shared/describeError'
import { formatBytes } from '../../shared/format'
import { messages } from '../../shared/messages'
import { ConfirmPanel } from '../../shared/ui/ConfirmPanel'
import { ErrorBanner } from '../../shared/ui/ErrorBanner'
import { ArrowRightIcon, CheckIcon, FileIcon, UploadIcon } from '../../shared/ui/icons'
import { useWizard } from '../../wizard/context'
import { useBusyRequest } from '../../wizard/useBusyRequest'
import { checkSelectedFiles, type FileRejection } from './checkFiles'
import styles from './UploadStep.module.css'

interface PickedFile {
  file: File
  fileType: SourceFileType
}

type Phase =
  | { name: 'idle' }
  | { name: 'confirming'; picked: PickedFile }
  | { name: 'uploading'; picked: PickedFile; percent: number }
  | { name: 'failed'; error: ErrorText; retry?: PickedFile }

function rejectionMessage(reason: FileRejection): string {
  switch (reason) {
    case 'extension':
      return messages.upload.rejected.extension
    case 'multiple':
      return messages.upload.rejected.multiple
    case 'empty':
      return messages.upload.rejected.empty
    case 'tooLarge':
      return messages.upload.rejected.tooLarge(config.maxUploadMb)
  }
}

export function UploadStep() {
  const { state, dispatch } = useWizard()
  const runBusy = useBusyRequest()
  const [phase, setPhase] = useState<Phase>({ name: 'idle' })
  const [dragging, setDragging] = useState(false)
  const controllerRef = useRef<AbortController | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const refocusInput = useRef(false)
  const titleId = useId()

  const pickerLocked = phase.name === 'uploading' || phase.name === 'confirming'

  // Rời bước giữa chừng thì huỷ request đang chạy.
  useEffect(() => () => controllerRef.current?.abort(), [])

  // Sau khi huỷ hoặc lỗi, nút đang giữ focus biến mất; trả focus về ô chọn file.
  useEffect(() => {
    if (refocusInput.current && !pickerLocked) {
      refocusInput.current = false
      inputRef.current?.focus()
    }
  }, [phase, pickerLocked])

  function settle(next: Phase) {
    refocusInput.current = true
    setPhase(next)
  }

  async function upload(picked: PickedFile) {
    // Mỗi lúc chỉ một upload; controller là "khoá" của lần upload đang chạy.
    if (controllerRef.current) return
    const controller = new AbortController()
    controllerRef.current = controller
    setPhase({ name: 'uploading', picked, percent: 0 })

    try {
      const session = await runBusy(() =>
        uploadSourceFile(picked.file, {
          signal: controller.signal,
          onProgress: (percent) =>
            setPhase((current) =>
              current.name === 'uploading' && current.percent !== percent ? { ...current, percent } : current,
            ),
        }),
      )
      // Chỉ khi có session mới thì state cũ mới bị thay (nguyên khối, trong reducer). Upload thay thế mà
      // lỗi hoặc bị huỷ thì session và cấu hình hiện tại vẫn còn nguyên.
      dispatch({ type: 'sessionCreated', session: toSessionInfo(session) })
    } catch (error) {
      if (!(error instanceof ApiError)) {
        settle({ name: 'failed', error: { headline: messages.unexpected } })
      } else if (error.kind === 'aborted') {
        settle({ name: 'idle' })
      } else {
        settle({
          name: 'failed',
          error: describeApiError(error) ?? { headline: messages.unexpected },
          retry: isRetryable(error) ? picked : undefined,
        })
      }
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null
    }
  }

  function handleFiles(files: File[]) {
    if (pickerLocked) return
    const check = checkSelectedFiles(files, config.maxUploadMb)
    if (!check) return
    if (!check.ok) {
      setPhase({ name: 'failed', error: { headline: rejectionMessage(check.reason) } })
      return
    }

    const picked = { file: check.file, fileType: check.fileType }
    if (state.session) {
      setPhase({ name: 'confirming', picked })
    } else {
      void upload(picked)
    }
  }

  const retry = phase.name === 'failed' ? phase.retry : undefined
  const uploading = phase.name === 'uploading' ? phase : null
  const sentAll = uploading !== null && uploading.percent >= 100

  return (
    <section aria-labelledby={titleId} className={styles.step}>
      <h2 id={titleId} className={styles.title} tabIndex={-1}>
        {messages.upload.title}
      </h2>

      {state.session && !uploading && (
        <div className={styles.current}>
          <span className={styles.currentIcon}>
            <CheckIcon size={18} />
          </span>
          <div className={styles.currentBody}>
            <span className={styles.currentLabel}>{messages.upload.currentFile}</span>
            <FileSummary
              name={state.session.fileName}
              sizeBytes={state.session.sizeBytes}
              fileType={state.session.fileType}
            />
          </div>
          <p className={styles.note}>{messages.upload.replaceNote}</p>
        </div>
      )}

      {phase.name === 'confirming' && (
        <ConfirmPanel
          message={messages.upload.confirmReplace(phase.picked.file.name)}
          confirmLabel={messages.upload.confirm}
          cancelLabel={messages.upload.cancel}
          onConfirm={() => void upload(phase.picked)}
          onCancel={() => settle({ name: 'idle' })}
        />
      )}

      {phase.name === 'failed' && (
        <ErrorBanner
          text={phase.error}
          action={retry ? { label: messages.upload.retry, onClick: () => void upload(retry) } : undefined}
        />
      )}

      {/* Lúc đang upload, vùng thả được thay bằng thẻ file (mockup); input vẫn nằm trong DOM, ở trạng thái khoá. */}
      <label
        className={styles.dropZone}
        hidden={uploading !== null}
        data-dragging={dragging || undefined}
        data-disabled={pickerLocked || undefined}
        onDragOver={(event) => {
          event.preventDefault()
          if (pickerLocked) {
            event.dataTransfer.dropEffect = 'none'
            return
          }
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDragging(false)
          handleFiles(Array.from(event.dataTransfer.files))
        }}
      >
        <span className={styles.dropLead}>
          <span className={styles.dropIcon}>
            <UploadIcon size={28} />
          </span>
          <span className={styles.dropText}>{messages.upload.dropZone}</span>
        </span>
        <span className={styles.chooseButton} aria-hidden="true">
          {messages.upload.choose}
          <ArrowRightIcon size={16} />
        </span>
        <input
          ref={inputRef}
          type="file"
          accept=".csv,.xlsx"
          aria-label={messages.upload.inputLabel}
          className={styles.input}
          disabled={pickerLocked}
          onChange={(event) => {
            handleFiles(Array.from(event.target.files ?? []))
            // Cho phép chọn lại đúng file vừa chọn.
            event.target.value = ''
          }}
        />
      </label>

      {uploading && (
        <div role="group" aria-label={messages.upload.inProgressLabel} className={styles.progress}>
          <div className={styles.progressHead}>
            <span className={styles.fileIcon}>
              <FileIcon />
            </span>
            <FileSummary
              name={uploading.picked.file.name}
              sizeBytes={uploading.picked.file.size}
              fileType={uploading.picked.fileType}
              stacked
            />
            {/* Nút Huỷ hiện ra lúc ô chọn file bị khoá, nên nhận focus thay cho ô đó. */}
            <button
              type="button"
              className={styles.cancel}
              onClick={() => controllerRef.current?.abort()}
              autoFocus
            >
              {messages.upload.cancel}
            </button>
          </div>
          <div className={styles.progressBody}>
            <progress max={100} value={uploading.percent} aria-label={messages.upload.progressLabel} />
            <span className={styles.progressText}>
              {sentAll ? messages.upload.serverProcessing : messages.upload.uploading(uploading.percent)}
            </span>
          </div>
        </div>
      )}

      {/* Luôn có trong DOM để screen reader bắt được thay đổi; chỉ đổi nội dung lúc bắt đầu và lúc gửi xong. */}
      <p role="status" className="sr-only">
        {uploading &&
          (sentAll ? messages.upload.serverProcessing : messages.upload.announceStart(uploading.picked.file.name))}
      </p>

      <ul className={styles.hints}>
        <li>
          <span className={styles.hintLabel}>{messages.upload.hintLabels.csv}</span>
          <span>{messages.upload.hintCsv}</span>
        </li>
        <li>
          <span className={styles.hintLabel}>{messages.upload.hintLabels.xlsx}</span>
          <span>{messages.upload.hintXlsx}</span>
        </li>
        <li>
          <span className={styles.hintLabel}>{messages.upload.hintLabels.size}</span>
          <span>{messages.upload.hintMaxSize(config.maxUploadMb)}</span>
        </li>
      </ul>
    </section>
  )
}

function FileSummary({
  name,
  sizeBytes,
  fileType,
  stacked = false,
}: {
  name: string
  sizeBytes: number
  fileType: SourceFileType
  /** Tên ở trên, dung lượng và loại file ở dưới (thẻ đang upload). */
  stacked?: boolean
}) {
  return (
    <span className={styles.file} data-stacked={stacked || undefined}>
      <span className={styles.fileName}>{name}</span>
      <span className={styles.fileMeta}>
        <span>{formatBytes(sizeBytes)}</span>
        <span aria-hidden="true">·</span>
        <span>{fileType}</span>
      </span>
    </span>
  )
}
