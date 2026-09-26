import { useEffect, useId, useState } from 'react'
import { messages } from '../shared/messages'
import { ArrowRightIcon } from '../shared/ui/icons'
import styles from './AppShell.module.css'
import type { ToolDefinition } from './tools'
import { usePreventFileDrop } from './usePreventFileDrop'

/** Khung dashboard: sidebar liệt kê công cụ, vùng nội dung là trang của công cụ đang mở (spec app-shell). */
export function AppShell({ tools }: { tools: readonly ToolDefinition[] }) {
  const [activeId, setActiveId] = useState(tools[0]?.id)
  const active = tools.find((tool) => tool.id === activeId) ?? tools[0]
  const navHeadingId = useId()

  usePreventFileDrop()

  // Tên app nằm ở một chỗ (design D2): sidebar và tiêu đề tab cùng lấy từ messages.appName.
  useEffect(() => {
    document.title = messages.appName
  }, [])

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <div className={styles.brand}>
          <span className={styles.mark} aria-hidden="true" />
          <span className={styles.appName}>{messages.appName}</span>
        </div>

        <nav aria-labelledby={navHeadingId} className={styles.nav}>
          <p id={navHeadingId} className={styles.navHeading}>
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
                    onClick={() => setActiveId(tool.id)}
                  >
                    <span className={styles.toolIndex} aria-hidden="true">
                      {String(index + 1).padStart(2, '0')}
                    </span>
                    <span className={styles.toolLabel}>{tool.label}</span>
                    {current && <ArrowRightIcon />}
                  </button>
                </li>
              )
            })}
          </ul>
        </nav>

        <p className={styles.version}>{messages.appVersion}</p>
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
