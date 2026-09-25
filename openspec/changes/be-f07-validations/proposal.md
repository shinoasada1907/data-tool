## Why

FR-07 yêu cầu validate giá trị **sau** transformation bằng 4 rule: `required`, `type`, `email`, `unique`. Lỗi phải truy được tới row, field và rule, và một row có thể có nhiều lỗi. F10 còn yêu cầu JSON export đúng kiểu dữ liệu, nên bước validate cũng phải ép giá trị về kiểu thật. Pipeline (F08) cần engine này để quyết định row hợp lệ hay không, và để có giá trị đã ép kiểu đem đi export.

## What Changes

- Thêm contract `ValidationRule` và `ValidationRegistry` vào domain, cùng 4 rule:
  - `required`: fail khi giá trị rỗng, tính cả NBSP.
  - `type`: parse và ép kiểu. `number` → `BigDecimal`; `boolean` nhận `true/false/1/0`; `date` → `LocalDate` theo ISO STRICT; `email` theo regex.
  - `email`: rule do người dùng thêm, chỉ dùng cho field `string`.
  - `unique`: kiểm hai pha trên giá trị đã canonical; bỏ qua giá trị rỗng; bản gặp đầu tiên thắng; một giá trị chỉ được ghi nhận khi cả row hợp lệ.
- Thêm `FieldValidator`. Với mỗi field, rule chạy theo thứ tự `required → type → email → unique` và **chỉ báo lỗi đầu tiên**. Field optional mà rỗng thì bỏ qua mọi rule và output là `null`.
- `required` và `type` được **suy ra từ schema** (D10):
  - Payload chỉ nhận `email` và `unique`.
  - Gửi `required`/`type` thì BE bỏ qua và trả warning `RULE_IMPLIED_BY_SCHEMA`; gửi `email` cho field kiểu `email` cũng vậy.
  - Gửi `email` cho field kiểu `number`/`boolean`/`date` thì trả 422 `CONFIG_INVALID`.
- Thêm endpoint `PUT /api/import-sessions/{id}/validations` nhận danh sách phẳng `{ targetField, type, params? }`.
- Prune khi schema đổi:
  - Bỏ rule của field đã bị xoá.
  - Bỏ rule `email` khi field đổi sang kiểu khác `string`.
  - Mỗi lần prune kèm warning `CONFIG_PRUNED`.

## Capabilities

### New Capabilities
- `validation`: 4 validation rule và ngữ nghĩa của chúng; luật parse và ép kiểu từng field type; thứ tự rule và quy tắc chỉ báo lỗi đầu tiên; `unique` hai pha; cấu hình validation qua API (rule suy ra từ schema, kiểm tra, lưu, prune).

### Modified Capabilities
(không có)

## Impact

- **Code**:
  - `MAIN/domain/validation/*`.
  - `MAIN/api/importsession/ValidationConfigController.java` và DTO.
  - `MAIN/infrastructure/config/EngineConfig.java`: thêm bean.
  - Service cấu hình session của F04: thêm hàm cập nhật validations và phần prune.
- **API**: endpoint #7 trong bảng "API contract V0.1" của be-f01.
- **DB**: không có migration mới. Dùng cột `import_configuration.validations_json` (V3, F04).
- **Phụ thuộc**:
  - F04 (schema, config, khung prune, readiness, khoá).
  - F06: `RowErrorCode`, `TextValues`, `EngineConfig`.
- **Câu hỏi để ngỏ**: quy tắc "mỗi field chỉ báo lỗi đầu tiên" là Open Question của be-f01 và cần xác nhận khi review. Nếu đổi, chỉ phải sửa `FieldValidator`.
