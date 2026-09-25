## ADDED Requirements

### Requirement: Transformation trim
Transformation `trim` SHALL bỏ ở hai đầu giá trị mọi ký tự khoảng trắng (`Character.isWhitespace` hoặc `Character.isSpaceChar`), gồm cả NBSP `U+00A0`. Khoảng trắng ở giữa giá trị MUST được giữ nguyên.

#### Scenario: Bỏ khoảng trắng hai đầu
- **WHEN** `trim` nhận `"  An  "`
- **THEN** kết quả là `"An"`

#### Scenario: Bỏ NBSP
- **WHEN** `trim` nhận `" Bình "`
- **THEN** kết quả là `"Bình"`

#### Scenario: Giữ khoảng trắng ở giữa
- **WHEN** `trim` nhận `"Nguyễn  Văn"`
- **THEN** kết quả là `"Nguyễn  Văn"`

### Requirement: Transformation uppercase và lowercase không phụ thuộc locale
Transformation `uppercase` và `lowercase` SHALL đổi chữ hoa, chữ thường theo `Locale.ROOT`. Kết quả MUST giống nhau dù locale mặc định của JVM là gì.

#### Scenario: Uppercase tiếng Việt
- **WHEN** `uppercase` nhận `"nguyễn văn an"`
- **THEN** kết quả là `"NGUYỄN VĂN AN"`

#### Scenario: Lowercase tiếng Việt
- **WHEN** `lowercase` nhận `"ĐÀ NẴNG"`
- **THEN** kết quả là `"đà nẵng"`

#### Scenario: Không bị ảnh hưởng bởi locale Thổ Nhĩ Kỳ
- **WHEN** locale mặc định của JVM là `tr` và `uppercase` nhận `"istanbul"`
- **THEN** kết quả là `"ISTANBUL"` (không phải `"İSTANBUL"`)

### Requirement: Transformation defaultValue
Transformation `defaultValue` SHALL thay giá trị rỗng bằng `params.value`. Giá trị rỗng là `null` hoặc chuỗi chỉ gồm ký tự khoảng trắng, tính cả NBSP. Giá trị không rỗng MUST được giữ nguyên.

#### Scenario: Ô trống nhận giá trị mặc định
- **WHEN** `defaultValue` với `params.value = "VN"` nhận `null`
- **THEN** kết quả là `"VN"`

#### Scenario: Chuỗi toàn khoảng trắng cũng là rỗng
- **WHEN** `defaultValue` với `params.value = "VN"` nhận `"  "`
- **THEN** kết quả là `"VN"`

#### Scenario: Giá trị có sẵn được giữ
- **WHEN** `defaultValue` với `params.value = "VN"` nhận `" JP "`
- **THEN** kết quả là `" JP "`

### Requirement: Transformation dateFormat
Transformation `dateFormat` SHALL parse giá trị theo `params.inputFormat` và ghi lại theo `params.outputFormat`; khi không có `outputFormat` thì dùng `yyyy-MM-dd`. Cả hai pattern theo cú pháp `DateTimeFormatter` của Java.

Hệ thống SHALL:
- đổi mọi chữ `y` nằm ngoài phần trong nháy đơn của pattern thành `u`;
- parse với `ResolverStyle.STRICT` và `Locale.ENGLISH`.

Ngày không tồn tại MUST bị coi là không khớp pattern. Hệ thống MUST NOT tự trim giá trị trước khi parse.

#### Scenario: Đổi dd/MM/yyyy sang ISO mặc định
- **WHEN** `dateFormat` với `inputFormat = "dd/MM/yyyy"`, không có `outputFormat`, nhận `"25/12/1990"`
- **THEN** kết quả là `"1990-12-25"`

#### Scenario: Ghi theo outputFormat
- **WHEN** `dateFormat` với `inputFormat = "yyyy-MM-dd"` và `outputFormat = "dd/MM/yyyy"` nhận `"1990-12-25"`
- **THEN** kết quả là `"25/12/1990"`

#### Scenario: Ngày không tồn tại bị từ chối
- **WHEN** `dateFormat` với `inputFormat = "dd/MM/yyyy"` nhận `"31/02/2024"`
- **THEN** transformation thất bại với message `Value does not match pattern dd/MM/yyyy`

#### Scenario: Năm nhuận
- **WHEN** `dateFormat` với `inputFormat = "dd/MM/yyyy"` nhận lần lượt `"29/02/2024"` và `"29/02/2023"`
- **THEN** giá trị thứ nhất cho `"2024-02-29"`, giá trị thứ hai thất bại

