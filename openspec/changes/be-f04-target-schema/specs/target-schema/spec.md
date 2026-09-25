## ADDED Requirements

### Requirement: Cập nhật schema đích qua PUT /schema
Hệ thống SHALL cho phép định nghĩa schema đích qua `PUT /api/import-sessions/{id}/schema` với body `{ "fields": [ { "name", "type", "required", "order" } ] }`. Mỗi lần PUT SHALL ghi đè toàn bộ schema cũ. Khi thành công, hệ thống SHALL trả `200` với body `{ session, warnings }`, trong đó `session.config.schema.fields` đã được trim tên, sắp theo `order` tăng dần, và đánh lại `order` từ 0.

#### Scenario: Định nghĩa schema hợp lệ
- **WHEN** client gửi PUT schema với fields `[{name " email ", type "email", required true, order 5}, {name "name", type "string", required false, order 1}]`
- **THEN** hệ thống trả `200`
- **AND** `session.config.schema.fields` là `[{name "name", type "string", required false, order 0}, {name "email", type "email", required true, order 1}]`
- **AND** `warnings` là mảng rỗng

#### Scenario: PUT lần hai ghi đè toàn bộ
- **WHEN** schema đang có field `name` và `email`, rồi client PUT schema chỉ gồm field `phone`
- **THEN** `session.config.schema.fields` chỉ còn field `phone`

### Requirement: Kiểu dữ liệu của target field
`type` của mỗi field SHALL là một trong các mã chữ thường `string`, `number`, `boolean`, `date`, `email`. Mã khác, kể cả khác hoa thường như `String`, SHALL bị từ chối với `422` và `code` là `SCHEMA_INVALID`.

#### Scenario: Đủ 5 kiểu dữ liệu
- **WHEN** client PUT schema gồm 5 field với type lần lượt là `string`, `number`, `boolean`, `date`, `email`
- **THEN** hệ thống trả `200` và mỗi field giữ đúng type đã gửi

#### Scenario: Kiểu không hỗ trợ
- **WHEN** client PUT schema có field `{name "note", type "text", order 0}`
- **THEN** hệ thống trả `422` với `code` là `SCHEMA_INVALID` và `errors` chứa `{field "note", code "SCHEMA_INVALID", message "Unknown field type."}`

### Requirement: Tên target field hợp lệ và không trùng
Tên field SHALL được trim. Sau khi trim, tên SHALL dài từ 1 tới 100 ký tự và SHALL không trùng tên nào khác trong schema, so sánh không phân biệt hoa thường. Vi phạm SHALL bị từ chối với `422` và `code` là `SCHEMA_INVALID`, kèm một item trong `errors` cho mỗi vi phạm.

#### Scenario: Tên trùng khác hoa thường
- **WHEN** client PUT schema có hai field type `string`: `Email` (order 0) và `email` (order 1)
- **THEN** hệ thống trả `422` với `code` là `SCHEMA_INVALID` và `errors` chứa `{field "email", code "SCHEMA_INVALID", message "Duplicate field name."}`

#### Scenario: Tên trống
- **WHEN** client PUT schema có field `{name "   ", type "string", order 0}`
- **THEN** hệ thống trả `422` và `errors` chứa `{field null, code "SCHEMA_INVALID", message "Field name must not be blank."}`

#### Scenario: Tên quá dài
- **WHEN** client PUT schema có field với tên gồm 101 ký tự `a`, type `string`, order 0
- **THEN** hệ thống trả `422` và `errors` chứa một item với message `Field name must be at most 100 characters.`

### Requirement: Thứ tự target field
Mỗi field SHALL có `order`. Các giá trị `order` SHALL không trùng nhau. Thiếu `order` hoặc trùng `order` SHALL bị từ chối với `422` và `code` là `SCHEMA_INVALID`. Khi hợp lệ, hệ thống SHALL chuẩn hoá `order` thành 0..n-1 theo thứ tự tăng dần của giá trị đã gửi.

#### Scenario: Trùng order
- **WHEN** client PUT schema có hai field type `string` là `a` và `b`, cùng `order` 1
- **THEN** hệ thống trả `422` và `errors` chứa `{field "b", code "SCHEMA_INVALID", message "Duplicate field order."}`

#### Scenario: Thiếu order
- **WHEN** client PUT schema có field `{name "a", type "string"}` không có `order`
- **THEN** hệ thống trả `422` và `errors` chứa `{field "a", code "SCHEMA_INVALID", message "Field order is required."}`

### Requirement: Schema phải có ít nhất một field
PUT schema với `fields` là mảng rỗng SHALL bị từ chối với `422`, `code` là `SCHEMA_INVALID`, và `errors` chứa `{field null, code "SCHEMA_INVALID", message "Schema must contain at least one field."}`.

#### Scenario: Schema rỗng
- **WHEN** client PUT schema với `{"fields": []}`
- **THEN** hệ thống trả `422` với `code` là `SCHEMA_INVALID`

### Requirement: Báo mọi lỗi schema trong một lần
Khi schema có nhiều vi phạm, hệ thống SHALL trả toàn bộ vi phạm trong một response, xếp theo thứ tự field trong request. Hệ thống MUST NOT lưu bất kỳ phần nào của một schema không hợp lệ.

#### Scenario: Nhiều lỗi cùng lúc
- **WHEN** client PUT schema gồm `{name "", type "string", order 0}`, `{name "age", type "int", order 1}`, `{name "AGE", type "number", order 2}`
- **THEN** hệ thống trả `422` và `errors` có đúng 3 item với message lần lượt `Field name must not be blank.`, `Unknown field type.`, `Duplicate field name.`
- **AND** schema đã lưu trước đó không thay đổi

### Requirement: Request schema sai cấu trúc
Body không phải JSON hợp lệ, thiếu thuộc tính `fields`, hoặc giá trị sai kiểu JSON (ví dụ `required` là chuỗi) SHALL bị từ chối với `400` và `code` là `REQUEST_INVALID`.

#### Scenario: Thiếu thuộc tính fields
- **WHEN** client PUT schema với body `{}`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`
