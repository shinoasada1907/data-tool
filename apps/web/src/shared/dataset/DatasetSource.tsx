import { useEffect, useId, useRef, useState } from 'react'
import { ApiError } from '../../api/apiError'
import { config } from '../../config'
import { checkSelectedFiles, type FileRejection } from '../../features/upload/checkFiles'
import { describeApiError, type ErrorText } from '../describeError'
import { formatBytes } from '../format'
import { messages } from '../messages'
import buttons from '../ui/Button.module.css'
import { DataTable, EmptyCell, type DataTableColumn } from '../ui/DataTable'
import { ErrorBanner } from '../ui/ErrorBanner'
import { FileIcon, UploadIcon } from '../ui/icons'
import { Spinner } from '../ui/Spinner'
import {
  DATASET_TYPES,
  DELIMITERS,
  ENCODINGS,
  deleteDataset,
  getDatasetPreview,
  uploadDataset,
  type DatasetDto,
  type DatasetPreviewDto,
  type ReadOptions,
} from './datasetApi'
import styles from './DatasetSource.module.css'
import { datasetMessages as text } from './messages'

interface DatasetSourceProps {
  dataset: DatasetDto | null
  /** Lựa chọn của user; preview gọi lại mỗi khi đổi. */
  options: ReadOptions
  /** Null khi chưa đọc xong theo `options` hiện tại: component tự gọi preview. */
  preview: DatasetPreviewDto | null
  /** Công cụ đang chạy việc khác: khoá đổi file và tuỳ chọn. */
  disabled?: boolean
  /** File mới đã lên máy chủ, hoặc null khi user xoá file. Dataset cũ đã được component xoá. */
  onDatasetChange: (dataset: DatasetDto | null) => void
  onOptionsChange: (options: ReadOptions) => void
  onPreviewLoaded: (preview: DatasetPreviewDto) => void
}

type UploadPhase = { name: 'idle' } | { name: 'uploading'; fileName: string; percent: number }

function rejectionMessage(reason: FileRejection): string {
  return reason === 'tooLarge' ? text.rejected.tooLarge(config.maxUploadMb) : text.rejected[reason]
}

function describe(error: unknown): ErrorText | null {
  if (!(error instanceof ApiError)) return { headline: messages.unexpected }
  return describeApiError(error, text.errorOverrides)
}

/**
 * Nguồn dữ liệu của một công cụ toolbox (spec dataset-source): upload vào `/api/datasets`, tuỳ chọn đọc theo định dạng,
 * xem trước. State nằm ở công cụ (design V3); component chỉ giữ tiến độ upload và lỗi.
 */
