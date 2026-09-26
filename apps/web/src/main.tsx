import '@fontsource/bricolage-grotesque/700.css'
import '@fontsource/bricolage-grotesque/800.css'
import '@fontsource/be-vietnam-pro/400.css'
import '@fontsource/be-vietnam-pro/500.css'
import '@fontsource/be-vietnam-pro/600.css'
import '@fontsource/be-vietnam-pro/700.css'
import '@fontsource/jetbrains-mono/400.css'
import '@fontsource/jetbrains-mono/500.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

/**
 * Chế độ `pnpm dev:mock` (design D15): bật MSW worker làm BE giả trước khi render. Chỉ bật khi chạy dev server ở mode
 * `mock`. `import.meta.env.DEV` là hằng số `false` lúc build, nên mọi bản build (kể cả `vite build --mode mock`) bỏ hẳn
 * nhánh này, và không biến env nào đặt nhầm bật được BE giả trên bản deploy (review FE-F11).
 */
async function startMockBackend() {
  if (!import.meta.env.DEV || import.meta.env.MODE !== 'mock') return
  const { worker } = await import('./mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass' })
}

startMockBackend()
  .catch((error: unknown) => {
    // Không đăng ký được service worker (origin không an toàn khi mở qua IP LAN, trình duyệt ẩn danh…): vẫn hiện app,
    // và nói rõ vì sao mọi request /api sẽ lỗi, thay vì để trang trắng.
    console.error('[dev:mock] Không khởi động được BE giả; request /api sẽ đi tới proxy như `pnpm dev`.', error)
  })
  .finally(() => {
    createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <App />
      </StrictMode>,
    )
  })
