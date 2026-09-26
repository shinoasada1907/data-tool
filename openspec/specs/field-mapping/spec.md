# field-mapping Specification

## Purpose
Chỉ ra mỗi target field lấy giá trị từ đâu: một cột nguồn, hoặc một giá trị hằng (FR-05). Nội dung gồm luật cấu hình mapping, cảnh báo field chưa map, prune khi schema đổi, và engine map một row thành giá trị thô theo thứ tự schema (bước đầu của pipeline). Tạo bởi change `be-f05-mapping-engine` (2026-09-26).
## Requirements
### Requirement: Cấu hình mapping qua PUT /mapping
Hệ thống SHALL nhận cấu hình mapping qua `PUT /api/import-sessions/{id}/mapping` với body `{ "mappings": [ { "targetField", "mappingType", "sourceColumn", "constantValue" } ] }`. Danh sách chỉ gồm các field đã map. Mỗi lần PUT SHALL ghi đè toàn bộ mapping cũ. Khi thành công, hệ thống SHALL trả `200` với body `{ session, warnings }`, trong đó `session.config.mapping.mappings` được sắp theo thứ tự field trong schema. PUT mapping SHALL tuân theo luật trạng thái session dùng chung cho mọi PUT cấu hình.

#### Scenario: Map hợp lệ
- **WHEN** schema có field `name` (order 0) và `country` (order 1), cột nguồn có `Họ tên`, và client PUT mapping `[{targetField "country", mappingType "CONSTANT", sourceColumn null, constantValue "VN"}, {targetField "name", mappingType "SOURCE_COLUMN", sourceColumn "Họ tên", constantValue null}]`
- **THEN** hệ thống trả `200`
- **AND** `session.config.mapping.mappings` là `[{targetField "name", …}, {targetField "country", …}]`, theo thứ tự schema

#### Scenario: Session không tồn tại
- **WHEN** client PUT mapping cho một UUID chưa từng được tạo
- **THEN** hệ thống trả `404` với `code` là `SESSION_NOT_FOUND`

#### Scenario: PUT lần hai ghi đè toàn bộ
- **WHEN** mapping đang có `name` và `country`, rồi client PUT mapping chỉ gồm `name`
- **THEN** `session.config.mapping.mappings` chỉ còn mapping của `name`

### Requirement: Map từ cột nguồn
Mapping `SOURCE_COLUMN` SHALL có `sourceColumn` không rỗng và `constantValue` là `null`. `sourceColumn` SHALL khớp chính xác (phân biệt hoa thường) với `name` của một cột trong source schema. Nếu không khớp cột nào, hệ thống SHALL trả `422` với `code` là `SOURCE_COLUMN_NOT_FOUND`, và `errors` chứa `{field <targetField>, code "SOURCE_COLUMN_NOT_FOUND", message "Source column does not exist."}`.

#### Scenario: Cột nguồn không tồn tại
- **WHEN** cột nguồn chỉ có `name,email` và client PUT mapping `[{targetField "name", mappingType "SOURCE_COLUMN", sourceColumn "Name", constantValue null}]`
- **THEN** hệ thống trả `422` với `code` là `SOURCE_COLUMN_NOT_FOUND`, và `errors[0].field` là `name`

#### Scenario: Thiếu sourceColumn
- **WHEN** client PUT mapping `[{targetField "name", mappingType "SOURCE_COLUMN", sourceColumn null, constantValue null}]`
- **THEN** hệ thống trả `422` với `code` là `MAPPING_INVALID`, và message của item là `sourceColumn is required for SOURCE_COLUMN mappings.`

### Requirement: Map giá trị hằng
Mapping `CONSTANT` SHALL có `constantValue` không `null` và không chỉ gồm khoảng trắng, và SHALL có `sourceColumn` là `null`. Vi phạm SHALL bị từ chối với `422` và `code` là `MAPPING_INVALID`.

#### Scenario: Hằng rỗng
- **WHEN** client PUT mapping `[{targetField "country", mappingType "CONSTANT", sourceColumn null, constantValue "  "}]`
- **THEN** hệ thống trả `422` với `code` là `MAPPING_INVALID`, và message của item là `constantValue must not be blank for CONSTANT mappings.`

#### Scenario: Hằng kèm cột nguồn
- **WHEN** client PUT mapping `[{targetField "country", mappingType "CONSTANT", sourceColumn "name", constantValue "VN"}]`
- **THEN** hệ thống trả `422` với `code` là `MAPPING_INVALID`, và message của item là `sourceColumn must be null for CONSTANT mappings.`

