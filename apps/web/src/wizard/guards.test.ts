import { describe, expect, test } from 'vitest'
import type { SessionInfo, SourcePreview } from '../domain/types'
import { canEnter, isStepDone } from './guards'
import { initialWizardState, type WizardState } from './state'

const session: SessionInfo = { id: 's-1', fileName: 'khach-hang.csv', fileType: 'CSV', sizeBytes: 1024 }
const withSession: WizardState = { ...initialWizardState, session, step: 'preview' }
const preview: SourcePreview = { sheetName: null, columns: ['name'], rows: [], totalRows: 0 }
const withPreview: WizardState = { ...withSession, preview }
const withSavedSchema: WizardState = {
  ...withPreview,
  schema: { draft: [{ key: 'f1', name: 'email', type: 'email', required: true }], saved: true },
}
const withSavedMapping: WizardState = {
  ...withSavedSchema,
  mapping: { draft: { f1: { kind: 'column', column: 'Email' } }, saved: true },
}

describe('isStepDone', () => {
  test('bước Upload xong khi đã có session', () => {
    expect(isStepDone('upload', initialWizardState)).toBe(false)
    expect(isStepDone('upload', withSession)).toBe(true)
  })

  test('bước Xem trước xong khi đã tải được preview', () => {
    expect(isStepDone('preview', withSession)).toBe(false)
    expect(isStepDone('preview', withPreview)).toBe(true)
  })

  test('bước Schema xong khi schema đã lưu', () => {
    expect(isStepDone('schema', withPreview)).toBe(false)
    expect(isStepDone('schema', withSavedSchema)).toBe(true)
  })

  test('bước Mapping xong khi schema và mapping đều đã lưu', () => {
    expect(isStepDone('mapping', withSavedSchema)).toBe(false)
    expect(isStepDone('mapping', withSavedMapping)).toBe(true)
  })

  test('lát F05 chưa có bước nào sau Mapping được coi là xong', () => {
    expect(isStepDone('rules', withSavedMapping)).toBe(false)
  })
})

describe('canEnter', () => {
  test('bước Upload luôn vào được', () => {
    expect(canEnter('upload', initialWizardState)).toEqual({ allowed: true })
  })

  test('chưa có session thì khoá bước Xem trước, kèm lý do', () => {
    expect(canEnter('preview', initialWizardState)).toEqual({ allowed: false, reason: 'Cần upload file trước' })
  })

  test('có session thì vào được bước Xem trước', () => {
    expect(canEnter('preview', withSession)).toEqual({ allowed: true })
  })

  test('chưa tải xong preview thì khoá bước Schema, kèm lý do', () => {
    expect(canEnter('schema', withSession)).toEqual({ allowed: false, reason: 'Cần tải xong dữ liệu xem trước' })
  })

  test('tải xong preview thì vào được bước Schema, kể cả khi file không có dòng dữ liệu', () => {
    expect(canEnter('schema', withPreview)).toEqual({ allowed: true })
  })

  test('schema chưa lưu thì khoá bước Mapping, kèm lý do', () => {
    const unsaved: WizardState = { ...withSavedSchema, schema: { ...withSavedSchema.schema, saved: false } }

    expect(canEnter('mapping', withPreview)).toEqual({ allowed: false, reason: 'Cần lưu schema trước' })
    expect(canEnter('mapping', unsaved)).toEqual({ allowed: false, reason: 'Cần lưu schema trước' })
  })

  test('schema đã lưu và có ít nhất một field thì vào được bước Mapping', () => {
    expect(canEnter('mapping', withSavedSchema)).toEqual({ allowed: true })
  })

  test('mapping chưa lưu thì khoá bước Biến đổi & kiểm tra, kèm lý do', () => {
    expect(canEnter('rules', withSavedSchema)).toEqual({ allowed: false, reason: 'Cần lưu mapping trước' })
  })

  test('schema và mapping đều đã lưu thì vào được bước Biến đổi & kiểm tra', () => {
    expect(canEnter('rules', withSavedMapping)).toEqual({ allowed: true })
  })

  test('mapping đã lưu nhưng schema vừa sửa (chưa lưu) thì vẫn khoá', () => {
    const schemaEdited: WizardState = { ...withSavedMapping, schema: { ...withSavedMapping.schema, saved: false } }

    expect(canEnter('rules', schemaEdited).allowed).toBe(false)
  })

  // Lát F05 chưa có kết quả trong state nên bước Kết quả luôn khoá.
  test('bước Kết quả vẫn khoá khi mapping đã lưu', () => {
    expect(canEnter('result', withSavedMapping)).toEqual({ allowed: false, reason: 'Cần chạy xử lý trước' })
  })
})
