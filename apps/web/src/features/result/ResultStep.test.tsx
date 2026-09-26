import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse } from 'msw'
import { describe, expect, test, vi } from 'vitest'
import App from '../../App'
import { pipelineResultFixture, pipelineSummaryFixture, validResultFixture } from '../../mocks/fixtures'
import {
  fieldRegion,
  openResultStep,
  pagesByView,
  RESULT_HEADING,
  stepButton,
  type User,
} from '../../test/flows'
import {
  gate,
  mockSaveValidations,
  problemResponse,
  problemWithErrors,
  recordRequests,
} from '../../test/http'

function table() {
  return screen.getByRole('table')
}

function headers() {
  return within(table())
    .getAllByRole('columnheader')
    .map((header) => header.textContent)
}

/** Dòng dữ liệu (không gồm dòng chi tiết lỗi) có số dòng này. */
function dataRow(rowNumber: number) {
  return within(table())
    .getAllByRole('row')
    .find((row) => within(row).queryByRole('rowheader')?.textContent === String(rowNumber))!
}

/** Ô của dòng `rowNumber` ở cột `header`. */
function cell(rowNumber: number, header: string) {
  return within(dataRow(rowNumber)).getAllByRole('cell')[headers().indexOf(header) - 1]
}

function tab(name: RegExp) {
  return screen.getByRole('tab', { name })
}

