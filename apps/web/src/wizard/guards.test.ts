import { describe, expect, test } from 'vitest'
import type { SessionInfo, SourcePreview } from '../domain/types'
import { canEnter, isStepDone } from './guards'
import { initialWizardState, type WizardState } from './state'

const session: SessionInfo = { id: 's-1', fileName: 'khach-hang.csv', fileType: 'CSV', sizeBytes: 1024 }
const withSession: WizardState = { ...initialWizardState, session, step: 'preview' }
const preview: SourcePreview = { sheetName: null, columns: ['name'], rows: [], totalRows: 0 }
const withPreview: WizardState = { ...withSession, preview }

describe('isStepDone', () => {
  test('bước Upload xong khi đã có session', () => {
    expect(isStepDone('upload', initialWizardState)).toBe(false)
    expect(isStepDone('upload', withSession)).toBe(true)
  })

  test('bước Xem trước xong khi đã tải được preview', () => {
    expect(isStepDone('preview', withSession)).toBe(false)
    expect(isStepDone('preview', withPreview)).toBe(true)
  })

  test('lát F02 chưa có bước nào sau Xem trước được coi là xong', () => {
    expect(isStepDone('schema', withPreview)).toBe(false)
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

  // Lát F02 chưa có schema, mapping, kết quả trong state nên các bước sau luôn khoá.
  test.each([
    ['mapping', 'Cần lưu schema trước'],
    ['rules', 'Cần lưu mapping trước'],
    ['result', 'Cần chạy xử lý trước'],
  ] as const)('bước %s vẫn khoá khi đã có preview, lý do "%s"', (step, reason) => {
    expect(canEnter(step, withPreview)).toEqual({ allowed: false, reason })
  })
})
