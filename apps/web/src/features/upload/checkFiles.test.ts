import { describe, expect, test } from 'vitest'
import { MB, testFile as fileOf } from '../../test/files'
import { checkSelectedFiles } from './checkFiles'

describe('checkSelectedFiles', () => {
  test('nhận CSV dù trình duyệt báo MIME của Excel (Windows)', () => {
    const file = fileOf('khach-hang.csv', { type: 'application/vnd.ms-excel' })

    expect(checkSelectedFiles([file], 20)).toEqual({ ok: true, file, fileType: 'CSV' })
  })

  test('nhận XLSX, đuôi không phân biệt hoa thường', () => {
    const file = fileOf('BAO-CAO.XLSX')

    expect(checkSelectedFiles([file], 20)).toEqual({ ok: true, file, fileType: 'XLSX' })
  })

  test.each(['data.json', 'data.xls', 'data.csv.exe', 'README'])('từ chối %j vì sai đuôi', (name) => {
    expect(checkSelectedFiles([fileOf(name)], 20)).toEqual({ ok: false, reason: 'extension' })
  })

  test('nhận bảng đuôi file riêng của công cụ (toolbox nhận thêm JSON)', () => {
    const file = fileOf('data.JSON')
    const types = { csv: 'CSV', xlsx: 'XLSX', json: 'JSON' } as const

    expect(checkSelectedFiles([file], 20, types)).toEqual({ ok: true, file, fileType: 'JSON' })
  })

  test('từ chối khi chọn nhiều file', () => {
    expect(checkSelectedFiles([fileOf('a.csv'), fileOf('b.csv')], 20)).toEqual({ ok: false, reason: 'multiple' })
  })

  test('từ chối file 0 byte', () => {
    expect(checkSelectedFiles([fileOf('rong.csv', { size: 0 })], 20)).toEqual({ ok: false, reason: 'empty' })
  })

  test('file đúng bằng giới hạn thì nhận, lớn hơn 1 byte thì từ chối', () => {
    expect(checkSelectedFiles([fileOf('vua.csv', { size: 20 * MB })], 20)).toMatchObject({ ok: true })
    expect(checkSelectedFiles([fileOf('to.csv', { size: 20 * MB + 1 })], 20)).toEqual({
      ok: false,
      reason: 'tooLarge',
    })
  })

  test('không có file nào (hộp chọn bị đóng) thì không có gì để kiểm', () => {
    expect(checkSelectedFiles([], 20)).toBeNull()
  })
})
