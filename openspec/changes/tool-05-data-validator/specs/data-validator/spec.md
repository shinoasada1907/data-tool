## ADDED Requirements

### Requirement: Kiểm dataset theo schema
`POST /api/validator/runs` với body `{ source, schema }` SHALL kiểm từng row của dataset theo schema và tạo một run (spec `tool-runs`). Thứ tự rule, lỗi đầu tiên của mỗi field, và giá trị rỗng theo spec `data-rules`. Unique SHALL dùng phạm vi `ALL_ROWS`. Một row SHALL hợp lệ khi không field nào có lỗi. Mỗi field của một row SHALL có tối đa một lỗi, và mọi field đều được kiểm.

Response SHALL là `201` kèm `Location`, gồm `schemaName`, `fields`, `compatibility { matched, missingOptional, extraColumns }` và `summary { totalRows, validRows, invalidRows, errorCount, errorCountsByCode, errorCountsByField }`. `defaultValue` của schema MUST NOT được dùng để điền giá trị.

#### Scenario: File có row lỗi
- **WHEN** dataset có 3 row, schema có `email` (email, required) và `age` (number, `min` 18), và row 3 có `email` là `a@` và `age` là `16`
- **THEN** run có `validRows` là `2`, `invalidRows` là `1`, `errorCount` là `2`, và `errorCountsByCode` là `{ VALIDATION_EMAIL: 1, VALIDATION_MIN: 1 }`

#### Scenario: Trùng với row lỗi vẫn là trùng
- **WHEN** field `code` có `unique`, row 2 có `code` là `A` và một field khác bị lỗi, row 3 có `code` là `A`
- **THEN** row 3 có lỗi `VALIDATION_UNIQUE` với message `Duplicate value; first seen in row 2.`

### Requirement: Schema sai chỉ đúng chỗ
Schema vi phạm luật của spec `schema-core` SHALL trả `422 SCHEMA_INVALID`, với `errors[]` là mọi vi phạm. `pointer` của mỗi vi phạm SHALL tính từ gốc body, có tiền tố `/schema`. Khi schema sai, hệ thống MUST NOT mở dataset.

#### Scenario: Constraint sai kiểu
- **WHEN** field thứ hai của schema có kiểu `string` và `min` là `1`
- **THEN** hệ thống trả `422` với `code` là `SCHEMA_INVALID`, và `errors[0]` có `code` là `CONSTRAINT_INVALID` và `pointer` là `/schema/fields/1/constraints/min`

### Requirement: Khớp field với cột
Mỗi field SHALL khớp với cột có tên giống hệt. Không có thì khớp với cột đầu tiên có tên giống khi bỏ khoảng trắng hai đầu và không phân biệt hoa thường. Mỗi cột SHALL chỉ khớp một field.
- Field required không có cột SHALL làm request trả `422 SCHEMA_INCOMPATIBLE`. `errors[]` liệt kê mọi field như vậy, mỗi phần tử có `field` và `code` là `FIELD_MISSING`.
- Field optional không có cột SHALL nằm trong `missingOptional` và luôn là `null`.
- Cột không khớp field nào SHALL nằm trong `extraColumns` và bị bỏ qua.

#### Scenario: Tên cột khác hoa thường
- **WHEN** schema có field `email` và file có cột ` Email `
- **THEN** `compatibility.matched` có `{ field: "email", column: " Email " }`

#### Scenario: Thiếu cột bắt buộc
- **WHEN** schema có field required `phone` và file không có cột nào khớp
- **THEN** hệ thống trả `422` với `code` là `SCHEMA_INCOMPATIBLE`, `errors[0].field` là `phone`, `errors[0].code` là `FIELD_MISSING`, và không có run nào được tạo

### Requirement: Xem row của run
`GET /api/validator/runs/{id}/rows` SHALL nhận `view` là `VALID` (mặc định) hoặc `INVALID`, không phân biệt hoa thường. Giá trị khác SHALL trả `400 REQUEST_INVALID`. Mỗi row SHALL có `rowNumber`, `values` (giá trị ô gốc theo thứ tự `fields`), và `errors[] { field, code, rule, message, value }` với `value` là giá trị ô gốc. Với `INVALID`, `field` và `code` SHALL lọc giữ row có ít nhất một lỗi khớp mọi điều kiện được cho. Phân trang theo spec `tool-runs`.

#### Scenario: Lọc theo mã
- **WHEN** run có 2 row lỗi, một lỗi `VALIDATION_EMAIL` và một lỗi `VALIDATION_MIN`, và client gọi `rows?view=invalid&code=VALIDATION_MIN`
- **THEN** hệ thống trả đúng 1 row, và `page.totalElements` là `1`

### Requirement: Tải kết quả
`POST /api/validator/runs/{id}/export` với `{ content, output }` SHALL trả file theo `output` như Converter:
- `VALID`: các cột là field của schema, chỉ gồm row hợp lệ. Kiểu ô lấy từ schema: `number` là số, `boolean` là `true`/`false`, `date` là ngày ISO.
- `INVALID`: cột `_row`, các field, rồi `_errors` (`field: message` nối bằng `; `), chỉ gồm row lỗi, giá trị gốc.
- `ERRORS`: cột `row`, `field`, `code`, `rule`, `message`, `value`, mỗi lỗi một dòng.

Tên file SHALL là tên gốc bỏ đuôi, cộng `-valid`, `-invalid` hoặc `-errors`, cộng đuôi của định dạng. XLSX không chứa nổi số row hoặc độ dài ô SHALL trả `422 LIMIT_EXCEEDED` trước byte đầu tiên.

#### Scenario: Ngày theo format riêng thành ngày ISO
- **WHEN** field `dob` có kiểu `date`, `format` là `dd/MM/yyyy`, một row hợp lệ có `dob` là `31/01/2024`, và client export `VALID` sang JSON với typing `PRESERVE`
- **THEN** file có `"dob":"2024-01-31"`

#### Scenario: Danh sách lỗi
- **WHEN** run có 1 row lỗi (row 3) với lỗi `VALIDATION_EMAIL` ở field `email`, giá trị `a@`, và client export `ERRORS` sang CSV
- **THEN** file có dòng tiêu đề `row,field,code,rule,message,value` và dòng `3,email,VALIDATION_EMAIL,email,Value is not a valid email address.,a@`
