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

  test('lát F04 chưa có bước nào sau Schema được coi là xong', () => {
    expect(isStepDone('mapping', withSavedSchema)).toBe(false)
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

  // Lát F04 chưa có mapping và kết quả trong state nên các bước sau luôn khoá.
  test.each([
    ['rules', 'Cần lưu mapping trước'],
    ['result', 'Cần chạy xử lý trước'],
  ] as const)('bước %s vẫn khoá khi schema đã lưu, lý do "%s"', (step, reason) => {
    expect(canEnter(step, withSavedSchema)).toEqual({ allowed: false, reason })
  })
})
