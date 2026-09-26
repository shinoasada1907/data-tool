import { canUseRule } from '../domain/configRules'
import { fieldsFromPreview } from '../domain/inferSchema'
import {
  ISO_DATE_FORMAT,
  USER_RULES,
  type FieldKey,
  type MappingDraft,
  type TargetField,
  type Transformation,
  type TransformationsDraft,
  type ValidationsDraft,
} from '../domain/types'
import { canEnter } from './guards'
import {
  initialWizardState,
  isBusy,
  type SchemaEdit,
  type TransformationEdit,
  type TransformationPatch,
  type WizardAction,
  type WizardState,
} from './state'

export function wizardReducer(state: WizardState, action: WizardAction): WizardState {
  switch (action.type) {
    case 'sessionCreated':
      // Session mới thay nguyên khối state của session cũ (design D13). Bộ đếm request giữ nguyên:
      // request đang chạy vẫn sẽ báo kết thúc sau đó.
      return { ...initialWizardState, pendingRequests: state.pendingRequests, session: action.session, step: 'preview' }
    case 'previewLoaded': {
      // Response về muộn của session cũ không được ghi đè lên session hiện tại.
      if (action.sessionId !== state.session?.id) return state
      const loaded = { ...state, preview: action.preview }
      // Sinh sẵn schema từ cột nguồn, một lần cho mỗi session: preview chỉ tải một lần, và không bao giờ đè lên field
      // đã có (spec target-schema, "Sinh schema từ cột nguồn").
      return state.schema.draft.length === 0 ? generateSchema(loaded) : loaded
    }
    case 'schemaEdited':
      return withStaleResult(state, editSchema(state, action.edit))
    case 'mappingEdited': {
      const { [action.key]: _previous, ...others } = state.mapping.draft
      const draft: MappingDraft = action.mapping ? { ...others, [action.key]: action.mapping } : others
      return withStaleResult(state, { ...state, mapping: { draft, saved: false } })
    }
    case 'transformationsEdited':
      return withStaleResult(state, editTransformations(state, action.key, action.edit))
    case 'validationToggled':
      return withStaleResult(state, toggleValidation(state, action.key, action.rule, action.enabled))
    case 'sectionSaved':
      return markSaved(state, action)
    case 'processCompleted': {
      const { summary, columns, query, page } = action
      return { ...state, step: 'result', result: { summary, columns, query, page, stale: false } }
    }
    case 'resultPageLoaded':
      if (!state.result) return state
      return { ...state, result: { ...state.result, query: action.query, page: action.page } }
    case 'resultUnavailable':
      if (!state.result || state.result.stale) return state
      return { ...state, result: { ...state.result, stale: true } }
    case 'navigate':
      if (isBusy(state) || !canEnter(action.step, state).allowed) return state
      return { ...state, step: action.step }
    case 'requestStarted':
      return { ...state, pendingRequests: state.pendingRequests + 1 }
    case 'requestSettled':
      return { ...state, pendingRequests: Math.max(0, state.pendingRequests - 1) }
    case 'reset':
      return { ...initialWizardState, pendingRequests: state.pendingRequests }
  }
}

/**
 * Mọi thay đổi cấu hình làm kết quả hiện có thành cũ (spec import-wizard): FE đánh dấu ngay lúc sửa, không muộn hơn
 * lúc BE xoá kết quả (design D18). Thao tác không đổi gì (reducer trả lại đúng state cũ) thì không tính là sửa.
 */
function withStaleResult(before: WizardState, after: WizardState): WizardState {
  if (after === before || !after.result || after.result.stale) return after
  return { ...after, result: { ...after.result, stale: true } }
}

/**
 * So tham chiếu: draft là bất biến, nên sửa gì sau khi gửi cũng tạo ra bản mới, và bản đang hiển thị vẫn là chưa lưu.
 * Mỗi section một nhánh: thêm section mới vào union mà quên nhánh ở đây thì compiler báo lỗi.
 */
function markSaved(state: WizardState, action: Extract<WizardAction, { type: 'sectionSaved' }>): WizardState {
  switch (action.section) {
    case 'schema':
      if (state.schema.draft !== action.draft) return state
      return { ...state, schema: { ...state.schema, saved: true } }
    case 'mapping':
      if (state.mapping.draft !== action.draft) return state
      return { ...state, mapping: { ...state.mapping, saved: true } }
    case 'transformations':
      if (state.transformations.draft !== action.draft) return state
      return { ...state, transformations: { ...state.transformations, saved: true } }
    case 'validations':
      if (state.validations.draft !== action.draft) return state
      return { ...state, validations: { ...state.validations, saved: true } }
  }
}

