## ADDED Requirements

### Requirement: Mô hình schema dữ liệu độc lập domain
Hệ thống SHALL biểu diễn một schema dữ liệu gồm `name` và danh sách field có thứ tự. Mỗi field có:
- `name`;
- `type`, là một trong `string`, `number`, `boolean`, `date`, `email`;
- `required`;
- `constraints` gồm `unique`, `min`, `max`, `minLength`, `maxLength`, `pattern`, `format`, `defaultValue`, cái nào cũng có thể vắng.

Thứ tự field SHALL là thứ tự trong danh sách. Schema MUST NOT chứa khái niệm của domain cụ thể nào.

#### Scenario: Schema tối thiểu
- **WHEN** định nghĩa schema `Khách hàng` với một field `{name: "email", type: "email", required: true}`, không có constraint
- **THEN** schema hợp lệ, có một field `email` kiểu `email`, `required` là `true` và `unique` là `false`

### Requirement: Kiểm định nghĩa schema và chỉ đúng chỗ sai
Khi kiểm một định nghĩa schema, hệ thống SHALL trả **mọi** vi phạm, không dừng ở vi phạm đầu tiên, theo thứ tự: tên schema trước, rồi từng field theo index. Mỗi vi phạm có `code`, `message`, `field` (tên field sau trim, hoặc `null`), và `pointer` là JSON Pointer tới chỗ sai.

| Luật | `code` | `pointer` |
|---|---|---|
| tên schema 1–200 ký tự sau trim | `SCHEMA_NAME_INVALID` | `/name` |
| có 1–500 field | `SCHEMA_FIELDS_INVALID` | `/fields` |
| tên field sau trim dài 1–100 | `FIELD_NAME_INVALID` | `/fields/i/name` |
| tên field duy nhất, không phân biệt hoa thường | `FIELD_NAME_DUPLICATE` | `/fields/i/name` |
| `type` là một trong 5 kiểu | `FIELD_TYPE_INVALID` | `/fields/i/type` |
| `min`/`max` chỉ cho `number`, và `min ≤ max` | `CONSTRAINT_INVALID` | `/fields/i/constraints/<tên>` |
| `minLength`/`maxLength` chỉ cho `string`/`email`, trong khoảng 0–32 767, và `minLength ≤ maxLength` | `CONSTRAINT_INVALID` | `/fields/i/constraints/<tên>` |
| `pattern` chỉ cho `string`/`email`, dài 1–500, là regex RE2 hợp lệ | `CONSTRAINT_INVALID` | `/fields/i/constraints/pattern` |
| `format` chỉ cho `date`, là pattern ngày hợp lệ theo luật `dateFormat` hiện hành | `CONSTRAINT_INVALID` | `/fields/i/constraints/format` |
| `defaultValue` ép được sang kiểu của field và thoả các constraint khác (trừ `unique`) | `CONSTRAINT_INVALID` | `/fields/i/constraints/defaultValue` |

#### Scenario: Nhiều lỗi cùng lúc
- **WHEN** định nghĩa có `name` là `""` và hai field `{name: "Email", type: "email"}` và `{name: "email", type: "text"}`
- **THEN** kết quả có đúng 3 vi phạm theo thứ tự:
  1. `SCHEMA_NAME_INVALID` tại `/name`;
  2. `FIELD_NAME_DUPLICATE` tại `/fields/1/name`;
  3. `FIELD_TYPE_INVALID` tại `/fields/1/type`

#### Scenario: Constraint sai kiểu
- **WHEN** field `age` kiểu `string` có `min` là `18`
- **THEN** có vi phạm `CONSTRAINT_INVALID` tại `/fields/0/constraints/min`, `message` là `'min' is only allowed on number fields.`

#### Scenario: min lớn hơn max
- **WHEN** field `age` kiểu `number` có `min` là `65` và `max` là `18`
- **THEN** có vi phạm `CONSTRAINT_INVALID` tại `/fields/0/constraints/max`, `message` là `'max' must not be less than 'min'.`

#### Scenario: Pattern không phải RE2
- **WHEN** field `code` kiểu `string` có `pattern` là `(a)\1`
- **THEN** có vi phạm `CONSTRAINT_INVALID` tại `/fields/0/constraints/pattern`, `message` là `'pattern' is not a valid RE2 regular expression.`

#### Scenario: Giá trị mặc định không hợp kiểu
- **WHEN** field `qty` kiểu `number` có `min` là `1` và `defaultValue` là `0`
- **THEN** có vi phạm `CONSTRAINT_INVALID` tại `/fields/0/constraints/defaultValue`

### Requirement: Ép kiểu dùng chung
Hệ thống SHALL ép một giá trị chữ khác rỗng sang kiểu của field theo đúng một bộ luật dùng chung cho mọi tool:

| Kiểu | Chấp nhận | Lỗi (`code`, `message`) |
|---|---|---|
| `number` | `^-?[0-9]+(\.[0-9]+)?$`, tối đa 1 000 ký tự | `VALIDATION_TYPE`, `Value is not a valid number.` |
| `boolean` | `true`/`false` (không phân biệt hoa thường) hoặc `1`/`0` | `VALIDATION_TYPE`, `Value is not a valid boolean (true/false/1/0).` |
| `date` không có `format` | ngày ISO `yyyy-MM-dd` có thật | `VALIDATION_TYPE`, `Value is not a valid date (yyyy-MM-dd).` |
| `date` có `format` | ngày có thật theo đúng pattern đó, parse strict | `VALIDATION_DATE_FORMAT`, `Value is not a valid date (<format>).` |
| `email` | email hợp lệ | `VALIDATION_EMAIL`, `Value is not a valid email address.` |
| `string` | mọi giá trị | — |

Giá trị MUST NOT bị trim trước khi ép kiểu. Message MUST NOT chứa giá trị.

#### Scenario: Ngày theo format riêng
- **WHEN** ép `31/01/2024` sang field `date` có `format` là `dd/MM/yyyy`
- **THEN** kết quả là ngày 2024-01-31

#### Scenario: Ngày không có thật theo format riêng
- **WHEN** ép `30/02/2024` sang field `date` có `format` là `dd/MM/yyyy`
- **THEN** kết quả là lỗi `VALIDATION_DATE_FORMAT`, message là `Value is not a valid date (dd/MM/yyyy).`

#### Scenario: Luật cũ của Importer không đổi
- **WHEN** ép ` 12` (có khoảng trắng đầu) sang field `number`
- **THEN** kết quả là lỗi `VALIDATION_TYPE`, message là `Value is not a valid number.`

### Requirement: Lỗi có chỗ sai tuỳ chọn
Mỗi phần tử của `errors[]` trong response lỗi SHALL có thể mang thêm `pointer` (JSON Pointer). Khi không có `pointer`, response MUST NOT có thuộc tính `pointer`, và mọi thuộc tính khác (`field`, `code`, `message`) SHALL giữ nguyên như trước.

#### Scenario: Lỗi của Importer không đổi
- **WHEN** `PUT /api/import-sessions/{id}/schema` với hai field cùng tên `email`
- **THEN** response `422` có `errors[0]` đúng bằng `{ "field": "email", "code": "SCHEMA_INVALID", "message": "Duplicate field name." }`, không có thuộc tính `pointer`
