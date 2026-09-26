## Why

F01 → F10 làm từng mảnh. F11 khép kín luồng V0.1 theo pack F11: định dạng lỗi nhất quán trên mọi endpoint, vòng đời session được kiểm từ đầu đến cuối, happy path CSV và XLSX chạy end-to-end, có chiến lược dọn file tạm (D12), và README có demo flow. Nếu không dọn dẹp, thư mục storage và bảng `import_session` sẽ phình mãi, vì mỗi lần upload đều để lại file gốc và kết quả trên đĩa.

## What Changes

- **Dọn dẹp định kỳ** (`@Scheduled`: chạy một lần khi khởi động, sau đó mỗi giờ):
  - Xoá session có `updatedAt` cũ hơn `IMPORTER_SESSION_TTL` (mặc định `24h`): xoá thư mục storage trước, rồi xoá row trong DB.
  - Xoá thư mục storage mồ côi: tên là UUID, không có row trong DB, và cũ hơn TTL.
- **Flyway `V10__index_import_session_updated_at.sql`** thêm index cho truy vấn dọn dẹp. Số V10 được chọn để không đụng V2–V9 của các change F02–F08.
- **Mở rộng port**:
  - `ImportSessionRepository`: thêm `findIdsUpdatedBefore`, `existsById`, `deleteById`.
  - `FileStorage`: thêm `listEntries`.
- **Kiểm contract lỗi**: integration test theo bảng, gọi mọi endpoint với các tình huống lỗi đã công bố. Thêm requirement "mỗi endpoint chỉ trả mã lỗi đã công bố".
- **Integration test end-to-end**:
  - Happy path cho cả CSV và XLSX: upload → preview → schema → mapping → transformations → validations → process → result → export và báo cáo lỗi.
  - Sửa config rồi process lại.
  - Vòng đời session đầy đủ, kể cả nhánh `FAILED`.
- **Xác nhận không có CORS**: FE gọi qua Vite proxy (D3).
- **README ở gốc repo**: demo flow, bảng biến môi trường BE và FE, các giới hạn đã biết của V0.1.

## Capabilities

### New Capabilities
(không có)

### Modified Capabilities
- `import-session`: thêm (ADDED) hai requirement: dọn session hết hạn theo TTL, và dọn thư mục lưu trữ mồ côi. Không sửa requirement nào đã có.
- `api-errors`: thêm (ADDED) requirement "mỗi endpoint chỉ trả mã lỗi đã công bố", kèm bảng mã lỗi theo từng endpoint.

## Impact

- **Code**:
  - `MAIN/application/importsession/`: `SessionCleanupService`, `CleanupProperties`, `CleanupReport`.
  - `MAIN/infrastructure/scheduling/`: `SessionCleanupScheduler`, `SchedulingConfig`.
  - Adapter JPA và `LocalFileStorage` cài đặt các method mới của port.
- **DB**: migration `V10`. Mọi migration sau F11 phải dùng số lớn hơn 10.
- **Biến môi trường mới**: `IMPORTER_SESSION_TTL` (mặc định `24h`).
- **Tài liệu**: `README.md` ở gốc repo.
- **Phụ thuộc**:
  - Toàn bộ be-f01 → be-f10.
  - Khoá theo session của be-f08 (D11).
  - FK từ `import_configuration` (be-f04) phải xoá theo khi xoá session.
  - Fixture XLSX của be-f03, nếu dùng lại được.