/**
 * Mọi thay đổi schema đưa schema, mapping, transformations và validations về chưa lưu: PUT /schema làm BE xoá cấu hình
 * của field bị đổi tên hoặc xoá, nên chúng phải được PUT lại dưới tên mới (design D4, spec import-wizard). Cấu hình
 * gắn theo key nên đổi tên không làm mất nó; xoá field thì xoá cấu hình của field đó; đổi kiểu thì áp luật theo kiểu
 * (spec target-schema).
 */
function editSchema(state: WizardState, edit: SchemaEdit): WizardState {
  const fields = state.schema.draft

  if (edit.kind === 'regenerate') return state.preview ? generateSchema(state) : state

  if (edit.kind === 'add') {
    const field: TargetField = { key: `f${state.nextFieldSeq}`, name: '', type: 'string', required: false }
    return withFields({ ...state, nextFieldSeq: state.nextFieldSeq + 1 }, [...fields, field])
  }

  const index = fields.findIndex((field) => field.key === edit.key)
  if (index === -1) return state

  switch (edit.kind) {
    case 'update': {
      const updated = { ...fields[index], ...edit.patch }
      const next = withFields(state, fields.map((field, i) => (i === index ? updated : field)))
      return applyTypeRules(next, updated)
    }
    case 'remove':
      return withFields(withoutFieldConfig(state, edit.key), fields.filter((_, i) => i !== index))
    case 'move': {
      const target = index + edit.offset
      if (target < 0 || target >= fields.length) return state
      const moved = [...fields]
      ;[moved[index], moved[target]] = [moved[target], moved[index]]
      return withFields(state, moved)
    }
  }
}

/**
 * Key đánh tiếp từ `nextFieldSeq`, nên field sinh lại không mang key của field cũ (design D3). Mỗi field sinh ra được
 * map sẵn với cột nguồn cùng tên (spec field-mapping, "Map mặc định theo tên cột"); mapping cũ bị thay cùng field cũ.
 */
function generateSchema(state: WizardState): WizardState {
  if (!state.preview) return state
  const { preview } = state
  const { fields, nextFieldSeq } = fieldsFromPreview(preview, state.nextFieldSeq)
  // Lấy đúng tên cột của file (BE so khớp chính xác), không lấy tên field: tên field có thể được chuẩn hoá về sau.
  const draft: MappingDraft = Object.fromEntries(
    fields.map((field, index) => [field.key, { kind: 'column', column: preview.columns[index] }]),
  )
  return {
    ...state,
    nextFieldSeq,
    schema: { draft: fields, saved: false },
    mapping: { draft, saved: false },
    // Field cũ bị thay hết, nên rules gắn theo key cũ cũng đi theo.
    transformations: { draft: {}, saved: false },
    validations: { draft: {}, saved: false },
  }
}

/**
 * Mọi thay đổi schema đưa mapping, transformations và validations về chưa lưu, với tham chiếu mới dù nội dung không
 * đổi: payload của chúng gửi kèm tên field, nên bản đã gửi trước lần sửa schema này phải bị coi là cũ khi so tham
 * chiếu ở `sectionSaved` (review FE-F05; bảng "chuyển về chưa lưu" của spec import-wizard).
 */
function withFields(state: WizardState, draft: TargetField[]): WizardState {
  return {
    ...state,
    schema: { draft, saved: false },
    mapping: { draft: { ...state.mapping.draft }, saved: false },
    transformations: { draft: { ...state.transformations.draft }, saved: false },
    validations: { draft: { ...state.validations.draft }, saved: false },
  }
}

/** Xoá field thì xoá mọi cấu hình gắn với nó (spec target-schema, "Xoá field đã có cấu hình"). */
function withoutFieldConfig(state: WizardState, key: FieldKey): WizardState {
  const { [key]: _mapping, ...mapping } = state.mapping.draft
  const { [key]: _transformations, ...transformations } = state.transformations.draft
  const { [key]: _validations, ...validations } = state.validations.draft
  return {
    ...state,
    mapping: { ...state.mapping, draft: mapping },
    transformations: { ...state.transformations, draft: transformations },
    validations: { ...state.validations, draft: validations },
  }
}

