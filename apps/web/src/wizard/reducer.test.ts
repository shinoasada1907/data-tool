import { describe, expect, test } from 'vitest'
import type { PipelineSummary, ResultPage, ResultQuery, SessionInfo, SourcePreview, TargetField } from '../domain/types'
import { wizardReducer } from './reducer'
import {
  initialWizardState,
  isBusy,
  type ResultState,
  type SchemaEdit,
  type TransformationEdit,
  type WizardAction,
  type WizardState,
} from './state'

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

describe('sinh schema từ cột nguồn', () => {
  const twoColumns: SourcePreview = {
    sheetName: null,
    columns: ['Mã', 'Số lượng'],
    rows: [{ rowNumber: 2, values: ['A01', '10'] }],
    totalRows: 1,
  }

  test('previewLoaded sinh schema từ các cột khi schema đang trống: chưa lưu, key từ f1', () => {
    const next = wizardReducer(atPreview, { type: 'previewLoaded', sessionId: 's-1', preview: twoColumns })

    expect(next.schema).toEqual({
      draft: [
        { key: 'f1', name: 'Mã', type: 'string', required: false },
        { key: 'f2', name: 'Số lượng', type: 'number', required: false },
      ],
      saved: false,
    })
    expect(next.nextFieldSeq).toBe(3)
  })

  test('previewLoaded không ghi đè schema đã có field', () => {
    const withField = wizardReducer(atPreview, { type: 'schemaEdited', edit: { kind: 'add' } })

    const next = wizardReducer(withField, { type: 'previewLoaded', sessionId: 's-1', preview: twoColumns })

    expect(next.schema).toBe(withField.schema)
  })

  test('regenerate thay toàn bộ field bằng field sinh từ preview, với key mới không trùng key cũ', () => {
    let state = wizardReducer(atPreview, { type: 'previewLoaded', sessionId: 's-1', preview: twoColumns })
    state = wizardReducer(state, { type: 'schemaEdited', edit: { kind: 'update', key: 'f1', patch: { name: 'Mã KH' } } })
    state = wizardReducer(state, { type: 'schemaEdited', edit: { kind: 'add' } })

    const next = wizardReducer(state, { type: 'schemaEdited', edit: { kind: 'regenerate' } })

    expect(next.schema.draft).toEqual([
      { key: 'f4', name: 'Mã', type: 'string', required: false },
      { key: 'f5', name: 'Số lượng', type: 'number', required: false },
    ])
    expect(next.schema.saved).toBe(false)
    expect(next.nextFieldSeq).toBe(6)
  })

  test('regenerate khi chưa có preview thì không làm gì', () => {
    expect(wizardReducer(atPreview, { type: 'schemaEdited', edit: { kind: 'regenerate' } })).toBe(atPreview)
  })
})

