# target-schema Specification

## Purpose
TBD - created by archiving change fe-import-wizard-v0-1. Update Purpose after archive.
## Requirements
### Requirement: Thêm, xoá và sắp xếp field
FE SHALL cho user thêm field, xoá field và đổi thứ tự field bằng nút "Lên"/"Xuống". Field mới MUST có tên rỗng, kiểu `string`, `required = false`, và ô nhập tên được focus. Thứ tự hiển thị MUST đúng bằng thứ tự gửi lên BE.

#### Scenario: Thêm field
- **WHEN** user bấm "Thêm field"
- **THEN** cuối danh sách có một field mới kiểu `string`, không required, và con trỏ nằm ở ô tên của field đó

#### Scenario: Đổi thứ tự
- **WHEN** danh sách là `[a, b, c]` và user bấm "Lên" ở `c`
- **THEN** danh sách thành `[a, c, b]`

#### Scenario: Nút ở biên bị khoá
- **WHEN** danh sách có từ 2 field trở lên
- **THEN** nút "Lên" của field đầu và nút "Xuống" của field cuối bị khoá

#### Scenario: Xoá field
- **WHEN** user bấm "Xoá" ở field `b`
- **THEN** `b` biến mất khỏi danh sách

### Requirement: Sinh schema từ cột nguồn
Khi preview của một session tải xong, FE SHALL sinh sẵn schema: mỗi cột nguồn thành một field cùng tên, theo đúng thứ tự `columns[]`, `required = false`, kiểu đoán từ các ô không rỗng của preview. Việc sinh này chỉ xảy ra một lần cho mỗi session; sau đó FE MUST NOT tự sinh lại đè lên những gì user đã sửa. Schema sinh ra là chưa lưu, và đi qua cùng các luật kiểm tên như field user tự thêm.

Kiểu được đoán theo đúng luật kiểm kiểu của BE (BE-F07), không trim giá trị. Ô rỗng (`null` hoặc chỉ gồm khoảng trắng) bị bỏ qua. Trong các ô còn lại:
1. mọi ô là `true`/`false`/`1`/`0` (không phân biệt hoa thường) và có ít nhất một ô là `true` hoặc `false` → `boolean`;
2. mọi ô khớp `^-?\d+(\.\d+)?$` → `number`;
3. mọi ô là ngày `yyyy-MM-dd` có thật trên lịch → `date`;
4. mọi ô khớp `^[^@\s]+@[^@\s]+\.[^@\s]+$` → `email`;
5. còn lại, hoặc cột không có ô nào không rỗng → `string`.

#### Scenario: Vào bước Schema lần đầu
- **WHEN** preview có `columns` là `["Mã", "Email", "Số lượng"]` và user vào bước Schema lần đầu
- **THEN** danh sách field là `Mã`, `Email`, `Số lượng` theo đúng thứ tự đó, đều không bắt buộc

#### Scenario: Đoán kiểu
- **WHEN** các ô không rỗng của bốn cột lần lượt là `["TRUE", "0"]`, `["42", "-3.50"]`, `["2024-02-29"]`, `["an@example.com"]`
- **THEN** bốn field có kiểu lần lượt `boolean`, `number`, `date`, `email`

#### Scenario: Cột chỉ có 1 và 0
- **WHEN** các ô không rỗng của một cột là `["1", "0", "1"]`
- **THEN** field có kiểu `number`

#### Scenario: Giá trị lẫn kiểu hoặc sai định dạng
- **WHEN** các ô không rỗng của một cột là `["42", " 7"]`, hoặc `["2024-02-30"]`, hoặc `["2024-12-25T13:45:30"]`
- **THEN** field có kiểu `string`

#### Scenario: Không tự sinh lại
- **WHEN** schema đã được sinh, user xoá một field, sang bước Preview rồi quay lại bước Schema
- **THEN** field đã xoá không xuất hiện lại

#### Scenario: Tạo lại từ file
- **WHEN** schema đang có field và user bấm "Tạo lại từ file"
- **THEN** FE hỏi xác nhận; đồng ý thì toàn bộ field bị thay bằng field sinh từ cột nguồn, huỷ thì giữ nguyên
- **AND** field sinh lại mang key nội bộ mới, không dùng lại key cũ

#### Scenario: Tạo lại khi chưa có field
- **WHEN** user đã xoá hết field rồi bấm "Tạo lại từ file"
- **THEN** FE sinh field ngay, không hỏi xác nhận

### Requirement: Kiểu dữ liệu và required
Mỗi field SHALL có một kiểu thuộc `string`, `number`, `boolean`, `date`, `email`, và một checkbox required.

#### Scenario: Chọn kiểu
- **WHEN** user mở danh sách kiểu của một field
- **THEN** có đúng 5 lựa chọn `string`, `number`, `boolean`, `date`, `email`

