import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, test } from 'vitest'
import App from '../App'
import { captureDownloads } from '../test/downloads'
import { testFile } from '../test/files'
import { nextButton, RESULT_HEADING, runButton } from '../test/flows'
import { createDevHandlers } from './devHandlers'
import { server } from './node'

// BE giả của `pnpm dev:mock` phải đi được hết luồng và giữ đúng các luật mà dev dựa vào khi làm giao diện; nếu không,
// chế độ này hỏng mà không ai biết cho tới lúc demo.

beforeEach(() => {
  server.use(...createDevHandlers())
})

describe('BE giả của dev:mock, gọi thẳng', () => {
  const url = (path: string) => new URL(path, location.href).toString()

  /** Multipart viết tay: FormData của jsdom không đi được qua fetch của Node. */
  async function upload(fileName: string): Promise<string> {
    const body = `--X\r\nContent-Disposition: form-data; name="file"; filename="${fileName}"\r\nContent-Type: text/csv\r\n\r\nid\r\n--X--\r\n`
    const response = await fetch(url('/api/import-sessions'), {
      method: 'POST',
      headers: { 'Content-Type': 'multipart/form-data; boundary=X' },
      body,
    })
    expect(response.status).toBe(201)
    return ((await response.json()) as { id: string }).id
  }

  const put = (id: string, section: string, body: unknown) =>
    fetch(url(`/api/import-sessions/${id}/${section}`), { method: 'PUT', body: JSON.stringify(body) })
  const post = (id: string, path: string) => fetch(url(`/api/import-sessions/${id}/${path}`), { method: 'POST' })
  const get = (id: string, path: string) => fetch(url(`/api/import-sessions/${id}/${path}`))

  /** Schema và mapping theo cột nguồn; `rename` đổi tên field ứng với một cột. */
  async function configure(id: string, columns: string[], rename: Record<string, string> = {}) {
    const name = (column: string) => rename[column] ?? column
    await put(id, 'schema', {
      fields: columns.map((column, order) => ({ name: name(column), type: 'string', required: false, order })),
    })
    await put(id, 'mapping', {
      mappings: columns.map((column) => ({
        targetField: name(column),
        mappingType: 'SOURCE_COLUMN',
        sourceColumn: column,
        constantValue: null,
      })),
    })
  }

  test('kết quả dựng theo schema và mapping đã PUT: đổi tên field thì giá trị và lỗi mang tên mới', async () => {
    const id = await upload('khach-hang.csv')
    await configure(id, ['2024', 'Họ tên', '1', 'Email'], { '2024': 'Mã' })

    const summary = await (await post(id, 'process')).json()
    const valid = await (await get(id, 'result?view=valid&page=0&size=50')).json()
    const invalid = await (await get(id, 'result?view=invalid&page=0&size=50')).json()

    expect(summary).toMatchObject({ total: 3, valid: 2, invalid: 1, errorCountsByField: { Mã: 1 } })
    expect(valid.rows[0].values).toEqual({ Mã: 'A01', 'Họ tên': 'Nguyễn An', '1': '10', Email: 'an@example.com' })
    expect(invalid.rows[0].errors[0]).toMatchObject({ fieldName: 'Mã', code: 'VALIDATION_TYPE' })
    const json = await (await get(id, 'export?format=json')).text()
    expect(json).toMatch(/^\[\{"Mã":"A01","Họ tên":"Nguyễn An","1":"10","Email":"an@example.com"\}/)
  })

  test('giá trị theo mapping, không theo vị trí cột: hằng số, cột khác, và field chưa map (null)', async () => {
    const id = await upload('khach-hang.csv')
    await put(id, 'schema', {
      fields: ['Nguồn', 'Tên', 'Ghi chú'].map((name, order) => ({ name, type: 'string', required: false, order })),
    })
    await put(id, 'mapping', {
      mappings: [
        { targetField: 'Nguồn', mappingType: 'CONSTANT', sourceColumn: null, constantValue: 'web' },
        { targetField: 'Tên', mappingType: 'SOURCE_COLUMN', sourceColumn: 'Họ tên', constantValue: null },
      ],
    })

    await post(id, 'process')
    const valid = await (await get(id, 'result?view=valid&page=0&size=50')).json()

    expect(valid.rows[0].values).toEqual({ Nguồn: 'web', Tên: 'Nguyễn An', 'Ghi chú': null })
  })

  test('XLSX: bảng mẫu của XLSX (có tên sheet), totalRows khớp số dòng mẫu, kết quả theo cột của sheet', async () => {
    const id = await upload('khach-hang.xlsx')

    const preview = await (await get(id, 'preview?limit=50')).json()
    const columns = (preview.columns as { name: string }[]).map((column) => column.name)
    await configure(id, columns)
    await post(id, 'process')
    const valid = await (await get(id, 'result?view=valid&page=0&size=50')).json()

    expect(preview).toMatchObject({ fileType: 'XLSX', sheetName: 'Khách hàng', totalRows: preview.rows.length })
    expect(Object.keys(valid.rows[0].values).sort()).toEqual([...columns].sort())
    expect(valid.rows[0].values['Mã KH']).toBe('KH001')
  })

  test('PUT giống hệt bản đã có thì giữ kết quả; PUT làm đổi cấu hình thì result và export trả 409 tới lần process sau', async () => {
    const id = await upload('khach-hang.csv')
    const columns = ['2024', 'Họ tên', '1', 'Email']
    await configure(id, columns)
    await post(id, 'process')

    await configure(id, columns)
    expect((await get(id, 'result?view=valid&page=0&size=50')).status).toBe(200)

    await put(id, 'validations', { validations: [{ targetField: 'Email', type: 'unique' }] })
    expect((await get(id, 'result?view=valid&page=0&size=50')).status).toBe(409)
    expect((await get(id, 'export?format=csv')).status).toBe(409)
    expect((await get(id, 'errors/export')).status).toBe(409)

    await post(id, 'process')
    expect((await get(id, 'result?view=valid&page=0&size=50')).status).toBe(200)
  })

  test('process khi chưa có schema: 409 SESSION_NOT_READY kèm readiness issue SCHEMA_EMPTY', async () => {
    const id = await upload('khach-hang.csv')

    const response = await post(id, 'process')

    expect(response.status).toBe(409)
    expect(await response.json()).toMatchObject({ code: 'SESSION_NOT_READY', errors: [{ code: 'SCHEMA_EMPTY' }] })
  })

  test('session lạ: 404 SESSION_NOT_FOUND', async () => {
    expect((await get('khong-co', 'preview?limit=50')).status).toBe(404)
  })
})

describe('BE giả của dev:mock, qua giao diện', () => {
  test('đi hết luồng: upload CSV → … → chạy xử lý → xem kết quả → tải JSON với key theo schema', async () => {
    const saved = captureDownloads()
    const user = userEvent.setup()
    render(<App />)

    await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile('khach-hang.csv'))
    await screen.findByRole('table', { name: 'Dữ liệu xem trước' })
    expect(screen.getByText('Xem trước 3 / 3 dòng', { selector: 'li' })).toBeInTheDocument()
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
    expect(await saved[0].blob.text()).toMatch(/^\[\{"2024":"A01","Họ tên":"Nguyễn An","1":"10","Email":"an@example.com"\}/)
  })
})
