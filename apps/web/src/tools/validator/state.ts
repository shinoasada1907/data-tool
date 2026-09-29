import type { FieldKey } from '../../domain/types'
import type { ErrorText } from '../../shared/describeError'
import type { DatasetDto, DatasetPreviewDto, ReadOptions } from '../../shared/dataset/datasetApi'
import { fieldsFromColumns, newField, type ConstraintKey, type FieldDraft, type SchemaDraft, type SchemaProblems } from './schema'
import type { RowsQuery, ValidatorRowsDto, ValidatorRunDto } from './validatorApi'

export const STEPS = ['source', 'schema', 'result'] as const
export type StepId = (typeof STEPS)[number]

/** Lỗi của lần chạy gần nhất: dòng chính, lỗi gắn vào ô (theo `pointer`), và các dòng chi tiết khác. */
export interface RunFailure {
  text: ErrorText
  problems: SchemaProblems | null
  items: string[]
}

/** Kết quả là ảnh chụp của một run (design V8). */
export interface ResultState {
  run: ValidatorRunDto
  query: RowsQuery
  /** Null khi trang của `query` chưa tải xong. */
  page: ValidatorRowsDto | null
  /** Schema, tuỳ chọn hoặc file đã đổi sau lần chạy này. */
  stale: boolean
  /** BE báo `RUN_NOT_FOUND`: không xem hay tải thêm được nữa. */
  expired: boolean
}

export interface ValidatorState {
  step: StepId
  running: boolean
  /** Tăng mỗi khi nguồn hoặc schema đổi: so với lúc bắt đầu chạy để biết kết quả về tới đã cũ chưa. */
  revision: number
  dataset: DatasetDto | null
  options: ReadOptions
  /** Null khi chưa đọc xong theo `options` hiện tại. */
  preview: DatasetPreviewDto | null
  schema: SchemaDraft
  /** Đã sinh, mở file hoặc sửa schema: từ đó vào bước Schema không tự sinh lại nữa. */
  schemaStarted: boolean
  nextFieldSeq: number
  runFailure: RunFailure | null
  result: ResultState | null
}

export const initialValidatorState: ValidatorState = {
  step: 'source',
  running: false,
  revision: 0,
  dataset: null,
  options: {},
  preview: null,
  schema: { name: '', fields: [] },
  schemaStarted: false,
  nextFieldSeq: 1,
  runFailure: null,
  result: null,
}

export interface FieldPatch {
  name?: string
  type?: FieldDraft['type']
  required?: boolean
  unique?: boolean
  constraints?: Partial<Record<ConstraintKey, string>>
}

export type ValidatorAction =
  | { type: 'datasetChanged'; dataset: DatasetDto | null }
  | { type: 'optionsChanged'; options: ReadOptions }
  | { type: 'previewLoaded'; preview: DatasetPreviewDto }
  | { type: 'navigate'; step: StepId }
  | { type: 'schemaGenerated' }
  | { type: 'schemaLoaded'; name: string; fields: FieldDraft[]; nextSeq: number }
  | { type: 'schemaRenamed'; name: string }
  | { type: 'fieldAdded' }
  | { type: 'fieldUpdated'; key: FieldKey; patch: FieldPatch }
  | { type: 'fieldRemoved'; key: FieldKey }
  | { type: 'fieldMoved'; key: FieldKey; offset: -1 | 1 }
  | { type: 'runStarted' }
  | { type: 'runFailed'; failure: RunFailure }
  /** `revision`: của state lúc bấm chạy. */
  | { type: 'runCompleted'; run: ValidatorRunDto; revision: number }
  | { type: 'queryChanged'; query: RowsQuery }
  | { type: 'rowsLoaded'; runId: string; query: RowsQuery; page: ValidatorRowsDto }
  | { type: 'resultExpired'; runId: string }

/** Bước có vào được không; trả lý do khi bị khoá (dùng cho stepper). */
export function lockReason(step: StepId, state: ValidatorState): 'needPreview' | 'needResult' | null {
  if (step === 'schema' && !state.preview) return 'needPreview'
  if (step === 'result' && !state.result) return 'needResult'
  return null
}

