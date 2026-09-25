## ADDED Requirements

### Requirement: Rule required suy ra từ schema
Với mỗi field có `required = true` trong schema, hệ thống SHALL kiểm giá trị sau transformation không rỗng. Giá trị rỗng là `null` hoặc chuỗi chỉ gồm ký tự khoảng trắng, tính cả NBSP `U+00A0`. Giá trị rỗng SHALL sinh lỗi `code = VALIDATION_REQUIRED`, `rule = "required"`, `message = "Value is required."`.

#### Scenario: Ô trống ở field required
- **WHEN** field `name` là required và giá trị sau transformation là `null`
- **THEN** field có lỗi `VALIDATION_REQUIRED` với `rule = "required"`

#### Scenario: Chuỗi toàn NBSP cũng là rỗng
- **WHEN** field `name` là required và giá trị là `"  "`
- **THEN** field có lỗi `VALIDATION_REQUIRED`

#### Scenario: defaultValue lấp chỗ trống trước khi kiểm required
- **WHEN** field `country` là required, có transformation `defaultValue` với `value = "VN"`, và ô nguồn trống
- **THEN** field không có lỗi và giá trị là `"VN"`

### Requirement: Rule type kiểm và ép kiểu theo field type
Với mỗi field, hệ thống SHALL kiểm giá trị không rỗng theo `type` của field trong schema, rồi ép sang kiểu thật như sau:
- `string`: nhận mọi chuỗi, giữ nguyên.
- `number`: chỉ nhận `^-?\d+(\.\d+)?$` (chữ số ASCII, không trim), ép sang `BigDecimal`.
- `boolean`: chỉ nhận `true`, `false`, `1`, `0` (không phân biệt hoa thường), ép sang `Boolean`.
- `date`: chỉ nhận `yyyy-MM-dd`, parse STRICT, ép sang `LocalDate`.
- `email`: chỉ nhận `^[^@\s]+@[^@\s]+\.[^@\s]+$`, giữ chuỗi.

Sai kiểu SHALL sinh lỗi `code = VALIDATION_TYPE`, `rule = "type"`. Riêng field kiểu `email` sinh `code = VALIDATION_EMAIL`, `rule = "email"`. Message MUST NOT chứa giá trị ô.

#### Scenario: Số hợp lệ
- **WHEN** field `age` kiểu `number` nhận lần lượt `"42"`, `"-3.50"`, `"007"`
- **THEN** giá trị lần lượt là `BigDecimal` `42`, `-3.50`, `7`

#### Scenario: Số không hợp lệ
- **WHEN** field `age` kiểu `number` nhận lần lượt `"1,234"`, `"1e3"`, `" 42"`, `"+5"`, `".5"`
- **THEN** mỗi giá trị sinh lỗi `VALIDATION_TYPE` với message `Value is not a valid number.`

#### Scenario: Boolean
- **WHEN** field `active` kiểu `boolean` nhận lần lượt `"TRUE"`, `"0"`, `"yes"`
- **THEN** hai giá trị đầu cho `true` và `false`; giá trị thứ ba sinh lỗi `VALIDATION_TYPE` với message `Value is not a valid boolean (true/false/1/0).`

#### Scenario: Ngày
- **WHEN** field `dob` kiểu `date` nhận lần lượt `"1990-12-25"`, `"2024-02-30"`, `"25/12/1990"`, `"1990-1-5"`
- **THEN** giá trị đầu cho `LocalDate` `1990-12-25`; ba giá trị sau sinh lỗi `VALIDATION_TYPE` với message `Value is not a valid date (yyyy-MM-dd).`

#### Scenario: Field kiểu email
- **WHEN** field `email` kiểu `email` nhận lần lượt `"an@example.com"`, `"an@example"`, `"an example@x.com"`, `"a@b@c.com"`
- **THEN** giá trị đầu hợp lệ; ba giá trị sau sinh lỗi `VALIDATION_EMAIL`, `rule = "email"`, message `Value is not a valid email address.`

