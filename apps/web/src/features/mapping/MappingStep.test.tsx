import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import App from '../../App'
import { configUpdateFixture, importSessionFixture, problemFixture } from '../../mocks/fixtures'
import { testFile } from '../../test/files'
import { gate, mockSaveMapping, mockSaveSchema, mockUpload, problemResponse } from '../../test/http'

type User = ReturnType<typeof userEvent.setup>

const saved = () => HttpResponse.json(configUpdateFixture())

// Preview mặc định (csvPreviewFixture): cột "2024" (A01, A02, A03), "Họ tên" (Nguyễn An, ô chỉ khoảng trắng, Lê Chi),
// "1" (10, null, 7), "Email" (an@…, binh@…, null). Schema sinh sẵn 4 field cùng tên, map sẵn với cột cùng tên.

function nextButton() {
  return screen.getByRole('button', { name: /^Tiếp/ })
}

function stepButton(name: RegExp) {
  return screen.getByRole('button', { name })
}

function row(fieldName: string) {
  return screen.getByRole('group', { name: fieldName })
}

function source(fieldName: string) {
  return within(row(fieldName)).getByRole('combobox', { name: 'Nguồn' })
}

function selectedText(select: HTMLElement) {
  const element = select as HTMLSelectElement
  return element.options[element.selectedIndex].text
}

/**
 * Upload → Xem trước → Schema (sinh sẵn; `required` đánh dấu bắt buộc theo tên) → lưu schema → Mapping.
 * Trả về mock của PUT schema để test kiểm được số lần gửi.
 */
async function openMappingStep(user: User, { required = [] as string[] } = {}) {
  mockUpload(() => HttpResponse.json(importSessionFixture(), { status: 201 }))
  const schemaSave = mockSaveSchema(saved)
  render(<App />)
  await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile('khach-hang.csv'))
  await screen.findByRole('table', { name: 'Dữ liệu xem trước' })
  await user.click(nextButton())
  await screen.findByRole('heading', { level: 2, name: 'Schema đích' })
  for (const name of required) {
    const group = screen
      .getAllByRole('group', { name: /^Field \d+$/ })
      .find((candidate) => (within(candidate).getByRole('textbox', { name: 'Tên field' }) as HTMLInputElement).value === name)
    await user.click(within(group!).getByRole('checkbox', { name: 'Bắt buộc' }))
  }
  await user.click(nextButton())
  await screen.findByRole('heading', { level: 2, name: 'Mapping' })
  return schemaSave
}

