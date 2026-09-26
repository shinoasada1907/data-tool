import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import App from '../../App'
import { configUpdateFixture, importSessionFixture, problemFixture } from '../../mocks/fixtures'
import { testFile } from '../../test/files'
import { gate, mockSaveSchema, mockUpload, problemResponse } from '../../test/http'

type User = ReturnType<typeof userEvent.setup>

const saved = () => HttpResponse.json(configUpdateFixture())

/**
 * Upload, chờ bảng preview (handler mặc định: `csvPreviewFixture`), rồi bấm "Tiếp" sang bước Schema. Schema được sinh
 * sẵn từ cột nguồn; mặc định xoá hết qua giao diện, để các test về thao tác sửa bắt đầu từ danh sách trống.
 */
async function openSchemaStep(user: User, { keepGenerated = false }: { keepGenerated?: boolean } = {}) {
  mockUpload(() => HttpResponse.json(importSessionFixture(), { status: 201 }))
  render(<App />)
  await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile('khach-hang.csv'))
  await screen.findByRole('table', { name: 'Dữ liệu xem trước' })
  await user.click(nextButton())
  await screen.findByRole('heading', { level: 2, name: 'Schema đích' })
  if (!keepGenerated) {
    while (screen.queryAllByRole('group', { name: /^Field \d+$/ }).length > 0) {
      await user.click(within(fieldGroup(1)).getByRole('button', { name: 'Xoá' }))
    }
  }
}

function fieldTypes() {
  return screen.getAllByRole('combobox', { name: 'Kiểu' }).map((select) => (select as HTMLSelectElement).value)
}

function nextButton() {
  return screen.getByRole('button', { name: /^Tiếp/ })
}

function stepButton(name: RegExp) {
  return screen.getByRole('button', { name })
}

function fieldGroup(position: number) {
  return screen.getByRole('group', { name: `Field ${position}` })
}

function nameInput(position: number) {
  return within(fieldGroup(position)).getByRole('textbox', { name: 'Tên field' })
}

function fieldNames() {
  return screen.getAllByRole('textbox', { name: 'Tên field' }).map((input) => (input as HTMLInputElement).value)
}

