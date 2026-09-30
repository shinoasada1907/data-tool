import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import App from '../../App'
import {
  addStep,
  fieldRegion,
  nextButton,
  openRulesStep,
  runButton,
  schemaGroupByName,
  type User,
} from '../../test/flows'

function steps(fieldName: string) {
  return within(within(fieldRegion(fieldName)).getByRole('list', { name: 'Các bước biến đổi' })).queryAllByRole(
    'group',
  )
}

function summary(fieldName: string) {
  return within(fieldRegion(fieldName)).getByTestId('transformation-summary').textContent
}

describe('bước Biến đổi & kiểm tra', () => {
  test('mỗi field một khối theo thứ tự schema; chưa có biến đổi thì tóm tắt "Không biến đổi"', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)

    const names = screen
      .getAllByRole('heading', { level: 3 })
      .map((heading) => heading.textContent)
    expect(names).toEqual(['2024', 'Họ tên', '1', 'Email'])
    expect(summary('Họ tên')).toBe('Không biến đổi')
  })

  describe('biến đổi', () => {
    test('thêm trim rồi uppercase: tóm tắt "trim → uppercase"; "Lên" ở uppercase thì thành "uppercase → trim"', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)

      await addStep(user, 'Họ tên', 'trim')
      await addStep(user, 'Họ tên', 'uppercase')
      expect(summary('Họ tên')).toBe('trim → uppercase')

      await user.click(within(steps('Họ tên')[1]).getByRole('button', { name: 'Lên' }))

      expect(summary('Họ tên')).toBe('uppercase → trim')
      // Nút "Lên" của bước vừa lên đầu bị khoá: focus sang nút "Xuống" của chính bước đó.
      expect(within(steps('Họ tên')[0]).getByRole('button', { name: 'Xuống' })).toHaveFocus()
    })

    test('defaultValue để trống: lỗi tại ô giá trị, "Chạy xử lý" khoá kèm lý do liệt kê field', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)

      await addStep(user, 'Họ tên', 'defaultValue')

      const input = within(steps('Họ tên')[0]).getByRole('textbox', { name: 'Giá trị mặc định' })
      expect(input).toHaveFocus()
      expect(input).toHaveAccessibleDescription('Cần giá trị mặc định')
      expect(runButton()).toBeDisabled()
      expect(runButton()).toHaveAccessibleDescription('Còn bước biến đổi thiếu tham số: Họ tên')

      await user.type(input, 'Khách lẻ')
      expect(input).not.toHaveAttribute('aria-invalid', 'true')
    })

    test('dateFormat: hai ô định dạng có gợi ý mẫu; outputFormat điền sẵn yyyy-MM-dd và sửa được ở field string', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)

      await addStep(user, 'Họ tên', 'dateFormat')

      const step = steps('Họ tên')[0]
      const input = within(step).getByRole('combobox', { name: 'Định dạng đầu vào' })
      const output = within(step).getByRole('combobox', { name: 'Định dạng đầu ra' })
      expect(input).toHaveAccessibleDescription(/Cần định dạng đầu vào/)
      expect(output).toHaveValue('yyyy-MM-dd')
      expect(output).not.toHaveAttribute('readonly')
      const suggestions = [...document.getElementById(input.getAttribute('list')!)!.querySelectorAll('option')]
      expect(suggestions.map((option) => option.value)).toEqual([
        'yyyy-MM-dd',
        'dd/MM/yyyy',
        'MM/dd/yyyy',
        'dd-MM-yyyy',
        'yyyy/MM/dd',
        'dd.MM.yyyy',
      ])
    })

    test('mẫu ngày có khoảng trắng ở đầu hoặc cuối: cảnh báo tại ô (BE nhận nhưng mọi dòng sẽ lỗi)', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)
      await addStep(user, 'Họ tên', 'dateFormat')

      const input = within(steps('Họ tên')[0]).getByRole('combobox', { name: 'Định dạng đầu vào' })
      await user.type(input, 'dd/MM/yyyy ')

      expect(input).toHaveAccessibleDescription(/khoảng trắng ở đầu hoặc cuối/)
    })

    test('dateFormat ở field kiểu date: outputFormat chỉ đọc, luôn là yyyy-MM-dd', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user, { types: { '2024': 'date' } })

      await addStep(user, '2024', 'dateFormat')

      const output = within(steps('2024')[0]).getByRole('combobox', { name: 'Định dạng đầu ra' })
      expect(output).toHaveValue('yyyy-MM-dd')
      expect(output).toHaveAttribute('readonly')
    })

    test('dateFormat không có trim đứng trước: nhắc thêm trim, vì BE không tự bỏ khoảng trắng', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)

      await addStep(user, 'Họ tên', 'dateFormat')
      expect(within(steps('Họ tên')[0]).getByText(/thêm trim trước/)).toBeInTheDocument()
      // Người đi bằng Tab cũng nghe được: lời nhắc là mô tả của ô định dạng đầu vào.
      expect(within(steps('Họ tên')[0]).getByRole('combobox', { name: 'Định dạng đầu vào' })).toHaveAccessibleDescription(
        /thêm trim trước/,
      )

      await addStep(user, 'Họ tên', 'trim')
      await user.click(within(steps('Họ tên')[1]).getByRole('button', { name: 'Lên' }))
      expect(within(steps('Họ tên')[1]).queryByText(/thêm trim trước/)).not.toBeInTheDocument()
    })

    test('"Xoá" bước: focus sang control đầu tiên bấm được của bước kề bên (ô tham số nếu có)', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)
      await addStep(user, 'Họ tên', 'trim')
      await addStep(user, 'Họ tên', 'defaultValue')

      await user.click(within(steps('Họ tên')[0]).getByRole('button', { name: 'Xoá' }))

      expect(within(steps('Họ tên')[0]).getByRole('textbox', { name: 'Giá trị mặc định' })).toHaveFocus()
    })

    test('tóm tắt chuỗi biến đổi là vùng live: thêm, xoá, đổi thứ tự đều được đọc cho screen reader', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)

      expect(within(fieldRegion('Họ tên')).getByTestId('transformation-summary')).toHaveAttribute('aria-live', 'polite')
    })

    test('"Xoá" bước: focus sang bước kề bên; hết bước thì về ô chọn loại biến đổi', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)
      await addStep(user, 'Họ tên', 'trim')
      await addStep(user, 'Họ tên', 'lowercase')

      await user.click(within(steps('Họ tên')[1]).getByRole('button', { name: 'Xoá' }))
      // Control đầu của bước kề bên (như bước Schema): bước trim không có tham số, "Lên" đang khoá, nên là "Xuống"… hoặc
      // nút đầu tiên bấm được. Ở đây bước còn lại là bước duy nhất nên "Lên"/"Xuống" đều khoá: focus vào "Xoá".
      expect(within(steps('Họ tên')[0]).getByRole('button', { name: 'Xoá' })).toHaveFocus()

      await user.click(within(steps('Họ tên')[0]).getByRole('button', { name: 'Xoá' }))
      expect(within(fieldRegion('Họ tên')).getByRole('combobox', { name: 'Loại biến đổi' })).toHaveFocus()
      expect(summary('Họ tên')).toBe('Không biến đổi')
    })
  })

  describe('sửa schema sau khi đã cấu hình rules', () => {
    async function backToSchema(user: User) {
      await user.click(screen.getByRole('button', { name: 'Quay lại' }))
      await user.click(screen.getByRole('button', { name: 'Quay lại' }))
      await screen.findByRole('heading', { level: 2, name: 'Schema đích' })
    }

    async function forwardToRules(user: User) {
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Mapping' })
      await user.click(nextButton())
      await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })
    }

    test('đổi kiểu field khỏi string: rule email biến mất, unique vẫn bật', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)
      await user.click(within(fieldRegion('Họ tên')).getByRole('checkbox', { name: /email/ }))
      await user.click(within(fieldRegion('Họ tên')).getByRole('checkbox', { name: /unique/ }))

      await backToSchema(user)
      await user.selectOptions(within(schemaGroupByName('Họ tên')).getByRole('combobox', { name: 'Kiểu' }), 'number')
      await forwardToRules(user)

      expect(within(fieldRegion('Họ tên')).queryByRole('checkbox', { name: /email/ })).not.toBeInTheDocument()
      expect(within(fieldRegion('Họ tên')).getByRole('checkbox', { name: /unique/ })).toBeChecked()
    })

    test('đổi kiểu field sang date: định dạng đầu ra của dateFormat thành yyyy-MM-dd, chỉ đọc', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)
      await addStep(user, 'Họ tên', 'dateFormat')
      const output = within(steps('Họ tên')[0]).getByRole('combobox', { name: 'Định dạng đầu ra' })
      await user.clear(output)
      await user.type(output, 'dd.MM.yyyy')

      await backToSchema(user)
      await user.selectOptions(within(schemaGroupByName('Họ tên')).getByRole('combobox', { name: 'Kiểu' }), 'date')
      await forwardToRules(user)

      const locked = within(steps('Họ tên')[0]).getByRole('combobox', { name: 'Định dạng đầu ra' })
      expect(locked).toHaveValue('yyyy-MM-dd')
      expect(locked).toHaveAttribute('readonly')
    })
  })

  describe('kiểm tra', () => {
    test('field bắt buộc kiểu number: rule suy ra required và type:number; có unique, không có email', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user, { required: ['1'] })

      const region = fieldRegion('1')
      const implied = within(within(region).getByRole('list', { name: 'Rule suy ra từ schema' }))
        .getAllByRole('listitem')
        .map((item) => item.textContent)
      expect(implied).toEqual(['required', 'type:number'])
      expect(within(region).getByRole('checkbox', { name: /unique/ })).not.toBeChecked()
      expect(within(region).queryByRole('checkbox', { name: /email/ })).not.toBeInTheDocument()
    })

    test('field kiểu email: rule suy ra type:email, không có tuỳ chọn email', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)

      const region = fieldRegion('Email')
      expect(within(region).getByRole('list', { name: 'Rule suy ra từ schema' })).toHaveTextContent('type:email')
      expect(within(region).queryByRole('checkbox', { name: /email/ })).not.toBeInTheDocument()
    })

    test('field kiểu string có tuỳ chọn email và unique; lựa chọn còn nguyên khi quay lại bước', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)

      await user.click(within(fieldRegion('Họ tên')).getByRole('checkbox', { name: /unique/ }))
      await user.click(within(fieldRegion('Họ tên')).getByRole('checkbox', { name: /email/ }))
      await user.click(screen.getByRole('button', { name: 'Quay lại' }))
      await user.click(nextButton())

      expect(within(fieldRegion('Họ tên')).getByRole('checkbox', { name: /unique/ })).toBeChecked()
      expect(within(fieldRegion('Họ tên')).getByRole('checkbox', { name: /email/ })).toBeChecked()
    })
  })
})
