/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Đọc .env cạnh file config (không phụ thuộc thư mục đang đứng khi chạy lệnh). Prefix rỗng để đọc cả
  // biến không có VITE_; API_PROXY_TARGET chỉ dùng ở đây, không lộ ra client.
  const env = loadEnv(mode, import.meta.dirname, '')

  return {
    plugins: [react()],
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
    },
  }
})
