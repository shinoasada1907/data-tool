## Why

Data Validator (Notion 05) là tool kế tiếp theo thứ tự Notion. Người dùng bảo "làm Validator tiếp" ngày 2026-09-29. Tool này kiểm một file dữ liệu theo schema có constraint, báo row nào hợp lệ, row nào sai và sai ở đâu, rồi cho tải về phần hợp lệ, phần lỗi, hoặc danh sách lỗi.

## What Changes

- `POST /api/validator/runs` nhận `{ source, schema }`: kiểm schema (lỗi có `pointer`), mở dataset, khớp field với cột, chạy rule engine của core-03 trên mọi row với unique phạm vi `ALL_ROWS`, rồi lưu kết quả thành run (core-06).
- `GET /api/validator/runs/{id}`, `GET …/rows?view=VALID|INVALID&field=&code=&page=&size=`, `POST …/export` (`VALID|INVALID|ERRORS`), `DELETE …`.
- Mã lỗi mới `SCHEMA_INCOMPATIBLE` (422). Dùng lại `SCHEMA_INVALID`, `RUN_NOT_FOUND` và các mã của dataset.

## Capabilities

### New Capabilities
- `data-validator`: kiểm dataset theo schema và xem, tải kết quả.

### Modified Capabilities
(không có)

## Impact

- **Code**: `tools.validator.{api, application}`. `ArchitectureTest.TOOL_NAMES` thêm `validator`. OpenAPI có group `validator`.
- **Phụ thuộc**: core-03 (schema, rule engine), core-06 (run store). Chưa có rate limit hay gate, vì core-05 chưa làm; site chưa được mở public.
- **FE**: contract ở design, mục VD1.
