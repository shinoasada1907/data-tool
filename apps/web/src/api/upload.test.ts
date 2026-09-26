import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { importSessionFixture, problemFixture } from '../mocks/fixtures'
import { FakeXhr } from '../test/fakeXhr'
import { ApiError } from './apiError'
import { uploadSourceFile } from './upload'

const file = new File(['id,name\n1,An\n'], 'khach-hang.csv', { type: 'application/vnd.ms-excel' })

beforeEach(() => {
  FakeXhr.instances = []
  vi.stubGlobal('XMLHttpRequest', FakeXhr)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('uploadSourceFile', () => {
  test('gửi POST /api/import-sessions dạng multipart, part "file" là đúng file đã chọn', () => {
    void uploadSourceFile(file)
    const xhr = FakeXhr.last()

    expect(xhr.method).toBe('POST')
    expect(xhr.url).toBe('/api/import-sessions')
    expect(xhr.requestHeaders.Accept).toBe('application/json, application/problem+json')
    expect(xhr.body).toBeInstanceOf(FormData)
    expect((xhr.body as FormData).get('file')).toBe(file)
  })

  test('201 thì trả về ImportSessionDto', async () => {
    const session = importSessionFixture()
    const promise = uploadSourceFile(file)

    FakeXhr.last().respond(201, JSON.stringify(session), { 'Content-Type': 'application/json' })

    await expect(promise).resolves.toEqual(session)
  })

  test('lỗi của BE thành ApiError mang đúng status, code và detail', async () => {
    const promise = uploadSourceFile(file)

    FakeXhr.last().respond(
      415,
      JSON.stringify(problemFixture(415, 'FILE_UNSUPPORTED', 'Only .csv and .xlsx files are supported.')),
      { 'Content-Type': 'application/problem+json' },
    )

    await expect(promise).rejects.toMatchObject({
      kind: 'http',
      status: 415,
      code: 'FILE_UNSUPPORTED',
      detail: 'Only .csv and .xlsx files are supported.',
    })
  })

  test.each([
    ['body không phải JSON', 'không phải json'],
    ['JSON thiếu id của session', JSON.stringify({ status: 'CONFIGURING' })],
  ])('2xx mà %s thì báo INVALID_RESPONSE, không trả dữ liệu rác', async (_case, body) => {
    const promise = uploadSourceFile(file)

    FakeXhr.last().respond(201, body, { 'Content-Type': 'application/json' })

    await expect(promise).rejects.toMatchObject({ kind: 'http', status: 201, code: 'INVALID_RESPONSE' })
  })

  test('request đã xong thì huỷ signal sau đó không đụng tới XHR nữa (listener đã được gỡ)', async () => {
    const controller = new AbortController()
    const promise = uploadSourceFile(file, { signal: controller.signal })
    const xhr = FakeXhr.last()
    xhr.respond(201, JSON.stringify(importSessionFixture()), { 'Content-Type': 'application/json' })
    await promise

    controller.abort()

    expect(xhr.aborted).toBe(false)
  })

  test('mất kết nối thì ApiError kind "network"', async () => {
    const promise = uploadSourceFile(file)

    FakeXhr.last().failNetwork()

    await expect(promise).rejects.toMatchObject({ kind: 'network' })
  })

  test('báo tiến độ theo phần trăm, bỏ qua sự kiện không tính được tổng', () => {
    const onProgress = vi.fn()
    void uploadSourceFile(file, { onProgress })
    const xhr = FakeXhr.last()

    xhr.progress(0, 0)
    xhr.progress(512, 1024)
    xhr.progress(1024, 1024)

    expect(onProgress.mock.calls).toEqual([[50], [100]])
  })

  test('huỷ qua AbortSignal thì huỷ request và trả ApiError kind "aborted"', async () => {
    const controller = new AbortController()
    const promise = uploadSourceFile(file, { signal: controller.signal })

    controller.abort()

    expect(FakeXhr.last().aborted).toBe(true)
    await expect(promise).rejects.toBeInstanceOf(ApiError)
    await expect(promise).rejects.toMatchObject({ kind: 'aborted' })
  })

  test('signal đã bị huỷ từ trước thì không gửi request nào', async () => {
    await expect(uploadSourceFile(file, { signal: AbortSignal.abort() })).rejects.toMatchObject({ kind: 'aborted' })
    expect(FakeXhr.instances).toHaveLength(0)
  })
})
