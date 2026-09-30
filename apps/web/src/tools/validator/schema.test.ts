import { describe, expect, test } from 'vitest'
import {
  checkSchema,
  fieldsFromColumns,
  hasProblems,
  matchColumns,
  newField,
  problemsFromPointers,
  readSchemaFile,
  schemaFileContent,
  schemaFileName,
  toSchemaDto,
  type FieldDraft,
  type SchemaDraft,
} from './schema'

function field(key: string, patch: Partial<Omit<FieldDraft, 'constraints'>> & { constraints?: Partial<FieldDraft['constraints']> } = {}): FieldDraft {
  const base = newField(key)
  return { ...base, ...patch, constraints: { ...base.constraints, ...patch.constraints } }
}

function draft(fields: FieldDraft[], name = 'khach'): SchemaDraft {
  return { name, fields }
}

describe('fieldsFromColumns', () => {
  test('mỗi cột một field, kiểu theo inferredType (empty thành string), không bắt buộc, key tăng dần', () => {
    const { fields, nextSeq } = fieldsFromColumns(
      [
        { index: 0, name: 'ma', inferredType: 'string', emptyCount: 0 },
        { index: 1, name: 'gia', inferredType: 'number', emptyCount: 0 },
        { index: 2, name: 'ghi chu', inferredType: 'empty', emptyCount: 3 },
      ],
      4,
    )

    expect(fields.map(({ key, name, type, required, unique }) => ({ key, name, type, required, unique }))).toEqual([
      { key: 'f4', name: 'ma', type: 'string', required: false, unique: false },
      { key: 'f5', name: 'gia', type: 'number', required: false, unique: false },
      { key: 'f6', name: 'ghi chu', type: 'string', required: false, unique: false },
    ])
    expect(nextSeq).toBe(7)
  })
})

describe('checkSchema', () => {
  test('schema hợp lệ thì không có lỗi', () => {
    expect(hasProblems(checkSchema(draft([field('f1', { name: 'ma' })])))).toBe(false)
  })

  test('tên schema rỗng, không có field', () => {
    const problems = checkSchema(draft([], '  '))

    expect(problems.name).toBe('Tên schema không được để trống')
    expect(problems.general).toEqual(['Cần ít nhất một field'])
  })

  test('tên field rỗng, và trùng sau khi trim + không phân biệt hoa thường thì báo ở cả hai field', () => {
    const problems = checkSchema(draft([field('f1', { name: 'Email' }), field('f2', { name: ' email ' }), field('f3')]))

    expect(problems.fields.f1?.name).toBe('Tên field bị trùng')
    expect(problems.fields.f2?.name).toBe('Tên field bị trùng')
    expect(problems.fields.f3?.name).toBe('Tên field không được để trống')
  })

  test('number: min > max báo ở max; số không đọc được báo ở đúng ô', () => {
    const problems = checkSchema(
      draft([field('f1', { name: 'gia', type: 'number', constraints: { min: '10', max: '5' } }), field('f2', { name: 'sl', type: 'number', constraints: { min: 'abc' } })]),
    )

    expect(problems.fields.f1).toEqual({ max: 'Phải lớn hơn hoặc bằng min' })
    expect(problems.fields.f2).toEqual({ min: 'Phải là một số' })
  })

  test('độ dài: phải là số nguyên không âm, và tối thiểu ≤ tối đa', () => {
    const problems = checkSchema(
      draft([field('f1', { name: 'ten', constraints: { minLength: '-1' } }), field('f2', { name: 'ma', type: 'email', constraints: { minLength: '5', maxLength: '2' } })]),
    )

    expect(problems.fields.f1).toEqual({ minLength: 'Phải là số nguyên từ 0 đến 32767' })
    expect(problems.fields.f2).toEqual({ maxLength: 'Phải lớn hơn hoặc bằng độ dài tối thiểu' })
  })

  test('ràng buộc không hợp với kiểu hiện tại thì bỏ qua (chữ nhập vẫn giữ, chỉ ẩn đi)', () => {
    const problems = checkSchema(draft([field('f1', { name: 'ngay', type: 'date', constraints: { min: 'abc' } })]))

    expect(hasProblems(problems)).toBe(false)
  })
})

