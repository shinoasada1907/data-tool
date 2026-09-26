interface ImportMetaEnv {
  /** Giới hạn dung lượng upload (MB) để kiểm trước khi gửi; phải khớp IMPORTER_MAX_FILE_SIZE của BE. */
  readonly VITE_MAX_UPLOAD_MB?: string
  /** "true" thì bật MSW worker trên trình duyệt (chế độ dev:mock). */
  readonly VITE_USE_MOCK?: string
}
