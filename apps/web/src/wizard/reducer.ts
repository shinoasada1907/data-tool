import { fieldsFromPreview } from '../domain/inferSchema'
import type { TargetField } from '../domain/types'
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
 * So tham chiếu: draft là mảng bất biến, nên sửa gì sau khi gửi cũng tạo ra mảng mới, và bản đang hiển thị vẫn là
 * chưa lưu. Mỗi section một nhánh: thêm section mới vào union mà quên nhánh ở đây thì compiler báo lỗi.
 */
function markSaved(state: WizardState, action: Extract<WizardAction, { type: 'sectionSaved' }>): WizardState {
  switch (action.section) {
    case 'schema':
      if (state.schema.draft !== action.draft) return state
      return { ...state, schema: { ...state.schema, saved: true } }
  }
}

/** Mọi thay đổi schema đưa schema về chưa lưu. Cascade sang mapping và rules được thêm ở F05–F07 (spec target-schema). */
function editSchema(state: WizardState, edit: SchemaEdit): WizardState {
  const fields = state.schema.draft

  if (edit.kind === 'regenerate') return state.preview ? generateSchema(state) : state

  if (edit.kind === 'add') {
    const field: TargetField = { key: `f${state.nextFieldSeq}`, name: '', type: 'string', required: false }
    return { ...state, nextFieldSeq: state.nextFieldSeq + 1, schema: { draft: [...fields, field], saved: false } }
  }

  const index = fields.findIndex((field) => field.key === edit.key)
  if (index === -1) return state

  switch (edit.kind) {
    case 'update':
      return withFields(state, fields.map((field, i) => (i === index ? { ...field, ...edit.patch } : field)))
    case 'remove':
      return withFields(state, fields.filter((_, i) => i !== index))
    case 'move': {
      const target = index + edit.offset
      if (target < 0 || target >= fields.length) return state
      const moved = [...fields]
      ;[moved[index], moved[target]] = [moved[target], moved[index]]
      return withFields(state, moved)
    }
  }
}

/** Key đánh tiếp từ `nextFieldSeq`, nên field sinh lại không mang key của field cũ (design D3). */
function generateSchema(state: WizardState): WizardState {
  if (!state.preview) return state
  const { fields, nextFieldSeq } = fieldsFromPreview(state.preview, state.nextFieldSeq)
  return { ...state, nextFieldSeq, schema: { draft: fields, saved: false } }
}

function withFields(state: WizardState, draft: TargetField[]): WizardState {
  return { ...state, schema: { draft, saved: false } }
}
