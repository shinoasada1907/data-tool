import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import App from './App'
import {
  importSessionFixture,
  pipelineResultFixture,
  pipelineSummaryFixture,
  xlsxPreviewFixture,
} from './mocks/fixtures'
import { server } from './mocks/node'
import { captureDownloads } from './test/downloads'
import { testFile } from './test/files'
import {
  nextButton,
  openResultStep,
  RESULT_HEADING,
  RULES_HEADING,
  runButton,
  saved,
  schemaGroupByName,
  stepButton,
} from './test/flows'
import {
  mockPreview,
  mockProcess,
  mockResult,
  mockSaveMapping,
  mockSaveSchema,
  mockSaveTransformations,
  mockSaveValidations,
  mockUpload,
  recordRequests,
} from './test/http'

// Test tích hợp trên cả app (task 13.1): mỗi ca đi qua mọi bước bằng giao diện, với response của BE giả lập bằng MSW.
// Chi tiết từng bước (lỗi, focus, trạng thái rỗng…) nằm ở test của từng feature.

describe('luồng đầy đủ', () => {
  test('CSV: upload → xem trước → schema → mapping → biến đổi & kiểm tra → chạy → kết quả → tải JSON, đúng trình tự request', async () => {
    const downloads = captureDownloads()
    server.use(
      http.get('/api/import-sessions/:id/export', () =>
        HttpResponse.json([{ Email: 'an@example.com' }], {
          headers: { 'Content-Disposition': "attachment; filename*=UTF-8''khach-hang-valid.json" },
        }),
      ),
    )
    const log = recordRequests()
    const user = userEvent.setup()
    render(<App />)

    await openResultStep(user)
    expect(await screen.findByRole('table', { name: 'Dòng lỗi' })).toBeInTheDocument()
    await user.click(within(screen.getByRole('group', { name: 'Tải kết quả' })).getByRole('button', { name: 'Tải JSON' }))
    await screen.findByText('Đã tải khach-hang-valid.json', { selector: '[role="status"]' })

    expect(log).toEqual([
      'POST import-sessions',
      'GET preview?limit=50',
      'PUT schema',
      'PUT mapping',
      'PUT transformations',
      'PUT validations',
      'POST process',
      'GET result?view=invalid&page=0&size=50',
      'GET export?format=json',
    ])
    expect(downloads.map((file) => file.fileName)).toEqual(['khach-hang-valid.json'])
    // Mọi bước trước bước Kết quả đều "đã xong" trên stepper.
    for (const step of [/Upload file/, /Xem trước dữ liệu/, /Schema đích/, /Mapping/, /Biến đổi & kiểm tra/]) {
      expect(stepButton(step)).toHaveAccessibleName(/đã xong/)
    }
  })

  test('XLSX: bước Xem trước có tên sheet; schema sinh từ cột của sheet; đi tới kết quả và tải CSV', async () => {
    const downloads = captureDownloads()
    server.use(
      http.get('/api/import-sessions/:id/export', () =>
        new HttpResponse('\uFEFFMã KH,Ngày sinh,Số dư\r\n', {
          headers: {
            'Content-Type': 'text/csv;charset=UTF-8',
            'Content-Disposition': "attachment; filename*=UTF-8''khach-hang-valid.csv",
          },
        }),
      ),
    )
    mockUpload(() =>
      HttpResponse.json(importSessionFixture({ originalFileName: 'khach-hang.xlsx', fileType: 'XLSX' }), { status: 201 }),
    )
    mockPreview(() => HttpResponse.json(xlsxPreviewFixture()))
    const schema = mockSaveSchema(saved)
    mockSaveMapping(saved)
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    mockProcess(() => HttpResponse.json(pipelineSummaryFixture({ errorCountsByField: { 'Mã KH': 20 } })))
    mockResult(() => HttpResponse.json(pipelineResultFixture({ rows: [] })))
    const user = userEvent.setup()
    render(<App />)

    await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile('khach-hang.xlsx'))

    expect(await screen.findByText('Sheet: Khách hàng')).toBeInTheDocument()
    await user.click(nextButton())
    await screen.findByRole('heading', { level: 2, name: 'Schema đích' })
    await user.click(nextButton())
    await screen.findByRole('heading', { level: 2, name: 'Mapping' })
    await user.click(nextButton())
    await screen.findByRole('heading', RULES_HEADING)
    await user.click(runButton())
    await screen.findByRole('heading', RESULT_HEADING)

    expect((schema.bodies[0] as { fields: { name: string }[] }).fields.map((field) => field.name)).toEqual([
      'Mã KH',
      'Ngày sinh',
      'Số dư',
    ])
    expect(within(screen.getByRole('group', { name: 'Tóm tắt kết quả' })).getAllByRole('definition')[0]).toHaveTextContent(
      '120',
    )
    await user.click(within(screen.getByRole('group', { name: 'Tải kết quả' })).getByRole('button', { name: 'Tải CSV' }))
    await screen.findByText('Đã tải khach-hang-valid.csv', { selector: '[role="status"]' })
    expect(downloads.map((file) => file.fileName)).toEqual(['khach-hang-valid.csv'])
  })

  test('sửa cấu hình rồi chạy lại: đổi tên field ở Schema, lưu lại Schema và Mapping, chạy lại gửi tên mới; kết quả mới dùng tên mới', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openResultStep(user)
    const transformations = mockSaveTransformations(saved)
    const validations = mockSaveValidations(saved)
    const log = recordRequests()

    await user.click(stepButton(/Schema đích/))
    const name = within(schemaGroupByName('Họ tên')).getByRole('textbox', { name: 'Tên field' })
    await user.clear(name)
    await user.type(name, 'Khách hàng')
    // Sửa schema thì kết quả cũ và bước Kết quả bị khoá cho tới khi mapping được lưu lại (spec import-wizard).
    expect(stepButton(/Kết quả/)).toHaveAttribute('aria-disabled', 'true')
    await user.click(nextButton())
    await screen.findByRole('heading', { level: 2, name: 'Mapping' })
    expect(stepButton(/Kết quả/)).toHaveAttribute('aria-disabled', 'true')
    await user.click(nextButton())
    await screen.findByRole('heading', RULES_HEADING)
    // Mapping đã lưu lại: bước Kết quả mở lại (kết quả cũ vẫn xem được), trước khi chạy lại.
    expect(stepButton(/Kết quả/)).not.toHaveAttribute('aria-disabled')
    await user.click(runButton())
    await screen.findByRole('heading', RESULT_HEADING)
    await screen.findByRole('table', { name: 'Dòng lỗi' })

    expect(log).toEqual([
      'PUT schema',
      'PUT mapping',
      'PUT transformations',
      'PUT validations',
      'POST process',
      'GET result?view=invalid&page=0&size=50',
    ])
    expect(transformations.bodies.at(-1)).toEqual({ transformations: [] })
    expect(validations.bodies.at(-1)).toEqual({ validations: [] })
    expect(within(screen.getByRole('table')).getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      'Dòng',
      '2024',
      'Khách hàng',
      '1',
      'Email',
    ])
    expect(screen.queryByText(/kết quả này là của lần chạy trước/)).not.toBeInTheDocument()
  })
})