export function DatasetSource({
  dataset,
  options,
  preview,
  disabled = false,
  onDatasetChange,
  onOptionsChange,
  onPreviewLoaded,
}: DatasetSourceProps) {
  const [upload, setUpload] = useState<UploadPhase>({ name: 'idle' })
  const [uploadError, setUploadError] = useState<ErrorText | null>(null)
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const inputId = useId()

  const previewError = usePreview(dataset, options, preview, onPreviewLoaded)
  const locked = disabled || upload.name === 'uploading'

  // Rời màn hình giữa lúc upload thì huỷ.
  useEffect(() => () => controllerRef.current?.abort(), [])

  async function handleFiles(files: File[]) {
    if (locked) return
    const check = checkSelectedFiles(files, config.maxUploadMb, DATASET_TYPES)
    if (!check) return
    if (!check.ok) {
      setUploadError({ headline: rejectionMessage(check.reason) })
      return
    }

    const controller = new AbortController()
    controllerRef.current = controller
    setUploadError(null)
    setUpload({ name: 'uploading', fileName: check.file.name, percent: 0 })
    try {
      const uploaded = await uploadDataset(check.file, {
        signal: controller.signal,
        onProgress: (percent) => setUpload((current) => (current.name === 'uploading' ? { ...current, percent } : current)),
      })
      if (dataset) deleteDataset(dataset.id)
      onDatasetChange(uploaded)
    } catch (error) {
      setUploadError(describe(error))
    } finally {
      controllerRef.current = null
      setUpload({ name: 'idle' })
    }
  }

  function remove() {
    if (!dataset) return
    deleteDataset(dataset.id)
    setUploadError(null)
    onDatasetChange(null)
  }

  function setOption<K extends keyof ReadOptions>(key: K, value: ReadOptions[K] | undefined) {
    const next = { ...options }
    if (value === undefined) delete next[key]
    else next[key] = value
    onOptionsChange(next)
  }

  const fileInput = (
    <input
      ref={inputRef}
      id={inputId}
      type="file"
      accept=".csv,.xlsx,.json"
      className="sr-only"
      aria-label={text.inputLabel}
      disabled={locked}
      onChange={(event) => {
        const files = Array.from(event.target.files ?? [])
        event.target.value = ''
        void handleFiles(files)
      }}
    />
  )

  return (
    <div className={styles.source}>
      {fileInput}

      {upload.name === 'uploading' ? (
        <div className={styles.uploading}>
          <p role="status">{text.uploading(upload.percent)}</p>
          <progress aria-label={text.progressLabel} max={100} value={upload.percent} />
          <button type="button" className={buttons.small} onClick={() => controllerRef.current?.abort()}>
            {text.cancel}
          </button>
        </div>
      ) : dataset ? (
        <div className={styles.file}>
          <span className={styles.fileIcon}>
            <FileIcon />
          </span>
          <div className={styles.fileBody}>
            <span className={styles.fileLabel}>{text.fileLabel}</span>
            <strong>{dataset.originalFileName}</strong>
            <span className={styles.fileMeta}>
              {dataset.format} · {formatBytes(dataset.sizeBytes)}
            </span>
          </div>
          <button type="button" className={buttons.small} disabled={locked} onClick={() => inputRef.current?.click()}>
            {text.replace}
          </button>
          <button type="button" className={buttons.small} disabled={locked} onClick={remove}>
            {text.remove}
          </button>
        </div>
      ) : (
        <label
          htmlFor={inputId}
          className={styles.dropZone}
          data-dragging={dragging || undefined}
          onDragOver={(event) => {
            event.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault()
            setDragging(false)
            void handleFiles(Array.from(event.dataTransfer.files))
          }}
        >
          <span className={styles.dropIcon}>
            <UploadIcon />
          </span>
          <span className={styles.dropText}>
            <strong>{text.dropZone}</strong>
            <span>
              {text.hintFormats} {text.hintMaxSize(config.maxUploadMb)}
            </span>
          </span>
          <span className={buttons.primary}>{text.choose}</span>
        </label>
      )}

      <p className={styles.retention}>{text.retention}</p>
      {uploadError && <ErrorBanner text={uploadError} />}

      {dataset && (
        <>
          <ReadOptionsForm
            dataset={dataset}
            options={options}
            preview={preview}
            disabled={locked}
            onChange={setOption}
          />
          {previewError ? (
            <ErrorBanner text={previewError.text} action={{ label: text.retry, onClick: previewError.retry }} />
          ) : preview ? (
            <PreviewTable preview={preview} />
          ) : (
            <Spinner label={text.loading} />
          )}
        </>
      )}
    </div>
  )
}

/**
 * Gọi preview khi có dataset mà chưa có preview (công cụ đặt preview về null mỗi khi đổi file hoặc tuỳ chọn). Đổi
 * tuỳ chọn giữa chừng thì huỷ lượt cũ. Lỗi gắn với đúng lượt gọi, nên đổi tuỳ chọn là lỗi cũ biến mất.
 */
function usePreview(
  dataset: DatasetDto | null,
  options: ReadOptions,
  preview: DatasetPreviewDto | null,
  onLoaded: (preview: DatasetPreviewDto) => void,
) {
  const [attempt, setAttempt] = useState(0)
  const [failure, setFailure] = useState<{ key: string; text: ErrorText } | null>(null)
  const onLoadedRef = useRef(onLoaded)
  useEffect(() => {
    onLoadedRef.current = onLoaded
  })

  const datasetId = dataset?.id ?? null
  const optionsKey = JSON.stringify(options)
  const key = `${datasetId}|${optionsKey}|${attempt}`
  const needed = datasetId !== null && preview === null

  useEffect(() => {
    if (!needed || datasetId === null) return
    const controller = new AbortController()
    getDatasetPreview(datasetId, JSON.parse(optionsKey) as ReadOptions, { signal: controller.signal }).then(
      (loaded) => onLoadedRef.current(loaded),
      (error: unknown) => {
        const described = describe(error)
        if (described) setFailure({ key, text: described })
      },
    )
    return () => controller.abort()
  }, [needed, datasetId, optionsKey, key])

  if (!needed || failure?.key !== key) return null
  return { text: failure.text, retry: () => setAttempt((count) => count + 1) }
}