### Requirement: Mỗi target field tối đa một mapping
Mỗi `targetField` SHALL có trong schema (so khớp chính xác) và SHALL xuất hiện tối đa một lần trong danh sách mapping. `mappingType` SHALL là `SOURCE_COLUMN` hoặc `CONSTANT`. Vi phạm SHALL bị từ chối với `422` và `code` là `MAPPING_INVALID`. Hệ thống SHALL báo mọi vi phạm trong một response và MUST NOT lưu phần nào của mapping không hợp lệ. Nếu có cả vi phạm `MAPPING_INVALID` và `SOURCE_COLUMN_NOT_FOUND`, `code` top-level SHALL là `MAPPING_INVALID`.

#### Scenario: Map trùng target field
- **WHEN** client PUT mapping có hai phần tử cùng `targetField "name"`
- **THEN** hệ thống trả `422` với `code` là `MAPPING_INVALID`, và `errors` chứa `{field "name", code "MAPPING_INVALID", message "Target field is mapped more than once."}`

#### Scenario: Target field không có trong schema
- **WHEN** schema chỉ có `name`, và client PUT mapping cho `targetField "phone"`
- **THEN** hệ thống trả `422` với `code` là `MAPPING_INVALID`, và message của item là `Target field does not exist in the schema.`

#### Scenario: Loại mapping lạ
- **WHEN** client PUT mapping có `mappingType "source_column"`
- **THEN** hệ thống trả `422` với `code` là `MAPPING_INVALID`, và message của item là `Unknown mapping type.`

### Requirement: Cảnh báo target field chưa map
Sau mỗi PUT mapping thành công, `warnings` SHALL chứa một item `{field, code "TARGET_FIELD_UNMAPPED", message "Field is not mapped."}` cho mỗi field trong schema chưa được map, theo thứ tự schema. Field optional chưa map MUST NOT làm session mất trạng thái `READY`.

#### Scenario: Field optional chưa map
- **WHEN** schema có `name` (required) và `note` (optional), và client chỉ PUT mapping cho `name`
- **THEN** `warnings` là `[{field "note", code "TARGET_FIELD_UNMAPPED", message "Field is not mapped."}]` và `session.status` là `READY`

### Requirement: Prune mapping khi schema đổi
Khi PUT schema làm mất một field, mapping của field đó SHALL bị xoá. `warnings` của PUT schema SHALL chứa `{field, code "CONFIG_PRUNED", message "Mapping for this field was removed because the field no longer exists."}` cho mỗi mapping bị xoá. Đổi tên field được xử lý như xoá field cũ rồi thêm field mới.

#### Scenario: Đổi tên field
- **WHEN** schema có `phone` đã được map, rồi client PUT schema thay `phone` bằng `mobile`
- **THEN** `warnings` chứa `{field "phone", code "CONFIG_PRUNED", message "Mapping for this field was removed because the field no longer exists."}`
- **AND** `session.config.mapping.mappings` không còn mapping nào cho `phone`

### Requirement: Engine map một row theo cấu hình
Engine mapping SHALL chuyển mỗi `ImportRow` thành giá trị thô của từng target field, theo thứ tự schema:
- `SOURCE_COLUMN` lấy giá trị ô của cột tương ứng; ô thiếu (row có ít ô hơn header) lấy `null`.
- `CONSTANT` lấy `constantValue`.
- Field chưa map lấy `null`.

Kết quả SHALL chỉ phụ thuộc vào row và cấu hình.

#### Scenario: Map một row
- **WHEN** schema là `[name, country, note]`; mapping là `name ← cột "Họ tên"` (index 1), `country ← hằng "VN"`, `note` chưa map; row có `rowNumber 2`, values `["x","An"]`
- **THEN** kết quả là `{name "An", country "VN", note null}`, theo đúng thứ tự `name, country, note`

#### Scenario: Row thiếu ô
- **WHEN** mapping là `name ← cột index 3`, và row có values `["a"]`
- **THEN** giá trị của `name` là `null`

### Requirement: Request mapping sai cấu trúc
Body không phải JSON hợp lệ, thiếu thuộc tính `mappings`, hoặc có phần tử `null` SHALL bị từ chối với `400` và `code` là `REQUEST_INVALID`.

#### Scenario: Thiếu thuộc tính mappings
- **WHEN** client PUT mapping với body `{}`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