#### Scenario: Chuỗi giữ nguyên
- **WHEN** field `note` kiểu `string` nhận `" a "`
- **THEN** giá trị là `" a "` và không có lỗi

### Requirement: Rule email cho field string
Hệ thống SHALL cho phép người dùng thêm rule `email` cho field kiểu `string`. Rule này kiểm giá trị theo cùng regex với field type `email`, và khi sai sinh lỗi `code = VALIDATION_EMAIL`, `rule = "email"`.

#### Scenario: Chuỗi không phải email
- **WHEN** field `contact` kiểu `string` có rule `email` và nhận `"an.example.com"`
- **THEN** field có lỗi `VALIDATION_EMAIL` với `rule = "email"`

### Requirement: Thứ tự rule và chỉ báo lỗi đầu tiên của mỗi field
Với mỗi field, hệ thống SHALL chạy rule theo thứ tự cố định `required → type → email → unique`, không phụ thuộc thứ tự trong cấu hình. Hệ thống SHALL dừng ở lỗi đầu tiên, nên mỗi field có tối đa một lỗi validation trên một row. Một row vẫn MUST có thể có nhiều lỗi ở nhiều field khác nhau.

#### Scenario: Ô trống không kéo theo lỗi type
- **WHEN** field `email` kiểu `email`, required, có rule `unique`, và nhận `""`
- **THEN** field chỉ có một lỗi là `VALIDATION_REQUIRED`

#### Scenario: Sai định dạng email thì không kiểm unique
- **WHEN** field `email` kiểu `email` có rule `unique` và nhận `"bad"`
- **THEN** field chỉ có một lỗi là `VALIDATION_EMAIL`

#### Scenario: Nhiều lỗi trên một row
- **WHEN** một row có `email = "bad"` (kiểu `email`) và `age = "abc"` (kiểu `number`)
- **THEN** row có 2 lỗi: `VALIDATION_EMAIL` ở `email` và `VALIDATION_TYPE` ở `age`

### Requirement: Field optional rỗng bỏ qua mọi rule
Khi field không `required` và giá trị sau transformation rỗng, hệ thống SHALL bỏ qua mọi rule của field đó (kể cả `type`, `email`, `unique`). Giá trị output của field SHALL là `null`.

#### Scenario: Số optional để trống
- **WHEN** field `age` kiểu `number`, không required, có rule `unique`, và nhận `"   "`
- **THEN** field không có lỗi và giá trị là `null`

### Requirement: Rule unique chỉ ghi nhận giá trị của row hợp lệ
Rule `unique` SHALL so giá trị đã ép kiểu ở dạng canonical:
- `BigDecimal` được bỏ số 0 thừa ở phần thập phân;
- `LocalDate` và `Boolean` so trực tiếp;
- `String` so khớp chính xác, phân biệt hoa thường.

Mỗi field có tập giá trị đã ghi nhận riêng. Một giá trị SHALL chỉ được ghi nhận sau khi row chứa nó không có lỗi nào. Giá trị đã được ghi nhận mà gặp lại SHALL sinh lỗi `code = VALIDATION_UNIQUE`, `rule = "unique"`, `message = "Duplicate value; first seen in row <n>."`, với `<n>` là row đã ghi nhận giá trị đó. Giá trị rỗng MUST NOT tham gia kiểm unique.

#### Scenario: Bản đầu tiên thắng
- **WHEN** field `email` có rule `unique`; row 2 hợp lệ với `"a@x.com"`; row 3 có `"a@x.com"`
- **THEN** row 3 có lỗi `VALIDATION_UNIQUE` với message `Duplicate value; first seen in row 2.`

#### Scenario: Row lỗi không giữ chỗ giá trị
- **WHEN** field `email` có rule `unique`; row 2 có `"a@x.com"` nhưng lỗi ở field khác; row 3 có `"a@x.com"` và hợp lệ mọi mặt
- **THEN** row 3 không có lỗi unique và được tính là hợp lệ

