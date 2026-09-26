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
    collapse: 'Thu gọn thanh bên',
    expand: 'Mở rộng thanh bên',
  },

  tools: {
    import: {
      label: 'Import dữ liệu',
      description: 'Nhập file CSV hoặc XLSX, map sang schema đích, kiểm tra rồi xuất dữ liệu hợp lệ.',
    },
  },

  stepperLabel: 'Các bước import',
  stepDone: '(đã xong)',
  errorCodeLabel: 'Mã lỗi',

  network: 'Không kết nối được máy chủ',
  timeout: 'Máy chủ không phản hồi',
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

  mapping: {
    intro: 'Chọn nguồn giá trị cho từng field: một cột của file, hoặc một giá trị cố định.',
    editorLabel: 'Mapping các field',
    columns: { field: 'Field đích', source: 'Nguồn', value: 'Giá trị' },
    sourceLabel: 'Nguồn',
    unmapped: 'Chưa map',
    columnsGroup: 'Cột nguồn',
    constantOption: 'Giá trị cố định…',
    constantLabel: 'Giá trị cố định',
    samplesLabel: 'Giá trị mẫu',
    noSamples: 'Không có giá trị mẫu trong dữ liệu xem trước',
    requiredBadge: 'Bắt buộc',
    typeLabel: 'Kiểu',
    optionalUnmapped: 'Chưa map (field không bắt buộc)',
    requiredUnmapped: 'Field bắt buộc chưa được map',
    blankConstant: 'Giá trị cố định không được để trống',
    blockedRequired: (names: string[]) => `Field bắt buộc chưa map: ${names.join(', ')}`,
    blockedConstant: (names: string[]) => `Giá trị cố định đang trống: ${names.join(', ')}`,
  },

  rules: {
    intro: 'Mỗi field chạy các bước biến đổi theo thứ tự từ trên xuống, rồi mới kiểm tra.',
    transformationsLegend: 'Biến đổi',
    validationsLegend: 'Kiểm tra',
    stepsLabel: 'Các bước biến đổi',
    step: (position: number, type: string) => `Bước ${position}: ${type}`,
    noTransformation: 'Không biến đổi',
    addTypeLabel: 'Loại biến đổi',
    add: 'Thêm biến đổi',
    typeOptions: {
      trim: 'trim (bỏ khoảng trắng đầu và cuối)',
      uppercase: 'uppercase (chữ hoa)',
      lowercase: 'lowercase (chữ thường)',
      defaultValue: 'defaultValue (giá trị mặc định khi ô trống)',
      dateFormat: 'dateFormat (đổi định dạng ngày)',
    },
    valueLabel: 'Giá trị mặc định',
    inputFormatLabel: 'Định dạng đầu vào',
    outputFormatLabel: 'Định dạng đầu ra',
    dateOutputLocked: 'Field kiểu date luôn xuất yyyy-MM-dd.',
    trimHint: 'BE không tự bỏ khoảng trắng trước khi đọc ngày: thêm trim trước dateFormat nếu dữ liệu có khoảng trắng.',
    moveUp: 'Lên',
    moveDown: 'Xuống',
    remove: 'Xoá',
    impliedLabel: 'Rule suy ra từ schema',
    ruleOptions: {
      email: 'email (kiểm định dạng email)',
      unique: 'unique (không cho trùng giá trị)',
    },
    run: 'Chạy xử lý',
    surroundingSpaces: 'Định dạng có khoảng trắng ở đầu hoặc cuối: mọi dòng sẽ không khớp.',
    missingValue: 'Cần giá trị mặc định',
    missingInputFormat: 'Cần định dạng đầu vào',
    missingOutputFormat: 'Cần định dạng đầu ra',
    blockedMissingParams: (names: string[]) => `Còn bước biến đổi thiếu tham số: ${names.join(', ')}`,
  },

  run: {
    running: 'Đang xử lý…',
  },

  result: {
    intro: 'Kết quả của lần chạy gần nhất trên toàn bộ file.',
    summaryLabel: 'Tóm tắt kết quả',
    total: 'Tổng',
    valid: 'Hợp lệ',
    invalid: 'Lỗi',
    stale: 'Cấu hình đã thay đổi — kết quả này là của lần chạy trước',
    rerun: 'Chạy lại',
    tabsLabel: 'Dòng kết quả',
    tab: (label: string, count: number) => `${label} (${formatNumber(count)})`,
    tableLabel: { valid: 'Dòng hợp lệ', invalid: 'Dòng lỗi' },
    rowNumber: 'Dòng',
    flagged: 'có lỗi',
    rowErrors: (rowNumber: number) => `Lỗi của dòng ${rowNumber}`,
    transformationRule: (rule: string, step: number | null) =>
      step === null ? `biến đổi ${rule}` : `biến đổi ${rule} ở bước ${step}`,
    validationRule: (rule: string) => `rule ${rule}`,
    /** BE ghi lỗi bất ngờ khi map bằng `rule = "mapping"`, `step = null` (BE-F08): mapping không phải bước biến đổi. */
    mappingRule: 'lỗi khi map giá trị',
    sourceValue: 'Giá trị nguồn',
    filtersLabel: 'Lọc dòng lỗi',
    fieldFilter: 'Lọc theo field',
    allFields: 'Tất cả field',
    codeFilter: 'Lọc theo mã lỗi',
    allCodes: 'Tất cả mã lỗi',
    filterOption: (label: string, count: number) => `${label} (${formatNumber(count)})`,
    clearFilters: 'Xoá lọc',
    empty: { valid: 'Không có dòng hợp lệ', invalid: 'Không có dòng lỗi' },
    emptyFiltered: 'Không có dòng lỗi khớp bộ lọc',
    loading: 'Đang tải kết quả…',
    pageStatus: (label: string, page: number, total: number) => `${label}: trang ${page} / ${total}`,
  },

  export: {
    heading: 'Tải kết quả',
    json: 'Tải JSON',
    csv: 'Tải CSV',
    errors: 'Tải báo cáo lỗi',
    downloading: { json: 'Đang tải file JSON…', csv: 'Đang tải file CSV…', errors: 'Đang tải báo cáo lỗi…' },
    saved: (fileName: string) => `Đã tải ${fileName}`,
    staleReason: 'Chạy lại để tải kết quả khớp cấu hình hiện tại',
    noValid: 'Không có dòng hợp lệ để tải',
    noInvalid: 'Không có dòng lỗi để tải',
  },

  pagination: {
    label: 'Phân trang',
    previous: 'Trước',
    next: 'Sau',
    page: (page: number, total: number) => `Trang ${page} / ${total}`,
  },

  schema: {
    intro: 'Khai báo các field của dữ liệu đích. Thứ tự ở đây là thứ tự cột khi xuất file.',
    editorLabel: 'Danh sách field',
    empty: 'Chưa có field nào',
    emptyHint: 'Bấm “Thêm field”, hoặc “Tạo lại từ file” để lấy lại các cột của file.',
    add: 'Thêm field',
    regenerate: 'Tạo lại từ file',
    regenerateConfirm: (current: number, columns: number) =>
      `Thay toàn bộ ${current} field hiện có bằng ${columns} field sinh từ các cột của file? Mapping được đặt lại theo tên cột; các bước biến đổi và rule kiểm tra bị xoá.`,
    regenerateAction: 'Tạo lại',
    cancel: 'Huỷ',
    group: (position: number) => `Field ${position}`,
    columns: { name: 'Tên field', type: 'Kiểu', required: 'Bắt buộc' },
    typeLabel: 'Kiểu',
    requiredLabel: 'Bắt buộc',
    moveUp: 'Lên',
    moveDown: 'Xuống',
    remove: 'Xoá',
    nameErrors: {
      blank: 'Tên field không được để trống',
      tooLong: (max: number) => `Tên field tối đa ${max} ký tự`,
      duplicate: 'Tên field bị trùng',
    },
    blocked: {
      empty: 'Cần ít nhất một field',
      unnamed: 'Còn field chưa đặt tên',
      invalid: 'Còn lỗi ở tên field',
    },
  },
} as const
