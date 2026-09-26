import { describe, expect, test } from 'vitest'
import { fallbackFileName } from './fileNames'

// Cùng luật với BE (be-f10 F10-D6), để tên dự phòng giống tên BE đặt.
describe('fallbackFileName', () => {
  test('bỏ đuôi cuối cùng của tên file gốc rồi nối hậu tố', () => {
    expect(fallbackFileName('customers.xlsx', '-valid.json')).toBe('customers-valid.json')
    expect(fallbackFileName('report.final.xlsx', '-errors.csv')).toBe('report.final-errors.csv')
    expect(fallbackFileName('khách hàng.csv', '-valid.csv')).toBe('khách hàng-valid.csv')
  })

  test('tên không có đuôi thì giữ nguyên', () => {
    expect(fallbackFileName('customers', '-valid.csv')).toBe('customers-valid.csv')
  })

  test('thay ", \\, / và ký tự điều khiển bằng "_"', () => {
    expect(fallbackFileName('a"b\\c/d\u0007e.csv', '-valid.csv')).toBe('a_b_c_d_e-valid.csv')
  })

  test('thay cả ký tự điều khiển C1 và ký tự định dạng vô hình (Cf), như \\p{Cc}\\p{Cf} của BE', () => {
    expect(fallbackFileName('a\u0085b.csv', '-valid.csv')).toBe('a_b-valid.csv')
    // U+202E đảo chiều chữ: "fdp.exe" có thể hiện thành "exe.pdf".
    expect(fallbackFileName('bao\u202Ecao.csv', '-valid.csv')).toBe('bao_cao-valid.csv')
    expect(fallbackFileName('\uFEFFkhach\u00ADhang.csv', '-valid.csv')).toBe('_khach_hang-valid.csv')
  })

  test('phần tên rỗng hoặc chỉ có khoảng trắng thì dùng "export"', () => {
    expect(fallbackFileName('.csv', '-valid.csv')).toBe('export-valid.csv')
    expect(fallbackFileName('', '-errors.csv')).toBe('export-errors.csv')
    expect(fallbackFileName('   .csv', '-valid.csv')).toBe('export-valid.csv')
  })
})
