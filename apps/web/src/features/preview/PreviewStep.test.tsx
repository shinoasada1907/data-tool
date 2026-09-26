import { render, screen, within } from '@testing-library/react'
import { StrictMode } from 'react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import App from '../../App'
import type { SourcePreviewDto } from '../../api/dto'
import { csvPreviewFixture, importSessionFixture, xlsxPreviewFixture } from '../../mocks/fixtures'
import { server } from '../../mocks/node'
import { testFile } from '../../test/files'
import { gate, mockPreview, mockUpload, PREVIEW_URL, problemResponse } from '../../test/http'

type User = ReturnType<typeof userEvent.setup>

function previewOk(dto: SourcePreviewDto = csvPreviewFixture()) {
  return () => HttpResponse.json(dto)
}

/** Upload một file (BE trả 201) và chờ tới bước Xem trước. */
async function uploadAndOpenPreview(
  user: User,
  {
    name = 'khach-hang.csv',
    fileType = 'CSV',
    strict = false,
  }: { name?: string; fileType?: 'CSV' | 'XLSX'; strict?: boolean } = {},
) {
  mockUpload(() =>
    HttpResponse.json(importSessionFixture({ originalFileName: name, fileType }), { status: 201 }),
  )
  render(strict ? <StrictMode><App /></StrictMode> : <App />)
  await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile(name))
  await screen.findByRole('heading', { level: 2, name: 'Xem trước dữ liệu' })
}

async function findTable() {
  return screen.findByRole('table', { name: 'Dữ liệu xem trước' })
}

function columnHeaders(table: HTMLElement) {
  return within(table)
    .getAllByRole('columnheader')
    .map((header) => header.textContent)
}

/** Các dòng dữ liệu (bỏ dòng header), mỗi dòng là mảng ô: ô số dòng (row header) rồi tới các ô giá trị. */
function bodyRows(table: HTMLElement) {
  return within(table)
    .getAllByRole('row')
    .slice(1)
    .map((row) => [within(row).getByRole('rowheader'), ...within(row).getAllByRole('cell')])
}

function stepButton(name: RegExp) {
  return screen.getByRole('button', { name })
}

function nextButton() {
  return screen.getByRole('button', { name: /^Tiếp/ })
}

/** Vùng live của bước Xem trước (luôn nằm trong DOM, chỉ đổi nội dung). */
function previewStatus() {
  return within(screen.getByRole('region', { name: 'Xem trước dữ liệu' })).getByRole('status')
}

