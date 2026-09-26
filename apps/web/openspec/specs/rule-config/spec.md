# rule-config Specification

## Purpose
TBD - created by archiving change fe-import-wizard-v0-1. Update Purpose after archive.
## Requirements
### Requirement: Cấu hình transformation có thứ tự theo từng field
Với mỗi target field, FE SHALL cho thêm, xoá và đổi thứ tự (nút "Lên"/"Xuống") transformation thuộc các loại `trim`, `uppercase`, `lowercase`, `defaultValue`, `dateFormat`. Thứ tự gửi lên BE MUST đúng bằng thứ tự hiển thị, `order` bắt đầu từ 0 trong phạm vi từng field. Chỉ `defaultValue` và `dateFormat` có `params`; FE MUST NOT gửi `params` cho `trim`, `uppercase`, `lowercase`.

#### Scenario: Nhiều transformation giữ đúng thứ tự
- **WHEN** field `name` có danh sách `[trim, uppercase]`
- **THEN** payload có `{targetField: "name", type: "trim", order: 0}` và `{targetField: "name", type: "uppercase", order: 1}`, không phần tử nào có `params`

#### Scenario: Đổi thứ tự transformation
- **WHEN** danh sách là `[trim, uppercase]` và user bấm "Lên" ở `uppercase`
- **THEN** danh sách thành `[uppercase, trim]` và lần lưu kế tiếp gửi `uppercase` với `order: 0`

### Requirement: Tóm tắt chuỗi transformation, không chạy thử ở FE
FE SHALL hiển thị một dòng tóm tắt chuỗi transformation của mỗi field. FE MUST NOT tự chạy transformation trên dữ liệu mẫu; việc biến đổi dữ liệu chỉ nằm ở BE.

#### Scenario: Dòng tóm tắt
- **WHEN** field `name` có `[trim, uppercase]`
- **THEN** FE hiển thị "trim → uppercase"

#### Scenario: Chưa có transformation
- **WHEN** field chưa có transformation nào
- **THEN** FE hiển thị "Không biến đổi"

### Requirement: Tham số bắt buộc của transformation
FE MUST kiểm tham số trước khi cho chạy:
- `defaultValue` MUST có `value` khác rỗng.
- `dateFormat` MUST có `inputFormat` và `outputFormat` khác rỗng. Pattern theo cú pháp `DateTimeFormatter` của Java; BE parse ở chế độ STRICT. Cả hai ô gợi ý các mẫu `yyyy-MM-dd`, `dd/MM/yyyy`, `MM/dd/yyyy`, `dd-MM-yyyy`, `yyyy/MM/dd`, `dd.MM.yyyy`, và vẫn cho nhập tự do.
  - Field kiểu `date`: ô `outputFormat` bị khoá ở `yyyy-MM-dd`, vì kiểu `date` chỉ nhận ISO (BE trả `422 CONFIG_INVALID` nếu khác).
  - Field kiểu khác: `outputFormat` được điền sẵn `yyyy-MM-dd` và cho sửa.

Khi còn tham số thiếu, lỗi MUST hiển thị tại transformation đó và nút "Chạy xử lý" bị khoá.

#### Scenario: dateFormat trên field kiểu date
- **WHEN** user thêm `dateFormat` cho field kiểu `date`
- **THEN** ô `outputFormat` hiển thị `yyyy-MM-dd` ở trạng thái chỉ đọc, và payload gửi `params: {inputFormat: <giá trị user nhập>, outputFormat: "yyyy-MM-dd"}`

#### Scenario: dateFormat thiếu định dạng đầu vào
- **WHEN** user thêm `dateFormat` mà để trống `inputFormat`
- **THEN** transformation đó hiện lỗi "Cần định dạng đầu vào" và nút "Chạy xử lý" bị khoá

#### Scenario: defaultValue rỗng
- **WHEN** user thêm `defaultValue` mà để trống `value`
- **THEN** transformation đó hiện lỗi và nút "Chạy xử lý" bị khoá

### Requirement: Validation rule theo từng field
FE SHALL hiển thị cho mỗi field hai loại rule:
- **Rule suy ra** (chỉ đọc, không thêm hay xoá được):
  - `required` khi field là required;
  - `type:<kiểu field>` cho mọi field.
- **Rule do user bật/tắt**:
  - `email`: chỉ có ở field kiểu `string`;
  - `unique`: có ở mọi kiểu.

Mỗi rule do user bật MUST xuất hiện tối đa một lần trên một field.

#### Scenario: Field required kiểu number
- **WHEN** field `age` có kiểu `number` và required
- **THEN** FE hiển thị rule suy ra `required` và `type:number`, có tuỳ chọn `unique`, và không có tuỳ chọn `email`

#### Scenario: Field kiểu email
- **WHEN** field `email` có kiểu `email`
- **THEN** FE hiển thị rule suy ra `type:email` và không có tuỳ chọn rule `email`

#### Scenario: Bật unique
- **WHEN** user bật `unique` cho field `code`
- **THEN** payload validations có đúng một phần tử `{targetField: "code", type: "unique"}` cho field này, không có `params`

### Requirement: Payload validations chỉ chứa rule do user bật
Payload `PUT /api/import-sessions/{id}/validations` MUST chỉ gồm các rule `email` và `unique` do user bật, và không có `params`. `required` và `type` MUST NOT có trong payload vì BE suy ra từ schema (BE đã xác nhận ở Q1).

#### Scenario: Field required không bật rule nào
- **WHEN** schema chỉ có field `email` (required, kiểu `email`) và user không bật rule nào
- **THEN** payload validations là `{validations: []}`