describe('mapping', () => {
  const columns: SourcePreview = {
    sheetName: null,
    columns: ['Mã', 'Email'],
    rows: [{ rowNumber: 2, values: ['A01', 'an@example.com'] }],
    totalRows: 1,
  }
  const loaded = wizardReducer(atPreview, { type: 'previewLoaded', sessionId: 's-1', preview: columns })

  function editSchema(state: WizardState, schemaEdit: SchemaEdit) {
    return wizardReducer(state, { type: 'schemaEdited', edit: schemaEdit })
  }

  function saveMapping(state: WizardState) {
    return wizardReducer(state, { type: 'sectionSaved', section: 'mapping', draft: state.mapping.draft })
  }

  test('schema sinh từ cột nguồn thì mỗi field map sẵn với cột cùng tên, chưa lưu', () => {
    expect(loaded.mapping).toEqual({
      draft: { f1: { kind: 'column', column: 'Mã' }, f2: { kind: 'column', column: 'Email' } },
      saved: false,
    })
  })

  test('"Tạo lại từ file": mapping thay theo field mới, không còn key cũ', () => {
    const next = editSchema(loaded, { kind: 'regenerate' })

    expect(next.mapping.draft).toEqual({ f3: { kind: 'column', column: 'Mã' }, f4: { kind: 'column', column: 'Email' } })
  })

  test('mappingEdited đặt cột, hằng, hoặc bỏ map (null) cho đúng field; mapping về chưa lưu', () => {
    let state = saveMapping(loaded)
    state = wizardReducer(state, { type: 'mappingEdited', key: 'f1', mapping: { kind: 'constant', value: 'VN' } })
    expect(state.mapping.saved).toBe(false)

    state = wizardReducer(state, { type: 'mappingEdited', key: 'f2', mapping: null })

    expect(state.mapping.draft).toEqual({ f1: { kind: 'constant', value: 'VN' } })
  })

  test('đổi tên field giữ nguyên mapping (gắn theo key) nhưng đưa mapping về chưa lưu, vì BE xoá mapping của tên cũ', () => {
    const saved = saveMapping(loaded)

    const renamed = editSchema(saved, { kind: 'update', key: 'f2', patch: { name: 'email_address' } })

    expect(renamed.mapping).toEqual({ draft: saved.mapping.draft, saved: false })
  })

  // Payload PUT mapping phụ thuộc cả tên field, nên đổi tên cũng phải làm bản đã gửi trở thành "cũ" khi so tham chiếu.
  test('PUT mapping gửi trước khi đổi tên field: về tới nơi cũng không đánh dấu bản hiện tại là đã lưu', () => {
    const sent = loaded.mapping.draft
    const renamed = editSchema(loaded, { kind: 'update', key: 'f2', patch: { name: 'email_address' } })

    const next = wizardReducer(renamed, { type: 'sectionSaved', section: 'mapping', draft: sent })

    expect(next.mapping.saved).toBe(false)
  })

  test.each<[string, SchemaEdit]>([
    ['đổi kiểu', { kind: 'update', key: 'f1', patch: { type: 'number' } }],
    ['đổi bắt buộc', { kind: 'update', key: 'f1', patch: { required: true } }],
    ['đổi thứ tự', { kind: 'move', key: 'f2', offset: -1 }],
    ['thêm field', { kind: 'add' }],
  ])('%s ở Schema cũng đưa mapping về chưa lưu', (_, schemaEdit) => {
    expect(editSchema(saveMapping(loaded), schemaEdit).mapping.saved).toBe(false)
  })

  test('map mặc định lấy đúng tên cột của file, kể cả khi tên field về sau bị sửa', () => {
    const nfdColumns: SourcePreview = { ...columns, columns: ['Mã'.normalize('NFD'), 'Email'] }

    const state = wizardReducer(atPreview, { type: 'previewLoaded', sessionId: 's-1', preview: nfdColumns })

    expect(state.mapping.draft.f1).toEqual({ kind: 'column', column: 'Mã'.normalize('NFD') })
  })

  test('xoá field thì xoá mapping của field đó', () => {
    const next = editSchema(loaded, { kind: 'remove', key: 'f2' })

    expect(next.mapping.draft).toEqual({ f1: { kind: 'column', column: 'Mã' } })
  })

  test('field tự thêm bắt đầu ở trạng thái chưa map', () => {
    const next = editSchema(loaded, { kind: 'add' })

    expect(next.mapping.draft).not.toHaveProperty('f3')
  })

  test('sectionSaved của một bản mapping cũ không đánh dấu bản hiện tại là đã lưu', () => {
    const sent = loaded.mapping.draft
    const editedMeanwhile = wizardReducer(loaded, { type: 'mappingEdited', key: 'f1', mapping: null })

    expect(wizardReducer(editedMeanwhile, { type: 'sectionSaved', section: 'mapping', draft: sent })).toBe(editedMeanwhile)
  })

  test('sessionCreated và reset xoá mapping', () => {
    const state = saveMapping(loaded)

    expect(wizardReducer(state, { type: 'sessionCreated', session: { ...session, id: 's-2' } }).mapping).toEqual({
      draft: {},
      saved: false,
    })
    expect(wizardReducer(state, { type: 'reset' }).mapping).toEqual({ draft: {}, saved: false })
  })
})