/**
 * Luật theo kiểu field (spec target-schema): không còn là `string` thì bỏ rule `email`; thành `date` thì mọi
 * `dateFormat` xuất ISO, vì kiểu `date` chỉ nhận `yyyy-MM-dd` (design D19). Transformation khác và `unique` giữ nguyên.
 */
function applyTypeRules(state: WizardState, field: TargetField): WizardState {
  let next = state
  const rules = next.validations.draft[field.key]
  if (rules && !canUseRule(field, 'email') && rules.includes('email')) {
    next = withValidations(next, field.key, rules.filter((rule) => rule !== 'email'))
  }
  const steps = next.transformations.draft[field.key]
  if (steps && field.type === 'date') {
    next = withTransformations(next, field.key, steps.map((step) => forDateField(step)))
  }
  return next
}

function forDateField(step: Transformation): Transformation {
  return step.type === 'dateFormat' ? { ...step, outputFormat: ISO_DATE_FORMAT } : step
}

function editTransformations(state: WizardState, key: FieldKey, edit: TransformationEdit): WizardState {
  const field = state.schema.draft.find((candidate) => candidate.key === key)
  if (!field) return state
  const steps = state.transformations.draft[key] ?? []

  if (edit.kind === 'add') {
    const id = `t${state.nextTransformationSeq}`
    const step: Transformation =
      edit.type === 'defaultValue'
        ? { id, type: 'defaultValue', value: '' }
        : edit.type === 'dateFormat'
          ? { id, type: 'dateFormat', inputFormat: '', outputFormat: ISO_DATE_FORMAT }
          : { id, type: edit.type }
    const next = { ...state, nextTransformationSeq: state.nextTransformationSeq + 1 }
    return withTransformations(next, key, [...steps, step])
  }

  const index = steps.findIndex((step) => step.id === edit.id)
  if (index === -1) return state

  switch (edit.kind) {
    case 'update': {
      const updated = patchStep(steps[index], edit.patch)
      // Field kiểu date: outputFormat bị khoá ở ISO, kể cả khi có ai gửi patch khác.
      const guarded = field.type === 'date' ? forDateField(updated) : updated
      return withTransformations(state, key, steps.map((step, i) => (i === index ? guarded : step)))
    }
    case 'remove':
      return withTransformations(state, key, steps.filter((_, i) => i !== index))
    case 'move': {
      const target = index + edit.offset
      if (target < 0 || target >= steps.length) return state
      const moved = [...steps]
      ;[moved[index], moved[target]] = [moved[target], moved[index]]
      return withTransformations(state, key, moved)
    }
  }
}

/** Chỉ áp khoá hợp với loại bước; khoá vắng (`undefined`) giữ nguyên giá trị cũ (review FE-F06/F07). */
function patchStep(step: Transformation, patch: TransformationPatch): Transformation {
  switch (step.type) {
    case 'defaultValue':
      return { ...step, value: patch.value ?? step.value }
    case 'dateFormat':
      return {
        ...step,
        inputFormat: patch.inputFormat ?? step.inputFormat,
        outputFormat: patch.outputFormat ?? step.outputFormat,
      }
    default:
      return step
  }
}

function toggleValidation(state: WizardState, key: FieldKey, rule: (typeof USER_RULES)[number], enabled: boolean) {
  const field = state.schema.draft.find((candidate) => candidate.key === key)
  if (!field || (enabled && !canUseRule(field, rule))) return state
  const rules = state.validations.draft[key] ?? []
  if (enabled === rules.includes(rule)) return state
  return withValidations(state, key, enabled ? [...rules, rule] : rules.filter((current) => current !== rule))
}

function withTransformations(state: WizardState, key: FieldKey, steps: readonly Transformation[]): WizardState {
  const draft: TransformationsDraft = { ...state.transformations.draft, [key]: steps }
  return { ...state, transformations: { draft, saved: false } }
}

function withValidations(state: WizardState, key: FieldKey, rules: ValidationsDraft[FieldKey]): WizardState {
  const draft: ValidationsDraft = { ...state.validations.draft, [key]: rules }
  return { ...state, validations: { draft, saved: false } }
}
