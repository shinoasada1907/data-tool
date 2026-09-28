## ADDED Requirements

### Requirement: Thứ tự và phạm vi của validation rule theo field
Khi kiểm một giá trị (sau transformation, nếu tool có transformation) theo một field của schema, hệ thống SHALL chạy các rule mà field có theo thứ tự cố định:

`required → type → email → minLength → maxLength → pattern → min → max → unique`

Hệ thống SHALL dừng ở rule đầu tiên thất bại và báo đúng một lỗi cho field đó. Một row có thể có nhiều lỗi, mỗi field tối đa một lỗi.

Giá trị rỗng (`null`, hoặc chỉ gồm khoảng trắng kể cả NBSP):
- với field required: SHALL báo `VALIDATION_REQUIRED` (`Value is required.`);
- với field optional: SHALL hợp lệ, và mọi rule khác MUST NOT chạy.

#### Scenario: Lỗi đầu tiên thắng
- **WHEN** field `code` kiểu `string` có `minLength` là `5` và `pattern` là `[A-Z]+`, và giá trị là `ab`
- **THEN** lỗi là `VALIDATION_MIN_LENGTH`, không có lỗi `VALIDATION_PATTERN`

#### Scenario: Optional rỗng bỏ qua mọi rule
- **WHEN** field `phone` optional kiểu `string` có `pattern` là `[0-9]{10}`, và giá trị là chuỗi rỗng
- **THEN** giá trị hợp lệ

### Requirement: Rule độ dài, pattern và khoảng giá trị
Hệ thống SHALL kiểm các constraint sau. Message MUST NOT chứa giá trị ô.

| Constraint | Áp cho | Luật | Lỗi (`code`, `message`) |
|---|---|---|---|
| `minLength` / `maxLength` | `string`, `email` | đếm số ký tự Unicode (code point) | `VALIDATION_MIN_LENGTH`, `Value must have at least N characters.` / `VALIDATION_MAX_LENGTH`, `Value must have at most N characters.` |
| `pattern` | `string`, `email` | khớp **toàn bộ** giá trị theo cú pháp RE2 | `VALIDATION_PATTERN`, `Value does not match the required pattern.` |
| `min` / `max` | `number` | so theo giá trị số sau khi ép kiểu | `VALIDATION_MIN`, `Value must be at least <min>.` / `VALIDATION_MAX`, `Value must be at most <max>.` |

Việc khớp pattern MUST chạy trong thời gian tuyến tính theo độ dài giá trị, bất kể pattern là gì.

#### Scenario: Độ dài tính theo ký tự Unicode
- **WHEN** field `name` có `maxLength` là `6`, và giá trị là `Nguyễn` viết dạng NFC
- **THEN** giá trị hợp lệ

#### Scenario: Pattern khớp toàn chuỗi
- **WHEN** field `sku` có `pattern` là `[A-Z]{3}-[0-9]{3}`, và giá trị là `ABC-123x`
- **THEN** lỗi là `VALIDATION_PATTERN`

#### Scenario: Pattern gây backtracking không làm treo
- **WHEN** field `s` có `pattern` là `(a+)+$`, và giá trị là 30 000 ký tự `a` theo sau là `!`
- **THEN** lỗi `VALIDATION_PATTERN` được trả về trong chưa tới 1 giây

#### Scenario: Khoảng giá trị số
- **WHEN** field `age` kiểu `number` có `min` là `18` và `max` là `65`, với các giá trị `17.99`, `18`, `65.0`, `66`
- **THEN** kết quả lần lượt là lỗi `VALIDATION_MIN` (`Value must be at least 18.`), hợp lệ, hợp lệ, lỗi `VALIDATION_MAX` (`Value must be at most 65.`)

### Requirement: Hai phạm vi của unique
Rule `unique` SHALL so giá trị sau khi ép kiểu: số theo giá trị (`1`, `1.0`, `1.00` bằng nhau), chuỗi phân biệt hoa thường. Hệ thống SHALL hỗ trợ hai phạm vi:
- `VALID_ROWS`: một giá trị chỉ được tính là "đã gặp" khi row chứa nó hợp lệ ở mọi field. Đây là luật V0.1 của Importer.
- `ALL_ROWS`: lần gặp đầu tiên của một giá trị luôn được ghi nhận, dù row đó có lỗi ở field khác. Mọi lần gặp sau đều là lỗi.

Lỗi SHALL có `code` là `VALIDATION_UNIQUE` và `message` là `Duplicate value; first seen in row N.`, trong đó N là số dòng của lần gặp đã được ghi nhận. Bộ nhớ dùng để nhớ giá trị MUST NOT phụ thuộc độ dài giá trị.

