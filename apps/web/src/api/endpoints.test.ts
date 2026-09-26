import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { csvPreviewFixture } from '../mocks/fixtures'
import { server } from '../mocks/node'
import { getPreview } from './endpoints'

describe('getPreview', () => {
  test('gọi GET /api/import-sessions/{id}/preview?limit=50 và trả về SourcePreviewDto', async () => {
    const preview = csvPreviewFixture({ sessionId: 's-1' })
    let url: URL | null = null
    server.use(
      http.get('/api/import-sessions/:id/preview', ({ request }) => {
        url = new URL(request.url)
        return HttpResponse.json(preview)
      }),
    )

    await expect(getPreview('s-1')).resolves.toEqual(preview)
    expect(url!.pathname).toBe('/api/import-sessions/s-1/preview')
    expect(url!.searchParams.get('limit')).toBe('50')
  })

  test.each([
    ['thiếu columns', { ...csvPreviewFixture(), columns: undefined }],
    ['rows không phải mảng', { ...csvPreviewFixture(), rows: 'rác' }],
    ['một dòng thiếu values', { ...csvPreviewFixture(), rows: [{ rowNumber: 2 }] }],
    // React không vẽ boolean (ô trông trống mà không có placeholder) và vỡ trang với object.
    ['ô là boolean', { ...csvPreviewFixture(), rows: [{ rowNumber: 2, values: [true, 'An', '1', 'x'] }] }],
    ['ô là object', { ...csvPreviewFixture(), rows: [{ rowNumber: 2, values: [{}, 'An', '1', 'x'] }] }],
    ['thiếu totalRows', { ...csvPreviewFixture(), totalRows: null }],
  ])('body 200 %s thì báo INVALID_RESPONSE', async (_, body) => {
    server.use(http.get('/api/import-sessions/:id/preview', () => HttpResponse.json(body)))

    await expect(getPreview('s-1')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
})
