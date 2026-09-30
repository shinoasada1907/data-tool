import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test, vi } from 'vitest'
import App from '../../App'
import { pipelineResultFixture, pipelineSummaryFixture } from '../../mocks/fixtures'
import { server } from '../../mocks/node'
import { captureDownloads } from '../../test/downloads'
import {
  fieldRegion,
  openResultStep,
  pagesByView,
  RESULT_HEADING,
  runButton,
  stepButton,
  type User,
} from '../../test/flows'
import { gate, mockProcess, problemResponse } from '../../test/http'

const EXPORT_URL = '/api/import-sessions/:id/export'
const ERRORS_URL = '/api/import-sessions/:id/errors/export'

function exportGroup() {
  return screen.getByRole('group', { name: 'Tải kết quả' })
}

function exportButton(name: string) {
  return within(exportGroup()).getByRole('button', { name })
}

function csvFile(content: string, fileName?: string) {
  const headers: Record<string, string> = { 'Content-Type': 'text/csv;charset=UTF-8' }
  if (fileName) headers['Content-Disposition'] = `attachment; filename*=UTF-8''${encodeURIComponent(fileName)}`
  return new HttpResponse(`﻿${content}`, { headers })
}

/** GET export ghi lại `format` của từng lần gọi rồi trả lần lượt từng response. */
function mockExport(...responders: (() => Response | Promise<Response>)[]) {
  const formats: string[] = []
  server.use(
    http.get(EXPORT_URL, ({ request }) => {
      formats.push(new URL(request.url).searchParams.get('format') ?? '')
      return responders[Math.min(formats.length - 1, responders.length - 1)]()
    }),
  )
  return { formats }
}

async function openWith(user: User, summary = pipelineSummaryFixture()) {
  render(<App />)
  await openResultStep(user, { summary })
}