#### Scenario: Tên tháng tiếng Anh
- **WHEN** `dateFormat` với `inputFormat = "dd MMM yyyy"` nhận `"05 Jan 2024"`
- **THEN** kết quả là `"2024-01-05"`

#### Scenario: Không tự trim
- **WHEN** `dateFormat` với `inputFormat = "dd/MM/yyyy"` nhận `" 25/12/1990"`
- **THEN** transformation thất bại

### Requirement: Giá trị rỗng đi qua transformation không đổi
Mọi transformation trừ `defaultValue` SHALL trả nguyên giá trị khi giá trị đó rỗng: `null` giữ `null`, chuỗi toàn khoảng trắng giữ nguyên chuỗi đó. Chúng MUST NOT báo lỗi với giá trị rỗng.

#### Scenario: dateFormat gặp ô trống
- **WHEN** `dateFormat` với `inputFormat = "dd/MM/yyyy"` nhận `null`
- **THEN** kết quả là `null` và không có lỗi

#### Scenario: uppercase gặp chuỗi toàn khoảng trắng
- **WHEN** `uppercase` nhận `"   "`
- **THEN** kết quả là `"   "`

### Requirement: Các bước transformation chạy theo order
Với mỗi target field, hệ thống SHALL chạy các bước transformation theo `order` tăng dần, không phụ thuộc thứ tự các bước trong request. Đầu ra của bước trước là đầu vào của bước sau.

#### Scenario: Trim rồi uppercase
- **WHEN** field `name` có `[{order: 1, type: "uppercase"}, {order: 0, type: "trim"}]` và giá trị `"  an "`
- **THEN** giá trị sau transformation là `"AN"`

#### Scenario: Trim trước dateFormat
- **WHEN** field `dob` có `[{order: 0, type: "trim"}, {order: 1, type: "dateFormat", params: {inputFormat: "dd/MM/yyyy"}}]` và giá trị `" 25/12/1990 "`
- **THEN** giá trị sau transformation là `"1990-12-25"`

### Requirement: Lỗi transformation có cấu trúc và dừng field
Khi một bước transformation thất bại, hệ thống SHALL dừng các bước còn lại và bỏ qua validation của field đó. Hệ thống SHALL trả một lỗi có cấu trúc gồm:
- `rule`: type của bước lỗi;
- `step`: `order` của bước lỗi;
- `message`: tiếng Anh, MUST NOT chứa giá trị ô.

Khi pipeline ghi lỗi này vào kết quả, lỗi SHALL mang `stage = TRANSFORMATION`, `code = TRANSFORMATION_FAILED`, và `sourceValue` là giá trị trước mọi transformation.

#### Scenario: dateFormat lỗi ở bước đầu
- **WHEN** field `dob` có `[{order: 0, type: "dateFormat", params: {inputFormat: "dd/MM/yyyy"}}, {order: 1, type: "uppercase"}]` và giá trị `"31/02/2024"`
- **THEN** kết quả là lỗi với `rule = "dateFormat"`, `step = 0`, `message = "Value does not match pattern dd/MM/yyyy"`
- **AND** bước `uppercase` không chạy

#### Scenario: sourceValue là giá trị gốc
- **WHEN** field `dob` có `[{order: 0, type: "trim"}, {order: 1, type: "dateFormat", params: {inputFormat: "dd/MM/yyyy"}}]` và giá trị gốc `" 31/02/2024 "`
- **THEN** lỗi trong kết quả có `step = 1` và `sourceValue = " 31/02/2024 "`

### Requirement: Lỗi bất ngờ trong transformation không làm dừng job
Khi một transformation ném exception không lường trước, hệ thống SHALL chuyển exception đó thành lỗi của field với `message = "Unexpected error while applying transformation."` và tiếp tục xử lý các row khác. Hệ thống MUST NOT đưa message của exception vào lỗi.

#### Scenario: Transformation có bug
- **WHEN** một transformation ném `IllegalStateException("boom")` khi xử lý row 3
- **THEN** row 3 có lỗi `TRANSFORMATION_FAILED` với message `Unexpected error while applying transformation.`
- **AND** các row 2 và 4 vẫn được xử lý bình thường

### Requirement: Cấu hình transformation qua API
Hệ thống SHALL nhận `PUT /api/import-sessions/{id}/transformations` với body `{ "transformations": [{ targetField, order, type, params? }] }`. Request thay toàn bộ cấu hình transformation của session. Khi hợp lệ, hệ thống SHALL:
- lưu danh sách, sắp theo vị trí field trong schema rồi theo `order`;
- tính lại readiness và status của session;
- trả `200` với `{ session, warnings }`.

