import { describe, expect, test } from 'vitest'
import type { SessionInfo, SourcePreview, TargetField } from '../domain/types'
import { wizardReducer } from './reducer'
import { initialWizardState, isBusy, type SchemaEdit, type WizardState } from './state'

const session: SessionInfo = { id: 's-1', fileName: 'khach-hang.csv', fileType: 'CSV', sizeBytes: 1024 }
const atPreview: WizardState = { ...initialWizardState, step: 'preview', session }
const preview: SourcePreview = { sheetName: null, columns: ['name'], rows: [], totalRows: 0 }

describe('wizardReducer', () => {
  test('sessionCreated lưu session và chuyển sang bước Xem trước', () => {
    const next = wizardReducer(initialWizardState, { type: 'sessionCreated', session })

    expect(next).toEqual({ ...initialWizardState, step: 'preview', session })
  })

  test('sessionCreated bỏ preview của session cũ', () => {
    const loaded: WizardState = { ...atPreview, preview }
    const other: SessionInfo = { ...session, id: 's-2' }

    expect(wizardReducer(loaded, { type: 'sessionCreated', session: other }).preview).toBeNull()
  })

  test('previewLoaded lưu preview của session hiện tại', () => {
    expect(wizardReducer(atPreview, { type: 'previewLoaded', sessionId: 's-1', preview }).preview).toEqual(preview)
  })

  // sessionId là id FE đã dùng để gửi request, không phải field BE gửi lại.
  test('previewLoaded của request gửi cho session khác (về muộn sau khi đã upload file mới) bị bỏ qua', () => {
    expect(wizardReducer(atPreview, { type: 'previewLoaded', sessionId: 's-old', preview })).toBe(atPreview)
  })

  test('không chuyển tới bước đang bị khoá', () => {
    const next = wizardReducer(initialWizardState, { type: 'navigate', step: 'preview' })

    expect(next).toBe(initialWizardState)
  })

  test('quay lại bước Upload vẫn giữ session', () => {
    expect(wizardReducer(atPreview, { type: 'navigate', step: 'upload' })).toEqual({ ...atPreview, step: 'upload' })
  })

  test('đang có request chạy thì không điều hướng', () => {
    const busy: WizardState = { ...atPreview, pendingRequests: 1 }

    expect(wizardReducer(busy, { type: 'navigate', step: 'upload' })).toBe(busy)
  })

  test('reset đưa wizard về trạng thái ban đầu', () => {
    expect(wizardReducer({ ...atPreview, preview }, { type: 'reset' })).toEqual(initialWizardState)
  })

  describe('đếm request đang chạy', () => {
    test('hai request chồng nhau: xong một cái vẫn còn bận, xong cả hai mới hết bận', () => {
      let state = wizardReducer(atPreview, { type: 'requestStarted' })
      state = wizardReducer(state, { type: 'requestStarted' })

      state = wizardReducer(state, { type: 'requestSettled' })
      expect(isBusy(state)).toBe(true)

      state = wizardReducer(state, { type: 'requestSettled' })
      expect(isBusy(state)).toBe(false)
    })

    test('requestSettled thừa không làm bộ đếm âm', () => {
      const state = wizardReducer(atPreview, { type: 'requestSettled' })

      expect(state.pendingRequests).toBe(0)
      expect(isBusy(wizardReducer(state, { type: 'requestStarted' }))).toBe(true)
    })

    test('sessionCreated và reset giữ nguyên số request đang chạy, vì chúng vẫn sẽ kết thúc sau đó', () => {
      const busy: WizardState = { ...atPreview, pendingRequests: 1 }

      expect(wizardReducer(busy, { type: 'sessionCreated', session }).pendingRequests).toBe(1)
      expect(wizardReducer(busy, { type: 'reset' }).pendingRequests).toBe(1)
    })
  })
})

