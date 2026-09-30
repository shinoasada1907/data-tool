import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import App from '../../App'
import { importSessionFixture } from '../../mocks/fixtures'
import { FakeXhr } from '../../test/fakeXhr'
import { MB, testFile } from '../../test/files'
import { gate, mockUpload, problemResponse } from '../../test/http'

const INPUT_LABEL = 'Chọn file CSV hoặc XLSX'
const DROP_ZONE_TEXT = 'Kéo-thả file vào đây, hoặc bấm để chọn file'

function setup() {
  // applyAccept: false để thử được cả file mà thuộc tính accept của input sẽ lọc bỏ.
  const user = userEvent.setup({ applyAccept: false })
  render(<App />)
  return { user, input: () => screen.getByLabelText(INPUT_LABEL) }
}

type User = ReturnType<typeof setup>['user']

function created(overrides = {}) {
  return () => HttpResponse.json(importSessionFixture(overrides), { status: 201 })
}

async function expectPreviewStep() {
  expect(await screen.findByRole('heading', { level: 2, name: 'Xem trước dữ liệu' })).toBeInTheDocument()
}

/** Các nút của stepper có tên bắt đầu bằng số thứ tự bước. */
function stepperButtons() {
  return screen.getAllByRole('button', { name: /^\d/ })
}

/** Vùng đọc cho screen reader của bước Upload (stepper cũng có vùng status riêng). */
function uploadStatus() {
  return within(screen.getByRole('region', { name: 'Upload file nguồn' })).getByRole('status')
}

function expectStepperUnlocked() {
  for (const button of stepperButtons()) expect(button).toBeEnabled()
}

async function uploadFirstFileThenGoBack(user: User, input: () => HTMLElement) {
  await user.upload(input(), testFile('khach-hang.csv'))
  await expectPreviewStep()
  await user.click(screen.getByRole('button', { name: /Upload file/ }))
}

