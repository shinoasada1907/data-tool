import { describe, expect, test } from 'vitest'
import { canUseRule, checkMapping, checkTransformations, impliedRules } from './configRules'
import type { FieldMapping, FieldType, TargetField, Transformation } from './types'

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

describe('checkTransformations', () => {
  const fields = [field('f1', 'name'), field('f2', ' email ')]

  test('tham số đủ thì không có vấn đề, không chặn', () => {
    const result = checkTransformations(fields, {
      f1: [
        { id: 't1', type: 'trim' },
        { id: 't2', type: 'defaultValue', value: 'N/A' },
      ],
      f2: [{ id: 't3', type: 'dateFormat', inputFormat: 'dd/MM/yyyy', outputFormat: 'yyyy-MM-dd' }],
    })

    expect(result).toEqual({ issues: {}, blockedReason: null })
  })

  test('defaultValue rỗng là lỗi tại ô giá trị', () => {
    const result = checkTransformations(fields, { f1: [{ id: 't1', type: 'defaultValue', value: '  ' }] })

    expect(result.issues).toEqual({ t1: { value: 'Cần giá trị mặc định' } })
  })

  test('dateFormat thiếu định dạng: lỗi tại đúng ô thiếu', () => {
    const steps: Transformation[] = [
      { id: 't1', type: 'dateFormat', inputFormat: '', outputFormat: 'yyyy-MM-dd' },
      { id: 't2', type: 'dateFormat', inputFormat: 'dd/MM/yyyy', outputFormat: ' ' },
    ]

    expect(checkTransformations(fields, { f1: steps }).issues).toEqual({
      t1: { inputFormat: 'Cần định dạng đầu vào' },
      t2: { outputFormat: 'Cần định dạng đầu ra' },
    })
  })

  // BE-F06 dùng TextValues.isEmpty cho tham số: khoảng trắng Java cộng cả NBSP (U+00A0, U+2007, U+202F).
  test.each([['\u00a0'], ['\u2007'], ['\u202f'], ['\u001f'], ['\t ']])(
    'tham số chỉ gồm %j là rỗng, như luật của BE cho tham số transformation',
    (value) => {
      const result = checkTransformations(fields, { f1: [{ id: 't1', type: 'defaultValue', value }] })

      expect(result.issues).toEqual({ t1: { value: 'Cần giá trị mặc định' } })
    },
  )

  test('lý do chặn liệt kê tên field có bước thiếu tham số, theo thứ tự schema, mỗi field một lần', () => {
    const result = checkTransformations(fields, {
      f2: [{ id: 't3', type: 'defaultValue', value: '' }],
      f1: [
        { id: 't1', type: 'defaultValue', value: '' },
        { id: 't2', type: 'dateFormat', inputFormat: '', outputFormat: '' },
      ],
    })

    expect(result.blockedReason).toBe('Còn bước biến đổi thiếu tham số: name, email')
  })
})

describe('rule kiểm tra', () => {
  test.each<[FieldType, boolean, string[]]>([
    ['number', true, ['required', 'type:number']],
    ['email', false, ['type:email']],
    ['string', false, ['type:string']],
  ])('field kiểu %s, bắt buộc %s → rule suy ra %j', (type, required, expected) => {
    expect(impliedRules({ key: 'f1', name: 'x', type, required })).toEqual(expected)
  })

  test.each<[FieldType, boolean]>([
    ['string', true],
    ['email', false],
    ['number', false],
    ['boolean', false],
    ['date', false],
  ])('rule email bật được ở field kiểu %s: %s', (type, expected) => {
    expect(canUseRule({ key: 'f1', name: 'x', type, required: false }, 'email')).toBe(expected)
  })

  test('rule unique bật được ở mọi kiểu', () => {
    for (const type of ['string', 'number', 'boolean', 'date', 'email'] as const) {
      expect(canUseRule({ key: 'f1', name: 'x', type, required: false }, 'unique')).toBe(true)
    }
  })
})
