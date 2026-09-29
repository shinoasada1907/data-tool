import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { server } from '../../mocks/node'
import {
  datasetFixture,
  datasetPreviewFixture,
  validatorRowsFixture,
  validatorRunFixture,
} from '../../mocks/toolboxFixtures'
import { captureDownloads } from '../../test/downloads'
import { testFile } from '../../test/files'
import { problemWithErrors } from '../../test/http'
import { ValidatorTool } from './ValidatorTool'

type User = ReturnType<typeof userEvent.setup>

/** BE giả cho cả luồng; ghi lại body gửi đi và query của từng lần lấy trang. */
function mockValidatorApi({ run = (): Response => HttpResponse.json(validatorRunFixture(), { status: 201 }) } = {}) {
  const runBodies: unknown[] = []
  const rowQueries: Record<string, string>[] = []
  const exportBodies: unknown[] = []
  server.use(
    http.post('/api/datasets', () => HttpResponse.json(datasetFixture(), { status: 201 })),
    http.get('/api/datasets/:id/preview', () => HttpResponse.json(datasetPreviewFixture())),
    http.delete('/api/datasets/:id', () => new HttpResponse(null, { status: 204 })),
    http.post('/api/validator/runs', async ({ request }) => {
      runBodies.push(await request.json())
      return run()
    }),
    http.get('/api/validator/runs/:id/rows', ({ request }) => {
      const query = Object.fromEntries(new URL(request.url).searchParams)
      rowQueries.push(query)
      return HttpResponse.json(validatorRowsFixture({ view: query.view as 'VALID' | 'INVALID' }))
    }),
    http.post('/api/validator/runs/:id/export', async ({ request }) => {
      exportBodies.push(await request.json())
      return new HttpResponse('row,field\n', {
        headers: { 'Content-Disposition': "attachment; filename*=UTF-8''khach-errors.csv" },
      })
    }),
    http.delete('/api/validator/runs/:id', () => new HttpResponse(null, { status: 204 })),
  )
  return { runBodies, rowQueries, exportBodies }
}

async function openSchemaStep(user: User) {
  render(<ValidatorTool />)
  await user.upload(screen.getByLabelText('Chọn file CSV, XLSX hoặc JSON'), testFile('khach.csv'))
  await screen.findByRole('table')
  await user.click(screen.getByRole('button', { name: 'Tiếp' }))
  await screen.findByRole('heading', { level: 2, name: 'Schema' })
}

function fieldItem(position: number) {
  return screen.getByRole('listitem', { name: `Field ${position}` })
}

