import { describe, expect, test } from 'vitest'
import { checkMapping } from './configRules'
import type { FieldMapping, TargetField } from './types'

function field(key: string, name: string, required = false): TargetField {
  return { key, name, type: 'string', required }
}

const column = (name: string): FieldMapping => ({ kind: 'column', column: name })
const constant = (value: string): FieldMapping => ({ kind: 'constant', value })

describe('checkMapping', () => {
  test('mọi field đã map hợp lệ: không có vấn đề, không bị chặn', () => {
    const result = checkMapping([field('f1', 'email', true), field('f2', 'country')], {
      f1: column('E-mail'),
      f2: constant('VN'),
    })

    expect(result).toEqual({ issues: {}, blockedReason: null })
  })

  test('field không bắt buộc chưa map: chỉ cảnh báo, không chặn', () => {
    const result = checkMapping([field('f1', 'note')], {})

    expect(result).toEqual({
      issues: { f1: { level: 'warning', message: 'Chưa map (field không bắt buộc)' } },
      blockedReason: null,
    })
  })

  test('field bắt buộc chưa map: lỗi, lý do chặn liệt kê tên field theo thứ tự schema', () => {
    const result = checkMapping([field('f1', 'email', true), field('f2', 'note'), field('f3', ' name ', true)], {})

    expect(result.issues.f1).toEqual({ level: 'error', message: 'Field bắt buộc chưa được map' })
    expect(result.issues.f3).toEqual({ level: 'error', message: 'Field bắt buộc chưa được map' })
    expect(result.blockedReason).toBe('Field bắt buộc chưa map: email, name')
  })

  test.each(['', '   '])('giá trị cố định %j (rỗng sau khi trim) là lỗi và chặn', (value) => {
    const result = checkMapping([field('f1', 'country')], { f1: constant(value) })

    expect(result.issues.f1).toEqual({ level: 'error', message: 'Giá trị cố định không được để trống' })
    expect(result.blockedReason).toBe('Giá trị cố định đang trống: country')
  })

  // Cùng luật với `String.isBlank()` của Java mà BE dùng, để FE chặn đúng những gì BE sẽ từ chối, không hơn không kém.
  test.each([
    ['tab và xuống dòng', true, '\t\n'],
    ['ký tự phân cách U+001F (Java coi là khoảng trắng)', true, '\u001f'],
    ['khoảng trắng Unicode U+2003', true, '\u2003'],
    ['NBSP U+00A0 (Java không coi là khoảng trắng)', false, '\u00a0'],
    ['có chữ', false, ' VN '],
  ])('giá trị cố định là %s → coi là rỗng: %s', (_, blank, value) => {
    const result = checkMapping([field('f1', 'country')], { f1: constant(value) })

    expect(result.issues.f1 !== undefined).toBe(blank)
  })

  test('có cả hai loại lỗi: lý do chặn nêu cả hai', () => {
    const result = checkMapping([field('f1', 'email', true), field('f2', 'country')], { f2: constant('') })

    expect(result.blockedReason).toBe('Field bắt buộc chưa map: email; Giá trị cố định đang trống: country')
  })

  test('một cột nguồn dùng cho nhiều field là hợp lệ', () => {
    const result = checkMapping([field('f1', 'name'), field('f2', 'display_name')], {
      f1: column('Họ tên'),
      f2: column('Họ tên'),
    })

    expect(result).toEqual({ issues: {}, blockedReason: null })
  })
})
