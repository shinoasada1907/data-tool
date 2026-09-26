/// <reference types="vitest/config" />
import { rmSync } from 'node:fs'
import { resolve } from 'node:path'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type Plugin } from 'vite'

/**
 * `public/mockServiceWorker.js` chỉ phục vụ `pnpm dev:mock`: gỡ khỏi bản build, để origin chạy thật không phục vụ một
 * service worker giả mạo mọi request `/api` (review FE-F11).
 */
function dropMockServiceWorker(): Plugin {
  let outDir = 'dist'
  return {
    name: 'drop-mock-service-worker',
    apply: 'build',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir)
    },
    closeBundle() {
      rmSync(resolve(outDir, 'mockServiceWorker.js'), { force: true })
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Đọc .env cạnh file config (không phụ thuộc thư mục đang đứng khi chạy lệnh). Prefix rỗng để đọc cả
  // biến không có VITE_; API_PROXY_TARGET chỉ dùng ở đây, không lộ ra client.
  const env = loadEnv(mode, import.meta.dirname, '')

  return {
    plugins: [react(), dropMockServiceWorker()],
    server: {
      proxy: {
        '/api': {
          target: env.API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['src/test/setup.ts'],
      // Test đi hết wizard (upload → … → kết quả) mất 1,5–2,5 giây khi máy rảnh; mức 5 giây mặc định làm chúng trượt
      // khi máy bận (đã gặp lúc chạy song song với phiên BE), dù code không sai.
      testTimeout: 15_000,
    },
  }
})