export function validatorReducer(state: ValidatorState, action: ValidatorAction): ValidatorState {
  switch (action.type) {
    case 'datasetChanged':
      return changed({
        ...state,
        dataset: action.dataset,
        options: {},
        preview: null,
        step: action.dataset ? state.step : 'source',
      })

    case 'optionsChanged':
      return changed({ ...state, options: action.options, preview: null })

    case 'previewLoaded':
      return action.preview.datasetId === state.dataset?.id ? { ...state, preview: action.preview } : state

    case 'navigate': {
      if (lockReason(action.step, state)) return state
      const next = { ...state, step: action.step }
      return action.step === 'schema' && !state.schemaStarted ? generate(next) : next
    }

    case 'schemaGenerated':
      return state.preview ? generate(state) : state

    case 'schemaLoaded':
      return edited(state, { name: action.name, fields: action.fields }, action.nextSeq)

    case 'schemaRenamed':
      return edited(state, { ...state.schema, name: action.name })

    case 'fieldAdded':
      return edited(state, { ...state.schema, fields: [...state.schema.fields, newField(`f${state.nextFieldSeq}`)] }, state.nextFieldSeq + 1)

    case 'fieldUpdated':
      return edited(state, {
        ...state.schema,
        fields: state.schema.fields.map((field) => {
          if (field.key !== action.key) return field
          const { constraints, ...rest } = action.patch
          return { ...field, ...rest, constraints: { ...field.constraints, ...constraints } }
        }),
      })

    case 'fieldRemoved':
      return edited(state, { ...state.schema, fields: state.schema.fields.filter((field) => field.key !== action.key) })

    case 'fieldMoved': {
      const fields = [...state.schema.fields]
      const from = fields.findIndex((field) => field.key === action.key)
      const to = from + action.offset
      if (from === -1 || to < 0 || to >= fields.length) return state
      ;[fields[from], fields[to]] = [fields[to], fields[from]]
      return edited(state, { ...state.schema, fields })
    }

    case 'runStarted':
      return { ...state, running: true, runFailure: null }

    case 'runFailed':
      return { ...state, running: false, runFailure: action.failure, step: 'schema' }

    case 'runCompleted': {
      const view = action.run.summary.invalidRows > 0 ? 'INVALID' : 'VALID'
      return {
        ...state,
        running: false,
        step: 'result',
        result: {
          run: action.run,
          query: { view, page: 0, field: null, code: null },
          page: null,
          stale: action.revision !== state.revision,
          expired: false,
        },
      }
    }

    case 'queryChanged':
      return state.result ? { ...state, result: { ...state.result, query: action.query, page: null } } : state

    case 'rowsLoaded': {
      const result = state.result
      if (!result || result.run.id !== action.runId || !sameQuery(result.query, action.query)) return state
      return { ...state, result: { ...result, page: action.page } }
    }

    case 'resultExpired':
      return state.result?.run.id === action.runId ? { ...state, result: { ...state.result, expired: true } } : state
  }
}

function sameQuery(a: RowsQuery, b: RowsQuery): boolean {
  return a.view === b.view && a.page === b.page && a.field === b.field && a.code === b.code
}

/** Nguồn hoặc schema đổi: kết quả đang có thành cũ. */
function changed(state: ValidatorState): ValidatorState {
  return {
    ...state,
    revision: state.revision + 1,
    result: state.result && !state.result.stale ? { ...state.result, stale: true } : state.result,
  }
}

/** Schema đổi: lỗi BE của lần chạy trước không còn đúng chỗ nữa nên bỏ. */
function edited(state: ValidatorState, schema: SchemaDraft, nextFieldSeq = state.nextFieldSeq): ValidatorState {
  return changed({ ...state, schema, nextFieldSeq, schemaStarted: true, runFailure: null })
}

function generate(state: ValidatorState): ValidatorState {
  if (!state.preview) return state
  const { fields, nextSeq } = fieldsFromColumns(state.preview.columns, state.nextFieldSeq)
  const name = state.schema.name.trim() ? state.schema.name : baseName(state.dataset?.originalFileName ?? '')
  return edited(state, { name, fields }, nextSeq)
}

function baseName(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot > 0 ? fileName.slice(0, dot) : fileName
}
