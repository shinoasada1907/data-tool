> **Tách phạm vi (2026-09-28):** để có Data Converter sớm và tiết kiệm token, change này chỉ còn **dataset API, output/download, header chung, Retry-After, dọn dẹp chung (session + dataset)**. Guard (rate limit, gate, đĩa, body), run store, guest identity, `ErrorCode` interface và metrics chuyển sang `core-05-platform-guard`. Phần bên dưới là mô tả gốc; chỗ nào lệch thì theo `tasks.md` và thư mục `specs/` hiện tại.

## Why

Mỗi tool mới (Converter, Validator, Cleaner, Diff) đều cần những thứ giống nhau: nhận file một lần rồi đọc lại theo tuỳ chọn, lưu kết quả để xem theo trang và tải về, dọn dữ liệu khách sau 24 giờ, và trả file tải về an toàn. Vì là **website public cho khách vãng lai**, site còn phải chịu được lạm dụng: rate limit, giới hạn số thao tác nặng chạy cùng lúc, không để đầy đĩa. Hiện những việc này hoặc gắn chặt vào import session, hoặc chưa có. Change này dựng lớp `platform` dùng chung, và đặt Importer dưới cùng lớp bảo vệ.

## What Changes

- **Dataset** (`/api/datasets`):
  - Upload một lần: nhận CSV/XLSX/JSON, kiểm định dạng; XLSX thì liệt kê sheet.
  - `GET /api/datasets/{id}/preview` đọc theo tuỳ chọn (sheet, delimiter, encoding, hasHeader), trả cột kèm `inferredType`, dữ liệu mẫu, `totalRows`, `blankRowsSkipped`, và option đã tự nhận.
  - `DELETE` xoá ngay.
  - TTL trượt 24 giờ. Cache inspect trong bộ nhớ; khoá đọc/ghi theo dataset.
  - `DatasetSources` cho tool mở nguồn bằng `{datasetId, options}`.
  - Flyway `V12__create_dataset.sql`.
- **Run store** (`platform.run`) cho các tool dạng run:
  - Kết quả ghi thành section NDJSON, commit nguyên khối (staging → rename → insert).
  - Bảng `tool_run` (Flyway `V13`), TTL trượt 24 giờ, `RUN_NOT_FOUND`, `DELETE`.
  - Chưa có endpoint nào; mỗi tool tự thêm controller.
- **Output và download** (`platform.output`):
  - `OutputDto` (CSV/JSON/XLSX kèm option) → `TableWriter`.
  - Tên file an toàn, header download, `Cache-Control: no-store`.
  - Mọi lỗi báo trước byte đầu tiên; lỗi giữa chừng thì huỷ kết nối.
- **Bảo vệ site public** (`platform.guard`):
  - Rate limit theo client key, ba bucket `upload`, `compute`, `read` → `429 RATE_LIMITED` + `Retry-After`.
  - `ProcessingGate` giới hạn thao tác nặng toàn server và theo client → `503 SERVER_BUSY` + `Retry-After`.
  - `DiskSpaceGuard` → `503 SERVER_BUSY` khi đĩa sắp đầy.
  - Body JSON tối đa 1 MB.
  - **Áp cho cả Importer.**
- **Guest identity** (`platform.identity`): header `X-Guest-Token` → `GuestKey` (SHA-256), dùng cho tài nguyên có chủ (tool-04).
- **Mã lỗi mở rộng được**:
  - `ErrorCode` thành interface kèm `ErrorKind` (→ HTTP), các enum `CommonError`, `ImporterError`.
  - Bảng mã có thêm mọi mã của contract Toolbox v1. Có test canh tên mã duy nhất và mã nào cũng nằm trong bảng.
- **Dọn dẹp chung** (`platform.cleanup`):
  - Session Importer, dataset và run cùng dọn theo SPI `ExpiringCatalog`.
  - Thư mục mồ côi chỉ bị xoá khi **không catalog nào nhận**. Đây là sửa lỗi tiềm ẩn: bộ dọn cũ chỉ hỏi bảng `import_session`, nên sẽ xoá thư mục của dataset.
  - Thư mục `*.staging-*` cũ bị dọn.
- Header `X-Content-Type-Options: nosniff` cho mọi response. OpenAPI có thêm nhóm `datasets`. Có Micrometer metrics theo tool.

## Capabilities

### New Capabilities
- `dataset-api`: upload, đọc thông tin, preview theo tuỳ chọn, xoá dataset; TTL; mã lỗi của từng endpoint dataset.
- `tool-runs`: ngữ nghĩa chung của mọi run: tạo nguyên khối, xem, xoá, TTL, độc lập với dataset, phân trang.

### Modified Capabilities
- `toolbox-platform`: thêm rate limit, `ProcessingGate`, `DiskSpaceGuard`, giới hạn body JSON, guest token, header bảo mật, quy ước file tải về.
- `api-errors`:
  - bảng mã lỗi thêm `GUEST_TOKEN_INVALID`, `DATASET_NOT_FOUND`, `RUN_NOT_FOUND`, `SCHEMA_NOT_FOUND`, `SCHEMA_VERSION_CONFLICT`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `SCHEMA_INCOMPATIBLE`, `DIFF_KEY_INVALID`, `RATE_LIMITED`, `SERVER_BUSY`;
  - `RATE_LIMITED` được phép ở mọi endpoint;
  - `SERVER_BUSY` được phép ở các endpoint nặng của Importer và dataset;
  - endpoint của các tool về sau công bố mã lỗi trong spec của tool đó.
- `import-session`: dọn mồ côi xét theo mọi loại tài nguyên, không chỉ session.

## Impact

- **Code**:
  - Mới: `platform.{dataset, run, output, guard, identity, cleanup}`.
  - Đổi: `core.common.ErrorCode` từ enum thành interface.
  - Controller Importer thêm annotation rate limit và dùng gate. `SessionCleanupService` của Importer thành một `ExpiringCatalog`.
- **DB**: `V12__create_dataset.sql`, `V13__create_tool_run.sql`. Chỉ thêm bảng, không đụng bảng cũ.
- **Cấu hình mới** (`toolbox.*`): `limits.*`, `retention.dataset-ttl`/`run-ttl`, `processing.max-concurrent`/`per-client`/`acquire-timeout`, `rate-limit.*`, `storage.min-free-space`.
- **FE**:
  - Mọi endpoint (kể cả Importer) có thể trả `429 RATE_LIMITED` và `503 SERVER_BUSY`, luôn kèm `Retry-After`.
  - Dataset API mới theo contract Toolbox v1.
- **Vận hành**: khi chạy sau reverse proxy thì bật `server.forward-headers-strategy`, để rate limit tính đúng IP khách.
