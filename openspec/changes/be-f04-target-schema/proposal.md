## Why

Người dùng phải tự định nghĩa schema đích (tên field, kiểu, required) mà không gắn với domain cụ thể nào (FR-04). Schema là gốc của mọi cấu hình về sau: mapping, transformation, validation đều tham chiếu theo tên field. Vì vậy F04 cũng dựng luôn khung cấu hình dùng chung:
- lưu cấu hình theo session;
- tự bỏ cấu hình của field đã bị xoá khỏi schema (auto-prune) kèm warning;
- tính readiness;
- khoá theo session;
- chuyển trạng thái theo D2;
- tính `configHash` (D7).

## What Changes

- `PUT /api/import-sessions/{id}/schema` nhận `TargetSchemaDto { fields[{name, type, required, order}] }`, ghi đè toàn bộ schema, trả `200 ConfigUpdateResponseDto { session, warnings }`.
- Luật `SCHEMA_INVALID` (422, kèm `errors[]` theo field):
  - tên rỗng, dài hơn 100 ký tự, hoặc trùng (không phân biệt hoa thường);
  - kiểu không thuộc `string|number|boolean|date|email`;
  - thiếu `order` hoặc `order` trùng;
  - schema rỗng.

  `order` được chuẩn hoá thành 0..n-1.
- `ImportSessionDto` có thêm `config` (từ F04 là `{ schema }`; F05–F07 bổ sung các phần còn lại) và `readiness { ready, issues }`. Áp dụng cho response của upload, `GET /{id}` và mọi PUT.
- Readiness issue `SCHEMA_EMPTY`.
- Khung auto-prune `FieldScopedSection` + `ConfigPruner` với warning `CONFIG_PRUNED`. F05–F07 gắn các phần cấu hình của mình vào khung này.
- `SessionLocks` khoá theo session (D11) và một luồng cập nhật cấu hình dùng chung:
  - `UPLOADED` hoặc `FAILED` → 409 `SESSION_STATE_INVALID`;
  - tính lại `READY` / `CONFIGURING`;
  - `PROCESSED` với config không đổi thì giữ nguyên.
- Port `ConfigHasher` và `JsonConfigHasher` (SHA-256 của JSON cấu hình đã chuẩn hoá), để F08 dùng.
- Flyway V3: bảng `import_configuration`.

## Capabilities

### New Capabilities
- `target-schema`: định nghĩa schema đích, kiểu dữ liệu, luật tên field, thứ tự field, lỗi schema.

### Modified Capabilities
(không có — requirement về cấu hình trong session, readiness và luồng PUT được **thêm mới** (ADDED) vào capability `import-session` do F01 tạo; không sửa requirement nào của F01)

## Impact

- **Code**:
  - `MAIN/domain/schema/*`, `MAIN/domain/config/*`
  - `MAIN/application/configuration/*`, `MAIN/application/common/SessionLocks`
  - `MAIN/infrastructure/persistence/*` (entity, document, hasher)
  - `MAIN/api/schema/*`, `MAIN/api/importsession/ImportSessionDto` (thêm `config`, `readiness`)
- **DB**: `V3__create_import_configuration.sql`.
- **API**: endpoint #4. Response của upload và `GET /{id}` có thêm `config` và `readiness` (chỉ thêm field, không phá contract).