describe('schema', () => {
  const withPreview: WizardState = { ...atPreview, preview, step: 'schema' }

  function edit(state: WizardState, ...edits: SchemaEdit[]): WizardState {
    return edits.reduce((current, schemaEdit) => wizardReducer(current, { type: 'schemaEdited', edit: schemaEdit }), state)
  }

  function save(state: WizardState): WizardState {
    return wizardReducer(state, { type: 'sectionSaved', section: 'schema', draft: state.schema.draft })
  }

  function names(state: WizardState) {
    return state.schema.draft.map((field) => field.name)
  }

  test('thêm field: tên rỗng, kiểu string, không required; key tăng dần f1, f2', () => {
    const state = edit(withPreview, { kind: 'add' }, { kind: 'add' })

    expect(state.schema.draft).toEqual<TargetField[]>([
      { key: 'f1', name: '', type: 'string', required: false },
      { key: 'f2', name: '', type: 'string', required: false },
    ])
  })

  test('key không bị dùng lại sau khi xoá field', () => {
    const state = edit(withPreview, { kind: 'add' }, { kind: 'add' }, { kind: 'remove', key: 'f2' }, { kind: 'add' })

    expect(state.schema.draft.map((field) => field.key)).toEqual(['f1', 'f3'])
  })

  test('sửa tên, kiểu, required của đúng field theo key', () => {
    const state = edit(
      withPreview,
      { kind: 'add' },
      { kind: 'add' },
      { kind: 'update', key: 'f2', patch: { name: 'email', type: 'email', required: true } },
    )

    expect(state.schema.draft[1]).toEqual({ key: 'f2', name: 'email', type: 'email', required: true })
    expect(state.schema.draft[0].name).toBe('')
  })

  test('Lên/Xuống đổi chỗ với field kề bên; ở biên thì không đổi gì', () => {
    const state = edit(
      withPreview,
      { kind: 'add' },
      { kind: 'add' },
      { kind: 'add' },
      { kind: 'update', key: 'f1', patch: { name: 'a' } },
      { kind: 'update', key: 'f2', patch: { name: 'b' } },
      { kind: 'update', key: 'f3', patch: { name: 'c' } },
    )

    expect(names(edit(state, { kind: 'move', key: 'f3', offset: -1 }))).toEqual(['a', 'c', 'b'])
    expect(names(edit(state, { kind: 'move', key: 'f1', offset: 1 }))).toEqual(['b', 'a', 'c'])
    expect(edit(state, { kind: 'move', key: 'f1', offset: -1 })).toBe(state)
    expect(edit(state, { kind: 'move', key: 'f3', offset: 1 })).toBe(state)
  })

  test('sectionSaved đánh dấu đã lưu; mọi thay đổi sau đó đưa schema về chưa lưu', () => {
    const saved = save(
      edit(
        withPreview,
        { kind: 'add' },
        { kind: 'add' },
        { kind: 'update', key: 'f1', patch: { name: 'a' } },
        { kind: 'update', key: 'f2', patch: { name: 'b' } },
      ),
    )
    expect(saved.schema.saved).toBe(true)

    // Đổi thứ tự cũng phải lưu lại: `order` là một phần contract, quyết định thứ tự cột khi xuất file.
    const edits: SchemaEdit[] = [
      { kind: 'add' },
      { kind: 'update', key: 'f1', patch: { required: true } },
      { kind: 'remove', key: 'f1' },
      { kind: 'move', key: 'f2', offset: -1 },
    ]
    for (const schemaEdit of edits) {
      expect(edit(saved, schemaEdit).schema.saved).toBe(false)
    }
  })

  // PUT đang chạy mà user sửa tiếp: bản vừa lưu không còn là bản đang hiển thị.
  test('sectionSaved của một bản draft cũ không đánh dấu bản hiện tại là đã lưu', () => {
    const sent = edit(withPreview, { kind: 'add' }, { kind: 'update', key: 'f1', patch: { name: 'a' } })
    const editedMeanwhile = edit(sent, { kind: 'update', key: 'f1', patch: { name: 'ab' } })

    const next = wizardReducer(editedMeanwhile, { type: 'sectionSaved', section: 'schema', draft: sent.schema.draft })

    expect(next).toBe(editedMeanwhile)
  })

  test('sessionCreated và reset xoá schema và đếm lại key từ f1', () => {
    const state = save(edit(withPreview, { kind: 'add' }, { kind: 'add' }))

    for (const next of [
      wizardReducer(state, { type: 'sessionCreated', session: { ...session, id: 's-2' } }),
      wizardReducer(state, { type: 'reset' }),
    ]) {
      expect(next.schema).toEqual({ draft: [], saved: false })
      expect(edit(next, { kind: 'add' }).schema.draft[0].key).toBe('f1')
    }
  })
})
