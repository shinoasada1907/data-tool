import type { RequestHandler } from 'msw'

// Handler mặc định dùng chung cho test và cho chế độ dev:mock.
// Mỗi test tự khai báo handler cho tình huống của nó bằng server.use(...).
export const handlers: RequestHandler[] = []
