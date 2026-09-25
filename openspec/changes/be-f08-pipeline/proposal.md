## Why

Mapping (F05), transformation (F06) và validation (F07) mới là những mảnh rời. F08 nối chúng thành một pipeline deterministic chạy qua toàn bộ file và cho ra kết quả từng row. Kết quả được lưu lại cho F09 (xem kết quả) và F10 (export) dùng. Pack yêu cầu:
- `total = valid + invalid`;
- một row lỗi không làm hỏng cả job;
- cùng input và cùng config thì ra cùng kết quả;
- người dùng sửa config xong chạy lại được.

## What Changes

- **Domain**: thêm `ImportPipeline`. Pipeline nhận stream các row nguồn cùng `PipelineConfig`, ghi từng `RowResult` vào một `RowResultSink`, và **chỉ trả `PipelineSummary`**. Đây là mục lệch pack B1 đã được duyệt; pipeline không gom row vào RAM.
- **Thứ tự xử lý mỗi row**, theo D10: map → transform → validate → ép kiểu.
  - Lỗi ghi thành `ImportError` gồm `rowNumber`, `fieldName`, `stage`, `rule`, `step`, `code`, `message`, `sourceValue`.
  - Cuối mỗi row, giá trị `unique` được xác nhận nếu row hợp lệ, hoặc bị bỏ nếu row lỗi.
- **Result store** theo D7:
  - Thư mục `{storage}/{sessionId}/result/` gồm `summary.json`, `valid.ndjson`, `invalid.ndjson`.
  - Ghi vào thư mục tạm rồi đổi tên nguyên khối.
  - `summary.json` lưu `configHash` để phát hiện kết quả đã cũ.
- **`POST /api/import-sessions/{id}/process`**: chạy pipeline đồng bộ khi đang giữ khoá session, trả `PipelineSummaryDto`.
  - Session chưa `READY`: 409 `SESSION_NOT_READY`, `errors[]` là các readiness issue.
  - Session `FAILED`: 409 `SESSION_STATE_INVALID`.
  - Lỗi đọc file: session chuyển sang `FAILED`, trả 422 `FILE_PARSE_ERROR` hoặc 500 `INTERNAL_ERROR`.
  - Được phép process lại.
- **PUT config làm đổi `configHash` khi session đang `PROCESSED`**: xoá kết quả, rồi tính lại status (`READY` hoặc `CONFIGURING`) theo D2. PUT không làm đổi config thì giữ kết quả.
- **Summary** có `errorCountsByCode` và `errorCountsByField`.

## Capabilities

### New Capabilities
- `import-pipeline`: điều phối pipeline theo row; cấu trúc `RowResult`/`ImportError`; tổng hợp summary; result store; endpoint process; vòng đời kết quả khi config đổi; tính xác định.

### Modified Capabilities
(không có)

## Impact

- **Code**:
  - `MAIN/domain/pipeline/*`
  - `MAIN/infrastructure/result/FileResultStore.java`
  - `MAIN/application/pipeline/ProcessService.java`
  - `MAIN/api/importsession/ProcessController.java` và `PipelineSummaryDto`
  - Service cấu hình session của F04: thêm bước xoá kết quả khi config đổi.
- **API**: endpoint #8 trong bảng "API contract V0.1" của be-f01. Bổ sung lỗi 422 `FILE_PARSE_ERROR` và 500 `INTERNAL_ERROR` cho `/process` (xem Open Questions trong design.md).
- **Storage**: thêm thư mục `result/` bên cạnh `source.bin` (D7). Không có migration mới.
- **Phụ thuộc**:
  - F02/F03: parser đọc row dạng stream.
  - F04: config, readiness, khoá, `configHash`.
  - F05: mapping.
  - F06: `TransformationEngine`, `RowErrorCode`.
  - F07: `FieldValidator`, `UniqueTracker`.
- **Sau F08**: F09 và F10 đọc định dạng file kết quả mà F08 định nghĩa.
