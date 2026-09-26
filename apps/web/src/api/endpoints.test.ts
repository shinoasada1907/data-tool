import { http, HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import { configUpdateFixture, csvPreviewFixture, problemFixture } from '../mocks/fixtures'
import { server } from '../mocks/node'
import { getPreview, putMapping, putSchema, putTransformations, putValidations } from './endpoints'

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

describe('putSchema', () => {
  const schema = { fields: [{ name: 'email', type: 'email' as const, required: true, order: 0 }] }

  test('gửi PUT /api/import-sessions/{id}/schema với body là TargetSchemaDto; 200 {session, warnings} thì resolve', async () => {
    let received: { path: string; body: unknown } | null = null
    server.use(
      http.put('/api/import-sessions/:id/schema', async ({ request }) => {
        received = { path: new URL(request.url).pathname, body: await request.json() }
        return HttpResponse.json(configUpdateFixture({ warnings: [{ field: 'x', code: 'CONFIG_PRUNED', message: 'm' }] }))
      }),
    )

    await expect(putSchema('s-1', schema)).resolves.toBeUndefined()
    expect(received).toEqual({ path: '/api/import-sessions/s-1/schema', body: schema })
  })

  test('422 SCHEMA_INVALID thành ApiError có errors[] theo field', async () => {
    const problem = problemFixture(422, 'SCHEMA_INVALID', 'Schema is invalid.', {
      errors: [{ field: 'email', code: 'SCHEMA_INVALID', message: 'Duplicate field name.' }],
    })
    server.use(
      http.put('/api/import-sessions/:id/schema', () =>
        HttpResponse.json(problem, { status: 422, headers: { 'Content-Type': 'application/problem+json' } }),
      ),
    )

    await expect(putSchema('s-1', schema)).rejects.toMatchObject({
      status: 422,
      code: 'SCHEMA_INVALID',
      fieldErrors: [{ field: 'email', code: 'SCHEMA_INVALID', message: 'Duplicate field name.' }],
    })
  })
})

describe('putMapping', () => {
  const mapping = {
    mappings: [
      { targetField: 'email', mappingType: 'SOURCE_COLUMN' as const, sourceColumn: 'E-mail', constantValue: null },
    ],
  }

  test('gửi PUT /api/import-sessions/{id}/mapping với body là MappingConfigDto; 200 thì resolve', async () => {
    let received: { path: string; body: unknown } | null = null
    server.use(
      http.put('/api/import-sessions/:id/mapping', async ({ request }) => {
        received = { path: new URL(request.url).pathname, body: await request.json() }
        return HttpResponse.json(configUpdateFixture())
      }),
    )

    await expect(putMapping('s-1', mapping)).resolves.toBeUndefined()
    expect(received).toEqual({ path: '/api/import-sessions/s-1/mapping', body: mapping })
  })
})

describe('putTransformations và putValidations', () => {
  test.each([
    ['transformations', () => putTransformations('s-1', { transformations: [] }), { transformations: [] }],
    ['validations', () => putValidations('s-1', { validations: [] }), { validations: [] }],
  ] as const)('PUT /api/import-sessions/{id}/%s với đúng body; 200 thì resolve', async (section, call, body) => {
    let received: { path: string; body: unknown } | null = null
    server.use(
      http.put(`/api/import-sessions/:id/${section}`, async ({ request }) => {
        received = { path: new URL(request.url).pathname, body: await request.json() }
        return HttpResponse.json(configUpdateFixture())
      }),
    )

    await expect(call()).resolves.toBeUndefined()
    expect(received).toEqual({ path: `/api/import-sessions/s-1/${section}`, body })
  })
})
