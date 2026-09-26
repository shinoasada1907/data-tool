import { describe, expect, test } from 'vitest'
import type { SessionInfo } from '../domain/types'
import { wizardReducer } from './reducer'
import { initialWizardState, isBusy, type WizardState } from './state'

const session: SessionInfo = { id: 's-1', fileName: 'khach-hang.csv', fileType: 'CSV', sizeBytes: 1024 }
const atPreview: WizardState = { step: 'preview', pendingRequests: 0, session }

describe('wizardReducer', () => {
  test('sessionCreated lưu session và chuyển sang bước Xem trước', () => {
    const next = wizardReducer(initialWizardState, { type: 'sessionCreated', session })

    expect(next).toEqual({ step: 'preview', pendingRequests: 0, session })
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
    expect(wizardReducer(atPreview, { type: 'reset' })).toEqual(initialWizardState)
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
