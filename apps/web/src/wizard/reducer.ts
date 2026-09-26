import { fieldsFromPreview } from '../domain/inferSchema'
import type { FieldKey, MappingDraft, TargetField } from '../domain/types'
import { canEnter } from './guards'
import { initialWizardState, isBusy, type SchemaEdit, type WizardAction, type WizardState } from './state'

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
      return editSchema(state, action.edit)
    case 'mappingEdited': {
      const { [action.key]: _previous, ...others } = state.mapping.draft
      const draft: MappingDraft = action.mapping ? { ...others, [action.key]: action.mapping } : others
      return { ...state, mapping: { draft, saved: false } }
    }
    case 'sectionSaved':
      return markSaved(state, action)
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
  }
}

/**
 * Mọi thay đổi schema đưa schema và mapping về chưa lưu: PUT /schema làm BE xoá mapping của field bị đổi tên hoặc
 * xoá, nên mapping phải được PUT lại dưới tên mới (design D4, spec import-wizard). Mapping gắn theo key nên đổi tên
 * không làm mất nó; xoá field thì xoá mapping của field đó (spec field-mapping). Rules được thêm ở F06–F07.
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
    case 'update':
      return withFields(state, fields.map((field, i) => (i === index ? { ...field, ...edit.patch } : field)))
    case 'remove':
      return withFields(withoutMapping(state, edit.key), fields.filter((_, i) => i !== index))
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
  return { ...state, nextFieldSeq, schema: { draft: fields, saved: false }, mapping: { draft, saved: false } }
}

/**
 * Mapping được tạo tham chiếu mới dù nội dung không đổi: payload PUT mapping phụ thuộc cả tên field, nên bản đã gửi
 * trước lần sửa schema này phải bị coi là cũ khi so tham chiếu ở `sectionSaved` (review FE-F05).
 */
function withFields(state: WizardState, draft: TargetField[]): WizardState {
  return { ...state, schema: { draft, saved: false }, mapping: { draft: { ...state.mapping.draft }, saved: false } }
}

function withoutMapping(state: WizardState, key: FieldKey): WizardState {
  const { [key]: _removed, ...others } = state.mapping.draft
  return { ...state, mapping: { ...state.mapping, draft: others } }
}