describe('transformations và validations', () => {
  const columns: SourcePreview = {
    sheetName: null,
    columns: ['name', 'dob'],
    rows: [{ rowNumber: 2, values: ['An', '1990-02-28'] }],
    totalRows: 1,
  }
  // f1 = name (string), f2 = dob (date)
  const loaded = wizardReducer(atPreview, { type: 'previewLoaded', sessionId: 's-1', preview: columns })

  function editRules(state: WizardState, key: string, edit: TransformationEdit) {
    return wizardReducer(state, { type: 'transformationsEdited', key, edit })
  }

  function toggle(state: WizardState, key: string, rule: 'email' | 'unique', enabled: boolean) {
    return wizardReducer(state, { type: 'validationToggled', key, rule, enabled })
  }

  function editSchema(state: WizardState, schemaEdit: SchemaEdit) {
    return wizardReducer(state, { type: 'schemaEdited', edit: schemaEdit })
  }

  function saveBoth(state: WizardState) {
    const withT = wizardReducer(state, {
      type: 'sectionSaved',
      section: 'transformations',
      draft: state.transformations.draft,
    })
    return wizardReducer(withT, { type: 'sectionSaved', section: 'validations', draft: withT.validations.draft })
  }

  test('thêm bước: id tăng dần; defaultValue và dateFormat có tham số mặc định; transformations về chưa lưu', () => {
    let state = editRules(loaded, 'f1', { kind: 'add', type: 'trim' })
    state = editRules(state, 'f1', { kind: 'add', type: 'defaultValue' })
    state = editRules(state, 'f1', { kind: 'add', type: 'dateFormat' })

    expect(state.transformations).toEqual({
      draft: {
        f1: [
          { id: 't1', type: 'trim' },
          { id: 't2', type: 'defaultValue', value: '' },
          { id: 't3', type: 'dateFormat', inputFormat: '', outputFormat: 'yyyy-MM-dd' },
        ],
      },
      saved: false,
    })
  })

  test('sửa tham số, đổi thứ tự (ở biên thì không đổi gì), xoá bước', () => {
    let state = editRules(loaded, 'f1', { kind: 'add', type: 'trim' })
    state = editRules(state, 'f1', { kind: 'add', type: 'defaultValue' })
    state = editRules(state, 'f1', { kind: 'update', id: 't2', patch: { value: 'N/A' } })
    expect(state.transformations.draft.f1[1]).toEqual({ id: 't2', type: 'defaultValue', value: 'N/A' })

    const moved = editRules(state, 'f1', { kind: 'move', id: 't2', offset: -1 })
    expect(moved.transformations.draft.f1.map((step) => step.id)).toEqual(['t2', 't1'])
    expect(editRules(moved, 'f1', { kind: 'move', id: 't2', offset: -1 })).toBe(moved)

    const removed = editRules(moved, 'f1', { kind: 'remove', id: 't2' })
    expect(removed.transformations.draft.f1).toEqual([{ id: 't1', type: 'trim' }])
  })

  test('field kiểu date: outputFormat của dateFormat luôn là yyyy-MM-dd, sửa cũng không đổi', () => {
    let state = editRules(loaded, 'f2', { kind: 'add', type: 'dateFormat' })
    state = editRules(state, 'f2', { kind: 'update', id: 't1', patch: { outputFormat: 'dd/MM/yyyy', inputFormat: 'dd/MM/yyyy' } })

    expect(state.transformations.draft.f2[0]).toEqual({
      id: 't1',
      type: 'dateFormat',
      inputFormat: 'dd/MM/yyyy',
      outputFormat: 'yyyy-MM-dd',
    })
  })

  test('bật/tắt rule; mỗi rule tối đa một lần; validations về chưa lưu', () => {
    let state = toggle(loaded, 'f1', 'unique', true)
    state = toggle(state, 'f1', 'unique', true)
    state = toggle(state, 'f1', 'email', true)
    expect(state.validations).toEqual({ draft: { f1: ['unique', 'email'] }, saved: false })

    state = toggle(state, 'f1', 'unique', false)
    expect(state.validations.draft.f1).toEqual(['email'])
  })

  test('rule email không bật được ở field không phải string', () => {
    expect(toggle(loaded, 'f2', 'email', true)).toBe(loaded)
  })

  describe('cascade từ schema', () => {
    function configured() {
      let state = editRules(loaded, 'f1', { kind: 'add', type: 'trim' })
      state = editRules(state, 'f1', { kind: 'add', type: 'dateFormat' })
      state = editRules(state, 'f1', { kind: 'update', id: 't2', patch: { inputFormat: 'dd/MM/yyyy', outputFormat: 'dd.MM.yyyy' } })
      state = toggle(state, 'f1', 'email', true)
      state = toggle(state, 'f1', 'unique', true)
      return saveBoth(state)
    }

    test('mọi sửa schema đưa transformations và validations về chưa lưu (BE xoá cấu hình của tên cũ)', () => {
      const renamed = editSchema(configured(), { kind: 'update', key: 'f1', patch: { name: 'full_name' } })

      expect(renamed.transformations.saved).toBe(false)
      expect(renamed.validations.saved).toBe(false)
      expect(renamed.transformations.draft).toEqual(configured().transformations.draft)
    })

    test('bản đã gửi trước lần sửa schema không đánh dấu bản hiện tại là đã lưu', () => {
      const state = configured()
      const sentT = state.transformations.draft
      const sentV = state.validations.draft
      let renamed = editSchema(state, { kind: 'update', key: 'f1', patch: { name: 'full_name' } })

      renamed = wizardReducer(renamed, { type: 'sectionSaved', section: 'transformations', draft: sentT })
      renamed = wizardReducer(renamed, { type: 'sectionSaved', section: 'validations', draft: sentV })

      expect(renamed.transformations.saved).toBe(false)
      expect(renamed.validations.saved).toBe(false)
    })

    test('xoá field thì xoá transformations và validations của field đó, field khác giữ nguyên', () => {
      const withOther = toggle(editRules(configured(), 'f2', { kind: 'add', type: 'trim' }), 'f2', 'unique', true)

      const next = editSchema(withOther, { kind: 'remove', key: 'f1' })

      expect(next.transformations.draft).not.toHaveProperty('f1')
      expect(next.validations.draft).not.toHaveProperty('f1')
      expect(next.transformations.draft.f2).toEqual(withOther.transformations.draft.f2)
      expect(next.validations.draft.f2).toEqual(['unique'])
    })

    test('đổi kiểu khỏi string thì bỏ rule email, giữ unique và transformation', () => {
      const next = editSchema(configured(), { kind: 'update', key: 'f1', patch: { type: 'number' } })

      expect(next.validations.draft.f1).toEqual(['unique'])
      expect(next.transformations.draft.f1).toHaveLength(2)
    })

    test('đổi kiểu sang date thì outputFormat của dateFormat về yyyy-MM-dd', () => {
      const next = editSchema(configured(), { kind: 'update', key: 'f1', patch: { type: 'date' } })

      expect(next.transformations.draft.f1[1]).toEqual({
        id: 't2',
        type: 'dateFormat',
        inputFormat: 'dd/MM/yyyy',
        outputFormat: 'yyyy-MM-dd',
      })
    })

    test('"Tạo lại từ file" xoá transformations và validations cùng field cũ', () => {
      const next = editSchema(configured(), { kind: 'regenerate' })

      expect(next.transformations).toEqual({ draft: {}, saved: false })
      expect(next.validations).toEqual({ draft: {}, saved: false })
    })
  })

  test('sessionCreated và reset xoá transformations, validations và đếm lại id từ t1', () => {
    const state = toggle(editRules(loaded, 'f1', { kind: 'add', type: 'trim' }), 'f1', 'unique', true)

    for (const next of [
      wizardReducer(state, { type: 'sessionCreated', session: { ...session, id: 's-2' } }),
      wizardReducer(state, { type: 'reset' }),
    ]) {
      expect(next.transformations).toEqual({ draft: {}, saved: false })
      expect(next.validations).toEqual({ draft: {}, saved: false })
      expect(next.nextTransformationSeq).toBe(1)
    }
  })
})