describe('bước Mapping', () => {
  test('vào lần đầu: mỗi field theo thứ tự schema, map sẵn với cột nguồn cùng tên', async () => {
    const user = userEvent.setup()
    await openMappingStep(user)

    // Mỗi ô "Nguồn" nằm trong nhóm của một field; tên nhóm là tên field.
    const fieldOrder = screen
      .getAllByRole('combobox', { name: 'Nguồn' })
      .map((select) => select.closest('fieldset')?.querySelector('legend')?.textContent)
    expect(fieldOrder).toEqual(['2024', 'Họ tên', '1', 'Email'])
    expect(selectedText(source('Email'))).toBe('Email')
    expect(selectedText(source('2024'))).toBe('2024')
  })

  test('danh sách nguồn: "Chưa map", các cột theo đúng thứ tự preview, rồi "Giá trị cố định…"', async () => {
    const user = userEvent.setup()
    await openMappingStep(user)

    const options = within(source('Email'))
      .getAllByRole('option')
      .map((option) => option.textContent)

    expect(options).toEqual(['Chưa map', '2024', 'Họ tên', '1', 'Email', 'Giá trị cố định…'])
  })

  test('cạnh field hiện tối đa 3 giá trị mẫu không rỗng của cột đã chọn, lấy từ preview', async () => {
    const user = userEvent.setup()
    await openMappingStep(user)

    const samples = (fieldName: string) =>
      within(within(row(fieldName)).getByRole('list', { name: 'Giá trị mẫu' }))
        .getAllByRole('listitem')
        .map((item) => item.textContent)

    expect(samples('2024')).toEqual(['A01', 'A02', 'A03'])
    // Ô chỉ có khoảng trắng bị bỏ qua.
    expect(samples('Họ tên')).toEqual(['Nguyễn An', 'Lê Chi'])

    await user.selectOptions(source('Email'), 'Họ tên')
    expect(samples('Email')).toEqual(['Nguyễn An', 'Lê Chi'])
  })

  test('"Giá trị cố định…": hiện ô nhập; để trống thì lỗi tại field và "Tiếp" khoá kèm lý do', async () => {
    const user = userEvent.setup()
    await openMappingStep(user)

    await user.selectOptions(source('Email'), 'Giá trị cố định…')
    const input = within(row('Email')).getByRole('textbox', { name: 'Giá trị cố định' })

    expect(input).toHaveAccessibleDescription('Giá trị cố định không được để trống')
    expect(nextButton()).toBeDisabled()
    expect(nextButton()).toHaveAccessibleDescription('Giá trị cố định đang trống: Email')

    await user.type(input, 'khong-co@example.com')
    expect(nextButton()).toBeEnabled()
  })

  test('field không bắt buộc chưa map: chỉ cảnh báo, "Tiếp" vẫn bấm được', async () => {
    const user = userEvent.setup()
    await openMappingStep(user)

    await user.selectOptions(source('1'), 'Chưa map')

    expect(within(row('1')).getByText('Chưa map (field không bắt buộc)')).toBeInTheDocument()
    expect(source('1')).not.toHaveAttribute('aria-invalid', 'true')
    expect(nextButton()).toBeEnabled()
  })

  test('field bắt buộc chưa map: lỗi tại field, "Tiếp" khoá với lý do liệt kê tên field', async () => {
    const user = userEvent.setup()
    await openMappingStep(user, { required: ['Email'] })

    await user.selectOptions(source('Email'), 'Chưa map')

    expect(source('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(source('Email')).toHaveAccessibleDescription(/Field bắt buộc chưa được map/)
    expect(nextButton()).toHaveAccessibleDescription('Field bắt buộc chưa map: Email')
  })

  test('screen reader: ô "Nguồn" được mô tả bằng kiểu, bắt buộc và giá trị mẫu của cột đang chọn', async () => {
    const user = userEvent.setup()
    await openMappingStep(user, { required: ['Email'] })

    expect(source('Email')).toHaveAccessibleDescription(/email/)
    expect(source('Email')).toHaveAccessibleDescription(/Bắt buộc/)
    expect(source('Email')).toHaveAccessibleDescription(/an@example\.com/)
    expect(source('1')).not.toHaveAccessibleDescription(/Bắt buộc/)
  })

  test('lướt sang lựa chọn khác rồi quay lại "Giá trị cố định…" thì giá trị đã gõ vẫn còn', async () => {
    const user = userEvent.setup()
    await openMappingStep(user)
    await user.selectOptions(source('2024'), 'Giá trị cố định…')
    await user.type(within(row('2024')).getByRole('textbox', { name: 'Giá trị cố định' }), 'VN')

    await user.selectOptions(source('2024'), 'Chưa map')
    await user.selectOptions(source('2024'), 'Họ tên')
    await user.selectOptions(source('2024'), 'Giá trị cố định…')

    expect(within(row('2024')).getByRole('textbox', { name: 'Giá trị cố định' })).toHaveValue('VN')
  })

  test('quay lại bước Schema rồi trở lại Mapping: lựa chọn đang sửa (chưa lưu) vẫn còn', async () => {
    const user = userEvent.setup()
    await openMappingStep(user)
    await user.selectOptions(source('Họ tên'), 'Chưa map')

    await user.click(screen.getByRole('button', { name: 'Quay lại' }))
    await user.click(nextButton())

    expect(selectedText(source('Họ tên'))).toBe('Chưa map')
  })

  describe('lưu khi bấm "Tiếp"', () => {
    test('payload chỉ gồm field đã map, theo thứ tự schema; lưu xong sang bước Biến đổi & kiểm tra', async () => {
      const save = mockSaveMapping(saved)
      const user = userEvent.setup()
      await openMappingStep(user)
      await user.selectOptions(source('2024'), 'Giá trị cố định…')
      await user.type(within(row('2024')).getByRole('textbox', { name: 'Giá trị cố định' }), 'VN')
      await user.selectOptions(source('Họ tên'), 'Chưa map')

      await user.click(nextButton())

      expect(await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })).toHaveFocus()
      expect(save.bodies).toEqual([
        {
          mappings: [
            { targetField: '2024', mappingType: 'CONSTANT', sourceColumn: null, constantValue: 'VN' },
            { targetField: '1', mappingType: 'SOURCE_COLUMN', sourceColumn: '1', constantValue: null },
            { targetField: 'Email', mappingType: 'SOURCE_COLUMN', sourceColumn: 'Email', constantValue: null },
          ],
        },
      ])
      expect(stepButton(/Mapping/)).toHaveAccessibleName('4 Mapping (đã xong)')
    })

    test('đã lưu và không sửa gì: quay lại rồi "Tiếp" thì không PUT lần nữa', async () => {
      const save = mockSaveMapping(saved)
      const user = userEvent.setup()
      await openMappingStep(user)
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })

      await user.click(stepButton(/Mapping/))
      await user.click(nextButton())

      expect(screen.getByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })).toBeInTheDocument()
      expect(save.calls()).toBe(1)
    })

    test('đang lưu: khoá phần sửa, stepper và nút điều hướng', async () => {
      const hold = gate()
      mockSaveMapping(async () => {
        await hold.promise
        return saved()
      })
      const user = userEvent.setup()
      await openMappingStep(user)

      await user.click(nextButton())

      expect(source('Email')).toBeDisabled()
      expect(nextButton()).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Quay lại' })).toBeDisabled()
      expect(stepButton(/Schema đích/)).toBeDisabled()
      hold.open()
      await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })
    })

    test('422 SOURCE_COLUMN_NOT_FOUND: lỗi tại dòng field đó, ở lại bước Mapping, focus vào ô nguồn của field', async () => {
      mockSaveMapping(() =>
        HttpResponse.json(
          problemFixture(422, 'SOURCE_COLUMN_NOT_FOUND', 'Mapping is invalid.', {
            errors: [{ field: 'Email', code: 'SOURCE_COLUMN_NOT_FOUND', message: 'Source column does not exist.' }],
          }),
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      )
      const user = userEvent.setup()
      await openMappingStep(user)

      await user.click(nextButton())

      expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy cột nguồn')
      expect(source('Email')).toHaveAccessibleDescription(/Source column does not exist\./)
      expect(source('Email')).toHaveFocus()
      expect(screen.getByRole('heading', { level: 2, name: 'Mapping' })).toBeInTheDocument()
      expect(stepButton(/Biến đổi & kiểm tra/)).toHaveAttribute('aria-disabled', 'true')
    })

    test('422 cho field đang dùng giá trị cố định: lỗi và focus nằm ở ô nhập giá trị, không ở ô chọn nguồn', async () => {
      mockSaveMapping(() =>
        HttpResponse.json(
          problemFixture(422, 'MAPPING_INVALID', 'Mapping is invalid.', {
            errors: [{ field: '2024', code: 'MAPPING_INVALID', message: 'constantValue must not be blank for CONSTANT mappings.' }],
          }),
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      )
      const user = userEvent.setup()
      await openMappingStep(user)
      await user.selectOptions(source('2024'), 'Giá trị cố định…')
      await user.type(within(row('2024')).getByRole('textbox', { name: 'Giá trị cố định' }), 'VN')

      await user.click(nextButton())
      await screen.findByRole('alert')

      const input = within(row('2024')).getByRole('textbox', { name: 'Giá trị cố định' })
      expect(input).toHaveFocus()
      expect(input).toHaveAttribute('aria-invalid', 'true')
      expect(input).toHaveAccessibleDescription('constantValue must not be blank for CONSTANT mappings.')
    })

    test('sau 422, sửa một field khác thì lỗi BE và khối lỗi biến mất (chúng nói về bản đã gửi)', async () => {
      mockSaveMapping(() =>
        HttpResponse.json(
          problemFixture(422, 'SOURCE_COLUMN_NOT_FOUND', 'Mapping is invalid.', {
            errors: [{ field: 'Email', code: 'SOURCE_COLUMN_NOT_FOUND', message: 'Source column does not exist.' }],
          }),
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      )
      const user = userEvent.setup()
      await openMappingStep(user)
      await user.click(nextButton())
      await screen.findByRole('alert')

      await user.selectOptions(source('1'), 'Chưa map')

      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(source('Email')).not.toHaveAttribute('aria-invalid', 'true')
      expect(source('Email')).not.toHaveAccessibleDescription(/Source column does not exist/)
    })

    test('sau 404 SESSION_NOT_FOUND, sửa mapping thì nút "Upload lại" vẫn còn', async () => {
      mockSaveMapping(() => problemResponse(404, 'SESSION_NOT_FOUND', 'Import session not found.'))
      const user = userEvent.setup()
      await openMappingStep(user)
      await user.click(nextButton())
      await screen.findByRole('alert')

      await user.selectOptions(source('1'), 'Chưa map')

      expect(within(screen.getByRole('alert')).getByRole('button', { name: 'Upload lại' })).toBeInTheDocument()
    })

    test('404 SESSION_NOT_FOUND: có nút "Upload lại", bấm thì về bước Upload trống', async () => {
      mockSaveMapping(() => problemResponse(404, 'SESSION_NOT_FOUND', 'Import session not found.'))
      const user = userEvent.setup()
      await openMappingStep(user)

      await user.click(nextButton())
      await user.click(within(await screen.findByRole('alert')).getByRole('button', { name: 'Upload lại' }))

      expect(screen.getByRole('heading', { level: 2, name: 'Upload file nguồn' })).toHaveFocus()
    })
  })

  describe('sửa schema sau khi đã lưu mapping', () => {
    async function backToSchemaAfterSavingMapping(user: User) {
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })
      await user.click(stepButton(/Schema đích/))
    }

    function schemaNameInput(value: string) {
      return screen
        .getAllByRole('textbox', { name: 'Tên field' })
        .find((input) => (input as HTMLInputElement).value === value)!
    }

    test('đổi tên field: mapping giữ nguyên, và lần PUT mapping kế tiếp gửi tên mới', async () => {
      const mappingSave = mockSaveMapping(saved)
      const user = userEvent.setup()
      const schemaSave = await openMappingStep(user)
      await backToSchemaAfterSavingMapping(user)

      await user.type(schemaNameInput('Email'), '_address')
      // Sửa schema thì mapping phải lưu lại: bước sau bị khoá.
      expect(stepButton(/Biến đổi & kiểm tra/)).toHaveAttribute('aria-disabled', 'true')
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Mapping' })

      expect(selectedText(source('Email_address'))).toBe('Email')
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })
      expect(schemaSave.calls()).toBe(2)
      expect(mappingSave.bodies[1]).toEqual({
        mappings: [
          { targetField: '2024', mappingType: 'SOURCE_COLUMN', sourceColumn: '2024', constantValue: null },
          { targetField: 'Họ tên', mappingType: 'SOURCE_COLUMN', sourceColumn: 'Họ tên', constantValue: null },
          { targetField: '1', mappingType: 'SOURCE_COLUMN', sourceColumn: '1', constantValue: null },
          { targetField: 'Email_address', mappingType: 'SOURCE_COLUMN', sourceColumn: 'Email', constantValue: null },
        ],
      })
    })

    test('xoá field: mapping của field đó không còn trong lần PUT mapping kế tiếp', async () => {
      const mappingSave = mockSaveMapping(saved)
      const user = userEvent.setup()
      await openMappingStep(user)
      await backToSchemaAfterSavingMapping(user)

      const emailGroup = schemaNameInput('Email').closest('fieldset')!
      await user.click(within(emailGroup as HTMLElement).getByRole('button', { name: 'Xoá' }))
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Mapping' })
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })

      const targets = (mappingSave.bodies[1] as { mappings: { targetField: string }[] }).mappings.map(
        (item) => item.targetField,
      )
      expect(targets).toEqual(['2024', 'Họ tên', '1'])
    })
  })
})
