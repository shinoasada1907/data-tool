## Why

Validator (tool-05) là tool dạng run đầu tiên: nó phải lưu kết quả để xem theo trang và export mà không chạy lại. Run store vốn nằm trong `core-05-platform-guard` (nhóm 12). Ngày 2026-09-29 người dùng bảo "làm Validator tiếp", và vẫn muốn tiết kiệm token. Vì vậy run store được tách khỏi core-05 thành change này, để Validator không phải chờ rate limit, gate, guest token và metrics.

## What Changes

- Bảng `tool_run` (Flyway `V13`).
- `RunStore`: ghi các section NDJSON vào thư mục tạm rồi rename nguyên khối, xem, đọc section, xoá, TTL trượt 24 giờ, `RUN_NOT_FOUND`.
- Dọn run hết hạn theo lịch. Dọn thư mục tạm bị bỏ dở.
- `RunStore` cài `StorageOwner`, nên lượt dọn mồ côi không bao giờ xoá thư mục của run.
- Mã lỗi mới `RUN_NOT_FOUND` (404).

## Capabilities

### New Capabilities
- `tool-runs`: ngữ nghĩa chung của mọi run. Spec chuyển nguyên từ core-05.

### Modified Capabilities
(không có)

## Impact

- **Code**: `platform.run`.
- **DB**: `V13__create_tool_run.sql`.
- **core-05**: bỏ nhóm 12 và spec `tool-runs`.
