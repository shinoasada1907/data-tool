import { describe, expect, test } from 'vitest'
import { checkSchema, matchServerErrors, MAX_FIELD_NAME_LENGTH } from './schemaRules'

// Cùng một chữ Việt ở hai dạng Unicode: dựng sẵn (NFC) và tổ hợp (NFD). Nhìn giống hệt nhau, khác chuỗi.
const nfc = (text: string) => text.normalize('NFC')
const nfd = (text: string) => text.normalize('NFD')
import type { TargetField } from './types'

function field(key: string, name: string): TargetField {
  return { key, name, type: 'string', required: false }
}

describe('checkSchema', () => {
  test('schema hợp lệ: không có lỗi, không bị chặn', () => {
    const result = checkSchema([field('f1', 'name'), field('f2', 'email')])

    expect(result).toEqual({ errors: {}, blockedReason: null })
  })

  test('schema rỗng thì bị chặn với lý do "Cần ít nhất một field"', () => {
    expect(checkSchema([])).toEqual({ errors: {}, blockedReason: 'Cần ít nhất một field' })
  })

  test.each(['', '   '])('tên %j (rỗng sau khi trim) là lỗi, và lý do chặn là "Còn field chưa đặt tên"', (name) => {
    const result = checkSchema([field('f1', 'ok'), field('f2', name)])

    expect(result.errors).toEqual({ f2: 'Tên field không được để trống' })
    expect(result.blockedReason).toBe('Còn field chưa đặt tên')
  })

  test('tên dài đúng 100 ký tự sau khi trim thì hợp lệ, 101 ký tự thì lỗi', () => {
    const exact = `  ${'a'.repeat(MAX_FIELD_NAME_LENGTH)}  `
    const tooLong = 'b'.repeat(MAX_FIELD_NAME_LENGTH + 1)

    const result = checkSchema([field('f1', exact), field('f2', tooLong)])

    expect(result.errors).toEqual({ f2: 'Tên field tối đa 100 ký tự' })
    expect(result.blockedReason).toBe('Còn lỗi ở tên field')
  })

  test('trùng tên sau khi trim, không phân biệt hoa thường: đánh dấu cả hai field', () => {
    const result = checkSchema([field('f1', 'Email'), field('f2', 'name'), field('f3', ' email ')])

    expect(result.errors).toEqual({ f1: 'Tên field bị trùng', f3: 'Tên field bị trùng' })
    expect(result.blockedReason).toBe('Còn lỗi ở tên field')
  })

  test('cùng một tên gõ ở dạng NFC và NFD là trùng tên', () => {
    const result = checkSchema([field('f1', nfc('Họ tên')), field('f2', nfd('Họ tên'))])

    expect(result.errors).toEqual({ f1: 'Tên field bị trùng', f2: 'Tên field bị trùng' })
  })

  test('độ dài đếm sau khi chuẩn hoá NFC: 100 chữ "ệ" gõ dạng NFD (300 code unit) vẫn hợp lệ', () => {
    expect(checkSchema([field('f1', nfd('ệ'.repeat(MAX_FIELD_NAME_LENGTH)))]).errors).toEqual({})
  })

  test('nhiều field cùng rỗng không bị coi là trùng nhau', () => {
    expect(checkSchema([field('f1', ''), field('f2', ' ')]).errors).toEqual({
      f1: 'Tên field không được để trống',
      f2: 'Tên field không được để trống',
    })
  })

  test('vừa có tên rỗng vừa có lỗi khác: lý do chặn ưu tiên "Còn field chưa đặt tên"', () => {
    const result = checkSchema([field('f1', ''), field('f2', 'x'.repeat(101))])

    expect(result.blockedReason).toBe('Còn field chưa đặt tên')
  })
})

describe('matchServerErrors', () => {
  const fields = [field('f1', ' email '), field('f2', 'name')]

  test('lỗi có field trùng tên (đã trim) của một field thì gắn vào key của field đó', () => {
    const result = matchServerErrors(fields, [{ field: 'email', message: 'Duplicate field name.' }])

    expect(result).toEqual({ byKey: { f1: 'Duplicate field name.' }, general: [] })
  })

  test('lỗi không có field, hoặc field không khớp tên nào, đưa lên đầu form (kèm tên field nếu có)', () => {
    const result = matchServerErrors(fields, [
      { field: null, message: 'Schema must contain at least one field.' },
      { field: 'ghost', message: 'Unknown field type.' },
    ])

    expect(result).toEqual({
      byKey: {},
      general: ['Schema must contain at least one field.', 'ghost: Unknown field type.'],
    })
  })

  test('BE trả tên dạng NFC vẫn khớp field gõ dạng NFD', () => {
    const result = matchServerErrors([field('f1', nfd(' Họ tên '))], [{ field: nfc('Họ tên'), message: 'Unknown field type.' }])

    expect(result.byKey).toEqual({ f1: 'Unknown field type.' })
  })

  test('nhiều lỗi cho cùng một field thì nối lại', () => {
    const result = matchServerErrors(fields, [
      { field: 'name', message: 'Unknown field type.' },
      { field: 'name', message: 'Duplicate field order.' },
    ])

    expect(result.byKey).toEqual({ f2: 'Unknown field type. Duplicate field order.' })
  })
})
