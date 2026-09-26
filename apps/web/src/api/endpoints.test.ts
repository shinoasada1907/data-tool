import { delay, http, HttpResponse } from 'msw'
import { describe, expect, onTestFinished, test, vi } from 'vitest'
import {
  configUpdateFixture,
  importSessionFixture,
  csvPreviewFixture,
  pipelineResultFixture,
  pipelineSummaryFixture,
  problemFixture,
} from '../mocks/fixtures'
import { server } from '../mocks/node'
import { DEFAULT_TIMEOUT_MS } from './client'
import {
  getPreview,
  getResult,
  getSessionStatus,
  postProcess,
  PROCESS_TIMEOUT_MS,
  putMapping,
  putSchema,
  putTransformations,
  putValidations,
} from './endpoints'

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

describe('postProcess', () => {
  test('gửi POST /api/import-sessions/{id}/process không có body; 200 trả summary', async () => {
    let received: { method: string; path: string; body: string } | null = null
    server.use(
      http.post('/api/import-sessions/:id/process', async ({ request }) => {
        received = { method: request.method, path: new URL(request.url).pathname, body: await request.text() }
        return HttpResponse.json(pipelineSummaryFixture())
      }),
    )

    await expect(postProcess('s-1')).resolves.toEqual(pipelineSummaryFixture())
    expect(received).toEqual({ method: 'POST', path: '/api/import-sessions/s-1/process', body: '' })
  })

  test('body 200 thiếu số đếm thì báo INVALID_RESPONSE', async () => {
    server.use(http.post('/api/import-sessions/:id/process', () => HttpResponse.json({ sessionId: 's-1' })))

    await expect(postProcess('s-1')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
})

describe('getResult', () => {
  test('gửi view, page, size=50; bộ lọc chỉ gửi khi có', async () => {
    const urls: URL[] = []
    server.use(
      http.get('/api/import-sessions/:id/result', ({ request }) => {
        urls.push(new URL(request.url))
        return HttpResponse.json(pipelineResultFixture())
      }),
    )

    await getResult('s-1', { view: 'invalid', page: 2, field: null, code: null })
    await getResult('s-1', { view: 'invalid', page: 0, field: 'Họ tên', code: 'VALIDATION_EMAIL' })

    expect(urls[0].pathname).toBe('/api/import-sessions/s-1/result')
    expect(Object.fromEntries(urls[0].searchParams)).toEqual({ view: 'invalid', page: '2', size: '50' })
    expect(Object.fromEntries(urls[1].searchParams)).toEqual({
      view: 'invalid',
      page: '0',
      size: '50',
      field: 'Họ tên',
      code: 'VALIDATION_EMAIL',
    })
  })

  test('body 200 sai dạng (dòng thiếu errors) thì báo INVALID_RESPONSE', async () => {
    const broken = { ...pipelineResultFixture(), rows: [{ rowNumber: 2, valid: true, values: {} }] }
    server.use(http.get('/api/import-sessions/:id/result', () => HttpResponse.json(broken)))

    await expect(getResult('s-1', { view: 'valid', page: 0, field: null, code: null })).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
    })
  })
})

describe('getSessionStatus', () => {
  test('GET /api/import-sessions/{id} trả status của session', async () => {
    server.use(
      http.get('/api/import-sessions/:id', ({ params }) =>
        HttpResponse.json(importSessionFixture({ id: String(params.id), status: 'FAILED' })),
      ),
    )

    await expect(getSessionStatus('s-1')).resolves.toBe('FAILED')
  })

  test('body 200 thiếu status thì báo INVALID_RESPONSE', async () => {
    server.use(http.get('/api/import-sessions/:id', () => HttpResponse.json({ id: 's-1' })))

    await expect(getSessionStatus('s-1')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
})

describe('thời gian chờ của process', () => {
  test('process chờ tới 5 phút mới báo hết giờ (BE chạy đồng bộ), không dừng ở 30 giây như request khác', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    onTestFinished(() => {
      vi.useRealTimers()
    })
    server.use(
      http.post('/api/import-sessions/:id/process', async () => {
        await delay('infinite')
        return HttpResponse.json(pipelineSummaryFixture())
      }),
    )
    let failure: unknown = null
    const settled = postProcess('s-1').catch((error: unknown) => {
      failure = error
    })

    await vi.advanceTimersByTimeAsync(DEFAULT_TIMEOUT_MS + 1_000)
    expect(failure).toBeNull()

    await vi.advanceTimersByTimeAsync(PROCESS_TIMEOUT_MS)
    await settled
    expect(failure).toMatchObject({ kind: 'timeout' })
  })
})

describe('số trong kết quả giữ đúng chữ số BE gửi', () => {
  test('số quá độ chính xác của JS và số có 0 ở cuối thành chuỗi nguyên văn; số JS giữ đúng vẫn là number', async () => {
    const dto = pipelineResultFixture()
    const body = JSON.stringify({
      ...dto,
      rows: [{ ...dto.rows[0], values: { acct: '__ACCT__', price: '__PRICE__', age: 30, big: '__BIG__' } }],
    })
      .replace('"__ACCT__"', '12345678901234567890')
      .replace('"__PRICE__"', '10.50')
      .replace('"__BIG__"', '1E+21')
    server.use(
      http.get('/api/import-sessions/:id/result', () =>
        new HttpResponse(body, { headers: { 'Content-Type': 'application/json' } }),
      ),
    )

    const result = await getResult('s-1', { view: 'invalid', page: 0, field: null, code: null })

    expect(result.rows[0].values).toEqual({ acct: '12345678901234567890', price: '10.50', age: 30, big: '1E+21' })
    // Số đếm và số trang vẫn là number, để validator và phân trang dùng được.
    expect(result.summary.total).toBe(120)
    expect(result.page.number).toBe(0)
  })
})
