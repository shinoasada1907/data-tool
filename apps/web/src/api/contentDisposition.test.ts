import { describe, expect, test } from 'vitest'
import { fileNameFromContentDisposition } from './contentDisposition'

describe('fileNameFromContentDisposition', () => {
  test('filename* theo RFC 5987 (UTF-8, percent-encoded) là tên tiếng Việt nguyên vẹn', () => {
    expect(fileNameFromContentDisposition("attachment; filename*=UTF-8''kh%C3%A1ch-valid.csv")).toBe('khách-valid.csv')
  })

  test('có cả filename lẫn filename* thì ưu tiên filename*, dù đứng sau', () => {
    const header = `attachment; filename="khach-valid.csv"; filename*=UTF-8''kh%C3%A1ch-valid.csv`
    expect(fileNameFromContentDisposition(header)).toBe('khách-valid.csv')
  })

  test('tên tham số và charset không phân biệt hoa thường; có thẻ ngôn ngữ vẫn đọc được', () => {
    expect(fileNameFromContentDisposition("attachment; FILENAME*=utf-8'vi'b%C3%A1o-c%C3%A1o.csv")).toBe('báo-cáo.csv')
  })

  test('filename trong ngoặc kép, có ký tự thoát và dấu chấm phẩy bên trong', () => {
    expect(fileNameFromContentDisposition('attachment; filename="bao \\"cao\\"; 2024.csv"')).toBe('bao "cao"; 2024.csv')
  })

  test('filename không có ngoặc kép', () => {
    expect(fileNameFromContentDisposition('attachment; filename=customers-valid.json')).toBe('customers-valid.json')
  })

  test('filename* hỏng (percent-encoding sai) thì dùng filename', () => {
    expect(fileNameFromContentDisposition(`attachment; filename*=UTF-8''bad%E0%A4; filename="ok.csv"`)).toBe('ok.csv')
  })

  test('không có header, không có tham số tên, hoặc tên rỗng thì null', () => {
    expect(fileNameFromContentDisposition(null)).toBeNull()
    expect(fileNameFromContentDisposition('attachment')).toBeNull()
    expect(fileNameFromContentDisposition('attachment; filename=""')).toBeNull()
  })

  test('loại ký tự đường dẫn và ký tự điều khiển khỏi tên (thay bằng "_", như BE)', () => {
    expect(fileNameFromContentDisposition("attachment; filename*=UTF-8''..%2F..%5Cetc%2Fpasswd")).toBe('.._.._etc_passwd')
    expect(fileNameFromContentDisposition('attachment; filename="a/b\\\\c.csv"')).toBe('a_b_c.csv')
    expect(fileNameFromContentDisposition("attachment; filename*=UTF-8''a%0Db.csv")).toBe('a_b.csv')
  })
})