Mảng rỗng SHALL được chấp nhận, nghĩa là xoá mọi transformation. Thiếu `params` (hoặc `params: null`) SHALL được coi như `{}`, và MUST NOT bị báo lỗi với transformation không cần tham số.

#### Scenario: Transformation không có params
- **WHEN** client gửi `{"transformations": [{"targetField": "name", "order": 0, "type": "trim"}, {"targetField": "name", "order": 1, "type": "uppercase"}, {"targetField": "note", "order": 0, "type": "lowercase"}]}`, không bước nào có `params`
- **THEN** hệ thống trả `200` và không có lỗi

#### Scenario: Lưu cấu hình hợp lệ
- **WHEN** schema có field `name` (string) và `dob` (date), và client gửi `{"transformations": [{"targetField": "dob", "order": 0, "type": "dateFormat", "params": {"inputFormat": "dd/MM/yyyy"}}, {"targetField": "name", "order": 0, "type": "trim"}]}`
- **THEN** hệ thống trả `200` với `warnings = []`
- **AND** `GET /api/import-sessions/{id}` trả `config.transformations.transformations` theo thứ tự: bước `trim` của `name`, rồi bước `dateFormat` của `dob`

#### Scenario: Xoá mọi transformation
- **WHEN** client gửi `{"transformations": []}`
- **THEN** hệ thống trả `200` và cấu hình transformation của session rỗng

### Requirement: Kiểm cấu hình transformation
Hệ thống SHALL kiểm toàn bộ cấu hình trước khi lưu. Khi có lỗi, hệ thống SHALL trả `422` với `code = CONFIG_INVALID`, có `errors[]` liệt kê **mọi** lỗi, mỗi lỗi là `{ field, code: "CONFIG_INVALID", message }`. Hệ thống MUST NOT lưu bất kỳ phần nào của cấu hình lỗi.

Các trường hợp lỗi:
- target field không tồn tại;
- `order` thiếu, âm, hoặc trùng trong cùng field;
- type không nằm trong `trim`, `uppercase`, `lowercase`, `defaultValue`, `dateFormat`;
- params có key lạ;
- `defaultValue` thiếu `value`, hoặc `value` rỗng;
- `dateFormat` thiếu `inputFormat`;
- pattern sai cú pháp, hoặc `inputFormat` không đủ năm, tháng, ngày;
- `outputFormat` chứa giờ;
- field kiểu `date` có `outputFormat` khác `yyyy-MM-dd`.

#### Scenario: Target field không tồn tại
- **WHEN** schema không có field `phone` và client gửi `[{"targetField": "phone", "order": 0, "type": "trim"}]`
- **THEN** hệ thống trả `422`, `code = CONFIG_INVALID`, `errors[0] = {field: "phone", code: "CONFIG_INVALID", message: "Target field does not exist."}`

#### Scenario: Type lạ
- **WHEN** client gửi `[{"targetField": "name", "order": 0, "type": "replace"}]`
- **THEN** hệ thống trả `422` với message `Unknown transformation type 'replace'.`

#### Scenario: Thiếu params bắt buộc
- **WHEN** client gửi `[{"targetField": "dob", "order": 0, "type": "dateFormat", "params": {}}]`
- **THEN** hệ thống trả `422` với message `Parameter 'inputFormat' is required.`

#### Scenario: Pattern sai cú pháp
- **WHEN** client gửi `dateFormat` với `inputFormat = "dd/MM/yyyyb"`
- **THEN** hệ thống trả `422` với message `Invalid date pattern 'dd/MM/yyyyb'.`

#### Scenario: Pattern thiếu ngày
- **WHEN** client gửi `dateFormat` với `inputFormat = "MM/yyyy"`
- **THEN** hệ thống trả `422` với message `Date pattern 'MM/yyyy' must contain year, month and day.`

#### Scenario: Order trùng trong một field
- **WHEN** client gửi `[{"targetField": "name", "order": 0, "type": "trim"}, {"targetField": "name", "order": 0, "type": "uppercase"}]`
- **THEN** hệ thống trả `422` với message `Duplicate order 0 for field 'name'.`

#### Scenario: Field date phải ghi ra ISO
- **WHEN** field `dob` kiểu `date` và client gửi `dateFormat` với `outputFormat = "dd/MM/yyyy"`
- **THEN** hệ thống trả `422` với message `Fields of type date must output yyyy-MM-dd.`

