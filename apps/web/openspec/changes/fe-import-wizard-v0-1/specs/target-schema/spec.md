## ADDED Requirements

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