### Requirement: Kiểm tên field ngay trên form
FE MUST báo lỗi ngay tại field khi tên rỗng (sau khi trim), khi tên dài quá 100 ký tự (sau khi trim), hoặc khi trùng tên với field khác (so sánh sau khi trim, không phân biệt hoa thường). Các luật này trùng với luật của BE. Lỗi inline của một field chỉ hiện sau khi ô tên của field đó mất focus lần đầu, để field vừa thêm không báo lỗi ngay. Nút "Tiếp" MUST bị khoá khi còn lỗi hoặc schema chưa có field nào, và lý do khoá luôn hiển thị cạnh nút. Tên gửi lên BE MUST là tên đã trim.

#### Scenario: Field vừa thêm chưa báo lỗi
- **WHEN** user vừa bấm "Thêm field" và con trỏ còn ở ô tên rỗng
- **THEN** field đó chưa hiện lỗi inline; nút "Tiếp" bị khoá, kèm lý do "Còn field chưa đặt tên"

#### Scenario: Tên rỗng
- **WHEN** tên field chỉ gồm khoảng trắng và ô tên đã mất focus
- **THEN** field đó hiện lỗi "Tên field không được để trống" và nút "Tiếp" bị khoá

#### Scenario: Tên quá dài
- **WHEN** tên field dài 101 ký tự sau khi trim và ô tên đã mất focus
- **THEN** field đó hiện lỗi "Tên field tối đa 100 ký tự" và nút "Tiếp" bị khoá

#### Scenario: Trùng tên khác hoa thường
- **WHEN** hai field có tên `Email` và ` email `
- **THEN** cả hai field hiện lỗi trùng tên và nút "Tiếp" bị khoá

#### Scenario: Schema rỗng
- **WHEN** danh sách field rỗng
- **THEN** nút "Tiếp" bị khoá, kèm lý do "Cần ít nhất một field"

### Requirement: Lưu schema lên BE khi bấm Tiếp
Khi bấm "Tiếp" mà schema chưa được lưu, FE SHALL gọi `PUT /api/import-sessions/{id}/schema` với field theo thứ tự hiển thị (`order` từ 0). Nếu schema đã lưu và chưa sửa gì kể từ đó, FE MUST NOT gọi lại PUT. Lỗi BE trả về MUST hiển thị tại field có tên khớp; lỗi không khớp field nào hiển thị ở đầu form.

#### Scenario: Lưu thành công
- **WHEN** schema hợp lệ `[{name: "email", type: "email", required: true}]` và user bấm "Tiếp"
- **THEN** FE gửi PUT với `fields[0] = {name: "email", type: "email", required: true, order: 0}`, schema được đánh dấu đã lưu, và wizard sang bước Mapping

#### Scenario: Không thay đổi kể từ lần lưu trước
- **WHEN** schema đã lưu, user quay lại bước Schema rồi bấm "Tiếp" mà không sửa gì
- **THEN** FE không gọi PUT và chuyển thẳng sang bước Mapping

#### Scenario: BE từ chối schema
- **WHEN** BE trả `422` với `errors: [{field: "email", code: "SCHEMA_INVALID", message: "..."}]`
- **THEN** lỗi hiển thị tại field `email`, wizard ở lại bước Schema và schema vẫn là chưa lưu

### Requirement: Sửa schema giữ đúng cấu hình phía sau
Mỗi field SHALL có một key nội bộ cố định, không phụ thuộc tên. Mapping, transformation và validation MUST gắn theo key này. Khi schema thay đổi, FE MUST cập nhật cấu hình phía sau theo các scenario dưới đây. BE coi đổi tên là xoá rồi thêm, và PUT `/schema` xoá cấu hình của tên cũ. Vì vậy sau khi schema được lưu, FE MUST PUT lại mapping, transformations và validations dưới tên mới (bảng "chuyển về chưa lưu" trong spec `import-wizard`).

#### Scenario: Đổi tên field
- **WHEN** field `mail` đã có mapping và rule, user đổi tên thành `email`
- **THEN** mapping và rule của field được giữ nguyên trong state; sau khi PUT schema, các lần PUT mapping, transformations và validations kế tiếp đều gửi tên `email`

#### Scenario: Xoá field đã có cấu hình
- **WHEN** user xoá một field đã có mapping, transformation và validation
- **THEN** cả ba cấu hình của field đó bị xoá khỏi state

#### Scenario: Đổi kiểu khỏi string
- **WHEN** field kiểu `string` có rule `email`, user đổi kiểu sang `number`
- **THEN** rule `email` của field đó bị xoá; transformation vẫn được giữ

#### Scenario: Đổi kiểu sang date
- **WHEN** field kiểu `string` có `dateFormat` với `outputFormat: "dd/MM/yyyy"`, user đổi kiểu sang `date`
- **THEN** `outputFormat` của `dateFormat` đó thành `yyyy-MM-dd`, vì kiểu `date` chỉ nhận ISO

