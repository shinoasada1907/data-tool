import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import App from '../App'
import { captureDownloads } from '../test/downloads'
import { testFile } from '../test/files'
import { nextButton, RESULT_HEADING, runButton } from '../test/flows'
import { createDevHandlers } from './devHandlers'
import { server } from './node'

// BE giả của `pnpm dev:mock` phải đi được hết luồng; nếu không, chế độ này hỏng mà không ai biết cho tới lúc demo.
describe('BE giả của dev:mock', () => {
  test('đi hết luồng: upload CSV → … → chạy xử lý → xem kết quả → tải JSON với key theo schema', async () => {
    server.use(...createDevHandlers())
    const saved = captureDownloads()
    const user = userEvent.setup()
    render(<App />)

    await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile('khach-hang.csv'))
    await screen.findByRole('table', { name: 'Dữ liệu xem trước' })
    await user.click(nextButton())
    await screen.findByRole('heading', { level: 2, name: 'Schema đích' })
    await user.click(nextButton())
    await screen.findByRole('heading', { level: 2, name: 'Mapping' })
    await user.click(nextButton())
    await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })
    await user.click(runButton())
    await screen.findByRole('heading', RESULT_HEADING)
    expect(await screen.findByRole('table', { name: 'Dòng lỗi' })).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: /^Hợp lệ/ }))
    expect(await screen.findByRole('table', { name: 'Dòng hợp lệ' })).toBeInTheDocument()
    const exportGroup = screen.getByRole('group', { name: 'Tải kết quả' })
    await user.click(within(exportGroup).getByRole('button', { name: 'Tải JSON' }))

    // Tên file gốc thì không kiểm: XHR của jsdom gửi part multipart dưới tên "blob" (trình duyệt thật gửi đúng tên).
    await screen.findByText(/^Đã tải .+-valid\.json$/, { selector: '[role="status"]' })
    expect(saved.map((file) => file.fileName)).toEqual([expect.stringMatching(/-valid\.json$/)])
    // Kiểm trên chuỗi thô: JSON.parse đưa key dạng số ("1", "2024") lên đầu object.
    expect(await saved[0].blob.text()).toMatch(/^\[\{"2024":"A01","Họ tên":"Nguyễn An","1":10,"Email":"an@example.com"\}/)
  })

  test('sửa cấu hình sau khi chạy thì kết quả của BE giả không còn (409), như BE thật', async () => {
    server.use(...createDevHandlers())
    const user = userEvent.setup()
    render(<App />)
    await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile('khach-hang.csv'))
    await screen.findByRole('table', { name: 'Dữ liệu xem trước' })
    await user.click(nextButton())
    await user.click(await screen.findByRole('button', { name: /^Tiếp/ }))
    await screen.findByRole('heading', { level: 2, name: 'Mapping' })
    await user.click(nextButton())
    await user.click(await screen.findByRole('button', { name: /^Chạy xử lý/ }))
    await screen.findByRole('heading', RESULT_HEADING)
    await screen.findByRole('table', { name: 'Dòng lỗi' })

    // PUT validations qua "Chạy lại" sau khi bật rule: BE giả đánh dấu chưa process, rồi process lại được.
    await user.click(screen.getByRole('button', { name: 'Quay lại' }))
    const region = await screen.findByRole('group', { name: 'Họ tên' })
    await user.click(within(region).getByRole('checkbox', { name: /unique/ }))
    await user.click(runButton())

    expect(await screen.findByRole('table', { name: 'Dòng lỗi' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