#### Scenario: ALL_ROWS báo trùng với row lỗi
- **WHEN** phạm vi là `ALL_ROWS`; row 2 có `email` là `a@x.com` và `age` sai kiểu; row 3 có `email` là `a@x.com` và mọi field khác hợp lệ
- **THEN** row 3 có lỗi `VALIDATION_UNIQUE` với message `Duplicate value; first seen in row 2.`

#### Scenario: VALID_ROWS bỏ qua row lỗi
- **WHEN** cùng dữ liệu như trên, nhưng phạm vi là `VALID_ROWS`
- **THEN** row 3 hợp lệ

#### Scenario: Số bằng nhau theo giá trị
- **WHEN** field `code` kiểu `number` có `unique`, phạm vi `ALL_ROWS`; row 2 là `1.0` và row 3 là `1`
- **THEN** row 3 có lỗi `VALIDATION_UNIQUE`

### Requirement: Transformation titleCase
Transformation `titleCase` SHALL viết hoa ký tự đầu của mỗi từ và viết thường phần còn lại của từ đó. Từ là chuỗi ký tự không phải khoảng trắng. Mọi khoảng trắng SHALL được giữ nguyên. Giá trị `null` giữ `null`. `titleCase` không nhận tham số.

#### Scenario: Tên tiếng Việt
- **WHEN** `titleCase` được áp cho `nguyễn  VĂN a`
- **THEN** kết quả là `Nguyễn  Văn A`

### Requirement: Transformation replace
Transformation `replace` SHALL thay giá trị theo nghĩa đen, không dùng regex. Tham số:
- `find`: bắt buộc, 1–1 000 ký tự;
- `replaceWith`: mặc định `""`;
- `match`: `exact` (mặc định) hoặc `contains`;
  - `exact`: cả giá trị bằng `find` thì thay bằng `replaceWith`;
  - `contains`: thay mọi lần xuất hiện của `find`;
- `caseSensitive`: `true` (mặc định) hoặc `false`.

Giá trị `null` giữ `null`. Tham số sai SHALL là lỗi cấu hình với message theo mẫu transformation hiện hành.

#### Scenario: Thay toàn bộ giá trị
- **WHEN** `replace` với `find` là `N/A`, `replaceWith` là `""`, áp cho `N/A` và `N/A2`
- **THEN** kết quả lần lượt là `""` và `N/A2`

#### Scenario: Thay mọi lần xuất hiện, không phân biệt hoa thường
- **WHEN** `replace` với `find` là `hn`, `replaceWith` là `Hà Nội`, `match` là `contains`, `caseSensitive` là `false`, áp cho `HN-hn`
- **THEN** kết quả là `Hà Nội-Hà Nội`

#### Scenario: Ký tự đặc biệt của regex được hiểu theo nghĩa đen
- **WHEN** `replace` với `find` là `.`, `replaceWith` là `,`, `match` là `contains`, áp cho `1.5.0`
- **THEN** kết quả là `1,5,0`

### Requirement: Transformation normalizeNull
Transformation `normalizeNull` SHALL đổi thành `null` mọi giá trị mà sau khi trim bằng chuỗi rỗng hoặc bằng một token trong danh sách `tokens`. Mặc định `tokens` là `null`, `n/a`, `na`, `none`, `-`. So không phân biệt hoa thường, trừ khi `caseSensitive` là `true`. Giá trị không khớp SHALL giữ nguyên, không bị trim. Danh sách tối đa 50 token.

#### Scenario: Các cách viết rỗng thường gặp
- **WHEN** `normalizeNull` với tham số mặc định, áp cho `N/A`, ` - `, `NULL`, `  `, `Hà Nội `
- **THEN** kết quả lần lượt là `null`, `null`, `null`, `null`, `Hà Nội ` (giữ khoảng trắng cuối)

#### Scenario: Token tuỳ chọn
- **WHEN** `normalizeNull` với `tokens` là `["không có"]`, áp cho `Không Có` và `N/A`
- **THEN** kết quả lần lượt là `null` và `N/A`

### Requirement: Mỗi tool chỉ dùng tập transformation của mình
Hệ thống SHALL có một catalog transformation dùng chung gồm `trim`, `uppercase`, `lowercase`, `titleCase`, `defaultValue`, `dateFormat`, `replace`, `normalizeNull`. Mỗi tool SHALL chỉ nhận các transformation trong tập mà tool đó công bố. Importer MUST tiếp tục chỉ nhận 5 transformation của V0.1.

#### Scenario: Importer từ chối transformation mới
- **WHEN** `PUT /api/import-sessions/{id}/transformations` có một step `titleCase`
- **THEN** hệ thống trả `422` với `code` là `CONFIG_INVALID`, như với mọi type không hỗ trợ