#### Scenario: So số theo giá trị
- **WHEN** field `code` kiểu `number` có rule `unique`; row 2 hợp lệ với `"1.0"`; row 3 có `"1.00"`; row 4 có `"1"`
- **THEN** row 3 và row 4 đều có lỗi `VALIDATION_UNIQUE` với message `Duplicate value; first seen in row 2.`

#### Scenario: Chuỗi phân biệt hoa thường
- **WHEN** field `email` có rule `unique`; row 2 hợp lệ với `"A@x.com"`; row 3 có `"a@x.com"`
- **THEN** row 3 không có lỗi unique

#### Scenario: Các field độc lập
- **WHEN** field `email` và field `code` đều có rule `unique`, và row 2 hợp lệ có `email = "x@y.com"`, `code = "x@y.com"`
- **THEN** không có lỗi unique nào

### Requirement: Lỗi bất ngờ trong rule không làm dừng job
Khi một rule ném exception không lường trước, hệ thống SHALL chuyển exception đó thành lỗi của field, với `code` là mã của rule đó và `message = "Unexpected error while applying rule '<rule>'."`. Pipeline SHALL tiếp tục xử lý các row khác. Message của exception MUST NOT xuất hiện trong lỗi.

#### Scenario: Rule có bug
- **WHEN** rule `unique` ném `IllegalStateException("boom")` khi xử lý row 5
- **THEN** row 5 có lỗi `VALIDATION_UNIQUE` với message `Unexpected error while applying rule 'unique'.`
- **AND** các row khác vẫn được validate

### Requirement: Cấu hình validation qua API
Hệ thống SHALL nhận `PUT /api/import-sessions/{id}/validations` với body `{ "validations": [{ targetField, type, params? }] }`. Request thay toàn bộ cấu hình validation của session. Khi hợp lệ, hệ thống SHALL:
- chỉ lưu rule `email` và `unique`, sắp theo vị trí field trong schema rồi theo thứ tự `email` → `unique`;
- tính lại readiness và status;
- trả `200` với `{ session, warnings }`.

Mảng rỗng SHALL được chấp nhận. Thiếu `params` (hoặc `params: null`) SHALL được coi như `{}`, và MUST NOT làm request thất bại.

#### Scenario: Rule không có params
- **WHEN** field `email` có kiểu `email` và field `contact` có kiểu `string`, và client gửi `{"validations": [{"targetField": "email", "type": "unique"}, {"targetField": "contact", "type": "email"}]}`, không rule nào có `params`
- **THEN** hệ thống trả `200` và lưu cả hai rule

#### Scenario: Lưu rule unique
- **WHEN** schema có field `email` (email) và client gửi `{"validations": [{"targetField": "email", "type": "unique"}]}`
- **THEN** hệ thống trả `200` với `warnings = []`
- **AND** `GET /api/import-sessions/{id}` trả `config.validations.validations = [{targetField: "email", type: "unique"}]`

### Requirement: Rule required và type không cấu hình qua payload
Khi payload có rule `required` hoặc `type`, hệ thống SHALL bỏ qua rule đó và trả warning `{ field, code: "RULE_IMPLIED_BY_SCHEMA", message: "Rule '<type>' is derived from the schema and was ignored." }`. Khi payload có rule `email` cho field kiểu `email`, hệ thống SHALL bỏ qua rule đó và trả warning `RULE_IMPLIED_BY_SCHEMA` với message `Rule 'email' is implied by field type email and was ignored.` Các trường hợp này MUST NOT làm request thất bại.

#### Scenario: Gửi required
- **WHEN** client gửi `[{"targetField": "name", "type": "required"}, {"targetField": "email", "type": "unique"}]`
- **THEN** hệ thống trả `200` với một warning `RULE_IMPLIED_BY_SCHEMA` cho field `name`
- **AND** cấu hình được lưu chỉ gồm `email/unique`

#### Scenario: Gửi email cho field kiểu email
- **WHEN** field `email` có kiểu `email` và client gửi `[{"targetField": "email", "type": "email"}]`
- **THEN** hệ thống trả `200` với warning `RULE_IMPLIED_BY_SCHEMA` cho field `email`, và cấu hình được lưu rỗng