function rejectEmailAsDuplicate() {
  return mockSaveSchema(() =>
    HttpResponse.json(
      problemFixture(422, 'SCHEMA_INVALID', 'Schema is invalid.', {
        errors: [{ field: 'email', code: 'SCHEMA_INVALID', message: 'Duplicate field name.' }],
      }),
      { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
    ),
  )
}

/** Thêm một field ở cuối rồi điền tên (bằng dán, để nhanh), kiểu, bắt buộc. Trả về vị trí của field. */
async function addField(user: User, name: string, { type, required }: { type?: string; required?: boolean } = {}) {
  await user.click(screen.getByRole('button', { name: 'Thêm field' }))
  const position = screen.getAllByRole('group', { name: /^Field \d+$/ }).length
  if (name) await user.paste(name)
  if (type) await user.selectOptions(within(fieldGroup(position)).getByRole('combobox', { name: 'Kiểu' }), type)
  if (required) await user.click(within(fieldGroup(position)).getByRole('checkbox', { name: 'Bắt buộc' }))
  return position
}

describe('bước Schema', () => {
  test('xoá hết field: "Tiếp" khoá với lý do "Cần ít nhất một field"', async () => {
    const user = userEvent.setup()
    await openSchemaStep(user)

    expect(screen.queryAllByRole('group', { name: /^Field \d+$/ })).toHaveLength(0)
    expect(nextButton()).toBeDisabled()
    expect(nextButton()).toHaveAccessibleDescription('Cần ít nhất một field')
  })

  describe('sinh schema từ cột nguồn', () => {
    // csvPreviewFixture: cột "2024" (A01…), "Họ tên" (chữ, có ô chỉ khoảng trắng), "1" (10, null, 7), "Email".
    test('vào bước lần đầu: mỗi cột thành một field cùng tên, đúng thứ tự, kiểu đoán từ dữ liệu, không bắt buộc', async () => {
      const user = userEvent.setup()
      await openSchemaStep(user, { keepGenerated: true })

      expect(fieldNames()).toEqual(['2024', 'Họ tên', '1', 'Email'])
      expect(fieldTypes()).toEqual(['string', 'string', 'number', 'email'])
      for (const checkbox of screen.getAllByRole('checkbox', { name: 'Bắt buộc' })) {
        expect(checkbox).not.toBeChecked()
      }
      expect(nextButton()).toBeEnabled()
    })

    test('không tự sinh lại: xoá một field, sang Preview rồi quay lại thì field đó không xuất hiện lại', async () => {
      const user = userEvent.setup()
      await openSchemaStep(user, { keepGenerated: true })

      await user.click(within(fieldGroup(2)).getByRole('button', { name: 'Xoá' }))
      await user.click(screen.getByRole('button', { name: 'Quay lại' }))
      await user.click(nextButton())

      expect(fieldNames()).toEqual(['2024', '1', 'Email'])
    })

    test('"Tạo lại từ file" khi đang có field: hỏi xác nhận; "Huỷ" thì giữ nguyên và trả focus về nút', async () => {
      const user = userEvent.setup()
      await openSchemaStep(user, { keepGenerated: true })
      await user.type(nameInput(1), '-x')

      await user.click(screen.getByRole('button', { name: 'Tạo lại từ file' }))
      const confirm = screen.getByRole('group', {
        name: 'Thay toàn bộ 4 field hiện có bằng 4 field sinh từ các cột của file?',
      })
      await user.click(within(confirm).getByRole('button', { name: 'Huỷ' }))

      expect(fieldNames()).toEqual(['2024-x', 'Họ tên', '1', 'Email'])
      expect(screen.getByRole('button', { name: 'Tạo lại từ file' })).toHaveFocus()
    })

    test('"Tạo lại từ file", đồng ý: thay toàn bộ field bằng bản sinh từ cột nguồn, focus vào field đầu', async () => {
      const user = userEvent.setup()
      await openSchemaStep(user, { keepGenerated: true })
      await user.type(nameInput(1), '-x')
      await user.click(within(fieldGroup(2)).getByRole('button', { name: 'Xoá' }))
      await addField(user, 'thêm')

      await user.click(screen.getByRole('button', { name: 'Tạo lại từ file' }))
      await user.click(screen.getByRole('button', { name: 'Tạo lại' }))

      expect(fieldNames()).toEqual(['2024', 'Họ tên', '1', 'Email'])
      expect(fieldTypes()).toEqual(['string', 'string', 'number', 'email'])
      expect(nameInput(1)).toHaveFocus()
    })

    test('"Tạo lại từ file" khi đã xoá hết field: sinh ngay, không hỏi', async () => {
      const user = userEvent.setup()
      await openSchemaStep(user)

      await user.click(screen.getByRole('button', { name: 'Tạo lại từ file' }))

      expect(screen.queryByRole('button', { name: 'Tạo lại' })).not.toBeInTheDocument()
      expect(fieldNames()).toEqual(['2024', 'Họ tên', '1', 'Email'])
    })

    test('lưu schema sinh sẵn mà không sửa gì: PUT đúng tên, kiểu và thứ tự của các cột', async () => {
      const save = mockSaveSchema(saved)
      const user = userEvent.setup()
      await openSchemaStep(user, { keepGenerated: true })

      await user.click(nextButton())

      await screen.findByRole('heading', { level: 2, name: 'Mapping' })
      expect(save.bodies).toEqual([
        {
          fields: [
            { name: '2024', type: 'string', required: false, order: 0 },
            { name: 'Họ tên', type: 'string', required: false, order: 1 },
            { name: '1', type: 'number', required: false, order: 2 },
            { name: 'Email', type: 'email', required: false, order: 3 },
          ],
        },
      ])
    })
  })

  test('"Thêm field": field mới ở cuối, kiểu string, không bắt buộc, ô tên được focus và chưa báo lỗi', async () => {
    const user = userEvent.setup()
    await openSchemaStep(user)

    await user.click(screen.getByRole('button', { name: 'Thêm field' }))

    const group = fieldGroup(1)
    expect(nameInput(1)).toHaveFocus()
    expect(nameInput(1)).toHaveValue('')
    expect(within(group).getByRole('combobox', { name: 'Kiểu' })).toHaveValue('string')
    expect(within(group).getByRole('checkbox', { name: 'Bắt buộc' })).not.toBeChecked()
    expect(nameInput(1)).not.toHaveAttribute('aria-invalid', 'true')
    expect(nextButton()).toBeDisabled()
    expect(nextButton()).toHaveAccessibleDescription('Còn field chưa đặt tên')
  })

  test('danh sách kiểu có đúng 5 lựa chọn', async () => {
    const user = userEvent.setup()
    await openSchemaStep(user)
    await addField(user, 'a')

    const options = within(within(fieldGroup(1)).getByRole('combobox', { name: 'Kiểu' })).getAllByRole('option')

    expect(options.map((option) => (option as HTMLOptionElement).value)).toEqual([
      'string',
      'number',
      'boolean',
      'date',
      'email',
    ])
  })

  describe('kiểm tên ngay trên form', () => {
    test('tên chỉ có khoảng trắng: báo lỗi sau khi rời ô tên', async () => {
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, '   ')

      await user.tab()

      expect(nameInput(1)).toHaveAttribute('aria-invalid', 'true')
      expect(nameInput(1)).toHaveAccessibleDescription('Tên field không được để trống')
      expect(nextButton()).toBeDisabled()
    })

    test('tên dài 101 ký tự: báo lỗi và khoá "Tiếp"', async () => {
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'a'.repeat(101))

      await user.tab()

      expect(nameInput(1)).toHaveAccessibleDescription('Tên field tối đa 100 ký tự')
      expect(nextButton()).toHaveAccessibleDescription('Còn lỗi ở tên field')
    })

    test('"Email" và " email " trùng tên: cả hai field báo lỗi', async () => {
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'Email')
      await addField(user, ' email ')

      await user.tab()

      expect(nameInput(1)).toHaveAccessibleDescription('Tên field bị trùng')
      expect(nameInput(2)).toHaveAccessibleDescription('Tên field bị trùng')
      expect(nextButton()).toBeDisabled()
    })
  })

  test('"Lên", "Xuống" đổi thứ tự, nút ở biên bị khoá; "Xoá" bỏ field khỏi danh sách', async () => {
    const user = userEvent.setup()
    await openSchemaStep(user)
    await addField(user, 'a')
    await addField(user, 'b')
    await addField(user, 'c')

    expect(within(fieldGroup(1)).getByRole('button', { name: 'Lên' })).toBeDisabled()
    expect(within(fieldGroup(3)).getByRole('button', { name: 'Xuống' })).toBeDisabled()

    await user.click(within(fieldGroup(3)).getByRole('button', { name: 'Lên' }))
    expect(fieldNames()).toEqual(['a', 'c', 'b'])

    await user.click(within(fieldGroup(1)).getByRole('button', { name: 'Xuống' }))
    expect(fieldNames()).toEqual(['c', 'a', 'b'])

    await user.click(within(fieldGroup(2)).getByRole('button', { name: 'Xoá' }))
    expect(fieldNames()).toEqual(['c', 'b'])
  })

  test('"Xuống" ở giữa danh sách: nút "Xuống" của chính field vừa di chuyển vẫn giữ focus', async () => {
    const user = userEvent.setup()
    await openSchemaStep(user)
    await addField(user, 'a')
    await addField(user, 'b')
    await addField(user, 'c')

    await user.click(within(fieldGroup(1)).getByRole('button', { name: 'Xuống' }))

    expect(fieldNames()).toEqual(['b', 'a', 'c'])
    expect(within(fieldGroup(2)).getByRole('button', { name: 'Xuống' })).toHaveFocus()
  })

  test('rời bước rồi quay lại: field đã báo lỗi vẫn báo lỗi', async () => {
    const user = userEvent.setup()
    await openSchemaStep(user)
    await addField(user, 'a')
    await addField(user, '')
    await user.tab()
    expect(nameInput(2)).toHaveAttribute('aria-invalid', 'true')

    await user.click(screen.getByRole('button', { name: 'Quay lại' }))
    await user.click(nextButton())

    expect(nameInput(2)).toHaveAccessibleDescription('Tên field không được để trống')
  })

  test('focus không rơi về đầu trang khi nút vừa bấm bị khoá hoặc biến mất', async () => {
    const user = userEvent.setup()
    await openSchemaStep(user)
    await addField(user, 'a')
    await addField(user, 'b')

    // "a" xuống cuối: nút "Xuống" của nó bị khoá, focus sang nút "Lên" của chính field đó.
    await user.click(within(fieldGroup(1)).getByRole('button', { name: 'Xuống' }))
    expect(fieldNames()).toEqual(['b', 'a'])
    expect(within(fieldGroup(2)).getByRole('button', { name: 'Lên' })).toHaveFocus()

    // Xoá field cuối: focus vào ô tên của field liền trước.
    await user.click(within(fieldGroup(2)).getByRole('button', { name: 'Xoá' }))
    expect(nameInput(1)).toHaveFocus()

    // Xoá field cuối cùng: focus vào nút "Thêm field".
    await user.click(within(fieldGroup(1)).getByRole('button', { name: 'Xoá' }))
    expect(screen.getByRole('button', { name: 'Thêm field' })).toHaveFocus()
  })

  describe('lưu khi bấm "Tiếp"', () => {
    test('gửi PUT với tên đã trim và order theo thứ tự hiển thị, rồi sang Mapping', async () => {
      const save = mockSaveSchema(saved)
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, ' email ', { type: 'email', required: true })
      await addField(user, 'Họ tên')

      await user.click(nextButton())

      expect(await screen.findByRole('heading', { level: 2, name: 'Mapping' })).toHaveFocus()
      expect(save.bodies).toEqual([
        {
          fields: [
            { name: 'email', type: 'email', required: true, order: 0 },
            { name: 'Họ tên', type: 'string', required: false, order: 1 },
          ],
        },
      ])
      expect(stepButton(/Schema đích/)).toHaveAccessibleName('3 Schema đích (đã xong)')
    })

    test('đã lưu và không sửa gì: quay lại rồi "Tiếp" thì không gọi PUT lần nữa', async () => {
      const save = mockSaveSchema(saved)
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Mapping' })

      await user.click(stepButton(/Schema đích/))
      await user.click(nextButton())

      expect(screen.getByRole('heading', { level: 2, name: 'Mapping' })).toBeInTheDocument()
      expect(save.calls()).toBe(1)
    })

    test('sửa sau khi đã lưu: schema về chưa lưu, Mapping bị khoá, "Tiếp" gửi PUT lại', async () => {
      const save = mockSaveSchema(saved)
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Mapping' })
      await user.click(stepButton(/Schema đích/))

      await user.type(nameInput(1), '2')

      expect(stepButton(/Mapping/)).toHaveAttribute('aria-disabled', 'true')
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Mapping' })
      expect(save.calls()).toBe(2)
      expect(save.bodies[1]).toEqual({ fields: [{ name: 'email2', type: 'string', required: false, order: 0 }] })
    })

    test('bấm "Tiếp" hai lần liền chỉ gửi một PUT', async () => {
      const save = mockSaveSchema(saved)
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')

      await user.dblClick(nextButton())

      await screen.findByRole('heading', { level: 2, name: 'Mapping' })
      expect(save.calls()).toBe(1)
    })

    test('đang lưu: khoá stepper và các nút điều hướng', async () => {
      const hold = gate()
      mockSaveSchema(async () => {
        await hold.promise
        return saved()
      })
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')

      await user.click(nextButton())

      expect(nextButton()).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Quay lại' })).toBeDisabled()
      expect(stepButton(/Upload file/)).toBeDisabled()
      // Sửa trong lúc lưu thì bản vừa lưu không còn là bản đang hiển thị: khoá luôn phần sửa.
      expect(nameInput(1)).toBeDisabled()
      expect(within(fieldGroup(1)).getByRole('combobox', { name: 'Kiểu' })).toBeDisabled()
      expect(within(fieldGroup(1)).getByRole('button', { name: 'Xoá' })).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Thêm field' })).toBeDisabled()
      hold.open()
      await screen.findByRole('heading', { level: 2, name: 'Mapping' })
    })

    test('lưu xong rồi quay lại: phần sửa mở khoá', async () => {
      mockSaveSchema(saved)
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Mapping' })

      await user.click(stepButton(/Schema đích/))

      expect(nameInput(1)).toBeEnabled()
      expect(screen.getByRole('button', { name: 'Thêm field' })).toBeEnabled()
    })
  })

  describe('lỗi khi lưu', () => {
    test('422 có lỗi theo field: lỗi hiện tại field có tên khớp, ở lại bước Schema, focus vào ô tên đó', async () => {
      mockSaveSchema(() =>
        HttpResponse.json(
          problemFixture(422, 'SCHEMA_INVALID', 'Schema is invalid.', {
            errors: [{ field: 'email', code: 'SCHEMA_INVALID', message: 'Duplicate field name.' }],
          }),
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      )
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'name')
      await addField(user, ' email ')

      await user.click(nextButton())

      expect(await screen.findByRole('alert')).toHaveTextContent('Schema không hợp lệ')
      expect(nameInput(2)).toHaveAccessibleDescription('Duplicate field name.')
      expect(nameInput(2)).toHaveFocus()
      expect(nameInput(1)).not.toHaveAttribute('aria-invalid', 'true')
      expect(screen.getByRole('heading', { level: 2, name: 'Schema đích' })).toBeInTheDocument()
      expect(stepButton(/Mapping/)).toHaveAttribute('aria-disabled', 'true')
    })

    test('lúc ô tên nhận focus thì mô tả lỗi đã gắn sẵn, để screen reader đọc được ngay', async () => {
      rejectEmailAsDuplicate()
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')
      let atFocus: string | null = null
      nameInput(1).addEventListener('focus', (event) => {
        const id = (event.target as HTMLElement).getAttribute('aria-describedby')
        atFocus = id ? (document.getElementById(id)?.textContent ?? null) : null
      })

      await user.click(nextButton())
      await screen.findByRole('alert')

      expect(atFocus).toBe('Duplicate field name.')
    })

    test('sửa một field khác cũng xoá lỗi BE và khối lỗi, vì chúng nói về bản đã gửi', async () => {
      rejectEmailAsDuplicate()
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'name')
      await addField(user, 'email')
      await user.click(nextButton())
      await screen.findByRole('alert')
      expect(nameInput(2)).toHaveAttribute('aria-invalid', 'true')

      await user.type(nameInput(1), 's')

      expect(nameInput(2)).not.toHaveAttribute('aria-invalid', 'true')
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    test('sửa field đang có lỗi từ BE thì lỗi đó biến mất', async () => {
      mockSaveSchema(() =>
        HttpResponse.json(
          problemFixture(422, 'SCHEMA_INVALID', 'Schema is invalid.', {
            errors: [{ field: 'email', code: 'SCHEMA_INVALID', message: 'Duplicate field name.' }],
          }),
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      )
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')
      await user.click(nextButton())
      await screen.findByRole('alert')

      await user.type(nameInput(1), 'x')

      expect(nameInput(1)).not.toHaveAttribute('aria-invalid', 'true')
    })

    test('422 có lỗi không gắn với field nào: liệt kê ở đầu form', async () => {
      mockSaveSchema(() =>
        HttpResponse.json(
          problemFixture(422, 'SCHEMA_INVALID', 'Schema is invalid.', {
            errors: [{ field: null, code: 'SCHEMA_INVALID', message: 'Field name must not be blank.' }],
          }),
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      )
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')

      await user.click(nextButton())

      const alert = await screen.findByRole('alert')
      expect(within(alert).getByRole('listitem')).toHaveTextContent('Field name must not be blank.')
      expect(screen.getByRole('heading', { level: 2, name: 'Schema đích' })).toHaveFocus()
    })

    test.each([
      ['404 SESSION_NOT_FOUND', 404, 'SESSION_NOT_FOUND'],
      ['409 SESSION_STATE_INVALID', 409, 'SESSION_STATE_INVALID'],
    ])('%s: có nút "Upload lại", bấm thì về bước Upload trống', async (_, status, code) => {
      mockSaveSchema(() => problemResponse(status, code, 'Import session not found.'))
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')

      await user.click(nextButton())
      await user.click(within(await screen.findByRole('alert')).getByRole('button', { name: 'Upload lại' }))

      expect(screen.getByRole('heading', { level: 2, name: 'Upload file nguồn' })).toHaveFocus()
      expect(stepButton(/Schema đích/)).toHaveAttribute('aria-disabled', 'true')
    })

    test('lỗi mạng: báo lỗi, không có "Thử lại" (không tự gửi lại lệnh ghi); bấm "Tiếp" lần nữa thì lưu được', async () => {
      const save = mockSaveSchema(() => HttpResponse.error(), saved)
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')

      await user.click(nextButton())

      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent('Không kết nối được máy chủ')
      expect(within(alert).queryByRole('button')).not.toBeInTheDocument()

      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Mapping' })
      expect(save.calls()).toBe(2)
    })

    test('404 REQUEST_INVALID (BE chưa có endpoint): lỗi thường, hiện detail của BE, không mất state', async () => {
      mockSaveSchema(() => problemResponse(404, 'REQUEST_INVALID', 'No endpoint PUT /api/import-sessions/x/schema.'))
      const user = userEvent.setup()
      await openSchemaStep(user)
      await addField(user, 'email')

      await user.click(nextButton())

      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent('Yêu cầu không hợp lệ')
      expect(alert).toHaveTextContent('No endpoint PUT /api/import-sessions/x/schema.')
      expect(within(alert).queryByRole('button')).not.toBeInTheDocument()
      expect(fieldNames()).toEqual(['email'])
    })
  })
})
