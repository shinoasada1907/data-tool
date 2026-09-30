import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { describe, expect, test } from 'vitest'
import { DATASET_ID, datasetFixture, datasetPreviewFixture } from '../../mocks/toolboxFixtures'
import { server } from '../../mocks/node'
import { testFile } from '../../test/files'
import { problemResponse } from '../../test/http'
import type { DatasetDto, DatasetPreviewDto, ReadOptions } from './datasetApi'
import { DatasetSource } from './DatasetSource'

/** Công cụ giả giữ state như reducer thật: đổi file hay tuỳ chọn thì bỏ preview cũ. */
function Harness() {
  const [dataset, setDataset] = useState<DatasetDto | null>(null)
  const [options, setOptions] = useState<ReadOptions>({})
  const [preview, setPreview] = useState<DatasetPreviewDto | null>(null)
  return (
    <DatasetSource
      dataset={dataset}
      options={options}
      preview={preview}
      onDatasetChange={(next) => {
        setDataset(next)
        setOptions({})
        setPreview(null)
      }}
      onOptionsChange={(next) => {
        setOptions(next)
        setPreview(null)
      }}
      onPreviewLoaded={setPreview}
    />
  )
}

function mockDatasetApi(previewResponder: () => Response = () => HttpResponse.json(datasetPreviewFixture())) {
  const previewQueries: Record<string, string>[] = []
  const deleted: string[] = []
  server.use(
    http.post('/api/datasets', () => HttpResponse.json(datasetFixture(), { status: 201 })),
    http.get('/api/datasets/:id/preview', ({ request }) => {
      previewQueries.push(Object.fromEntries(new URL(request.url).searchParams))
      return previewResponder()
    }),
    http.delete('/api/datasets/:id', ({ params }) => {
      deleted.push(String(params.id))
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return { previewQueries, deleted }
}

async function uploadCsv(user: ReturnType<typeof userEvent.setup>) {
  await user.upload(screen.getByLabelText('Chọn file CSV, XLSX hoặc JSON'), testFile('khach.csv'))
}

describe('DatasetSource', () => {
  test('upload rồi xem trước: thẻ file, tuỳ chọn tự nhận, bảng theo đúng thứ tự cột', async () => {
    const api = mockDatasetApi()
    const user = userEvent.setup()
    render(<Harness />)
    expect(screen.getByText('File và kết quả tự xoá sau 24 giờ không dùng tới.')).toBeInTheDocument()

    await uploadCsv(user)

    const table = await screen.findByRole('table')
    expect(within(table).getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      'Dòng',
      'ma',
      'Email',
      'gia',
    ])
    expect(screen.getByText('khach.csv')).toBeInTheDocument()
    expect(screen.getByText('3 dòng · 3 cột · bỏ qua 1 dòng trống')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Dấu phân cách' })).toHaveDisplayValue('Tự nhận (dấu chấm phẩy ;)')
    expect(api.previewQueries).toEqual([{ limit: '50' }])
  })

  test('đổi bảng mã thì đọc lại với encoding đó', async () => {
    const api = mockDatasetApi()
    const user = userEvent.setup()
    render(<Harness />)
    await uploadCsv(user)
    await screen.findByRole('table')

    await user.selectOptions(screen.getByRole('combobox', { name: 'Bảng mã' }), 'WINDOWS-1258')

    await screen.findByRole('table')
    expect(api.previewQueries.at(-1)).toEqual({ limit: '50', encoding: 'WINDOWS-1258' })
  })

  test('sai đuôi file: báo lỗi và không gửi request', async () => {
    const user = userEvent.setup({ applyAccept: false })
    render(<Harness />)

    await user.upload(screen.getByLabelText('Chọn file CSV, XLSX hoặc JSON'), testFile('anh.png'))

    expect(screen.getByRole('alert')).toHaveTextContent('Chỉ nhận file .csv, .xlsx hoặc .json')
  })

  test('đọc file lỗi: thông điệp của toolbox kèm detail của BE; "Đọc lại" gọi lại preview', async () => {
    let calls = 0
    mockDatasetApi(() => {
      calls += 1
      return calls === 1
        ? problemResponse(422, 'FILE_PARSE_ERROR', 'File is not valid UTF-8 (near row 3). Choose the file’s encoding.')
        : HttpResponse.json(datasetPreviewFixture())
    })
    const user = userEvent.setup()
    render(<Harness />)
    await uploadCsv(user)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Không đọc được file với cách đọc hiện tại')
    expect(alert).toHaveTextContent('File is not valid UTF-8 (near row 3)')
    expect(screen.getByRole('combobox', { name: 'Bảng mã' })).toBeEnabled()

    await user.click(within(alert).getByRole('button', { name: 'Đọc lại' }))

    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  test('xoá file: gửi DELETE dataset và quay về ô chọn file', async () => {
    const api = mockDatasetApi()
    const user = userEvent.setup()
    render(<Harness />)
    await uploadCsv(user)
    await screen.findByRole('table')

    await user.click(screen.getByRole('button', { name: 'Xoá file' }))

    expect(screen.getByText('Kéo-thả file vào đây, hoặc bấm để chọn file')).toBeInTheDocument()
    await expect.poll(() => api.deleted).toEqual([DATASET_ID])
  })
})
