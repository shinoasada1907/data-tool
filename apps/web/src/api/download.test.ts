import { delay, http, HttpResponse } from 'msw'
import { describe, expect, onTestFinished, test, vi } from 'vitest'
import { problemFixture } from '../mocks/fixtures'
import { server } from '../mocks/node'
import { captureDownloads } from '../test/downloads'
import { download, DOWNLOAD_IDLE_TIMEOUT_MS, saveBlob } from './download'

const URL_PATH = '/api/files/thing'

describe('download', () => {
  test('2xx: trả blob đúng nội dung và tên file lấy từ Content-Disposition', async () => {
    server.use(
      http.get(URL_PATH, () =>
        new HttpResponse('﻿a,b\r\n1,2\r\n', {
          headers: {
            'Content-Type': 'text/csv;charset=UTF-8',
            'Content-Disposition': "attachment; filename*=UTF-8''kh%C3%A1ch-valid.csv",
          },
        }),
      ),
    )

    const result = await download(URL_PATH)

    expect(result.fileName).toBe('khách-valid.csv')
    expect(new Uint8Array(await result.blob.arrayBuffer()).slice(0, 3)).toEqual(new Uint8Array([0xef, 0xbb, 0xbf]))
    expect(await result.blob.text()).toContain('a,b\r\n1,2')
  })

  test('POST: gửi body JSON và nhận file như GET', async () => {
    let received: unknown
    server.use(
      http.post(URL_PATH, async ({ request }) => {
        received = await request.json()
        expect(request.headers.get('Content-Type')).toBe('application/json')
        return new HttpResponse('x', { headers: { 'Content-Disposition': 'attachment; filename="a.csv"' } })
      }),
    )

    const result = await download(URL_PATH, { method: 'POST', body: { content: 'ERRORS' } })

    expect(received).toEqual({ content: 'ERRORS' })
    expect(result.fileName).toBe('a.csv')
  })

  test('không có Content-Disposition thì fileName là null (bên gọi dùng tên dự phòng)', async () => {
    server.use(http.get(URL_PATH, () => HttpResponse.json([])))

    await expect(download(URL_PATH)).resolves.toMatchObject({ fileName: null })
  })

  test('lỗi HTTP: ném ApiError từ ProblemDetail, không trả blob', async () => {
    server.use(
      http.get(URL_PATH, () =>
        HttpResponse.json(problemFixture(500, 'EXPORT_FAILED', 'Export could not be created.'), {
          status: 500,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    )

    await expect(download(URL_PATH)).rejects.toMatchObject({
      kind: 'http',
      status: 500,
      code: 'EXPORT_FAILED',
      detail: 'Export could not be created.',
    })
  })

  test('mất kết nối (kể cả đứt giữa chừng khi BE đang stream) thì ApiError kind=network', async () => {
    server.use(http.get(URL_PATH, () => HttpResponse.error()))

    await expect(download(URL_PATH)).rejects.toMatchObject({ kind: 'network' })
  })

  // BE đã gửi status 200 và một phần file rồi mới lỗi: không đổi được status nữa nên BE cắt kết nối (be-f10 F10-D1).
  // `response.ok` là true nhưng đọc body bị reject; đó là tải thất bại, không phải file hợp lệ.
  test('kết nối đứt giữa lúc đang nhận file (200 nhưng body bị cắt): ApiError kind=network, không trả blob', async () => {
    server.use(
      http.get(URL_PATH, () => {
        const body = new ReadableStream<Uint8Array>({
          start(controller) {
            controller.enqueue(new TextEncoder().encode('﻿a,b\r\n1,'))
            controller.error(new Error('connection reset'))
          },
        })
        return new HttpResponse(body, {
          headers: {
            'Content-Type': 'text/csv;charset=UTF-8',
            'Content-Disposition': "attachment; filename*=UTF-8''x-valid.csv",
          },
        })
      }),
    )

    await expect(download(URL_PATH)).rejects.toMatchObject({ kind: 'network' })
  })

  describe('thời gian im lặng', () => {
    // Chunk của body được đẩy ra theo đồng hồ giả, để mô phỏng mạng chậm hoặc BE ngừng gửi giữa chừng.
    function slowBody(chunks: { afterMs: number; text: string }[], { stallAtEnd = false } = {}) {
      return new ReadableStream<Uint8Array>({
        start(controller) {
          let at = 0
          for (const chunk of chunks) {
            at += chunk.afterMs
            setTimeout(() => controller.enqueue(new TextEncoder().encode(chunk.text)), at)
          }
          if (!stallAtEnd) setTimeout(() => controller.close(), at)
        },
      })
    }

    function useFakeClock() {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
      onTestFinished(() => {
        vi.useRealTimers()
      })
    }

    async function settle<T>(promise: Promise<T>) {
      let outcome: { value?: T; error?: unknown } | null = null
      promise.then(
        (value) => (outcome = { value }),
        (error: unknown) => (outcome = { error }),
      )
      return () => outcome
    }

    test('chưa có header sau DOWNLOAD_IDLE_TIMEOUT_MS thì hết giờ', async () => {
      useFakeClock()
      server.use(
        http.get(URL_PATH, async () => {
          await delay('infinite')
          return HttpResponse.json([])
        }),
      )
      const outcome = await settle(download(URL_PATH))

      await vi.advanceTimersByTimeAsync(DOWNLOAD_IDLE_TIMEOUT_MS - 1_000)
      expect(outcome()).toBeNull()
      await vi.advanceTimersByTimeAsync(2_000)
      expect(outcome()?.error).toMatchObject({ kind: 'timeout' })
    })

    // BE bỏ giới hạn tổng thời gian (be-f10, F10-D8 bị gạch): file lớn trên mạng chậm có thể mất nhiều phút.
    test('file đến chậm nhưng đều (tổng lâu hơn nhiều lần mức im lặng) vẫn tải xong đủ nội dung', async () => {
      useFakeClock()
      const gap = DOWNLOAD_IDLE_TIMEOUT_MS - 5_000
      const chunks = Array.from({ length: 12 }, (_, i) => ({ afterMs: gap, text: `dong ${i}\r\n` }))
      server.use(http.get(URL_PATH, () => new HttpResponse(slowBody(chunks), { headers: { 'Content-Type': 'text/csv' } })))
      const outcome = await settle(download(URL_PATH))

      await vi.advanceTimersByTimeAsync(gap * chunks.length + 1_000)

      const result = outcome()
      expect(result?.error).toBeUndefined()
      expect(await result?.value?.blob.text()).toBe(chunks.map((chunk) => chunk.text).join(''))
    })

    test('huỷ (rời bước) khi đang nhận body: báo huỷ, không bao giờ trả file bị cắt cụt', async () => {
      useFakeClock()
      server.use(
        http.get(URL_PATH, () =>
          new HttpResponse(slowBody([{ afterMs: 10, text: 'a,b\\r\\n' }], { stallAtEnd: true }), {
            headers: { 'Content-Type': 'text/csv' },
          }),
        ),
      )
      const controller = new AbortController()
      const outcome = await settle(download(URL_PATH, { signal: controller.signal }))
      await vi.advanceTimersByTimeAsync(100)

      controller.abort()
      await vi.advanceTimersByTimeAsync(0)

      expect(outcome()?.error).toMatchObject({ kind: 'aborted' })
    })

    // Qua proxy của Vite, BE cắt kết nối giữa chừng thì request treo chứ không báo lỗi (review FE-F10).
    test('đang nhận file mà ngừng hẳn (không thêm byte nào) thì hết giờ sau DOWNLOAD_IDLE_TIMEOUT_MS', async () => {
      useFakeClock()
      server.use(
        http.get(URL_PATH, () =>
          new HttpResponse(slowBody([{ afterMs: 10, text: 'a,b\r\n' }], { stallAtEnd: true }), {
            headers: { 'Content-Type': 'text/csv' },
          }),
        ),
      )
      const outcome = await settle(download(URL_PATH))

      await vi.advanceTimersByTimeAsync(DOWNLOAD_IDLE_TIMEOUT_MS - 1_000)
      expect(outcome()).toBeNull()
      await vi.advanceTimersByTimeAsync(2_000)
      expect(outcome()?.error).toMatchObject({ kind: 'timeout' })
    })
  })
})

describe('saveBlob', () => {
  test('bấm một thẻ <a download> tạm (có trong document) với đúng tên và đúng blob, rồi gỡ thẻ đó', () => {
    const saved = captureDownloads()
    const blob = new Blob(['x'])

    saveBlob(blob, 'khách-valid.csv')

    expect(saved).toEqual([{ fileName: 'khách-valid.csv', blob }])
    expect(document.querySelector('a[download]')).toBeNull()
  })

  // Firefox huỷ lượt tải nếu object URL bị thu hồi ngay trong lúc click.
  test('thu hồi object URL sau một nhịp, không phải ngay khi click', () => {
    vi.useFakeTimers({ toFake: ['setTimeout'] })
    onTestFinished(() => {
      vi.useRealTimers()
    })
    captureDownloads()
    const revoke = vi.spyOn(URL, 'revokeObjectURL')
    let inDocumentWhenClicked = false
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      inDocumentWhenClicked = document.body.contains(this)
    })

    saveBlob(new Blob(['x']), 'a.csv')

    expect(inDocumentWhenClicked).toBe(true)
    expect(revoke).not.toHaveBeenCalled()
    vi.runAllTimers()
    expect(revoke).toHaveBeenCalledWith('blob:test/1')
  })
})