describe('bước Kết quả', () => {
  test('ba thẻ tóm tắt Tổng / Hợp lệ / Lỗi lấy từ summary', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openResultStep(user)

    const summary = screen.getByRole('group', { name: 'Tóm tắt kết quả' })
    expect(within(summary).getAllByRole('term').map((term) => term.textContent)).toEqual(['Tổng', 'Hợp lệ', 'Lỗi'])
    expect(within(summary).getAllByRole('definition').map((value) => value.textContent)).toEqual(['120', '100', '20'])
  })

  test('có dòng lỗi thì tab "Lỗi (20)" được chọn sẵn', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openResultStep(user)

    expect(tab(/^Lỗi \(20\)$/)).toHaveAttribute('aria-selected', 'true')
    expect(tab(/^Hợp lệ \(100\)$/)).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByRole('tabpanel')).toContainElement(table())
  })

  test('cột "Dòng" rồi các field theo thứ tự schema, không theo thứ tự key của values (kể cả tên dạng số)', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openResultStep(user)

    expect(headers()).toEqual(['Dòng', '2024', 'Họ tên', '1', 'Email'])
    expect(cell(3, '2024')).toHaveTextContent('A02')
    expect(cell(3, 'Họ tên')).toHaveTextContent('Trần Bình')
  })

  test('lỗi validation: ô của field được đánh dấu, dưới dòng ghi field, nhãn mã lỗi, rule và giá trị nguồn', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openResultStep(user)

    expect(cell(3, 'Email')).toHaveAttribute('data-flagged')
    expect(cell(3, 'Email')).toHaveTextContent('binh@ (có lỗi)')
    expect(cell(3, 'Họ tên')).not.toHaveAttribute('data-flagged')
    const details = within(screen.getByRole('list', { name: 'Lỗi của dòng 3' })).getAllByRole('listitem')
    expect(details[1]).toHaveTextContent('Email')
    expect(details[1]).toHaveTextContent('Email không hợp lệ')
    expect(details[1]).toHaveTextContent('VALIDATION_EMAIL')
    expect(details[1]).toHaveTextContent('rule email')
    expect(details[1]).toHaveTextContent('Giá trị nguồn: “binh@”')
    expect(details[1]).toHaveTextContent('Value is not a valid email address.')
  })

  test('lỗi transformation: ô null hiện placeholder và được đánh dấu; lỗi ghi rõ bước (step + 1) và giá trị nguồn', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openResultStep(user)

    expect(cell(3, '1')).toHaveAttribute('data-flagged')
    expect(cell(3, '1')).toHaveTextContent('Ô trống')
    const details = within(screen.getByRole('list', { name: 'Lỗi của dòng 3' })).getAllByRole('listitem')
    expect(details[0]).toHaveTextContent('Biến đổi dữ liệu thất bại')
    expect(details[0]).toHaveTextContent('dateFormat ở bước 2')
    expect(details[0]).toHaveTextContent('Giá trị nguồn: “31/02/2024”')
  })

  test('số dài hơn độ chính xác của JS hiện đủ chữ số, số có 0 ở cuối giữ nguyên như BE gửi', async () => {
    const user = userEvent.setup()
    const dto = validResultFixture()
    const body = JSON.stringify({ ...dto, rows: [{ ...dto.rows[0], values: { ...dto.rows[0].values, '1': '__N__' } }] }).replace(
      '"__N__"',
      '12345678901234567890.50',
    )
    render(<App />)
    await openResultStep(user, {
      summary: pipelineSummaryFixture({ valid: 120, invalid: 0, errorCountsByCode: {}, errorCountsByField: {} }),
      result: () => new HttpResponse(body, { headers: { 'Content-Type': 'application/json' } }),
    })

    expect(cell(2, '1')).toHaveTextContent('12345678901234567890.50')
  })

  test('tab Hợp lệ: gọi view=valid ở trang 0; giá trị đã ép kiểu hiện nguyên dạng, null hiện placeholder', async () => {
    const user = userEvent.setup()
    render(<App />)
    const requests = await openResultStep(user)

    await user.click(tab(/^Hợp lệ/))

    expect(await screen.findByRole('table', { name: 'Dòng hợp lệ' })).toBeInTheDocument()
    expect(requests.queries.at(-1)).toEqual({ view: 'valid', page: '0', size: '50' })
    expect(tab(/^Hợp lệ/)).toHaveAttribute('aria-selected', 'true')
    expect(cell(2, '1')).toHaveTextContent('10')
    expect(cell(5, 'Email')).toHaveTextContent('Ô trống')
    expect(screen.queryByRole('list', { name: /Lỗi của dòng/ })).not.toBeInTheDocument()
  })

  test('tab dùng phím mũi tên để chuyển focus, Enter mới tải tab đó', async () => {
    const user = userEvent.setup()
    render(<App />)
    const requests = await openResultStep(user)
    tab(/^Lỗi/).focus()

    await user.keyboard('{ArrowLeft}')
    expect(tab(/^Hợp lệ/)).toHaveFocus()
    expect(requests.calls()).toBe(1)

    await user.keyboard('{Enter}')
    await screen.findByRole('table', { name: 'Dòng hợp lệ' })
    expect(requests.queries.at(-1)).toMatchObject({ view: 'valid', page: '0' })
  })

  test('tab: mũi tên phải vòng về đầu, Home/End tới tab đầu/cuối; Space tải tab đang focus', async () => {
    const user = userEvent.setup()
    render(<App />)
    const requests = await openResultStep(user)
    tab(/^Lỗi/).focus()

    await user.keyboard('{ArrowRight}')
    expect(tab(/^Hợp lệ/)).toHaveFocus()
    await user.keyboard('{End}')
    expect(tab(/^Lỗi/)).toHaveFocus()
    await user.keyboard('{Home}')
    expect(tab(/^Hợp lệ/)).toHaveFocus()
    expect(requests.calls()).toBe(1)

    await user.keyboard(' ')
    await screen.findByRole('table', { name: 'Dòng hợp lệ' })
    expect(requests.queries.at(-1)).toMatchObject({ view: 'valid', page: '0' })
  })

  test('dòng chi tiết lỗi chỉ gắn với ô số dòng của nó, không với mọi tiêu đề cột', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openResultStep(user)

    const detailCell = screen.getByRole('list', { name: 'Lỗi của dòng 3' }).closest('td')!
    const rowHeader = within(dataRow(3)).getByRole('rowheader')
    expect(rowHeader.id).not.toBe('')
    expect(detailCell).toHaveAttribute('headers', rowHeader.id)
  })

  test('lỗi bất ngờ khi map (rule "mapping", không có bước): ghi là lỗi khi map, không phải một bước biến đổi', async () => {
    const user = userEvent.setup()
    const mappingError = pipelineResultFixture({
      rows: [
        {
          rowNumber: 4,
          valid: false,
          values: { Email: null, 'Họ tên': 'Lê Chi', '1': 7, '2024': 'A03' },
          errors: [
            {
              rowNumber: 4,
              fieldName: 'Email',
              stage: 'TRANSFORMATION',
              rule: 'mapping',
              step: null,
              code: 'TRANSFORMATION_FAILED',
              message: 'Unexpected error while mapping the value.',
              sourceValue: 'x',
            },
          ],
        },
      ],
    })
    render(<App />)
    await openResultStep(user, { result: pagesByView({ invalid: mappingError }) })

    const detail = within(screen.getByRole('list', { name: 'Lỗi của dòng 4' })).getByRole('listitem')
    expect(detail).toHaveTextContent('lỗi khi map giá trị')
    expect(detail).not.toHaveTextContent('biến đổi mapping')
  })

  describe('phân trang', () => {
    const threePages = pipelineResultFixture({ page: { number: 0, size: 50, totalElements: 120, totalPages: 3 } })

    test('"Sau" gọi trang kế tiếp và hiện "Trang 2 / 3"; tới trang cuối thì focus sang "Trước"', async () => {
      const user = userEvent.setup()
      render(<App />)
      const requests = await openResultStep(user, { result: pagesByView({ invalid: threePages }) })
      expect(screen.getByText('Trang 1 / 3')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Trước' })).toBeDisabled()

      await user.click(screen.getByRole('button', { name: 'Sau' }))

      expect(await screen.findByText('Trang 2 / 3')).toBeInTheDocument()
      expect(requests.queries.at(-1)).toEqual({ view: 'invalid', page: '1', size: '50' })
      expect(screen.getByRole('button', { name: 'Sau' })).toHaveFocus()

      await user.click(screen.getByRole('button', { name: 'Sau' }))
      expect(await screen.findByText('Trang 3 / 3')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Sau' })).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Trước' })).toHaveFocus()
    })

    // Nút đổi trang không bị khoá trong lúc tải (để giữ focus), nên cú bấm thứ hai tới khi trang còn đang tải phải bị
    // bỏ qua, không thì nhảy hai trang.
    test('bấm "Sau" lần nữa khi trang kế còn đang tải: bỏ qua, chỉ tải một trang', async () => {
      const user = userEvent.setup()
      const hold = gate()
      const pages = pagesByView({ invalid: threePages })
      render(<App />)
      const requests = await openResultStep(user, {
        result: async (query) => {
          if (query.page !== '0') await hold.promise
          return pages(query)
        },
      })

      await user.click(screen.getByRole('button', { name: 'Sau' }))
      await user.click(screen.getByRole('button', { name: 'Sau' }))
      hold.open()

      expect(await screen.findByText('Trang 2 / 3')).toBeInTheDocument()
      expect(requests.queries.map((query) => query.page)).toEqual(['0', '1'])
    })

    test('"Thử lại" tải được trang cuối: focus ở tiêu đề bước, không bị giật sang "Trước"', async () => {
      const user = userEvent.setup()
      const twoPages = pipelineResultFixture({ page: { number: 0, size: 50, totalElements: 60, totalPages: 2 } })
      const pages = pagesByView({ invalid: twoPages })
      let failNext = true
      render(<App />)
      await openResultStep(user, {
        result: (query) => {
          if (query.page === '1' && failNext) {
            failNext = false
            return problemResponse(503, 'INTERNAL_ERROR', 'Service unavailable.')
          }
          return pages(query)
        },
      })
      await user.click(screen.getByRole('button', { name: 'Sau' }))
      const alert = await screen.findByRole('alert')

      await user.click(within(alert).getByRole('button', { name: 'Thử lại' }))

      expect(await screen.findByText('Trang 2 / 2')).toBeInTheDocument()
      expect(screen.getByRole('heading', RESULT_HEADING)).toHaveFocus()
    })

    test('"Trước" về trang đầu thì nút bị khoá, focus sang "Sau"', async () => {
      const user = userEvent.setup()
      const twoPages = pipelineResultFixture({ page: { number: 0, size: 50, totalElements: 60, totalPages: 2 } })
      render(<App />)
      await openResultStep(user, { result: pagesByView({ invalid: twoPages }) })
      await user.click(screen.getByRole('button', { name: 'Sau' }))
      await screen.findByText('Trang 2 / 2')

      await user.click(screen.getByRole('button', { name: 'Trước' }))

      expect(await screen.findByText('Trang 1 / 2')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Trước' })).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Sau' })).toHaveFocus()
    })

    test('đổi tab quay về trang 0', async () => {
      const user = userEvent.setup()
      render(<App />)
      const requests = await openResultStep(user, { result: pagesByView({ invalid: threePages }) })
      await user.click(screen.getByRole('button', { name: 'Sau' }))
      await screen.findByText('Trang 2 / 3')

      await user.click(tab(/^Hợp lệ/))

      await screen.findByRole('table', { name: 'Dòng hợp lệ' })
      expect(requests.queries.at(-1)).toEqual({ view: 'valid', page: '0', size: '50' })
    })
  })

  describe('lọc dòng lỗi', () => {
    // Key dạng số ("1", "2024") bị JS đưa lên đầu object: lựa chọn phải theo thứ tự schema, không theo key.
    const summary = pipelineSummaryFixture({
      errorCountsByCode: { TRANSFORMATION_FAILED: 5, VALIDATION_EMAIL: 15 },
      errorCountsByField: { '2024': 1, 'Họ tên': 2, '1': 5, Email: 15 },
    })

    test('lựa chọn theo thứ tự schema kèm số lỗi; mã lỗi có nhãn tiếng Việt kèm số lỗi', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openResultStep(user, { summary })

      const fieldOptions = within(screen.getByRole('combobox', { name: 'Lọc theo field' })).getAllByRole('option')
      expect(fieldOptions.map((option) => option.textContent)).toEqual([
        'Tất cả field',
        '2024 (1)',
        'Họ tên (2)',
        '1 (5)',
        'Email (15)',
      ])
      const codeOptions = within(screen.getByRole('combobox', { name: 'Lọc theo mã lỗi' })).getAllByRole('option')
      expect(codeOptions.map((option) => option.textContent)).toEqual([
        'Tất cả mã lỗi',
        'Biến đổi dữ liệu thất bại (5)',
        'Email không hợp lệ (15)',
      ])
    })

    test('chọn mã lỗi rồi field: gửi code và field, luôn về trang 0; "Xoá lọc" gọi lại không có bộ lọc', async () => {
      const user = userEvent.setup()
      const threePages = pipelineResultFixture({ page: { number: 0, size: 50, totalElements: 120, totalPages: 3 } })
      render(<App />)
      const requests = await openResultStep(user, { summary, result: pagesByView({ invalid: threePages }) })
      await user.click(screen.getByRole('button', { name: 'Sau' }))
      await screen.findByText('Trang 2 / 3')

      await user.selectOptions(screen.getByRole('combobox', { name: 'Lọc theo mã lỗi' }), 'VALIDATION_EMAIL')
      await screen.findByText('Trang 1 / 3')
      expect(requests.queries.at(-1)).toEqual({ view: 'invalid', page: '0', size: '50', code: 'VALIDATION_EMAIL' })

      await user.selectOptions(screen.getByRole('combobox', { name: 'Lọc theo field' }), 'Email')
      await screen.findByText('Trang 1 / 3')
      expect(requests.queries.at(-1)).toEqual({
        view: 'invalid',
        page: '0',
        size: '50',
        code: 'VALIDATION_EMAIL',
        field: 'Email',
      })

      await user.click(screen.getByRole('button', { name: 'Xoá lọc' }))
      await screen.findByText('Trang 1 / 3')
      expect(requests.queries.at(-1)).toEqual({ view: 'invalid', page: '0', size: '50' })
      expect(screen.getByRole('button', { name: 'Xoá lọc' })).toBeDisabled()
      expect(screen.getByRole('combobox', { name: 'Lọc theo field' })).toHaveFocus()
    })

    // Chrome trên Windows phát `change` ở mỗi lần bấm mũi tên trên select đang đóng (tasks 8.2): lựa chọn mới trong lúc
    // lựa chọn trước còn đang tải phải được giữ và tải sau đó, không bị bỏ.
    test('đổi bộ lọc khi lựa chọn trước còn đang tải: giữ lựa chọn mới nhất và tải nó', async () => {
      const user = userEvent.setup()
      const pages = pagesByView()
      const first = gate()
      const second = gate()
      // Mỗi truy vấn một dòng khác nhau, để thấy trang nào đang được vẽ.
      const withRow = (rowNumber: number) =>
        HttpResponse.json(pipelineResultFixture({ rows: [{ ...pipelineResultFixture().rows[0], rowNumber, errors: [] }] }))
      render(<App />)
      const requests = await openResultStep(user, {
        summary,
        result: async (query) => {
          if (query.code && query.field) {
            await second.promise
            return withRow(9)
          }
          if (query.code) {
            await first.promise
            return withRow(7)
          }
          return pages(query)
        },
      })

      await user.selectOptions(screen.getByRole('combobox', { name: 'Lọc theo mã lỗi' }), 'VALIDATION_EMAIL')
      await user.selectOptions(screen.getByRole('combobox', { name: 'Lọc theo field' }), 'Email')
      expect(screen.getByRole('combobox', { name: 'Lọc theo field' })).toHaveValue('Email')
      first.open()
      // Trang của lựa chọn cũ về tới nơi khi lựa chọn mới đang chờ: không được vẽ ra.
      await vi.waitFor(() => expect(requests.calls()).toBe(3))
      expect(dataRow(3)).toBeInTheDocument()
      second.open()

      await vi.waitFor(() =>
        expect(requests.queries.at(-1)).toEqual({ view: 'invalid', page: '0', size: '50', code: 'VALIDATION_EMAIL', field: 'Email' }),
      )
      await vi.waitFor(() => expect(screen.getByRole('tabpanel')).toHaveAttribute('aria-busy', 'false'))
      expect(requests.calls()).toBe(3)
      expect(dataRow(9)).toBeInTheDocument()
      expect(screen.getByRole('combobox', { name: 'Lọc theo field' })).toHaveValue('Email')
      expect(screen.getByRole('combobox', { name: 'Lọc theo mã lỗi' })).toHaveValue('VALIDATION_EMAIL')
    })

    test('lựa chọn trước tải lỗi trong lúc lựa chọn mới đang chờ: không báo lỗi của lựa chọn cũ, tải lựa chọn mới', async () => {
      const user = userEvent.setup()
      const pages = pagesByView()
      const first = gate()
      render(<App />)
      const requests = await openResultStep(user, {
        summary,
        result: async (query) => {
          if (query.code && !query.field) {
            await first.promise
            return problemResponse(500, 'INTERNAL_ERROR', 'Unexpected error.')
          }
          return pages(query)
        },
      })

      await user.selectOptions(screen.getByRole('combobox', { name: 'Lọc theo mã lỗi' }), 'VALIDATION_EMAIL')
      await user.selectOptions(screen.getByRole('combobox', { name: 'Lọc theo field' }), 'Email')
      first.open()

      await vi.waitFor(() => expect(requests.calls()).toBe(3))
      await vi.waitFor(() => expect(screen.getByRole('tabpanel')).toHaveAttribute('aria-busy', 'false'))
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(screen.getByRole('combobox', { name: 'Lọc theo field' })).toHaveValue('Email')
      expect(screen.getByRole('combobox', { name: 'Lọc theo mã lỗi' })).toHaveValue('VALIDATION_EMAIL')
    })

    test('bộ lọc chỉ có ở tab Lỗi', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openResultStep(user)

      await user.click(tab(/^Hợp lệ/))
      await screen.findByRole('table', { name: 'Dòng hợp lệ' })

      expect(screen.queryByRole('combobox', { name: 'Lọc theo field' })).not.toBeInTheDocument()
    })
  })

  describe('trạng thái rỗng', () => {
    test('không có dòng lỗi: tab mặc định là Hợp lệ; mở tab Lỗi thì hiện "Không có dòng lỗi"', async () => {
      const user = userEvent.setup()
      const noErrors = pipelineSummaryFixture({ valid: 120, invalid: 0, errorCountsByCode: {}, errorCountsByField: {} })
      const emptyPage = pipelineResultFixture({
        summary: noErrors,
        page: { number: 0, size: 50, totalElements: 0, totalPages: 0 },
        rows: [],
      })
      render(<App />)
      await openResultStep(user, { summary: noErrors, result: pagesByView({ invalid: emptyPage }) })
      expect(tab(/^Hợp lệ \(120\)$/)).toHaveAttribute('aria-selected', 'true')

      await user.click(tab(/^Lỗi \(0\)$/))

      expect(await within(screen.getByRole('tabpanel')).findByText('Không có dòng lỗi')).toBeInTheDocument()
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Sau' })).not.toBeInTheDocument()
      expect(screen.queryByRole('combobox', { name: 'Lọc theo field' })).not.toBeInTheDocument()
    })

    test('không có dòng hợp lệ: tab Hợp lệ hiện "Không có dòng hợp lệ"', async () => {
      const user = userEvent.setup()
      const allInvalid = pipelineSummaryFixture({ valid: 0, invalid: 120 })
      const emptyValid = validResultFixture({ page: { number: 0, size: 50, totalElements: 0, totalPages: 0 }, rows: [] })
      render(<App />)
      await openResultStep(user, { summary: allInvalid, result: pagesByView({ valid: emptyValid }) })

      await user.click(tab(/^Hợp lệ \(0\)$/))

      expect(await within(screen.getByRole('tabpanel')).findByText('Không có dòng hợp lệ')).toBeInTheDocument()
    })
  })

  describe('kết quả cũ', () => {
    async function makeStale(user: User) {
      await user.click(stepButton(/Biến đổi & kiểm tra/))
      await user.click(within(fieldRegion('Họ tên')).getByRole('checkbox', { name: /unique/ }))
      await user.click(stepButton(/Kết quả/))
      await screen.findByRole('heading', RESULT_HEADING)
    }

    test('sửa cấu hình rồi quay lại: cảnh báo kèm "Chạy lại"; giữ trang đang xem; khoá tab, trang, bộ lọc', async () => {
      const user = userEvent.setup()
      const threePages = pipelineResultFixture({ page: { number: 0, size: 50, totalElements: 120, totalPages: 3 } })
      render(<App />)
      await openResultStep(user, { result: pagesByView({ invalid: threePages }) })

      await makeStale(user)

      expect(screen.getByText('Cấu hình đã thay đổi — kết quả này là của lần chạy trước')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Chạy lại' })).toBeEnabled()
      expect(dataRow(3)).toBeInTheDocument()
      expect(tab(/^Hợp lệ/)).toBeDisabled()
      // Tab đang chọn vẫn bấm Tab tới được: tablist không được mất điểm nhận focus.
      expect(tab(/^Lỗi/)).toBeEnabled()
      expect(screen.getByRole('button', { name: 'Sau' })).toBeDisabled()
      expect(screen.getByRole('combobox', { name: 'Lọc theo mã lỗi' })).toBeDisabled()
      // Stepper: bước Biến đổi & kiểm tra không còn "đã xong" khi kết quả đã cũ.
      expect(stepButton(/Biến đổi & kiểm tra/)).not.toHaveAccessibleName(/đã xong/)
    })

    test('"Chạy lại" dùng trình tự lưu và chạy: chỉ gửi phần chưa lưu; kết quả mới thay kết quả cũ, focus về tiêu đề', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openResultStep(user)
      await makeStale(user)
      const log = recordRequests()

      await user.click(screen.getByRole('button', { name: 'Chạy lại' }))

      await screen.findByRole('tab', { name: /^Lỗi/ })
      expect(await screen.findByRole('heading', RESULT_HEADING)).toHaveFocus()
      expect(log).toEqual(['PUT validations', 'POST process', 'GET result?view=invalid&page=0&size=50'])
      expect(screen.queryByText(/kết quả này là của lần chạy trước/)).not.toBeInTheDocument()
      expect(tab(/^Hợp lệ/)).toBeEnabled()
    })

    test('"Chạy lại" xong: vùng status không còn nói về trang của kết quả cũ', async () => {
      const user = userEvent.setup()
      const threePages = pipelineResultFixture({ page: { number: 0, size: 50, totalElements: 120, totalPages: 3 } })
      render(<App />)
      await openResultStep(user, { result: pagesByView({ invalid: threePages }) })
      await user.click(screen.getByRole('button', { name: 'Sau' }))
      await screen.findByText('Trang 2 / 3')
      await makeStale(user)

      await user.click(screen.getByRole('button', { name: 'Chạy lại' }))

      expect(await screen.findByText('Dòng lỗi: trang 1 / 3', { selector: '[role="status"]' })).toBeInTheDocument()
    })

    test('"Chạy lại" ngay tại bước, nhưng BE báo kết quả không còn khi tải trang đầu: cảnh báo cũ, không bảng, không câu status cũ', async () => {
      const user = userEvent.setup()
      const threePages = pipelineResultFixture({ page: { number: 0, size: 50, totalElements: 120, totalPages: 3 } })
      let calls = 0
      render(<App />)
      await openResultStep(user, {
        result: () => {
          calls += 1
          return calls === 1
            ? HttpResponse.json(threePages)
            : problemResponse(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.')
        },
      })
      // 409 khi đổi trang: kết quả cũ mà vẫn ở lại bước (vùng status còn câu "trang 1 / 3").
      await user.click(screen.getByRole('button', { name: 'Sau' }))
      await screen.findByRole('button', { name: 'Chạy lại' })

      await user.click(screen.getByRole('button', { name: 'Chạy lại' }))

      await vi.waitFor(() => expect(calls).toBe(3))
      expect(await screen.findByRole('button', { name: 'Chạy lại' })).toHaveFocus()
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      // Câu của vùng status thuộc về lần chạy trước, không còn đúng.
      expect(screen.queryByText(/trang \d+ \/ \d+/, { selector: '[role="status"]' })).not.toBeInTheDocument()
    })

    test('"Chạy lại" lỗi: khối lỗi hiện ngay ở bước Kết quả, lỗi theo field nằm trong danh sách', async () => {
      const user = userEvent.setup()
      render(<App />)
      await openResultStep(user)
      await makeStale(user)
      mockSaveValidations(() =>
        problemWithErrors(422, 'CONFIG_INVALID', [
          { field: 'Họ tên', code: 'CONFIG_INVALID', message: "Duplicate rule 'unique' for field 'Họ tên'." },
        ]),
      )

      await user.click(screen.getByRole('button', { name: 'Chạy lại' }))

      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent('Cấu hình transformation hoặc validation không hợp lệ')
      expect(within(alert).getByRole('listitem')).toHaveTextContent("Họ tên: Duplicate rule 'unique' for field 'Họ tên'.")
      expect(screen.getByRole('heading', RESULT_HEADING)).toHaveFocus()
    })

    test('BE trả 409 RESULT_NOT_AVAILABLE khi đổi trang: đánh dấu cũ, giữ trang đang xem, focus "Chạy lại"', async () => {
      const user = userEvent.setup()
      const threePages = pipelineResultFixture({ page: { number: 0, size: 50, totalElements: 120, totalPages: 3 } })
      render(<App />)
      await openResultStep(user, {
        result: (query) =>
          query.page === '0'
            ? HttpResponse.json(threePages)
            : problemResponse(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.'),
      })

      await user.click(screen.getByRole('button', { name: 'Sau' }))

      expect(await screen.findByRole('button', { name: 'Chạy lại' })).toHaveFocus()
      // User không sửa gì: không nói "cấu hình đã thay đổi".
      expect(screen.getByText('Máy chủ không còn giữ kết quả này — chạy lại để có kết quả mới')).toBeInTheDocument()
      expect(screen.getByText('Trang 1 / 3')).toBeInTheDocument()
      expect(dataRow(3)).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })

  test('tải trang lỗi 500: khối lỗi có "Thử lại" gửi lại đúng truy vấn đó', async () => {
    const user = userEvent.setup()
    const threePages = pipelineResultFixture({ page: { number: 0, size: 50, totalElements: 120, totalPages: 3 } })
    let failNext = true
    render(<App />)
    const requests = await openResultStep(user, {
      result: (query) => {
        if (query.page === '1' && failNext) {
          failNext = false
          return problemResponse(500, 'INTERNAL_ERROR', 'Unexpected error.')
        }
        return HttpResponse.json({ ...threePages, page: { ...threePages.page, number: Number(query.page) } })
      },
    })

    await user.click(screen.getByRole('button', { name: 'Sau' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Máy chủ gặp lỗi không lường trước')
    expect(screen.getByText('Trang 1 / 3')).toBeInTheDocument()

    await user.click(within(alert).getByRole('button', { name: 'Thử lại' }))

    expect(await screen.findByText('Trang 2 / 3')).toBeInTheDocument()
    // Nút "Thử lại" biến mất cùng khối lỗi: focus về tiêu đề bước, không rơi về đầu trang (design D14).
    expect(screen.getByRole('heading', RESULT_HEADING)).toHaveFocus()
    expect(requests.queries.slice(-2)).toEqual([
      { view: 'invalid', page: '1', size: '50' },
      { view: 'invalid', page: '1', size: '50' },
    ])
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('"Quay lại" về bước Biến đổi & kiểm tra', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openResultStep(user)

    await user.click(screen.getByRole('button', { name: 'Quay lại' }))

    expect(await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })).toBeInTheDocument()
  })
})