interface ReadOptionsFormProps {
  dataset: DatasetDto
  options: ReadOptions
  preview: DatasetPreviewDto | null
  disabled: boolean
  onChange: <K extends keyof ReadOptions>(key: K, value: ReadOptions[K] | undefined) => void
}

/** Tuỳ chọn đọc theo định dạng; ô chưa chọn hiện giá trị BE thực dùng (spec dataset-source). */
function ReadOptionsForm({ dataset, options, preview, disabled, onChange }: ReadOptionsFormProps) {
  if (dataset.format === 'JSON') return null
  const resolved = preview?.options
  const autoLabel = (key: 'delimiter' | 'encoding', value: string | null) =>
    preview?.autoDetected.includes(key) ? text.auto(value) : text.defaultOption(value)

  return (
    <fieldset className={styles.options} disabled={disabled}>
      <legend>{text.optionsLabel}</legend>

      {dataset.format === 'CSV' && (
        <>
          <label className={styles.option}>
            <span>{text.delimiterLabel}</span>
            <select
              value={options.delimiter ?? ''}
              onChange={(event) => onChange('delimiter', (event.target.value || undefined) as ReadOptions['delimiter'])}
            >
              <option value="">
                {autoLabel('delimiter', resolved?.delimiter ? text.delimiters[resolved.delimiter] : null)}
              </option>
              {DELIMITERS.map((delimiter) => (
                <option key={delimiter} value={delimiter}>
                  {text.delimiters[delimiter]}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.option}>
            <span>{text.encodingLabel}</span>
            <select
              value={options.encoding ?? ''}
              onChange={(event) => onChange('encoding', (event.target.value || undefined) as ReadOptions['encoding'])}
            >
              <option value="">
                {autoLabel('encoding', resolved?.encoding ? text.encodings[resolved.encoding] : null)}
              </option>
              {ENCODINGS.map((encoding) => (
                <option key={encoding} value={encoding}>
                  {text.encodings[encoding]}
                </option>
              ))}
            </select>
          </label>
        </>
      )}

      {dataset.format === 'XLSX' && dataset.sheets && (
        <label className={styles.option}>
          <span>{text.sheetLabel}</span>
          <select value={options.sheet ?? ''} onChange={(event) => onChange('sheet', event.target.value || undefined)}>
            <option value="">{text.defaultOption(preview?.sheetName ?? null)}</option>
            {dataset.sheets.map((sheet) => (
              <option key={sheet.name} value={sheet.name}>
                {sheet.visible ? sheet.name : text.hiddenSheet(sheet.name)}
              </option>
            ))}
          </select>
        </label>
      )}

      <label className={styles.check}>
        <input
          type="checkbox"
          checked={options.hasHeader ?? resolved?.hasHeader ?? true}
          onChange={(event) => onChange('hasHeader', event.target.checked)}
        />
        <span>{text.hasHeader}</span>
      </label>
    </fieldset>
  )
}

type PreviewRow = DatasetPreviewDto['rows'][number]

function PreviewTable({ preview }: { preview: DatasetPreviewDto }) {
  const columns: DataTableColumn<PreviewRow>[] = [
    { key: '#', header: text.rowNumber, rowNumber: true, cell: (row) => row.rowNumber },
    ...preview.columns.map((column, index) => ({
      key: `c${index}`,
      header: column.name,
      cell: (row: PreviewRow) => row.values[index] ?? <EmptyCell />,
    })),
  ]
  const summary = [
    text.summary(preview.totalRows, preview.columns.length),
    preview.blankRowsSkipped > 0 ? text.blankRows(preview.blankRowsSkipped) : null,
    preview.rows.length < preview.totalRows ? text.shown(preview.rows.length) : null,
  ]

  return (
    <div className={styles.preview}>
      <p className={styles.summary}>{summary.filter(Boolean).join(' · ')}</p>
      {preview.rows.length === 0 ? (
        <p className={styles.summary}>{text.noRows}</p>
      ) : (
        <DataTable label={text.tableLabel} columns={columns} rows={preview.rows} rowKey={(row) => row.rowNumber} />
      )}
    </div>
  )
}
