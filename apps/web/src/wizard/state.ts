import type {
  FieldKey,
  FieldMapping,
  FieldType,
  MappingDraft,
  PipelineSummary,
  ResultPage,
  ResultQuery,
  SessionInfo,
  SourcePreview,
  TargetField,
  TransformationId,
  TransformationsDraft,
  TransformationType,
  UserRule,
  ValidationsDraft,
} from '../domain/types'

export const STEPS = ['upload', 'preview', 'schema', 'mapping', 'rules', 'result'] as const
export type StepId = (typeof STEPS)[number]

/** Một phần cấu hình: bản đang sửa, và bản đó đã được PUT thành công và chưa sửa gì từ đó hay chưa (design D2). */
export interface Section<T> {
  draft: T
  saved: boolean
}

/**
 * Vì sao kết quả không còn khớp với BE (design D18). Câu cảnh báo và việc còn làm được đi theo lý do:
 * - `configChanged`: user sửa cấu hình sau lần chạy; chạy lại là đúng.
 * - `unavailable`: BE báo không còn kết quả (`409 RESULT_NOT_AVAILABLE`), hoặc process lỗi mà không biết BE đã làm gì
 *   (hết giờ, mất mạng, 5xx); chạy lại có thể được.
 * - `sessionUnusable`: session đã FAILED hoặc không còn (D12); chạy lại chắc chắn lỗi, chỉ còn cách upload lại.
 */
export type StaleReason = 'configChanged' | 'unavailable' | 'sessionUnusable'

/** Kết quả của lần chạy gần nhất (design D2, D18). */
export interface ResultState {
  summary: PipelineSummary
  /** Tên field (đã chuẩn hoá) theo thứ tự schema lúc chạy: cột của bảng kết quả, không lấy từ key của `values`. */
  columns: readonly string[]
  /** Tab, trang và bộ lọc của trang đang xem (hoặc sắp tải, khi `page` còn null). */
  query: ResultQuery
  /**
   * Null khi vừa chạy xong mà trang đầu chưa tải: bước Kết quả tự tải, để GET lỗi thì "Thử lại" chỉ tải lại trang chứ
   * không chạy lại cả pipeline (review FE-F08/F09).
   */
  page: ResultPage | null
  /** Khác null: kết quả đã cũ, chỉ còn xem trang đang có và làm việc mà lý do cho phép (chạy lại hoặc upload lại). */
  stale: StaleReason | null
}

export interface WizardState {
  step: StepId
  /** Số request làm đổi state đang chạy. Lớn hơn 0 thì khoá điều hướng (xem isBusy). */
  pendingRequests: number
  /** Số thứ tự cho key của field kế tiếp (`f1`, `f2`, …); không bao giờ dùng lại key (design D3). */
  nextFieldSeq: number
  /** Số thứ tự cho id của bước biến đổi kế tiếp (`t1`, `t2`, …). */
  nextTransformationSeq: number
  session: SessionInfo | null
  /** Tải một lần cho mỗi session; null khi chưa tải xong (spec source-preview). */
  preview: SourcePreview | null
  schema: Section<TargetField[]>
  /** Theo key của field; field chưa map thì không có mặt. */
  mapping: Section<MappingDraft>
  transformations: Section<TransformationsDraft>
  /** Chỉ rule do user bật (`email`, `unique`); `required` và `type` do BE suy ra (design D7). */
  validations: Section<ValidationsDraft>
  result: ResultState | null
}

export const initialWizardState: WizardState = {
  step: 'upload',
  pendingRequests: 0,
  nextFieldSeq: 1,
  nextTransformationSeq: 1,
  session: null,
  preview: null,
  schema: { draft: [], saved: false },
  mapping: { draft: {}, saved: false },
  transformations: { draft: {}, saved: false },
  validations: { draft: {}, saved: false },
  result: null,
}

export type SchemaEdit =
  | { kind: 'add' }
  /** Thay toàn bộ field bằng field sinh từ cột nguồn của preview ("Tạo lại từ file"). */
  | { kind: 'regenerate' }
  | { kind: 'update'; key: FieldKey; patch: Partial<{ name: string; type: FieldType; required: boolean }> }
  | { kind: 'remove'; key: FieldKey }
  | { kind: 'move'; key: FieldKey; offset: -1 | 1 }

/** Tham số cần sửa; reducer chỉ áp khoá hợp với loại bước (`value` cho defaultValue, hai định dạng cho dateFormat). */
export type TransformationPatch = Partial<{ value: string; inputFormat: string; outputFormat: string }>

export type TransformationEdit =
  | { kind: 'add'; type: TransformationType }
  | { kind: 'update'; id: TransformationId; patch: TransformationPatch }
  | { kind: 'remove'; id: TransformationId }
  | { kind: 'move'; id: TransformationId; offset: -1 | 1 }

export type WizardAction =
  | { type: 'sessionCreated'; session: SessionInfo }
  /** `sessionId`: id FE đã dùng để gửi request (không phải field BE gửi lại). */
  | { type: 'previewLoaded'; sessionId: string; preview: SourcePreview }
  | { type: 'schemaEdited'; edit: SchemaEdit }
  /** `mapping: null` là bỏ map field đó. */
  | { type: 'mappingEdited'; key: FieldKey; mapping: FieldMapping | null }
  | { type: 'transformationsEdited'; key: FieldKey; edit: TransformationEdit }
  | { type: 'validationToggled'; key: FieldKey; rule: UserRule; enabled: boolean }
  /** `draft`: đúng bản đã gửi đi; nếu user đã sửa tiếp trong lúc chờ thì bản hiện tại vẫn là chưa lưu. */
  | { type: 'sectionSaved'; section: 'schema'; draft: TargetField[] }
  | { type: 'sectionSaved'; section: 'mapping'; draft: MappingDraft }
  | { type: 'sectionSaved'; section: 'transformations'; draft: TransformationsDraft }
  | { type: 'sectionSaved'; section: 'validations'; draft: ValidationsDraft }
  /** Process xong: kết quả mới thay kết quả cũ, wizard sang bước Kết quả và bước đó tải trang `query`. */
  | { type: 'processCompleted'; summary: PipelineSummary; columns: readonly string[]; query: ResultQuery }
  | { type: 'resultPageLoaded'; query: ResultQuery; page: ResultPage }
  /** BE không còn kết quả: `409 RESULT_NOT_AVAILABLE`, hoặc process lỗi (design D18). */
  | { type: 'resultUnavailable'; reason: Exclude<StaleReason, 'configChanged'> }
  | { type: 'navigate'; step: StepId }
  | { type: 'requestStarted' }
  | { type: 'requestSettled' }
  | { type: 'reset' }

export function isBusy(state: WizardState): boolean {
  return state.pendingRequests > 0
}
