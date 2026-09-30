import { render, screen, within } from '@testing-library/react'
import { useEffect, useState } from 'react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test, vi } from 'vitest'
import App from '../App'
import { AppShell } from './AppShell'
import type { ToolDefinition } from './tools'

function NoIcon() {
  return null
}

const twoTools: ToolDefinition[] = [
  { id: 'import', label: 'Import dữ liệu', description: 'Mô tả import', Icon: NoIcon, Component: () => <p>Nội dung import</p> },
  { id: 'report', label: 'Báo cáo', description: 'Mô tả báo cáo', Icon: NoIcon, Component: () => <p>Nội dung báo cáo</p> },
]

function fireWindowEvent(type: 'dragover' | 'drop', dataTransfer: object): Event {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'dataTransfer', { value: dataTransfer })
  document.body.dispatchEvent(event)
  return event
}

function toolNav() {
  return within(screen.getByRole('complementary')).getByRole('navigation', { name: 'Công cụ' })
}

describe('khung app', () => {
  test('sidebar có tên app và mục "Import dữ liệu" đang được chọn', () => {
    render(<App />)

    expect(within(screen.getByRole('complementary')).getByText('Universal Data Tools')).toBeInTheDocument()
    expect(within(toolNav()).getByRole('button', { name: 'Import dữ liệu' })).toHaveAttribute('aria-current', 'page')
  })

  test('trang công cụ: h1 là tên công cụ, bên dưới là wizard import', () => {
    render(<App />)

    expect(screen.getByRole('heading', { level: 1, name: 'Import dữ liệu' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Các bước import' })).toBeInTheDocument()
  })

  test('tiêu đề tab lấy từ tên app', () => {
    document.title = ''
    render(<App />)

    expect(document.title).toBe('Universal Data Tools')
  })

  test('sidebar liệt kê đúng các công cụ đã đăng ký; chọn công cụ khác thì mở trang của nó', async () => {
    const user = userEvent.setup()
    render(<AppShell tools={twoTools} />)

    // Đúng hai mục, theo đúng thứ tự đăng ký; tên nút là tên công cụ (số thứ tự chỉ để nhìn, aria-hidden).
    expect(within(toolNav()).getAllByRole('button')).toEqual([
      within(toolNav()).getByRole('button', { name: 'Import dữ liệu' }),
      within(toolNav()).getByRole('button', { name: 'Báo cáo' }),
    ])
    expect(screen.getByText('Nội dung import')).toBeInTheDocument()

    await user.click(within(toolNav()).getByRole('button', { name: 'Báo cáo' }))

    expect(screen.getByRole('heading', { level: 1, name: 'Báo cáo' })).toBeInTheDocument()
    expect(screen.getByText('Mô tả báo cáo')).toBeInTheDocument()
    expect(screen.getByText('Nội dung báo cáo')).toBeInTheDocument()
    expect(within(toolNav()).getByRole('button', { name: 'Báo cáo' })).toHaveAttribute('aria-current', 'page')
    expect(within(toolNav()).getByRole('button', { name: 'Import dữ liệu' })).not.toHaveAttribute('aria-current')
  })

  test('chuyển công cụ rồi quay lại thì công cụ cũ còn nguyên trạng thái; công cụ chưa mở thì chưa mount', async () => {
    const mounted: string[] = []
    function Counter({ id }: { id: string }) {
      const [count, setCount] = useState(0)
      useEffect(() => {
        mounted.push(id)
      }, [id])
      return (
        <button type="button" onClick={() => setCount(count + 1)}>
          {`Đếm ${id}: ${count}`}
        </button>
      )
    }
    const tools: ToolDefinition[] = [
      { id: 'a', label: 'Công cụ A', description: 'A', Icon: NoIcon, Component: () => <Counter id="a" /> },
      { id: 'b', label: 'Công cụ B', description: 'B', Icon: NoIcon, Component: () => <Counter id="b" /> },
    ]
    const user = userEvent.setup()
    render(<AppShell tools={tools} />)
    expect(mounted).toEqual(['a'])

    await user.click(screen.getByRole('button', { name: 'Đếm a: 0' }))
    await user.click(within(toolNav()).getByRole('button', { name: 'Công cụ B' }))

    // Công cụ A bị ẩn (không còn trong cây truy cập) nhưng không bị unmount.
    expect(screen.queryByRole('button', { name: /Đếm a/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Đếm b: 0' })).toBeInTheDocument()

    await user.click(within(toolNav()).getByRole('button', { name: 'Công cụ A' }))

    expect(screen.getByRole('button', { name: 'Đếm a: 1' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Công cụ A' })).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(mounted).toEqual(['a', 'b'])
  })

  test('thả file ra ngoài vùng upload thì trình duyệt không tự mở file (rời khỏi app)', () => {
    render(<App />)

    const dragOver = fireWindowEvent('dragover', { types: ['Files'], dropEffect: 'copy' })
    const drop = fireWindowEvent('drop', { types: ['Files'] })

    expect(dragOver.defaultPrevented).toBe(true)
    expect((dragOver as Event & { dataTransfer: { dropEffect: string } }).dataTransfer.dropEffect).toBe('none')
    expect(drop.defaultPrevented).toBe(true)
  })
})

describe('thu gọn sidebar', () => {
  function toggle() {
    return within(screen.getByRole('complementary')).getByRole('button', { name: /thanh bên$/ })
  }

  test('mặc định mở: nút "Thu gọn thanh bên", aria-expanded="true", điều khiển chính sidebar', () => {
    render(<AppShell tools={twoTools} />)

    expect(toggle()).toHaveAccessibleName('Thu gọn thanh bên')
    expect(toggle()).toHaveAttribute('aria-expanded', 'true')
    expect(toggle()).toHaveAttribute('aria-controls', screen.getByRole('complementary').id)
  })

  test('bấm thì thu gọn: nút đổi tên, công cụ vẫn đọc đúng tên và vẫn đánh dấu đang mở', async () => {
    const user = userEvent.setup()
    render(<AppShell tools={twoTools} />)

    await user.click(toggle())

    expect(toggle()).toHaveAccessibleName('Mở rộng thanh bên')
    expect(toggle()).toHaveAttribute('aria-expanded', 'false')
    expect(toggle()).toHaveFocus()
    const current = within(toolNav()).getByRole('button', { name: 'Import dữ liệu' })
    expect(current).toHaveAttribute('aria-current', 'page')
    // Di chuột vào icon thì hiện tên công cụ.
    expect(current).toHaveAttribute('title', 'Import dữ liệu')
  })

  test('đang thu gọn vẫn chuyển được công cụ', async () => {
    const user = userEvent.setup()
    render(<AppShell tools={twoTools} />)
    await user.click(toggle())

    await user.click(within(toolNav()).getByRole('button', { name: 'Báo cáo' }))

    expect(screen.getByRole('heading', { level: 1, name: 'Báo cáo' })).toBeInTheDocument()
    expect(screen.getByText('Nội dung báo cáo')).toBeInTheDocument()
  })

  test('bấm lần nữa thì mở lại', async () => {
    const user = userEvent.setup()
    render(<AppShell tools={twoTools} />)

    await user.click(toggle())
    await user.click(toggle())

    expect(toggle()).toHaveAccessibleName('Thu gọn thanh bên')
    expect(toggle()).toHaveAttribute('aria-expanded', 'true')
  })

  test('nhớ trạng thái: thu gọn rồi tải lại thì vẫn thu gọn', async () => {
    const user = userEvent.setup()
    const first = render(<AppShell tools={twoTools} />)
    await user.click(toggle())
    first.unmount()

    render(<AppShell tools={twoTools} />)

    expect(toggle()).toHaveAttribute('aria-expanded', 'false')
  })

  test('trình duyệt chặn localStorage: mặc định mở, bật/tắt vẫn dùng được', async () => {
    const blocked = () => {
      throw new DOMException('blocked', 'SecurityError')
    }
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(blocked)
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(blocked)
    const user = userEvent.setup()
    render(<AppShell tools={twoTools} />)

    expect(toggle()).toHaveAttribute('aria-expanded', 'true')
    await user.click(toggle())
    expect(toggle()).toHaveAttribute('aria-expanded', 'false')

    vi.restoreAllMocks()
  })
})