describe('bước Xem trước', () => {
  test('vừa upload xong thì gọi GET preview đúng một lần và hiện bảng theo đúng thứ tự columns[]', async () => {
    const preview = mockPreview(previewOk())
    const user = userEvent.setup()
    await uploadAndOpenPreview(user)

    const table = await findTable()

    // Cột tên dạng số giữ nguyên vị trí; cột đầu là "Dòng".
    expect(columnHeaders(table)).toEqual(['Dòng', '2024', 'Họ tên', '1', 'Email'])
    expect(bodyRows(table).map((cells) => cells[0].textContent)).toEqual(['2', '3', '5'])
    expect(bodyRows(table)[0].map((cell) => cell.textContent)).toEqual([
      '2',
      'A01',
      'Nguyễn An',
      '10',
      'an@example.com',
    ])
    expect(preview.calls()).toBe(1)
  })

  test('số dòng là row header; khung cuộn ngang lấy tên từ caption của bảng', async () => {
    mockPreview(previewOk())
    const user = userEvent.setup()
    await uploadAndOpenPreview(user)
    const table = await findTable()

    expect(within(table).getAllByRole('rowheader').map((header) => header.textContent)).toEqual(['2', '3', '5'])
    expect(screen.getByRole('region', { name: 'Dữ liệu xem trước' })).toContainElement(table)
  })

  test('ô null hiện placeholder chứ không hiện chữ "null"; ô chỉ có khoảng trắng giữ nguyên', async () => {
    mockPreview(previewOk())
    const user = userEvent.setup()
    await uploadAndOpenPreview(user)

    const [, second] = bodyRows(await findTable())

    expect(second[2].textContent).toBe('  ')
    expect(second[3]).not.toHaveTextContent('null')
    expect(within(second[3]).getByText('Ô trống')).toBeInTheDocument()
  })

  test('dòng tổng quan có tên file, loại file và "Xem trước x / y dòng"; CSV không có dòng sheet', async () => {
    mockPreview(previewOk())
    const user = userEvent.setup()
    await uploadAndOpenPreview(user)
    await findTable()

    const summary = screen.getByRole('list', { name: 'Thông tin file' })
    expect(summary).toHaveTextContent('khach-hang.csv')
    expect(summary).toHaveTextContent('CSV')
    expect(summary).toHaveTextContent('Xem trước 3 / 1.200 dòng')
    expect(screen.queryByText(/^Sheet:/)).not.toBeInTheDocument()
  })

  test('XLSX dùng cùng bảng, có thêm tên sheet', async () => {
    mockPreview(previewOk(xlsxPreviewFixture()))
    const user = userEvent.setup()
    await uploadAndOpenPreview(user, { name: 'khach-hang.xlsx', fileType: 'XLSX' })

    const table = await findTable()

    expect(screen.getByText('Sheet: Khách hàng')).toBeInTheDocument()
    expect(columnHeaders(table)).toEqual(['Dòng', 'Mã KH', 'Ngày sinh', 'Số dư'])
    expect(bodyRows(table)[1].map((cell) => cell.textContent)).toEqual(['3', 'KH002', '2024-12-25T13:45:30', '-5.25'])
  })

  test('quay lại bước Upload rồi trở lại Xem trước thì không gọi lại API', async () => {
    const preview = mockPreview(previewOk())
    const user = userEvent.setup()
    await uploadAndOpenPreview(user)
    await findTable()

    await user.click(stepButton(/Upload file/))
    await user.click(stepButton(/Xem trước dữ liệu/))

    expect(await findTable()).toBeInTheDocument()
    expect(preview.calls()).toBe(1)
  })

  // main.tsx bọc App trong StrictMode: effect chạy, bị dọn (huỷ request), rồi chạy lại.
  test('StrictMode: request bị huỷ ở lần chạy effect đầu không hiện thành lỗi', async () => {
    mockPreview(previewOk())
    const user = userEvent.setup()
    await uploadAndOpenPreview(user, { strict: true })

    expect(await findTable()).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('đang tải: có chỉ báo đang tải và nút "Tiếp" bị khoá kèm lý do', async () => {
    const hold = gate()
    mockPreview(async () => {
      await hold.promise
      return previewOk()()
    })
    const user = userEvent.setup()
    await uploadAndOpenPreview(user)

    const status = previewStatus()
    expect(status).toHaveTextContent('Đang tải dữ liệu xem trước…')
    expect(nextButton()).toBeDisabled()
    expect(nextButton()).toHaveAccessibleDescription('Cần tải xong dữ liệu xem trước')
    // GET preview không khoá điều hướng (design D2): BE chậm thì user vẫn quay lại được.
    expect(stepButton(/Upload file/)).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Quay lại' })).toBeEnabled()

    hold.open()
    await findTable()
    expect(nextButton()).toBeEnabled()
    expect(screen.queryByText('Đang tải dữ liệu xem trước…')).not.toBeInTheDocument()
    // Cùng một vùng live, chỉ đổi nội dung, để screen reader báo "đã tải xong".
    expect(previewStatus()).toBe(status)
    expect(status).toHaveTextContent('Xem trước 3 / 1.200 dòng')
  })

  test('rời bước Xem trước khi đang tải thì request bị huỷ', async () => {
    const hold = gate()
    let aborted = false
    server.use(
      http.get(PREVIEW_URL, async ({ request }) => {
        request.signal.addEventListener('abort', () => {
          aborted = true
        })
        await hold.promise
        return previewOk()()
      }),
    )
    const user = userEvent.setup()
    await uploadAndOpenPreview(user)

    await user.click(stepButton(/Upload file/))

    expect(screen.getByRole('heading', { level: 2, name: 'Upload file nguồn' })).toBeInTheDocument()
    expect(aborted).toBe(true)
    hold.open()
  })

  test('file chỉ có header: vẫn hiện header, báo không có dòng dữ liệu, và sang được bước Schema', async () => {
    mockPreview(previewOk(csvPreviewFixture({ rows: [], totalRows: 0 })))
    const user = userEvent.setup()
    await uploadAndOpenPreview(user)

    const table = await findTable()

    expect(columnHeaders(table)).toEqual(['Dòng', '2024', 'Họ tên', '1', 'Email'])
    expect(screen.getByText('File không có dòng dữ liệu')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Thông tin file' })).toHaveTextContent('Xem trước 0 / 0 dòng')

    await user.click(nextButton())
    expect(screen.getByRole('heading', { level: 2, name: 'Schema đích' })).toBeInTheDocument()
  })

  test('"Tiếp" sang bước Schema, bước Xem trước được đánh dấu đã xong; "Quay lại" về bước Upload', async () => {
    mockPreview(previewOk())
    const user = userEvent.setup()
    await uploadAndOpenPreview(user)
    await findTable()

    await user.click(nextButton())

    // Nút vừa bấm biến mất cùng bước cũ, nên focus chuyển sang tiêu đề bước mới (design D14).
    expect(screen.getByRole('heading', { level: 2, name: 'Schema đích' })).toHaveFocus()
    expect(stepButton(/Xem trước dữ liệu/)).toHaveAccessibleName('2 Xem trước dữ liệu (đã xong)')

    await user.click(stepButton(/Xem trước dữ liệu/))
    // Bấm trên stepper thì nút stepper vẫn còn, focus giữ nguyên ở đó.
    expect(stepButton(/Xem trước dữ liệu/)).toHaveFocus()

    await user.click(screen.getByRole('button', { name: 'Quay lại' }))
    expect(screen.getByRole('heading', { level: 2, name: 'Upload file nguồn' })).toHaveFocus()
  })

  describe('lỗi', () => {
    test.each([
      ['lỗi mạng', () => HttpResponse.error(), 'Không kết nối được máy chủ'],
      ['500 INTERNAL_ERROR', () => problemResponse(500, 'INTERNAL_ERROR', 'Unexpected server error.'), 'Máy chủ gặp lỗi không lường trước'],
      [
        '502 HTML từ proxy',
        () => new HttpResponse('<html>Bad Gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }),
        'Máy chủ đang lỗi (502)',
      ],
    ])('%s: hiện lỗi, khoá "Tiếp"; bấm "Thử lại" thì gọi lại và hiện bảng', async (_, fail, headline) => {
      const preview = mockPreview(fail, previewOk())
      const user = userEvent.setup()
      await uploadAndOpenPreview(user)

      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent(headline)
      expect(nextButton()).toBeDisabled()

      await user.click(within(alert).getByRole('button', { name: 'Thử lại' }))

      // Nút "Thử lại" biến mất cùng khối lỗi: focus về tiêu đề bước, không rơi về đầu trang.
      expect(screen.getByRole('heading', { level: 2, name: 'Xem trước dữ liệu' })).toHaveFocus()
      expect(await findTable()).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(preview.calls()).toBe(2)
    })

    test.each([
      ['404 SESSION_NOT_FOUND', 404, 'SESSION_NOT_FOUND', 'Không tìm thấy phiên import (có thể đã hết hạn)'],
      ['409 SESSION_STATE_INVALID', 409, 'SESSION_STATE_INVALID', 'Phiên import không dùng được nữa'],
    ])('%s: báo lỗi kèm "Upload lại", không tự xoá state; bấm nút thì về bước Upload trống', async (_, status, code, headline) => {
      mockPreview(() => problemResponse(status, code, 'Import session not found.'))
      const user = userEvent.setup()
      await uploadAndOpenPreview(user)

      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent(headline)
      expect(within(alert).queryByRole('button', { name: 'Thử lại' })).not.toBeInTheDocument()
      // Chưa tự reset: session vẫn còn.
      expect(stepButton(/Upload file/)).toHaveAccessibleName('1 Upload file (đã xong)')

      await user.click(within(alert).getByRole('button', { name: 'Upload lại' }))

      expect(screen.getByRole('heading', { level: 2, name: 'Upload file nguồn' })).toHaveFocus()
      expect(screen.queryByText('File đang dùng')).not.toBeInTheDocument()
      expect(stepButton(/Xem trước dữ liệu/)).toHaveAttribute('aria-disabled', 'true')
    })

    test('404 REQUEST_INVALID (sai endpoint) là lỗi thường: không có "Upload lại" hay "Thử lại"', async () => {
      mockPreview(() => problemResponse(404, 'REQUEST_INVALID', 'No endpoint GET /api/import-sessions/x/preview.'))
      const user = userEvent.setup()
      await uploadAndOpenPreview(user)

      const alert = await screen.findByRole('alert')

      expect(alert).toHaveTextContent('Yêu cầu không hợp lệ')
      expect(alert).toHaveTextContent('REQUEST_INVALID')
      expect(within(alert).queryByRole('button')).not.toBeInTheDocument()
      expect(stepButton(/Upload file/)).toHaveAccessibleName('1 Upload file (đã xong)')
    })
  })
})
