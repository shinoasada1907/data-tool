import { formatNumber } from '../format'
import type { Delimiter, Encoding } from './datasetApi'

export const datasetMessages = {
  inputLabel: 'Chọn file CSV, XLSX hoặc JSON',
  dropZone: 'Kéo-thả file vào đây, hoặc bấm để chọn file',
  choose: 'Chọn file',
  hintFormats: 'Nhận CSV, XLSX hoặc JSON (mảng các object phẳng).',
  hintMaxSize: (mb: number) => `Tối đa ${mb} MB.`,
  retention: 'File và kết quả tự xoá sau 24 giờ không dùng tới.',
  uploading: (percent: number) => `Đang upload… ${percent}%`,
  progressLabel: 'Tiến độ upload',
  cancel: 'Huỷ',
  replace: 'Đổi file',
  remove: 'Xoá file',
  fileLabel: 'File đang dùng',
  rejected: {
    extension: 'Chỉ nhận file .csv, .xlsx hoặc .json',
    multiple: 'Chỉ chọn một file',
    empty: 'File rỗng (0 byte)',
    tooLarge: (mb: number) => `File vượt giới hạn ${mb} MB`,
  },
  /** Thông điệp riêng cho dataset; bản chung trong `errorCodeMessages` viết cho Importer (chỉ CSV/XLSX, UTF-8). */
  errorOverrides: {
    FILE_UNSUPPORTED: 'Máy chủ không nhận loại file này; chỉ hỗ trợ CSV, XLSX và JSON',
    FILE_PARSE_ERROR: 'Không đọc được file với cách đọc hiện tại; thử đổi bảng mã, dấu phân cách hoặc sheet',
    FILE_EMPTY: 'File không có dữ liệu',
  } as Readonly<Record<string, string>>,

  optionsLabel: 'Cách đọc file',
  auto: (resolved: string | null) => (resolved ? `Tự nhận (${resolved})` : 'Tự nhận'),
  defaultOption: (resolved: string | null) => (resolved ? `Mặc định (${resolved})` : 'Mặc định'),
  delimiterLabel: 'Dấu phân cách',
  delimiters: {
    COMMA: 'dấu phẩy ,',
    SEMICOLON: 'dấu chấm phẩy ;',
    TAB: 'tab',
    PIPE: 'gạch đứng |',
  } satisfies Record<Delimiter, string>,
  encodingLabel: 'Bảng mã',
  encodings: {
    'UTF-8': 'UTF-8',
    'UTF-16': 'UTF-16 (có BOM)',
    'WINDOWS-1258': 'Windows-1258 (tiếng Việt)',
    'WINDOWS-1252': 'Windows-1252',
  } satisfies Record<Encoding, string>,
  sheetLabel: 'Sheet',
  hiddenSheet: (name: string) => `${name} (ẩn)`,
  hasHeader: 'Dòng đầu là tiêu đề cột',

  loading: 'Đang đọc file…',
  retry: 'Đọc lại',
  summary: (rows: number, columns: number) => `${formatNumber(rows)} dòng · ${formatNumber(columns)} cột`,
  blankRows: (count: number) => `bỏ qua ${formatNumber(count)} dòng trống`,
  shown: (shown: number) => `Xem trước ${formatNumber(shown)} dòng đầu`,
  tableLabel: 'Dữ liệu xem trước',
  rowNumber: 'Dòng',
  noRows: 'File không có dòng dữ liệu',
}
