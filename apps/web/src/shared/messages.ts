import type { StepId } from '../wizard/state'
import { formatNumber } from './format'

/**
 * Thông điệp tiếng Việt theo `code` của BE. BE cam kết không đổi `code`; `message`/`detail` của BE
 * viết tiếng Anh nên chỉ dùng làm dòng phụ hoặc khi gặp mã lạ.
 */
export const errorCodeMessages: Readonly<Record<string, string>> = {
  // Lỗi API
  REQUEST_INVALID: 'Yêu cầu không hợp lệ',
  SESSION_NOT_FOUND: 'Không tìm thấy phiên import (có thể đã hết hạn)',
  SESSION_NOT_READY: 'Cấu hình chưa đủ để chạy xử lý',
  SESSION_STATE_INVALID: 'Phiên import không dùng được nữa',
  RESULT_NOT_AVAILABLE: 'Kết quả không còn khớp với cấu hình hiện tại, hãy chạy lại',
  FILE_TOO_LARGE: 'File vượt giới hạn của máy chủ',
  FILE_UNSUPPORTED: 'Máy chủ không nhận loại file này; chỉ hỗ trợ CSV và XLSX',
  FILE_EMPTY: 'File không có dữ liệu: file rỗng, thiếu dòng header, hoặc workbook/sheet trống',
  FILE_PARSE_ERROR: 'Không đọc được file; CSV phải mã hoá UTF-8 và phân cách bằng dấu phẩy',
  SCHEMA_INVALID: 'Schema không hợp lệ',
  MAPPING_INVALID: 'Mapping không hợp lệ',
  SOURCE_COLUMN_NOT_FOUND: 'Không tìm thấy cột nguồn',
  CONFIG_INVALID: 'Cấu hình transformation hoặc validation không hợp lệ',
  EXPORT_FAILED: 'Không tạo được file export',
  INTERNAL_ERROR: 'Máy chủ gặp lỗi không lường trước',
  // Lỗi theo dòng
  TRANSFORMATION_FAILED: 'Biến đổi dữ liệu thất bại',
  VALIDATION_REQUIRED: 'Thiếu giá trị bắt buộc',
  VALIDATION_TYPE: 'Sai kiểu dữ liệu',
  VALIDATION_EMAIL: 'Email không hợp lệ',
  VALIDATION_UNIQUE: 'Giá trị bị trùng',
  // Readiness issue
  SCHEMA_EMPTY: 'Schema chưa có field nào',
  TARGET_FIELD_REQUIRED: 'Field bắt buộc chưa được map',
  // Chỉ FE sinh ra, BE không dùng: response 2xx mà body sai dạng so với contract.
  INVALID_RESPONSE: 'Máy chủ trả về dữ liệu không đúng định dạng',
}

export const stepLabels: Readonly<Record<StepId, string>> = {
  upload: 'Upload file',
  preview: 'Xem trước dữ liệu',
  schema: 'Schema đích',
  mapping: 'Mapping',
  rules: 'Biến đổi & kiểm tra',
  result: 'Kết quả & export',
}

export const messages = {
  /** Tên app: nguồn duy nhất cho sidebar và tiêu đề tab. Đổi tên dự án thì sửa ở đây (fe-app-shell D2). */
  appName: 'Universal Importer',
  appVersion: 'V0.1',

  shell: {
    toolsHeading: 'Công cụ',
  },

  tools: {
    import: {
      label: 'Import dữ liệu',
      description: 'Nhập file CSV hoặc XLSX, map sang schema đích, kiểm tra rồi xuất dữ liệu hợp lệ.',
    },
  },

  stepperLabel: 'Các bước import',
  stepDone: '(đã xong)',
  stepInProgress: 'Bước này đang được phát triển.',
  errorCodeLabel: 'Mã lỗi',

  network: 'Không kết nối được máy chủ',
  unexpected: 'Đã xảy ra lỗi không mong đợi',
  serverError: (status: number) => `Máy chủ đang lỗi (${status})`,
  requestFailed: (status: number) => `Yêu cầu không thành công (${status})`,

  nav: {
    back: 'Quay lại',
    next: 'Tiếp',
  },

  /** Session hết hạn hoặc đã hỏng: chỉ còn cách upload lại (design D12). */
  sessionUnusableAction: 'Upload lại',
  retry: 'Thử lại',
  emptyCell: 'Ô trống',

  guard: {
    needUpload: 'Cần upload file trước',
    needPreview: 'Cần tải xong dữ liệu xem trước',
    needSchema: 'Cần lưu schema trước',
    needMapping: 'Cần lưu mapping trước',
    needResult: 'Cần chạy xử lý trước',
  },

  upload: {
    title: 'Upload file nguồn',
    hintCsv: 'CSV phải mã hoá UTF-8 và phân cách bằng dấu phẩy (từ Excel, lưu dạng “CSV UTF-8”).',
    hintXlsx: 'Với XLSX, hệ thống đọc sheet hiển thị đầu tiên.',
    hintMaxSize: (mb: number) => `Tối đa ${mb} MB.`,
    hintLabels: { csv: 'CSV', xlsx: 'XLSX', size: 'Dung lượng' },
    inputLabel: 'Chọn file CSV hoặc XLSX',
    dropZone: 'Kéo-thả file vào đây, hoặc bấm để chọn file',
    choose: 'Chọn file',
    currentFile: 'File đang dùng',
    replaceNote: 'Chọn file khác sẽ tạo phiên import mới.',
    inProgressLabel: 'Đang upload',
    uploading: (percent: number) => `Đang upload… ${percent}%`,
    announceStart: (fileName: string) => `Đang upload ${fileName}`,
    serverProcessing: 'Đang đọc file trên máy chủ…',
    progressLabel: 'Tiến độ upload',
    cancel: 'Huỷ',
    retry: 'Upload lại',
    confirmReplace: (fileName: string) => `Upload “${fileName}” sẽ xoá toàn bộ cấu hình của phiên hiện tại.`,
    confirm: 'Tiếp tục',
    rejected: {
      extension: 'Chỉ hỗ trợ file .csv hoặc .xlsx',
      multiple: 'Chỉ chọn một file',
      empty: 'File rỗng (0 byte)',
      tooLarge: (mb: number) => `File vượt giới hạn ${mb} MB`,
    },
  },

  preview: {
    summaryLabel: 'Thông tin file',
    rowsShown: (shown: number, total: number) => `Xem trước ${formatNumber(shown)} / ${formatNumber(total)} dòng`,
    sheet: (name: string) => `Sheet: ${name}`,
    loading: 'Đang tải dữ liệu xem trước…',
    tableLabel: 'Dữ liệu xem trước',
    rowNumber: 'Dòng',
    noRows: 'File không có dòng dữ liệu',
    noRowsHint: 'Vẫn có thể khai báo schema và mapping dựa trên các cột ở trên.',
  },
} as const
