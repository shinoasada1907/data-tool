import { http, HttpResponse, type RequestHandler } from 'msw'
import { csvPreviewFixture } from './fixtures'

// Handler mặc định dùng chung cho test và cho chế độ dev:mock.
// Mỗi test tự khai báo handler cho tình huống của nó bằng server.use(...).
export const handlers: RequestHandler[] = [
  // Upload xong là bước Xem trước gọi preview ngay; test không quan tâm tới preview vẫn nhận được bảng hợp lệ.
  http.get<{ id: string }>('/api/import-sessions/:id/preview', ({ params }) =>
    HttpResponse.json(csvPreviewFixture({ sessionId: params.id })),
  ),
]