describe('kết quả xử lý', () => {
  const summary: PipelineSummary = {
    total: 3,
    valid: 2,
    invalid: 1,
    errorCountsByCode: { VALIDATION_EMAIL: 1 },
    errorCountsByField: { email: 1 },
    processedAt: '2026-09-26T09:00:00Z',
  }
  const query: ResultQuery = { view: 'invalid', page: 0, field: null, code: null }
  const page: ResultPage = {
    number: 0,
    totalElements: 1,
    totalPages: 1,
    rows: [{ rowNumber: 2, valid: false, values: { email: 'x' }, errors: [] }],
  }
  const columns = ['email']
  // Đã lưu schema, mapping, transformations và validations; đang ở bước Biến đổi & kiểm tra.
  const ready: WizardState = {
    ...atPreview,
    step: 'rules',
    preview,
    schema: { draft: [{ key: 'f1', name: 'email', type: 'string', required: true }], saved: true },
    mapping: { draft: { f1: { kind: 'column', column: 'name' } }, saved: true },
    transformations: { draft: {}, saved: true },
    validations: { draft: {}, saved: true },
    nextFieldSeq: 2,
  }
  const processed = wizardReducer(ready, { type: 'processCompleted', summary, columns, query })
  const completed = wizardReducer(processed, { type: 'resultPageLoaded', query, page })
  const result = completed.result as ResultState

  test('processCompleted lưu kết quả (chưa cũ, trang đầu chưa tải) và sang bước Kết quả', () => {
    expect(processed.step).toBe('result')
    expect(processed.result).toEqual({ summary, columns, query, page: null, stale: null })
  })

  test('resultPageLoaded thay trang đang xem và truy vấn, giữ tóm tắt và cột', () => {
    const nextQuery: ResultQuery = { view: 'valid', page: 1, field: null, code: null }
    const nextPage: ResultPage = { ...page, number: 1, totalPages: 2 }

    const next = wizardReducer(completed, { type: 'resultPageLoaded', query: nextQuery, page: nextPage })

    expect(next.result).toEqual({ ...result, query: nextQuery, page: nextPage })
  })

  test('resultUnavailable đánh dấu kết quả là cũ kèm lý do, giữ tóm tắt và trang đang xem (design D18)', () => {
    expect(wizardReducer(completed, { type: 'resultUnavailable', reason: 'unavailable' }).result).toEqual({
      ...result,
      stale: 'unavailable',
    })
  })

  test('lý do cũ: session hỏng luôn thắng; lý do khác không đè lý do đã có', () => {
    const edited = wizardReducer(completed, { type: 'validationToggled', key: 'f1', rule: 'unique', enabled: true })
    expect(edited.result?.stale).toBe('configChanged')
    expect(wizardReducer(edited, { type: 'resultUnavailable', reason: 'unavailable' }).result?.stale).toBe('configChanged')

    const dead = wizardReducer(edited, { type: 'resultUnavailable', reason: 'sessionUnusable' })
    expect(dead.result?.stale).toBe('sessionUnusable')
    expect(wizardReducer(dead, { type: 'resultUnavailable', reason: 'unavailable' })).toBe(dead)
    // Sửa cấu hình sau khi session đã hỏng: vẫn chỉ còn cách upload lại.
    const editedAgain = wizardReducer(dead, { type: 'validationToggled', key: 'f1', rule: 'unique', enabled: false })
    expect(editedAgain.result?.stale).toBe('sessionUnusable')
  })

  test('chưa có kết quả thì resultPageLoaded và resultUnavailable không làm gì', () => {
    expect(wizardReducer(ready, { type: 'resultPageLoaded', query, page })).toBe(ready)
    expect(wizardReducer(ready, { type: 'resultUnavailable', reason: 'unavailable' })).toBe(ready)
  })

  const edits: [string, WizardAction][] = [
    ['sửa schema', { type: 'schemaEdited', edit: { kind: 'update', key: 'f1', patch: { required: false } } }],
    ['sửa mapping', { type: 'mappingEdited', key: 'f1', mapping: { kind: 'constant', value: 'a@b.c' } }],
    ['sửa transformation', { type: 'transformationsEdited', key: 'f1', edit: { kind: 'add', type: 'trim' } }],
    ['bật validation', { type: 'validationToggled', key: 'f1', rule: 'unique', enabled: true }],
  ]

  test.each(edits)('%s thì kết quả bị đánh dấu cũ vì cấu hình đổi (spec import-wizard)', (_label, action) => {
    const next = wizardReducer(completed, action)

    expect(next.result).toEqual({ ...result, stale: 'configChanged' })
  })

  test('thao tác không đổi gì (bật rule đã bật) thì kết quả không bị đánh dấu cũ', () => {
    const withUnique = wizardReducer(ready, { type: 'validationToggled', key: 'f1', rule: 'unique', enabled: true })
    const done = wizardReducer(withUnique, { type: 'processCompleted', summary, columns, query })

    const next = wizardReducer(done, { type: 'validationToggled', key: 'f1', rule: 'unique', enabled: true })

    expect(next).toBe(done)
  })

  test('lưu một phần cấu hình, điều hướng, request bận không làm kết quả cũ', () => {
    let next = wizardReducer(completed, { type: 'requestStarted' })
    next = wizardReducer(next, { type: 'requestSettled' })
    next = wizardReducer(next, { type: 'navigate', step: 'rules' })
    next = wizardReducer(next, { type: 'sectionSaved', section: 'validations', draft: next.validations.draft })

    expect(next.result?.stale).toBeNull()
  })

  test('chạy lại thành công thay kết quả cũ và bỏ đánh dấu cũ', () => {
    const stale = wizardReducer(completed, { type: 'resultUnavailable', reason: 'unavailable' })
    const newSummary = { ...summary, invalid: 0, valid: 3 }
    const validQuery: ResultQuery = { ...query, view: 'valid' }

    const next = wizardReducer(stale, { type: 'processCompleted', summary: newSummary, columns, query: validQuery })

    expect(next.result).toEqual({ summary: newSummary, columns, query: validQuery, page: null, stale: null })
  })

  test('sessionCreated và reset xoá kết quả', () => {
    expect(wizardReducer(completed, { type: 'sessionCreated', session: { ...session, id: 's-2' } }).result).toBeNull()
    expect(wizardReducer(completed, { type: 'reset' }).result).toBeNull()
  })
})
