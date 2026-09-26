import { describe, expect, test } from 'vitest'
import type { SessionInfo } from '../domain/types'
import { canEnter, isStepDone } from './guards'
import { initialWizardState, type WizardState } from './state'

const session: SessionInfo = { id: 's-1', fileName: 'khach-hang.csv', fileType: 'CSV', sizeBytes: 1024 }
const withSession: WizardState = { ...initialWizardState, session, step: 'preview' }

describe('isStepDone', () => {
  test('bước Upload xong khi đã có session', () => {
    expect(isStepDone('upload', initialWizardState)).toBe(false)
    expect(isStepDone('upload', withSession)).toBe(true)
  })

  test('lát F01 chưa có bước nào khác được coi là xong', () => {
    expect(isStepDone('preview', withSession)).toBe(false)
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

  // Lát F01 chưa có preview, schema, mapping, kết quả trong state nên các bước sau luôn khoá.
  test.each([
    ['schema', 'Cần tải xong dữ liệu xem trước'],
    ['mapping', 'Cần lưu schema trước'],
    ['rules', 'Cần lưu mapping trước'],
    ['result', 'Cần chạy xử lý trước'],
  ] as const)('bước %s vẫn khoá khi mới có session, lý do "%s"', (step, reason) => {
    expect(canEnter(step, withSession)).toEqual({ allowed: false, reason })
  })
})
