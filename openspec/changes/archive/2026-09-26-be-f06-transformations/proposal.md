## Why

Dữ liệu nguồn hiếm khi sạch: thừa khoảng trắng, chữ hoa thường lẫn lộn, ô trống cần giá trị mặc định, ngày viết `dd/MM/yyyy`. FR-06 yêu cầu người dùng cấu hình được các transformation cơ bản theo từng target field. Transformation phải chạy đúng thứ tự cấu hình, và khi lỗi phải truy được tới row, field và transformation gây lỗi. Pipeline (F08) cần engine này để biến giá trị đã map thành giá trị đem đi validate.

## What Changes

- Thêm contract `Transformation` và `TransformationRegistry` vào domain, cùng 5 transformation: `trim`, `uppercase`, `lowercase`, `defaultValue`, `dateFormat`. Ngữ nghĩa theo D10 của be-f01:
  - `trim` bỏ cả NBSP.
  - `uppercase`/`lowercase` dùng `Locale.ROOT`.
  - `dateFormat` parse ở chế độ STRICT, tự đổi `y` → `u`; `outputFormat` mặc định là ISO.
  - Giá trị rỗng được giữ nguyên, trừ với `defaultValue`.
- Thêm `TransformationEngine`: chạy các bước của một field theo `order`. Bước lỗi trả về lỗi có cấu trúc (`rule`, `step`, `message`), không ném exception ra ngoài, và dừng các bước sau của field đó.
- Thêm enum `RowErrorCode` với 5 mã lỗi theo row: `TRANSFORMATION_FAILED` và 4 mã `VALIDATION_*`. F07 và F08 dùng chung enum này.
- Thêm endpoint `PUT /api/import-sessions/{id}/transformations` nhận danh sách phẳng `{ targetField, order, type, params? }`. Cấu hình sai trả 422 `CONFIG_INVALID` kèm `errors[]`.
- Khi schema đổi, prune transformation của field đã bị xoá, và bước `dateFormat` không còn hợp lệ khi field chuyển sang kiểu `date`. Mỗi lần prune kèm warning `CONFIG_PRUNED`.

## Capabilities

### New Capabilities
- `transformation`: 5 transformation và ngữ nghĩa của chúng; thứ tự thực thi; lỗi transformation có cấu trúc; cấu hình transformation qua API (kiểm tra, lưu, prune khi schema đổi).

### Modified Capabilities
(không có)

## Impact

- **Code**:
  - `MAIN/domain/common/RowErrorCode.java`, `MAIN/domain/common/TextValues.java`
  - `MAIN/domain/transformation/*`
  - `MAIN/api/importsession/TransformationConfigController.java` và DTO
  - `MAIN/infrastructure/config/EngineConfig.java`
  - Service cấu hình session của F04 (thêm hàm cập nhật transformations và phần prune).
- **API**: endpoint #6 trong bảng "API contract V0.1" của be-f01.
- **DB**: không có migration mới. Dùng cột `import_configuration.transformations_json` do F04 tạo (V3).
- **Phụ thuộc**:
  - F04: schema, bảng config, khung prune, readiness, khoá session.
  - F05: có mapping thì mới có giá trị để transform. Endpoint vẫn dùng được khi chưa có mapping.