#### Scenario: Field date nhận outputFormat ISO viết tường minh
- **WHEN** field `dob` kiểu `date` và client gửi `dateFormat` với `inputFormat = "dd/MM/yyyy"` và `outputFormat` lần lượt là `"yyyy-MM-dd"` và `"uuuu-MM-dd"`
- **THEN** cả hai request đều trả `200`, vì BE so `outputFormat` với ISO sau khi đã đổi `y` → `u`

#### Scenario: Field string được đổi định dạng ngày tuỳ ý
- **WHEN** field `note` kiểu `string` và client gửi `dateFormat` với `inputFormat = "dd/MM/yyyy"` và `outputFormat = "dd/MM/yyyy"`
- **THEN** hệ thống trả `200`

#### Scenario: Nhiều lỗi được trả cùng lúc và không lưu gì
- **WHEN** client gửi hai bước, một bước có type lạ, một bước trỏ tới field không tồn tại
- **THEN** hệ thống trả `422` với `errors` gồm đúng 2 phần tử
- **AND** cấu hình transformation cũ của session không đổi

### Requirement: Request transformation sai cú pháp
Hệ thống SHALL trả `400` với `code = REQUEST_INVALID` khi body không phải JSON hợp lệ, thiếu mảng `transformations`, hoặc có giá trị sai kiểu JSON.

#### Scenario: Order không phải số
- **WHEN** client gửi `{"transformations": [{"targetField": "name", "order": "abc", "type": "trim"}]}`
- **THEN** hệ thống trả `400` với `code = REQUEST_INVALID`

#### Scenario: Thiếu mảng transformations
- **WHEN** client gửi `{}`
- **THEN** hệ thống trả `400` với `code = REQUEST_INVALID`

### Requirement: Prune transformation khi schema đổi
Khi schema của session được cập nhật, hệ thống SHALL:
- bỏ mọi bước transformation của field không còn trong schema, kèm một warning `CONFIG_PRUNED` cho mỗi field;
- bỏ bước `dateFormat` có output khác `yyyy-MM-dd` của field vừa chuyển sang kiểu `date`, kèm một warning `CONFIG_PRUNED` cho mỗi bước.

Bước transformation của field vẫn còn và vẫn hợp lệ MUST được giữ nguyên.

#### Scenario: Field bị xoá khỏi schema
- **WHEN** session có transformation cho `phone` và `name`, và client cập nhật schema chỉ còn `name`
- **THEN** response của `PUT /schema` có warning `{field: "phone", code: "CONFIG_PRUNED", message: "Transformations removed because field 'phone' no longer exists."}`
- **AND** chỉ còn transformation của `name`

#### Scenario: Field chuyển sang kiểu date
- **WHEN** field `dob` kiểu `string` có `dateFormat` với `outputFormat = "dd/MM/yyyy"`, và schema đổi `dob` sang kiểu `date`
- **THEN** bước `dateFormat` đó bị bỏ, kèm warning `CONFIG_PRUNED` cho field `dob`

#### Scenario: Field chuyển sang date nhưng output đã là ISO
- **WHEN** field `dob` kiểu `string` có `dateFormat` không có `outputFormat`, và schema đổi `dob` sang kiểu `date`
- **THEN** bước `dateFormat` được giữ và không có warning

#### Scenario: Field chuyển sang date với outputFormat ISO viết tường minh
- **WHEN** field `dob` kiểu `string` có `dateFormat` với `outputFormat = "yyyy-MM-dd"`, và schema đổi `dob` sang kiểu `date`
- **THEN** bước `dateFormat` được giữ và không có warning

### Requirement: Cập nhật transformation theo trạng thái session
Hệ thống SHALL trả `404` với `code = SESSION_NOT_FOUND` khi session không tồn tại, và `409` với `code = SESSION_STATE_INVALID` khi session đang `FAILED`. Các lệnh cập nhật transformation của cùng một session SHALL chạy lần lượt, theo khoá session (D11).

#### Scenario: Session FAILED
- **WHEN** session đang `FAILED` và client gửi `PUT /transformations` hợp lệ
- **THEN** hệ thống trả `409` với `code = SESSION_STATE_INVALID` và không lưu gì

#### Scenario: Session không tồn tại
- **WHEN** client gửi `PUT /api/import-sessions/{uuid-chưa-tạo}/transformations`
- **THEN** hệ thống trả `404` với `code = SESSION_NOT_FOUND` (FE nhận biết session đã mất nhờ `code`, không nhờ status)
