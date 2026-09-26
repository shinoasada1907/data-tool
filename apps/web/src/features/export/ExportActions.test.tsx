import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import App from '../../App'
import { pipelineSummaryFixture } from '../../mocks/fixtures'
import { server } from '../../mocks/node'
import { captureDownloads } from '../../test/downloads'
import { fieldRegion, openResultStep, RESULT_HEADING, stepButton, type User } from '../../test/flows'
import { gate, problemResponse } from '../../test/http'

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

  test('404 SESSION_NOT_FOUND: nút "Upload lại"', async () => {
    mockExport(() => problemResponse(404, 'SESSION_NOT_FOUND', 'Import session not found.'))
    const user = userEvent.setup()
    await openWith(user)

    await user.click(exportButton('Tải CSV'))

    const alert = await within(exportGroup()).findByRole('alert')
    await user.click(within(alert).getByRole('button', { name: 'Upload lại' }))
    expect(await screen.findByRole('heading', { level: 2, name: 'Upload file nguồn' })).toBeInTheDocument()
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