describe('bước Upload', () => {
  test('hiển thị gợi ý định dạng và giới hạn dung lượng', () => {
    setup()

    expect(screen.getByText(/CSV phải mã hoá UTF-8 và phân cách bằng dấu phẩy/)).toBeInTheDocument()
    expect(screen.getByText('Với XLSX, hệ thống đọc sheet hiển thị đầu tiên.')).toBeInTheDocument()
    expect(screen.getByText('Tối đa 20 MB.')).toBeInTheDocument()
  })

  test('CSV mang MIME của Excel: hiện tên, dung lượng, loại file trong lúc upload rồi sang bước Xem trước', async () => {
    const hold = gate()
    const upload = mockUpload(async () => {
      await hold.promise
      return created()()
    })
    const { user, input } = setup()

    await user.upload(input(), testFile('khach-hang.csv', { size: 1_258_291, type: 'application/vnd.ms-excel' }))

    const progress = await screen.findByRole('group', { name: 'Đang upload' })
    expect(within(progress).getByText('khach-hang.csv')).toBeInTheDocument()
    expect(within(progress).getByText('1,2 MB')).toBeInTheDocument()
    expect(within(progress).getByText('CSV')).toBeInTheDocument()

    hold.open()
    await expectPreviewStep()
    expect(upload.calls()).toBe(1)
  })

  test.each([
    ['sai đuôi file', [testFile('data.json')], 'Chỉ hỗ trợ file .csv hoặc .xlsx'],
    ['file rỗng', [testFile('rong.csv', { size: 0 })], 'File rỗng (0 byte)'],
    ['vượt giới hạn 20 MB', [testFile('to.csv', { size: 25 * MB })], 'File vượt giới hạn 20 MB'],
  ])('%s: báo lỗi và không gọi API', async (_case, files, message) => {
    const upload = mockUpload(created())
    const { user, input } = setup()

    await user.upload(input(), files)

    expect(screen.getByRole('alert')).toHaveTextContent(message)
    expect(upload.calls()).toBe(0)
  })

  test('kéo-thả nhiều file: báo chỉ chọn một file và không gọi API', () => {
    const upload = mockUpload(created())
    setup()

    fireEvent.drop(screen.getByText(DROP_ZONE_TEXT), {
      dataTransfer: { files: [testFile('a.csv'), testFile('b.csv')] },
    })

    expect(screen.getByRole('alert')).toHaveTextContent('Chỉ chọn một file')
    expect(upload.calls()).toBe(0)
  })

  test('kéo-thả một file hợp lệ thì upload như khi chọn file', async () => {
    mockUpload(created())
    setup()

    fireEvent.drop(screen.getByText(DROP_ZONE_TEXT), {
      dataTransfer: { files: [testFile('khach-hang.csv')] },
    })

    await expectPreviewStep()
  })

  test.each([
    [415, 'FILE_UNSUPPORTED', 'Only .csv and .xlsx files are supported.', 'Máy chủ không nhận loại file này; chỉ hỗ trợ CSV và XLSX'],
    [413, 'FILE_TOO_LARGE', 'Maximum upload size is 20MB.', 'File vượt giới hạn của máy chủ'],
    [422, 'FILE_EMPTY', 'The file has no header row.', 'File không có dữ liệu: file rỗng, thiếu dòng header, hoặc workbook/sheet trống'],
  ])('BE trả %i %s: báo lỗi theo mã, mở khoá lại wizard và chọn được file khác', async (status, code, detail, headline) => {
    mockUpload(() => problemResponse(status, code, detail), created())
    const { user, input } = setup()

    await user.upload(input(), testFile('khach-hang.csv'))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(headline)
    expect(alert).toHaveTextContent(code)
    // Lỗi 4xx: gửi lại đúng file đó cũng lỗi y hệt, nên không có nút "Upload lại".
    expect(within(alert).queryByRole('button', { name: 'Upload lại' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Upload file nguồn' })).toBeInTheDocument()
    expectStepperUnlocked()
    expect(input()).toHaveFocus()

    await user.upload(input(), testFile('khac.csv'))
    await expectPreviewStep()
  })

  test('FILE_PARSE_ERROR: thông điệp tiếng Việt ở dòng chính, detail của BE ở dòng phụ', async () => {
    mockUpload(() => problemResponse(422, 'FILE_PARSE_ERROR', 'CSV syntax error near row 12.'))
    const { user, input } = setup()

    await user.upload(input(), testFile('hong.csv'))

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Không đọc được file; CSV phải mã hoá UTF-8 và phân cách bằng dấu phẩy')).toBeInTheDocument()
    expect(within(alert).getByText('CSV syntax error near row 12.')).toBeInTheDocument()
  })

  test.each([
    ['mất kết nối', () => HttpResponse.error(), 'Không kết nối được máy chủ'],
    ['BE lỗi 500', () => problemResponse(500, 'INTERNAL_ERROR', 'Unexpected error.'), 'Máy chủ gặp lỗi không lường trước'],
    ['proxy trả 502', () => new HttpResponse('<html>Bad Gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }), 'Máy chủ đang lỗi (502)'],
  ])('%s: báo lỗi, bấm "Upload lại" thì gửi lại đúng file đó', async (_case, failure, headline) => {
    const upload = mockUpload(failure, created())
    const { user, input } = setup()

    await user.upload(input(), testFile('khach-hang.csv'))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(headline)
    expectStepperUnlocked()
    await user.click(within(alert).getByRole('button', { name: 'Upload lại' }))

    await expectPreviewStep()
    expect(upload.calls()).toBe(2)
  })

  describe('khi đã có session', () => {
    test('quay lại bước Upload thì thấy file đang dùng', async () => {
      mockUpload(created())
      const { user, input } = setup()

      await uploadFirstFileThenGoBack(user, input)

      expect(screen.getByText('khach-hang.csv')).toBeInTheDocument()
      expect(screen.getByText('Chọn file khác sẽ tạo phiên import mới.')).toBeInTheDocument()
    })

    test('chọn file mới: hỏi xác nhận kèm tên file, focus vào nút "Tiếp tục"', async () => {
      mockUpload(created())
      const { user, input } = setup()
      await uploadFirstFileThenGoBack(user, input)

      // Thả file thay vì user.upload: sau sự kiện change, user-event giả lập "hộp chọn file đóng thì trả
      // focus về ô input" và blur luôn nút vừa nhận focus, vì input lúc đó đã bị khoá. Trình duyệt thật
      // trả focus về input trước khi phát change, nên autoFocus vẫn thắng.
      fireEvent.drop(screen.getByText(DROP_ZONE_TEXT), { dataTransfer: { files: [testFile('don-hang.csv')] } })

      expect(
        screen.getByText('Upload “don-hang.csv” sẽ xoá toàn bộ cấu hình của phiên hiện tại.'),
      ).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Tiếp tục' })).toHaveFocus()
    })

    test('xác nhận "Tiếp tục": upload file mới và chỉ còn session mới', async () => {
      const upload = mockUpload(created(), created({ id: 'session-2', originalFileName: 'don-hang.csv' }))
      const { user, input } = setup()
      await uploadFirstFileThenGoBack(user, input)

      await user.upload(input(), testFile('don-hang.csv'))
      expect(upload.calls()).toBe(1)
      await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))

      await expectPreviewStep()
      expect(upload.calls()).toBe(2)
      await user.click(screen.getByRole('button', { name: /Upload file/ }))
      expect(screen.getByText('don-hang.csv')).toBeInTheDocument()
      expect(screen.queryByText('khach-hang.csv')).not.toBeInTheDocument()
    })

    test('upload thay thế bị BE từ chối: giữ nguyên session cũ, vẫn vào được bước Xem trước', async () => {
      mockUpload(created(), () => problemResponse(415, 'FILE_UNSUPPORTED', 'Only .csv and .xlsx files are supported.'))
      const { user, input } = setup()
      await uploadFirstFileThenGoBack(user, input)

      await user.upload(input(), testFile('don-hang.csv'))
      await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('FILE_UNSUPPORTED')
      expect(screen.getByText('khach-hang.csv')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Xem trước dữ liệu/ })).not.toHaveAttribute('aria-disabled')
    })

    test('huỷ xác nhận: giữ nguyên session hiện tại, không gọi API, focus về ô chọn file', async () => {
      const upload = mockUpload(created())
      const { user, input } = setup()
      await uploadFirstFileThenGoBack(user, input)

      await user.upload(input(), testFile('don-hang.csv'))
      await user.click(screen.getByRole('button', { name: 'Huỷ' }))

      expect(screen.queryByRole('button', { name: 'Tiếp tục' })).not.toBeInTheDocument()
      expect(screen.getByText('khach-hang.csv')).toBeInTheDocument()
      expect(upload.calls()).toBe(1)
      expect(input()).toHaveFocus()
    })
  })

  describe('đang upload', () => {
    // Interceptor XHR của MSW bỏ qua abort() khi handler còn treo (request vẫn hoàn tất với 201),
    // khác trình duyệt thật. Vì vậy riêng nhóm này thay XMLHttpRequest bằng bản giả có ngữ nghĩa abort đúng.
    beforeEach(() => {
      FakeXhr.instances = []
      vi.stubGlobal('XMLHttpRequest', FakeXhr)
    })
    afterEach(() => {
      vi.unstubAllGlobals()
    })

    async function respondCreated(overrides = {}) {
      await act(async () => {
        FakeXhr.last().respond(201, JSON.stringify(importSessionFixture(overrides)), {
          'Content-Type': 'application/json',
        })
      })
    }

    test('hiện phần trăm, khoá chọn file, focus vào nút Huỷ; vùng đọc cho screen reader không đọc lại theo từng %', async () => {
      const { input } = setup()

      // Thả file thay vì user.upload, cùng lý do như test focus của hộp xác nhận ở trên.
      fireEvent.drop(screen.getByText(DROP_ZONE_TEXT), { dataTransfer: { files: [testFile('khach-hang.csv')] } })
      act(() => FakeXhr.last().progress(512, 1024))

      expect(screen.getByRole('progressbar', { name: 'Tiến độ upload' })).toHaveValue(50)
      expect(screen.getByText('Đang upload… 50%')).toBeInTheDocument()
      expect(input()).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Huỷ' })).toHaveFocus()
      expect(uploadStatus()).toHaveTextContent('Đang upload khach-hang.csv')
      expect(uploadStatus()).not.toHaveTextContent('50%')
      for (const button of stepperButtons()) expect(button).toBeDisabled()
    })

    test('gửi xong 100% mà BE chưa trả lời thì báo đang đọc file trên máy chủ', async () => {
      const { user, input } = setup()

      await user.upload(input(), testFile('khach-hang.csv'))
      act(() => FakeXhr.last().progress(1024, 1024))

      const progress = screen.getByRole('group', { name: 'Đang upload' })
      expect(within(progress).getByText('Đang đọc file trên máy chủ…')).toBeInTheDocument()
      expect(uploadStatus()).toHaveTextContent('Đang đọc file trên máy chủ…')
    })

    test('bấm Huỷ thì huỷ request, về trạng thái chọn file, không báo lỗi, mở khoá wizard, focus về ô chọn file', async () => {
      const { user, input } = setup()
      await user.upload(input(), testFile('khach-hang.csv'))

      await user.click(screen.getByRole('button', { name: 'Huỷ' }))

      expect(FakeXhr.last().aborted).toBe(true)
      await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument())
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(input()).toBeEnabled()
      expect(input()).toHaveFocus()
      expectStepperUnlocked()
      expect(screen.getByRole('heading', { level: 2, name: 'Upload file nguồn' })).toBeInTheDocument()
    })

    test('thả thêm file trong lúc đang upload thì bỏ qua, không tạo request thứ hai', async () => {
      const { user, input } = setup()
      await user.upload(input(), testFile('khach-hang.csv'))

      fireEvent.drop(screen.getByText(DROP_ZONE_TEXT), { dataTransfer: { files: [testFile('khac.csv')] } })
      // File sai đuôi cũng không được làm mất khối tiến độ của upload đang chạy.
      fireEvent.drop(screen.getByText(DROP_ZONE_TEXT), { dataTransfer: { files: [testFile('data.json')] } })

      expect(FakeXhr.instances).toHaveLength(1)
      expect(screen.getByRole('progressbar')).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    test('huỷ upload thay thế: giữ nguyên session cũ', async () => {
      const { user, input } = setup()
      await user.upload(input(), testFile('khach-hang.csv'))
      await respondCreated()
      await expectPreviewStep()
      await user.click(screen.getByRole('button', { name: /Upload file/ }))

      await user.upload(input(), testFile('don-hang.csv'))
      await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))
      await user.click(screen.getByRole('button', { name: 'Huỷ' }))

      await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument())
      expect(screen.getByText('khach-hang.csv')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Xem trước dữ liệu/ })).not.toHaveAttribute('aria-disabled')
    })
  })
})