describe('tải kết quả', () => {
  test('"Tải CSV": gọi export?format=csv, lưu đúng nội dung với tên từ Content-Disposition, báo đã tải', async () => {
    const saved = captureDownloads()
    const requests = mockExport(() => csvFile('Họ tên,Email\r\nAn,an@x.com\r\n', 'khách-valid.csv'))
    const user = userEvent.setup()
    await openWith(user)

    await user.click(exportButton('Tải CSV'))

    expect(await screen.findByText('Đã tải khách-valid.csv', { selector: '[role="status"]' })).toBeInTheDocument()
    expect(requests.formats).toEqual(['csv'])
    expect(saved.map((file) => file.fileName)).toEqual(['khách-valid.csv'])
    // Lưu nguyên byte BE gửi, gồm cả BOM để Excel đọc đúng tiếng Việt (`text()` tự bỏ BOM khi giải mã).
    const bytes = new Uint8Array(await saved[0].blob.arrayBuffer())
    expect([...bytes.slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf])
    expect(await saved[0].blob.text()).toBe('Họ tên,Email\r\nAn,an@x.com\r\n')
  })

  test('"Tải JSON" không có Content-Disposition: tên dự phòng <tên file gốc bỏ đuôi>-valid.json', async () => {
    const saved = captureDownloads()
    const requests = mockExport(() => HttpResponse.json([{ Email: 'an@x.com' }]))
    const user = userEvent.setup()
    await openWith(user)

    await user.click(exportButton('Tải JSON'))

    await screen.findByText('Đã tải khach-hang-valid.json', { selector: '[role="status"]' })
    expect(requests.formats).toEqual(['json'])
    expect(saved.map((file) => file.fileName)).toEqual(['khach-hang-valid.json'])
  })

  test('"Tải báo cáo lỗi": gọi errors/export; tên dự phòng <tên file gốc bỏ đuôi>-errors.csv', async () => {
    const saved = captureDownloads()
    let calls = 0
    server.use(
      http.get(ERRORS_URL, () => {
        calls += 1
        return csvFile('rowNumber,fieldName,stage,rule,step,code,message,sourceValue\r\n')
      }),
    )
    const user = userEvent.setup()
    await openWith(user)

    await user.click(exportButton('Tải báo cáo lỗi'))

    await screen.findByText('Đã tải khach-hang-errors.csv', { selector: '[role="status"]' })
    expect(calls).toBe(1)
    expect(saved.map((file) => file.fileName)).toEqual(['khach-hang-errors.csv'])
  })

  test('500 EXPORT_FAILED: hiện thông điệp của mã lỗi, không lưu file nào', async () => {
    const saved = captureDownloads()
    mockExport(() => problemResponse(500, 'EXPORT_FAILED', 'Export could not be created.'))
    const user = userEvent.setup()
    await openWith(user)

    await user.click(exportButton('Tải CSV'))

    const alert = await within(exportGroup()).findByRole('alert')
    expect(alert).toHaveTextContent('Không tạo được file export')
    expect(alert).toHaveTextContent('Export could not be created.')
    expect(saved).toEqual([])
  })

  test('mất kết nối: báo "Không kết nối được máy chủ", nút bấm lại được; bấm lại thì tải được và hết báo lỗi', async () => {
    const saved = captureDownloads()
    mockExport(() => HttpResponse.error(), () => csvFile('a\r\n', 'khach-hang-valid.csv'))
    const user = userEvent.setup()
    await openWith(user)

    await user.click(exportButton('Tải CSV'))
    expect(await within(exportGroup()).findByRole('alert')).toHaveTextContent('Không kết nối được máy chủ')
    expect(exportButton('Tải CSV')).toBeEnabled()
    expect(exportButton('Tải CSV')).not.toHaveAttribute('aria-disabled', 'true')

    await user.click(exportButton('Tải CSV'))

    await screen.findByText('Đã tải khach-hang-valid.csv', { selector: '[role="status"]' })
    expect(saved.map((file) => file.fileName)).toEqual(['khach-hang-valid.csv'])
    expect(within(exportGroup()).queryByRole('alert')).not.toBeInTheDocument()
  })

  test('409 RESULT_NOT_AVAILABLE: không lưu file; kết quả thành cũ, ba nút khoá kèm lý do, focus "Chạy lại"', async () => {
    const saved = captureDownloads()
    mockExport(() => problemResponse(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.'))
    const user = userEvent.setup()
    await openWith(user)

    await user.click(exportButton('Tải JSON'))

    expect(await screen.findByRole('button', { name: 'Chạy lại' })).toHaveFocus()
    expect(saved).toEqual([])
    for (const name of ['Tải JSON', 'Tải CSV', 'Tải báo cáo lỗi']) {
      expect(exportButton(name)).toBeDisabled()
      expect(exportButton(name)).toHaveAccessibleDescription('Chạy lại để tải kết quả khớp cấu hình hiện tại')
    }
  })

  test('404 SESSION_NOT_FOUND: kết quả thành cũ vì session hỏng, ba nút khoá, focus "Upload lại" của cảnh báo', async () => {
    mockExport(() => problemResponse(404, 'SESSION_NOT_FOUND', 'Import session not found.'))
    const user = userEvent.setup()
    await openWith(user)

    await user.click(exportButton('Tải CSV'))

    const reupload = await screen.findByRole('button', { name: 'Upload lại' })
    expect(reupload).toHaveFocus()
    expect(screen.getByText(/Phiên import không dùng được nữa — kết quả này là của lần chạy trước/)).toBeInTheDocument()
    expect(exportButton('Tải CSV')).toHaveAccessibleDescription('Phiên import không dùng được nữa; hãy upload lại file')
    expect(within(exportGroup()).queryByRole('alert')).not.toBeInTheDocument()
    await user.click(reupload)
    expect(await screen.findByRole('heading', { level: 2, name: 'Upload file nguồn' })).toBeInTheDocument()
  })

  describe('gắn với lần chạy', () => {
    /** Làm kết quả cũ bằng một 409 của lượt tải JSON, rồi "Chạy lại" ngay tại bước Kết quả. */
    async function rerunAfterUnavailable(user: User) {
      await user.click(exportButton('Tải JSON'))
      await user.click(await screen.findByRole('button', { name: 'Chạy lại' }))
      await vi.waitFor(() => expect(screen.queryByRole('button', { name: 'Chạy lại' })).not.toBeInTheDocument())
      await screen.findByRole('table', { name: 'Dòng lỗi' })
    }

    test('lỗi tải của lần chạy trước không còn hiện trên kết quả mới', async () => {
      server.use(
        http.get(EXPORT_URL, ({ request }) =>
          new URL(request.url).searchParams.get('format') === 'csv'
            ? problemResponse(500, 'EXPORT_FAILED', 'Export could not be created.')
            : problemResponse(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.'),
        ),
      )
      const user = userEvent.setup()
      await openWith(user)
      await user.click(exportButton('Tải CSV'))
      await within(exportGroup()).findByRole('alert')

      await rerunAfterUnavailable(user)

      expect(within(exportGroup()).queryByRole('alert')).not.toBeInTheDocument()
    })

    test('lượt tải còn dở của lần chạy trước bị huỷ: không lưu file, không báo đã tải trên kết quả mới', async () => {
      const saved = captureDownloads()
      const hold = gate()
      server.use(
        http.get(EXPORT_URL, async ({ request }) => {
          if (new URL(request.url).searchParams.get('format') === 'json') {
            return problemResponse(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.')
          }
          await hold.promise
          return csvFile('a\r\n', 'old-valid.csv')
        }),
      )
      const user = userEvent.setup()
      await openWith(user)
      await user.click(exportButton('Tải CSV'))

      await rerunAfterUnavailable(user)
      hold.open()
      await new Promise((resolve) => setTimeout(resolve, 50))

      expect(saved).toEqual([])
      expect(screen.queryByText(/Đã tải/, { selector: '[role="status"]' })).not.toBeInTheDocument()
      expect(exportButton('Tải CSV')).not.toHaveAttribute('aria-disabled')
    })
  })

  describe('hai lượt tải cùng lúc', () => {
    test('lượt này lỗi không xoá câu "đang tải" của lượt kia; bắt đầu lại một lượt không xoá lỗi của lượt khác', async () => {
      captureDownloads()
      const json = gate()
      const csv = gate()
      server.use(
        http.get(EXPORT_URL, async ({ request }) => {
          if (new URL(request.url).searchParams.get('format') === 'json') {
            await json.promise
            return problemResponse(500, 'EXPORT_FAILED', 'Export could not be created.')
          }
          await csv.promise
          return csvFile('a\r\n', 'x-valid.csv')
        }),
        http.get(ERRORS_URL, () => csvFile('rowNumber\r\n', 'x-errors.csv')),
      )
      const user = userEvent.setup()
      await openWith(user)

      await user.click(exportButton('Tải JSON'))
      await user.click(exportButton('Tải CSV'))
      json.open()

      expect(await within(exportGroup()).findByRole('alert')).toHaveTextContent('Không tạo được file export')
      expect(screen.getByText('Đang tải file CSV…', { selector: '[role="status"]' })).toBeInTheDocument()
      csv.open()
      await screen.findByText('Đã tải x-valid.csv', { selector: '[role="status"]' })
      await user.click(exportButton('Tải báo cáo lỗi'))
      await screen.findByText('Đã tải x-errors.csv', { selector: '[role="status"]' })
      expect(within(exportGroup()).getByRole('alert')).toHaveTextContent('Không tạo được file export')
    })
  })

  test('409 khi một trang khác đang tải ("Chạy lại" còn khoá): focus về tiêu đề bước; trang về muộn không được vẽ', async () => {
    const exportHold = gate()
    const pageHold = gate()
    server.use(
      http.get(EXPORT_URL, async () => {
        await exportHold.promise
        return problemResponse(409, 'RESULT_NOT_AVAILABLE', 'Result is not available.')
      }),
    )
    const threePages = pipelineResultFixture({ page: { number: 0, size: 50, totalElements: 120, totalPages: 3 } })
    const pages = pagesByView({ invalid: threePages })
    const user = userEvent.setup()
    render(<App />)
    await openResultStep(user, {
      result: async (query) => {
        if (query.page === '1') await pageHold.promise
        return pages(query)
      },
    })

    await user.click(exportButton('Tải CSV'))
    await user.click(screen.getByRole('button', { name: 'Sau' }))
    exportHold.open()

    await screen.findByText('Máy chủ không còn giữ kết quả này — chạy lại để có kết quả mới')
    expect(screen.getByRole('button', { name: 'Chạy lại' })).toBeDisabled()
    expect(screen.getByRole('heading', RESULT_HEADING)).toHaveFocus()
    pageHold.open()
    await vi.waitFor(() => expect(screen.getByRole('button', { name: 'Chạy lại' })).toBeEnabled())
    expect(screen.getByText('Trang 1 / 3')).toBeInTheDocument()
  })

  describe('khoá nút', () => {
    test('valid = 0: khoá "Tải JSON" và "Tải CSV" kèm lý do; "Tải báo cáo lỗi" vẫn bấm được', async () => {
      const user = userEvent.setup()
      await openWith(user, pipelineSummaryFixture({ valid: 0, invalid: 120 }))

      expect(exportButton('Tải JSON')).toBeDisabled()
      expect(exportButton('Tải CSV')).toBeDisabled()
      expect(exportButton('Tải CSV')).toHaveAccessibleDescription('Không có dòng hợp lệ để tải')
      expect(exportButton('Tải báo cáo lỗi')).toBeEnabled()
    })

    test('invalid = 0: khoá "Tải báo cáo lỗi" kèm lý do', async () => {
      const user = userEvent.setup()
      await openWith(
        user,
        pipelineSummaryFixture({ valid: 120, invalid: 0, errorCountsByCode: {}, errorCountsByField: {} }),
      )

      expect(exportButton('Tải báo cáo lỗi')).toBeDisabled()
      expect(exportButton('Tải báo cáo lỗi')).toHaveAccessibleDescription('Không có dòng lỗi để tải')
      expect(exportButton('Tải CSV')).toBeEnabled()
    })

    test('kết quả cũ thì lý do khoá là "chạy lại", kể cả khi không có dòng hợp lệ', async () => {
      const user = userEvent.setup()
      await openWith(user, pipelineSummaryFixture({ valid: 0, invalid: 120 }))

      await user.click(stepButton(/Biến đổi & kiểm tra/))
      await user.click(within(fieldRegion('Họ tên')).getByRole('checkbox', { name: /unique/ }))
      await user.click(stepButton(/Kết quả/))
      await screen.findByRole('heading', RESULT_HEADING)

      expect(exportButton('Tải CSV')).toHaveAccessibleDescription('Chạy lại để tải kết quả khớp cấu hình hiện tại')
    })

    test('kết quả cũ (đã sửa cấu hình): khoá cả ba nút kèm lý do', async () => {
      const user = userEvent.setup()
      await openWith(user)

      await user.click(stepButton(/Biến đổi & kiểm tra/))
      await user.click(within(fieldRegion('Họ tên')).getByRole('checkbox', { name: /unique/ }))
      await user.click(stepButton(/Kết quả/))
      await screen.findByRole('heading', RESULT_HEADING)

      for (const name of ['Tải JSON', 'Tải CSV', 'Tải báo cáo lỗi']) {
        expect(exportButton(name)).toBeDisabled()
        expect(exportButton(name)).toHaveAccessibleDescription('Chạy lại để tải kết quả khớp cấu hình hiện tại')
      }
    })

    test('session hỏng sau khi đã có kết quả: khoá cả ba nút, lý do là phải upload lại (không phải chạy lại)', async () => {
      const user = userEvent.setup()
      await openWith(user)
      mockProcess(() => problemResponse(422, 'FILE_PARSE_ERROR', 'Malformed CSV at line 12.'))

      await user.click(stepButton(/Biến đổi & kiểm tra/))
      await user.click(runButton())
      await screen.findByRole('alert')
      await user.click(stepButton(/Kết quả/))
      await screen.findByRole('heading', RESULT_HEADING)

      for (const name of ['Tải JSON', 'Tải CSV', 'Tải báo cáo lỗi']) {
        expect(exportButton(name)).toBeDisabled()
        expect(exportButton(name)).toHaveAccessibleDescription('Phiên import không dùng được nữa; hãy upload lại file')
      }
    })

    test('đang tải: chính nút đó khoá (vẫn giữ focus) và có trạng thái đang tải; bấm lần nữa không gửi thêm; nút khác vẫn bấm được', async () => {
      captureDownloads()
      const hold = gate()
      const requests = mockExport(async () => {
        await hold.promise
        return csvFile('a\r\n', 'x.csv')
      })
      const user = userEvent.setup()
      await openWith(user)

      await user.click(exportButton('Tải CSV'))
      await user.click(exportButton('Tải CSV'))

      expect(screen.getByText('Đang tải file CSV…', { selector: '[role="status"]' })).toBeInTheDocument()
      expect(exportButton('Tải CSV')).toHaveAttribute('aria-disabled', 'true')
      expect(exportButton('Tải CSV')).toHaveFocus()
      expect(exportButton('Tải JSON')).toBeEnabled()
      hold.open()
      await screen.findByText('Đã tải x.csv', { selector: '[role="status"]' })
      expect(requests.formats).toEqual(['csv'])
      expect(exportButton('Tải CSV')).not.toHaveAttribute('aria-disabled')
    })
  })

  test('rời bước Kết quả khi đang tải: huỷ request, không lưu file', async () => {
    const saved = captureDownloads()
    const hold = gate()
    mockExport(async () => {
      await hold.promise
      return csvFile('a\r\n', 'x.csv')
    })
    const user = userEvent.setup()
    await openWith(user)

    await user.click(exportButton('Tải CSV'))
    await user.click(screen.getByRole('button', { name: 'Quay lại' }))
    await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })
    hold.open()
    await new Promise((resolve) => setTimeout(resolve, 50))

    expect(saved).toEqual([])
  })
})
