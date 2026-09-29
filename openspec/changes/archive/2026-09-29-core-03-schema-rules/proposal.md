## Why

Data Validator (Notion 05) cần kiểm dữ liệu theo schema có constraint: required, type, email, unique, min/max, length, pattern, date format. Schema Builder (04) cần định nghĩa và kiểm chính các constraint đó. Mock Generator (08) về sau cũng sinh dữ liệu theo chúng. Hiện tại:
- schema của Importer chỉ có `name/type/required`;
- `FieldValidator` cố định 4 rule và thứ tự của chúng;
- việc ép kiểu nằm lẫn trong `TypeRule`;
- Data Cleaner (03) cần thêm `titleCase`, `replace`, `normalizeNull` mà catalog transformation chưa có.

Change này đưa Schema Core và rule engine về `core` để các tool dùng chung. Importer không đổi hành vi.

## What Changes

- **Schema Core** (`core.schema`):
  - `DataSchema`, `SchemaField`, `FieldConstraints` gồm `unique`, `min`, `max`, `minLength`, `maxLength`, `pattern`, `format`, `defaultValue`.
  - `SchemaDefinition.check` trả **mọi** vi phạm, mỗi vi phạm kèm `pointer` (JSON Pointer).
  - `TypeConverter` là nơi duy nhất ép kiểu. Ngày có `format` thì theo pattern.
- `ProblemItem` thêm `pointer` (tuỳ chọn). Khi `pointer` trống thì JSON lỗi của Importer giữ nguyên.
- **Rule engine** (`core.validate`):
  - `FieldRulePlan` có thứ tự cố định `required → type → email → minLength → maxLength → pattern → min → max → unique`, mỗi field báo lỗi đầu tiên.
  - 6 mã lỗi row mới: `VALIDATION_MIN`, `VALIDATION_MAX`, `VALIDATION_MIN_LENGTH`, `VALIDATION_MAX_LENGTH`, `VALIDATION_PATTERN`, `VALIDATION_DATE_FORMAT`.
  - `pattern` dùng **RE2J**, chạy thời gian tuyến tính, không bị ReDoS.
  - `UniqueIndex` có hai phạm vi: `VALID_ROWS` (Importer, như V0.1) và `ALL_ROWS` (Validator). Lưu hash 128-bit thay vì lưu giá trị.
- **Catalog transformation** thêm `titleCase`, `replace`, `normalizeNull`. Mỗi tool dựng registry với tập con của mình; Importer vẫn đúng 5 loại như V0.1.
- `FieldValidator` và `TypeRule` của Importer thành lớp mỏng trên engine mới. Spec `validation` và `transformation` của Importer không đổi.

## Capabilities

### New Capabilities
- `schema-core`: định nghĩa schema dữ liệu độc lập domain (field, kiểu, required, constraint), luật hợp lệ của định nghĩa kèm `pointer`, và luật ép kiểu dùng chung.
- `data-rules`: catalog rule dùng chung, gồm thứ tự và ngữ nghĩa các validation constraint, hai phạm vi unique, và các transformation mới `titleCase`, `replace`, `normalizeNull`.

### Modified Capabilities
(không có. Importer giữ nguyên hành vi theo `validation`, `transformation`, `api-errors`)

## Impact

- **Code**:
  - Mới: `core.schema`, `core.validate` (engine mới), `core.transform` (3 transformation mới + catalog).
  - `tools.importer.domain.validation` chuyển thành adapter.
- **Dependencies**: `com.google.re2j:re2j` 1.8 (compile). Luật ArchUnit của `core` thêm `com.google.re2j..` vào allowlist.
- **API và DB**: không đổi.
