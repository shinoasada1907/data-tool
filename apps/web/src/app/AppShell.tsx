import { useEffect, useId, useState } from 'react'
import { messages } from '../shared/messages'
import { ArrowRightIcon, PanelLeftIcon } from '../shared/ui/icons'
import styles from './AppShell.module.css'
import type { ToolDefinition } from './tools'
import { usePreventFileDrop } from './usePreventFileDrop'

const COLLAPSED_KEY = 'universal-importer.sidebar-collapsed'

/** Trình duyệt chặn `localStorage` (chế độ riêng tư, chính sách công ty…) thì coi như chưa lưu gì: sidebar mở. */
function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === 'true'
  } catch {
    return false
  }
}

function saveCollapsed(collapsed: boolean) {
  try {
    localStorage.setItem(COLLAPSED_KEY, String(collapsed))
  } catch {
    // Không lưu được thì lần sau sidebar mở như mặc định; không có gì để báo cho user.
  }
}

/** Khung dashboard: sidebar liệt kê công cụ, vùng nội dung là trang của công cụ đang mở (spec app-shell). */
export function AppShell({ tools }: { tools: readonly ToolDefinition[] }) {
  const [activeId, setActiveId] = useState(tools[0]?.id)
  const active = tools.find((tool) => tool.id === activeId) ?? tools[0]
  const [collapsed, setCollapsed] = useState(readCollapsed)
  const navHeadingId = useId()
  const sidebarId = useId()
  const toggleLabel = collapsed ? messages.shell.expand : messages.shell.collapse

  function toggleSidebar() {
    const next = !collapsed
    setCollapsed(next)
    saveCollapsed(next)
  }

  usePreventFileDrop()

  // Tên app nằm ở một chỗ (design D2): sidebar và tiêu đề tab cùng lấy từ messages.appName.
  useEffect(() => {
    document.title = messages.appName
  }, [])

  return (
    <div className={styles.shell} data-collapsed={collapsed || undefined}>
      <aside id={sidebarId} className={styles.sidebar}>
        <div className={styles.top}>
          <div className={styles.brand}>
            <span className={styles.mark} aria-hidden="true" />
            {/* Thu gọn thì chỉ ẩn khỏi màn hình: screen reader vẫn đọc tên app. */}
            <span className={collapsed ? 'sr-only' : styles.appName}>{messages.appName}</span>
          </div>
          <button
            type="button"
            className={styles.toggle}
            aria-expanded={!collapsed}
            aria-controls={sidebarId}
            aria-label={toggleLabel}
            title={toggleLabel}
            onClick={toggleSidebar}
          >
            <PanelLeftIcon />
          </button>
        </div>

        <nav aria-labelledby={navHeadingId} className={styles.nav}>
          <p id={navHeadingId} className={collapsed ? 'sr-only' : styles.navHeading}>
            {messages.shell.toolsHeading}
          </p>
          <ul className={styles.toolList}>
            {tools.map((tool, index) => {
              const current = tool.id === active?.id
              return (
                <li key={tool.id}>
                  <button
                    type="button"
                    className={styles.toolButton}
                    aria-current={current ? 'page' : undefined}
                    // Thu gọn chỉ còn icon: di chuột vào thì hiện tên.
                    title={collapsed ? tool.label : undefined}
                    onClick={() => setActiveId(tool.id)}
                  >
                    {collapsed ? (
                      <>
                        <tool.Icon />
                        <span className="sr-only">{tool.label}</span>
                      </>
                    ) : (
                      <>
                        <span className={styles.toolIndex} aria-hidden="true">
                          {String(index + 1).padStart(2, '0')}
                        </span>
                        <span className={styles.toolLabel}>{tool.label}</span>
                        {current && <ArrowRightIcon />}
                      </>
                    )}
                  </button>
                </li>
              )
            })}
          </ul>
        </nav>

        {!collapsed && <p className={styles.version}>{messages.appVersion}</p>}
      </aside>

      <main className={styles.content}>
        {active && (
          <div className={styles.page}>
            <header className={styles.pageHeader}>
              <h1>{active.label}</h1>
              <p>{active.description}</p>
            </header>
            <active.Component />
          </div>
        )}
      </main>
    </div>
  )
}
