import { describe, expect, test } from 'vitest'
import { datasetFixture, datasetPreviewFixture, validatorRowsFixture, validatorRunFixture } from '../../mocks/toolboxFixtures'
import { initialValidatorState, validatorReducer, type ValidatorAction, type ValidatorState } from './state'

function apply(...actions: ValidatorAction[]): ValidatorState {
  return actions.reduce(validatorReducer, initialValidatorState)
}

const loaded: ValidatorAction[] = [
  { type: 'datasetChanged', dataset: datasetFixture() },
  { type: 'previewLoaded', preview: datasetPreviewFixture() },
]

describe('validatorReducer', () => {
  test('vào bước Schema lần đầu thì tự sinh field từ cột và đặt tên schema theo tên file', () => {
    const state = apply(...loaded, { type: 'navigate', step: 'schema' })

    expect(state.step).toBe('schema')
    expect(state.schema.name).toBe('khach')
    expect(state.schema.fields.map((field) => [field.key, field.name, field.type])).toEqual([
      ['f1', 'ma', 'string'],
      ['f2', 'Email', 'email'],
      ['f3', 'gia', 'number'],
    ])
  })

  test('đã bắt đầu schema (kể cả xoá hết field) thì quay lại không tự sinh nữa', () => {
    const state = apply(
      ...loaded,
      { type: 'navigate', step: 'schema' },
      { type: 'fieldRemoved', key: 'f1' },
      { type: 'fieldRemoved', key: 'f2' },
      { type: 'fieldRemoved', key: 'f3' },
      { type: 'navigate', step: 'source' },
      { type: 'navigate', step: 'schema' },
    )

    expect(state.schema.fields).toEqual([])
  })

  test('không vào được bước bị khoá (chưa có preview, chưa có kết quả)', () => {
    expect(apply({ type: 'navigate', step: 'schema' }).step).toBe('source')
    expect(apply(...loaded, { type: 'navigate', step: 'result' }).step).toBe('source')
  })

  test('đổi tuỳ chọn đọc thì bỏ preview cũ; preview của dataset khác bị bỏ qua', () => {
    const state = apply(...loaded, { type: 'optionsChanged', options: { encoding: 'WINDOWS-1258' } })

    expect(state.preview).toBeNull()
    expect(state.options).toEqual({ encoding: 'WINDOWS-1258' })
    expect(validatorReducer(state, { type: 'previewLoaded', preview: datasetPreviewFixture({ datasetId: 'khac' }) }).preview).toBeNull()
  })

  test('chạy xong: mở bước Kết quả, tab mặc định là Không hợp lệ khi có dòng lỗi', () => {
    const state = apply(...loaded, { type: 'navigate', step: 'schema' }, { type: 'runStarted' }, { type: 'runCompleted', run: validatorRunFixture(), revision: 2 })

    expect(state.step).toBe('result')
    expect(state.running).toBe(false)
    expect(state.result).toMatchObject({ stale: false, expired: false, page: null, query: { view: 'INVALID', page: 0, field: null, code: null } })
  })

  test('sửa schema sau khi chạy thì kết quả cũ; sửa trong lúc chạy thì kết quả về tới là cũ ngay', () => {
    const base = apply(...loaded, { type: 'navigate', step: 'schema' })
    const afterRun = validatorReducer(base, { type: 'runCompleted', run: validatorRunFixture(), revision: base.revision })
    expect(afterRun.result?.stale).toBe(false)

    const edited = validatorReducer(afterRun, { type: 'fieldUpdated', key: 'f3', patch: { required: true } })
    expect(edited.result?.stale).toBe(true)

    const editedDuringRun = validatorReducer(edited, { type: 'runCompleted', run: validatorRunFixture(), revision: base.revision })
    expect(editedDuringRun.result?.stale).toBe(true)
  })

  test('trang kết quả chỉ nhận khi khớp run và query đang xem', () => {
    const state = apply(...loaded, { type: 'navigate', step: 'schema' }, { type: 'runCompleted', run: validatorRunFixture(), revision: 2 })
    const query = { view: 'INVALID' as const, page: 0, field: null, code: null }

    expect(validatorReducer(state, { type: 'rowsLoaded', runId: 'khac', query, page: validatorRowsFixture() }).result?.page).toBeNull()
    expect(validatorReducer(state, { type: 'rowsLoaded', runId: state.result!.run.id, query: { ...query, page: 1 }, page: validatorRowsFixture() }).result?.page).toBeNull()
    expect(validatorReducer(state, { type: 'rowsLoaded', runId: state.result!.run.id, query, page: validatorRowsFixture() }).result?.page).not.toBeNull()
  })

  test('chạy lỗi: quay về bước Schema với lỗi; sửa schema thì lỗi của BE biến mất', () => {
    const failed = apply(...loaded, { type: 'navigate', step: 'schema' }, { type: 'runStarted' }, {
      type: 'runFailed',
      failure: { text: { headline: 'Schema không hợp lệ' }, problems: null, items: [] },
    })

    expect(failed).toMatchObject({ step: 'schema', running: false })
    expect(failed.runFailure).not.toBeNull()
    expect(validatorReducer(failed, { type: 'schemaRenamed', name: 'moi' }).runFailure).toBeNull()
  })

  test('xoá file thì về bước Dữ liệu, bỏ preview; schema giữ nguyên', () => {
    const state = apply(...loaded, { type: 'navigate', step: 'schema' }, { type: 'datasetChanged', dataset: null })

    expect(state).toMatchObject({ step: 'source', dataset: null, preview: null })
    expect(state.schema.fields).toHaveLength(3)
  })
})
