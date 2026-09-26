import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import App from '../App'
import { importSessionFixture } from '../mocks/fixtures'
import { testFile } from '../test/files'
import { gate, mockUpload } from '../test/http'

type User = ReturnType<typeof userEvent.setup>

function stepButton(name: RegExp) {
  return screen.getByRole('button', { name })
}

/** Vùng lý do khoá của stepper (bước Upload cũng có vùng status riêng). */
function stepperNotice() {
  return within(screen.getByRole('navigation', { name: 'Các bước import' })).getByRole('status')
}

function created(overrides = {}) {
  return () => HttpResponse.json(importSessionFixture(overrides), { status: 201 })
}

function fireBeforeUnload(): Event {
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  return event
}

async function expectPreviewStep() {
  await screen.findByRole('heading', { level: 2, name: 'Xem trước dữ liệu' })
}

async function uploadCsv(user: User, name = 'khach-hang.csv') {
  await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile(name))
  await expectPreviewStep()
}

describe('khung wizard', () => {
  test('mở lần đầu: đang ở bước Upload, các bước 2 → 6 bị khoá', () => {
    render(<App />)

    expect(stepButton(/Upload file/)).toHaveAttribute('aria-current', 'step')
    for (const label of [/Xem trước dữ liệu/, /Schema đích/, /Mapping/, /Biến đổi & kiểm tra/, /Kết quả & export/]) {
      expect(stepButton(label)).toHaveAttribute('aria-disabled', 'true')
    }
  })

  test('bấm vào bước bị khoá thì không chuyển bước và hiện lý do', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(stepButton(/Xem trước dữ liệu/))

    expect(stepperNotice()).toHaveTextContent('Cần upload file trước')
    expect(stepButton(/Upload file/)).toHaveAttribute('aria-current', 'step')
  })

  test('lý do khoá cũ không hiện lại khi quay về bước đó sau khi đã chuyển bước', async () => {
    mockUpload(created())
    const user = userEvent.setup()
    render(<App />)
    await user.click(stepButton(/Schema đích/))
    expect(stepperNotice()).toHaveTextContent('Cần tải xong dữ liệu xem trước')

    await uploadCsv(user)
    await user.click(stepButton(/Upload file/))

    expect(screen.queryByText('Cần tải xong dữ liệu xem trước')).not.toBeInTheDocument()
  })

  test('upload xong thì bước Upload được đánh dấu đã xong, và quay lại được qua stepper', async () => {
    mockUpload(created())
    const user = userEvent.setup()
    render(<App />)
    await uploadCsv(user)

    // Ô chọn file biến mất cùng bước Upload: focus sang tiêu đề bước Xem trước (design D14).
    expect(screen.getByRole('heading', { level: 2, name: 'Xem trước dữ liệu' })).toHaveFocus()

    expect(stepButton(/Upload file/)).toHaveAccessibleName('1 Upload file (đã xong)')
    await user.click(stepButton(/Upload file/))

    expect(screen.getByRole('heading', { level: 2, name: 'Upload file nguồn' })).toBeInTheDocument()
    expect(stepButton(/Xem trước dữ liệu/)).not.toHaveAttribute('aria-disabled')
  })

  test('đang upload thì khoá mọi nút trên stepper', async () => {
    const hold = gate()
    mockUpload(async () => {
      await hold.promise
      return created()()
    })
    const user = userEvent.setup()
    render(<App />)

    await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile('khach-hang.csv'))

    expect(await screen.findByRole('progressbar')).toBeInTheDocument()
    for (const button of screen.getAllByRole('button', { name: /^\d/ })) {
      expect(button).toBeDisabled()
    }
    hold.open()
    await expectPreviewStep()
  })

  describe('cảnh báo khi rời trang', () => {
    test('chưa có session thì không chặn tải lại trang', () => {
      render(<App />)

      expect(fireBeforeUnload().defaultPrevented).toBe(false)
    })

    test('đã có session thì chặn tải lại trang để hỏi xác nhận', async () => {
      mockUpload(created())
      const user = userEvent.setup()
      render(<App />)

      await uploadCsv(user)

      expect(fireBeforeUnload().defaultPrevented).toBe(true)
    })

    test('trong lúc upload thay thế, session cũ vẫn còn nên vẫn cảnh báo', async () => {
      const hold = gate()
      mockUpload(created(), async () => {
        await hold.promise
        return created({ id: 'session-2' })()
      })
      const user = userEvent.setup()
      render(<App />)
      await uploadCsv(user)
      await user.click(stepButton(/Upload file/))

      await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile('don-hang.csv'))
      await user.click(screen.getByRole('button', { name: 'Tiếp tục' }))

      expect(await screen.findByRole('progressbar')).toBeInTheDocument()
      expect(fireBeforeUnload().defaultPrevented).toBe(true)
      hold.open()
      await expectPreviewStep()
    })
  })
})