describe('Kiểm tra dữ liệu', () => {
  test('đi hết luồng: tự sinh schema, chạy, xem lỗi, lọc theo mã, tải báo cáo lỗi', async () => {
    const api = mockValidatorApi()
    const saved = captureDownloads()
    const user = userEvent.setup()
    await openSchemaStep(user)

    // Tự sinh từ cột: tên schema theo tên file, kiểu theo inferredType.
    expect(screen.getByLabelText('Tên schema')).toHaveValue('khach')
    expect(within(fieldItem(2)).getByLabelText('Tên field')).toHaveValue('Email')
    expect(within(fieldItem(2)).getByLabelText('Kiểu')).toHaveValue('email')
    expect(within(fieldItem(3)).getByText('Cột: gia')).toBeInTheDocument()

    await user.click(within(fieldItem(1)).getByLabelText('Bắt buộc'))
    await user.click(within(fieldItem(1)).getByLabelText('Không trùng'))
    await user.type(within(fieldItem(3)).getByLabelText('Nhỏ nhất'), '0')
    await user.click(screen.getByRole('button', { name: 'Chạy kiểm tra' }))

    await screen.findByRole('heading', { level: 2, name: 'Kết quả kiểm tra' })
    expect(api.runBodies).toEqual([
      {
        source: { datasetId: datasetFixture().id, options: { delimiter: 'SEMICOLON', encoding: 'UTF-8', hasHeader: true } },
        schema: {
          name: 'khach',
          fields: [
            { name: 'ma', type: 'string', required: true, constraints: { unique: true } },
            { name: 'Email', type: 'email', required: false },
            { name: 'gia', type: 'number', required: false, constraints: { min: 0 } },
          ],
        },
      },
    ])

    const summary = screen.getByLabelText('Tóm tắt')
    expect(within(summary).getByText('Không hợp lệ').closest('div')).toHaveTextContent('Không hợp lệ2')
    expect(within(summary).getByText('Số lỗi').closest('div')).toHaveTextContent('Số lỗi3')
    expect(screen.getByRole('button', { name: 'Không hợp lệ (2)' })).toHaveAttribute('aria-pressed', 'true')

    const table = await screen.findByRole('table')
    expect(within(table).getByRole('list', { name: 'Lỗi của dòng 5' })).toHaveTextContent('Giá trị bị trùng')
    expect(api.rowQueries[0]).toEqual({ view: 'INVALID', page: '0', size: '50' })

    await user.selectOptions(screen.getByLabelText('Lọc theo mã lỗi'), 'VALIDATION_UNIQUE')
    await expect.poll(() => api.rowQueries.at(-1)).toEqual({ view: 'INVALID', page: '0', size: '50', code: 'VALIDATION_UNIQUE' })

    await user.click(screen.getByRole('button', { name: 'Tải về' }))
    await screen.findByText('Đã tải khach-errors.csv')
    expect(api.exportBodies).toEqual([{ content: 'ERRORS', output: { format: 'CSV' } }])
    expect(saved.map((file) => file.fileName)).toEqual(['khach-errors.csv'])
  })

  test('BE trả SCHEMA_INVALID: lỗi gắn đúng ô theo pointer, ở lại bước Schema', async () => {
    mockValidatorApi({
      run: () =>
        problemWithErrors(422, 'SCHEMA_INVALID', [
          { field: null, code: 'CONSTRAINT_INVALID', message: 'Pattern is not valid RE2.', pointer: '/schema/fields/0/constraints/pattern' },
        ]),
    })
    const user = userEvent.setup()
    await openSchemaStep(user)

    await user.type(within(fieldItem(1)).getByLabelText('Pattern (RE2, khớp toàn chuỗi)'), '(?=a)')
    await user.click(screen.getByRole('button', { name: 'Chạy kiểm tra' }))

    const pattern = within(fieldItem(1)).getByLabelText('Pattern (RE2, khớp toàn chuỗi)')
    expect(await screen.findByText('Ràng buộc không hợp lệ: Pattern is not valid RE2.')).toBeInTheDocument()
    expect(pattern).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('alert')).toHaveTextContent('Schema không hợp lệ')

    // Sửa schema thì lỗi của lần chạy trước biến mất.
    await user.type(pattern, 'x')
    expect(screen.queryByText('Ràng buộc không hợp lệ: Pattern is not valid RE2.')).not.toBeInTheDocument()
  })

  test('field bắt buộc không có cột thì khoá "Chạy kiểm tra" và nêu lý do', async () => {
    mockValidatorApi()
    const user = userEvent.setup()
    await openSchemaStep(user)

    await user.click(screen.getByRole('button', { name: 'Thêm field' }))
    await user.type(within(fieldItem(4)).getByLabelText('Tên field'), 'sdt')
    await user.click(within(fieldItem(4)).getByLabelText('Bắt buộc'))

    expect(within(fieldItem(4)).getByText('Không có cột cho field bắt buộc')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Chạy kiểm tra' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Chạy kiểm tra' })).toHaveAccessibleDescription('Field bắt buộc không có cột: sdt')
  })

  test('sửa schema sau khi chạy thì kết quả được đánh dấu là của lần chạy trước', async () => {
    const api = mockValidatorApi()
    const user = userEvent.setup()
    await openSchemaStep(user)
    await user.click(screen.getByRole('button', { name: 'Chạy kiểm tra' }))
    await screen.findByRole('heading', { level: 2, name: 'Kết quả kiểm tra' })

    await user.click(screen.getByRole('button', { name: /Schema/ }))
    await user.selectOptions(within(fieldItem(3)).getByLabelText('Kiểu'), 'string')
    await user.click(screen.getByRole('button', { name: /Kết quả/ }))

    expect(screen.getByText('Schema hoặc file đã thay đổi — kết quả này là của lần chạy trước.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Chạy lại' }))
    await expect.poll(() => api.runBodies.length).toBe(2)
    await expect.poll(() => screen.queryByText(/kết quả này là của lần chạy trước/)).toBeNull()
  })

  test('mở file schema sai định dạng: báo lỗi, schema giữ nguyên', async () => {
    mockValidatorApi()
    const user = userEvent.setup()
    await openSchemaStep(user)

    const bad = new File([JSON.stringify({ name: 'x', fields: [] })], 'x.json', { type: 'application/json' })
    await user.upload(screen.getByLabelText('Chọn file schema (.json)'), bad)

    expect(await screen.findByRole('alert')).toHaveTextContent('không phải schema của Universal Data Tools')
    expect(screen.getAllByRole('listitem', { name: /^Field \d$/ })).toHaveLength(3)
  })
})