describe('toSchemaDto', () => {
  test('trim tên, đổi số, chỉ gửi ràng buộc hợp với kiểu và không rỗng; unique chỉ khi bật', () => {
    const dto = toSchemaDto(
      draft(
        [
          field('f1', { name: ' gia ', type: 'number', required: true, unique: true, constraints: { min: '0', max: '1.5', pattern: 'x' } }),
          field('f2', { name: 'ten', constraints: { maxLength: '50', pattern: '' } }),
          field('f3', { name: 'ngay', type: 'date', constraints: { format: 'dd/MM/yyyy' } }),
          field('f4', { name: 'ok', type: 'boolean', defaultValue: 'true' }),
        ],
        ' Khách ',
      ),
    )

    expect(dto).toEqual({
      name: 'Khách',
      fields: [
        { name: 'gia', type: 'number', required: true, constraints: { unique: true, min: 0, max: 1.5 } },
        { name: 'ten', type: 'string', required: false, constraints: { maxLength: 50 } },
        { name: 'ngay', type: 'date', required: false, constraints: { format: 'dd/MM/yyyy' } },
        { name: 'ok', type: 'boolean', required: false, constraints: { defaultValue: 'true' } },
      ],
    })
  })
})

describe('problemsFromPointers', () => {
  test('pointer tới field/ràng buộc thì gắn vào đúng ô; pointer lạ thì vào danh sách chung', () => {
    const schema = draft([field('f1', { name: 'ma' }), field('f7', { name: 'gia' })])

    const problems = problemsFromPointers(schema, [
      { field: null, code: 'CONSTRAINT_INVALID', message: 'Pattern is not valid RE2.', pointer: '/schema/fields/1/constraints/pattern' },
      { field: null, code: 'FIELD_NAME_INVALID', message: 'Name too long.', pointer: '/schema/fields/0/name' },
      { field: null, code: 'SCHEMA_NAME_INVALID', message: 'Name is blank.', pointer: '/schema/name' },
      { field: null, code: 'SCHEMA_FIELDS_INVALID', message: 'Too many fields.', pointer: '/schema/fields' },
    ])

    expect(problems.fields).toEqual({
      f7: { pattern: 'Ràng buộc không hợp lệ: Pattern is not valid RE2.' },
      f1: { name: 'Tên field không hợp lệ: Name too long.' },
    })
    expect(problems.name).toBe('Tên schema không hợp lệ: Name is blank.')
    expect(problems.general).toEqual(['Danh sách field không hợp lệ: Too many fields.'])
  })
})

describe('matchColumns', () => {
  test('tên giống hệt trước, rồi trim + không phân biệt hoa thường; mỗi cột một field; cột thừa liệt kê riêng', () => {
    const schema = draft([field('f1', { name: 'email' }), field('f2', { name: 'ma' }), field('f3', { name: 'sdt' })])

    expect(matchColumns(schema, ['MA', ' Email ', 'ma', 'ghi chu'])).toEqual({
      byField: { f1: ' Email ', f2: 'ma', f3: null },
      extraColumns: ['MA', 'ghi chu'],
    })
  })
})

describe('file schema', () => {
  test('lưu rồi mở lại được đúng schema (kể cả defaultValue không có ô nhập)', () => {
    const original = draft([
      field('f1', { name: 'gia', type: 'number', required: true, unique: true, constraints: { min: '1' } }),
      field('f2', { name: 'ok', type: 'boolean', defaultValue: 'false' }),
    ])
    const text = schemaFileContent(original)

    expect(JSON.parse(text)).toMatchObject({ format: 'udt.schema', version: 1, name: 'khach' })

    const read = readSchemaFile(text, 10)
    expect(read).toMatchObject({ ok: true, name: 'khach', nextSeq: 12 })
    if (!read.ok) throw new Error('unreachable')
    expect(toSchemaDto({ name: read.name, fields: read.fields })).toEqual(toSchemaDto(original))
    expect(read.fields.map((f) => f.key)).toEqual(['f10', 'f11'])
  })

  test.each([
    ['không phải JSON', '{'],
    ['thiếu format', JSON.stringify({ version: 1, name: 'a', fields: [] })],
    ['sai version', JSON.stringify({ format: 'udt.schema', version: 2, name: 'a', fields: [] })],
    ['kiểu lạ', JSON.stringify({ format: 'udt.schema', version: 1, name: 'a', fields: [{ name: 'x', type: 'int' }] })],
  ])('từ chối file %s', (_label, text) => {
    expect(readSchemaFile(text, 1)).toEqual({ ok: false })
  })

  test('tên file: bỏ ký tự không dùng được trong tên file', () => {
    expect(schemaFileName('Khách/hàng: 2026')).toBe('Khách_hàng_ 2026.schema.json')
    expect(schemaFileName('  ')).toBe('schema.schema.json')
  })
})