### Requirement: Kiểm cấu hình validation
Hệ thống SHALL kiểm toàn bộ cấu hình trước khi lưu. Khi có lỗi, hệ thống SHALL trả `422` với `code = CONFIG_INVALID` và `errors[]` gồm **mọi** lỗi, mỗi lỗi có `code = "CONFIG_INVALID"`. Khi có lỗi, hệ thống MUST NOT lưu gì.

Các trường hợp lỗi:
- target field không tồn tại;
- type lạ;
- `email` trên field kiểu `number`, `boolean` hoặc `date`;
- rule trùng trên cùng một field;
- params có key lạ.

#### Scenario: Email trên field số
- **WHEN** field `age` kiểu `number` và client gửi `[{"targetField": "age", "type": "email"}]`
- **THEN** hệ thống trả `422`, `errors[0] = {field: "age", code: "CONFIG_INVALID", message: "Rule 'email' only applies to fields of type string."}`

#### Scenario: Rule lạ
- **WHEN** client gửi `[{"targetField": "name", "type": "regex"}]`
- **THEN** hệ thống trả `422` với message `Unknown validation rule 'regex'.`

#### Scenario: Rule trùng
- **WHEN** client gửi `[{"targetField": "note", "type": "unique"}, {"targetField": "note", "type": "unique"}]`
- **THEN** hệ thống trả `422` với message `Duplicate rule 'unique' for field 'note'.`

#### Scenario: Target field không tồn tại
- **WHEN** client gửi `[{"targetField": "phone", "type": "unique"}]` và schema không có `phone`
- **THEN** hệ thống trả `422` với message `Target field does not exist.`

### Requirement: Request validation sai cú pháp
Hệ thống SHALL trả `400` với `code = REQUEST_INVALID` khi body không phải JSON hợp lệ, thiếu mảng `validations`, hoặc có giá trị sai kiểu JSON.

#### Scenario: Thiếu mảng validations
- **WHEN** client gửi `{}`
- **THEN** hệ thống trả `400` với `code = REQUEST_INVALID`

### Requirement: Prune validation khi schema đổi
Khi schema của session được cập nhật, hệ thống SHALL:
- bỏ mọi rule của field không còn trong schema;
- bỏ rule `email` của field không còn kiểu `string`, kể cả field đổi sang kiểu `email`.

Mỗi field hoặc rule bị bỏ SHALL kèm một warning `CONFIG_PRUNED`. Rule `unique` MUST được giữ khi field chỉ đổi kiểu.

#### Scenario: Field bị xoá
- **WHEN** session có rule `unique` cho `phone`, và schema mới không còn `phone`
- **THEN** response của `PUT /schema` có warning `{field: "phone", code: "CONFIG_PRUNED", message: "Validation rules removed because field 'phone' no longer exists."}`

#### Scenario: Field đổi khỏi kiểu string
- **WHEN** field `note` kiểu `string` có rule `email` và `unique`, rồi schema đổi `note` sang kiểu `number`
- **THEN** rule `email` bị bỏ, kèm warning `Rule 'email' removed because field 'note' is no longer of type string.`
- **AND** rule `unique` của `note` được giữ

### Requirement: Cập nhật validation theo trạng thái session
Hệ thống SHALL trả `404` với `code = SESSION_NOT_FOUND` khi session không tồn tại, và `409` với `code = SESSION_STATE_INVALID` khi session đang `FAILED`. Các lệnh cập nhật validation của cùng một session SHALL chạy lần lượt theo khoá session (D11).

#### Scenario: Session FAILED
- **WHEN** session đang `FAILED` và client gửi `PUT /validations` hợp lệ
- **THEN** hệ thống trả `409` với `code = SESSION_STATE_INVALID` và không lưu gì

#### Scenario: Session không tồn tại
- **WHEN** client gửi `PUT /api/import-sessions/{uuid-chưa-tạo}/validations`
- **THEN** hệ thống trả `404` với `code = SESSION_NOT_FOUND` (FE nhận biết session đã mất nhờ `code`, không nhờ status)
