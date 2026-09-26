import { delay, http, HttpResponse } from 'msw'
import { describe, expect, onTestFinished, test, vi } from 'vitest'
import { problemFixture } from '../mocks/fixtures'
import { server } from '../mocks/node'
import { captureDownloads } from '../test/downloads'
import { download, DOWNLOAD_TIMEOUT_MS, saveBlob } from './download'

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

  test('chờ tới 5 phút (BE stream file lớn), không dừng ở 30 giây', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    onTestFinished(() => {
      vi.useRealTimers()
    })
    server.use(
      http.get(URL_PATH, async () => {
        await delay('infinite')
        return HttpResponse.json([])
      }),
    )
    let failure: unknown = null
    const settled = download(URL_PATH).catch((error: unknown) => {
      failure = error
    })

    await vi.advanceTimersByTimeAsync(31_000)
    expect(failure).toBeNull()
    await vi.advanceTimersByTimeAsync(DOWNLOAD_TIMEOUT_MS)
    await settled
    expect(failure).toMatchObject({ kind: 'timeout' })
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
