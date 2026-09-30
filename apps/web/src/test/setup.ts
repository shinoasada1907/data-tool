import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from '../mocks/node'

// Request nào không có handler thì test fail, để không có lệnh gọi API "lọt" ngoài ý muốn.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))

afterEach(() => {
  cleanup()
  server.resetHandlers()
  // Trạng thái giao diện lưu trong trình duyệt (ví dụ sidebar thu gọn) không được rò sang test sau.
  localStorage.clear()
})

afterAll(() => server.close())
