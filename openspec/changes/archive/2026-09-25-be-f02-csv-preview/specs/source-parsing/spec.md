## ADDED Requirements

### Requirement: Đọc toàn bộ file nguồn khi upload
Khi upload một file đã có parser cho loại file đó, hệ thống SHALL đọc toàn bộ file ngay trong request upload để kiểm cấu trúc, lấy header và đếm số row dữ liệu không trống. Đọc thành công thì hệ thống SHALL lưu source schema (cột, `totalRows`, `sheetName`) và chuyển session sang `CONFIGURING`. Đọc thất bại thì hệ thống SHALL trả lỗi 422 (`FILE_PARSE_ERROR` hoặc `FILE_EMPTY`), xoá file đã lưu, và MUST NOT tạo session.

#### Scenario: CSV hợp lệ được đọc ngay khi upload
- **WHEN** client upload `customers.csv` có nội dung `name,email\nAn,an@x.com\nBinh,binh@x.com\n`
- **THEN** hệ thống trả `201` với `status` là `CONFIGURING`

#### Scenario: CSV hỏng bị từ chối và không để lại dấu vết
- **WHEN** client upload `broken.csv` có nội dung `a,b\n"unterminated,1\n`
- **THEN** hệ thống trả `422` với `code` là `FILE_PARSE_ERROR`
- **AND** không có session nào được tạo và thư mục storage không còn thư mục nào cho lần upload này

### Requirement: Đặt tên cột nguồn duy nhất
Hệ thống SHALL coi dòng 1 là header và đặt cho mỗi cột một `index` (bắt đầu từ 0) cùng một `name` duy nhất, không rỗng, theo luật:
- Trim tên gốc.
- Tên trống thành `Column <chữ cột Excel>` (`A`, `B`, …, `Z`, `AA`, …).
- Tên trùng (không phân biệt hoa thường) với tên đã dùng thì thêm hậu tố ` (k)`, với `k` là số nhỏ nhất từ 2 trở lên chưa bị dùng.

#### Scenario: Header trùng tên
- **WHEN** header CSV là `Email,email`
- **THEN** các cột là `{index 0, name "Email"}` và `{index 1, name "email (2)"}`

#### Scenario: Header trống
- **WHEN** header CSV là `, ,x`
- **THEN** các cột có tên lần lượt là `Column A`, `Column B`, `x`

#### Scenario: Tên tự sinh không đè tên sẵn có
- **WHEN** header CSV là `a (2),a,a`
- **THEN** các cột có tên lần lượt là `a (2)`, `a`, `a (3)`

### Requirement: Đánh số dòng nguồn như khi mở bằng Excel
Mỗi row dữ liệu SHALL mang `rowNumber` bằng số dòng mà người dùng thấy khi mở file bằng Excel: header là dòng 1, row dữ liệu đầu tiên là dòng 2. Với CSV, một giá trị nhiều dòng nằm trong dấu quote vẫn tính là một dòng. Row mà mọi ô đều trống hoặc chỉ có khoảng trắng SHALL bị bỏ qua: không trả về, không tính vào `totalRows`, nhưng vẫn giữ chỗ trong cách đánh số.

#### Scenario: Dòng trống bị bỏ qua nhưng vẫn được đếm số
- **WHEN** CSV có nội dung `a,b\n1,2\n\n3,4\n`
- **THEN** các row trả về là `{rowNumber 2, values ["1","2"]}` và `{rowNumber 4, values ["3","4"]}`, và `totalRows` là `2`

#### Scenario: Giá trị nhiều dòng trong quote
- **WHEN** CSV có nội dung `a,b\n"x,1","dòng1\ndòng2"\nc,d\n`
- **THEN** row đầu có `rowNumber` là `2` với values `["x,1","dòng1\ndòng2"]`, và row tiếp theo có `rowNumber` là `3`

### Requirement: Đọc CSV UTF-8 theo RFC 4180
Hệ thống SHALL đọc CSV theo các luật sau:
- Chỉ nhận encoding UTF-8. Nếu có BOM UTF-8 ở đầu file thì bỏ đi.
- Delimiter là dấu phẩy; quote theo RFC 4180.
- Byte không phải UTF-8 hợp lệ, hoặc quote không đóng, SHALL làm upload thất bại với `422` và `code` là `FILE_PARSE_ERROR`. Message nêu số dòng gần chỗ lỗi và MUST NOT chứa giá trị ô.

#### Scenario: Bỏ BOM ở header
- **WHEN** CSV bắt đầu bằng các byte `EF BB BF`, tiếp theo là `name\nAn\n`
- **THEN** cột đầu tiên có tên `name`, không kèm ký tự BOM

#### Scenario: Byte không phải UTF-8
- **WHEN** CSV có nội dung `a,b\n1,2\n` và dòng 3 chứa hai byte `C3 28`
- **THEN** upload trả `422` với `code` là `FILE_PARSE_ERROR`, và `detail` là `File is not valid UTF-8 (near row 3).`

### Requirement: Giá trị ô nguồn giữ nguyên dạng chuỗi
Mọi giá trị ô SHALL được trả về dưới dạng chuỗi, đúng như trong file, không trim. Ô rỗng SHALL thành `null`. Row có ít ô hơn header SHALL được bù `null` cho các ô còn thiếu. Row có nhiều ô hơn header SHALL bị bỏ phần thừa.

#### Scenario: Row thiếu và thừa ô
- **WHEN** CSV có nội dung `a,b,c\n1\n1,2,3,4\n`
- **THEN** row dòng 2 có values `["1",null,null]`, và row dòng 3 có values `["1","2","3"]`

#### Scenario: Khoảng trắng không bị trim
- **WHEN** CSV có nội dung `a,b\n  ,x\n,y\n`
- **THEN** row dòng 2 có values `["  ","x"]`, và row dòng 3 có values `[null,"y"]`

### Requirement: File nguồn không có header
File không có record nào, hoặc có dòng 1 trống hoàn toàn, SHALL làm upload thất bại với `422` và `code` là `FILE_EMPTY`. File chỉ có header SHALL được chấp nhận với `totalRows` là `0`.

#### Scenario: Dòng 1 trống
- **WHEN** client upload CSV có nội dung `\n1,2\n`
- **THEN** hệ thống trả `422` với `code` là `FILE_EMPTY`

#### Scenario: File chỉ có header
- **WHEN** client upload CSV có nội dung `name,email\n`
- **THEN** hệ thống trả `201` với `status` là `CONFIGURING`, và preview có `totalRows` là `0`, `rows` rỗng

### Requirement: Xem trước dữ liệu nguồn
Hệ thống SHALL cung cấp `GET /api/import-sessions/{id}/preview?limit=<n>`, trả `SourcePreviewDto { sessionId, fileType, sheetName, columns[{index,name}], rows[{rowNumber, values[]}], previewLimit, totalRows }`.
- `limit` mặc định là `50` và phải nằm trong khoảng 1–200; ngoài khoảng thì trả `400` với `code` là `REQUEST_INVALID`.
- `values[i]` ứng với `columns[i]`.
- `rows` gồm tối đa `limit` row dữ liệu đầu tiên, giữ nguyên giá trị gốc.
- Session chưa được đọc file SHALL trả `409` với `code` là `SESSION_STATE_INVALID`.
- Id không có session nào SHALL trả `404` với `code` là `SESSION_NOT_FOUND`.

#### Scenario: Preview mặc định
- **WHEN** session được tạo từ CSV `name,email\nAn,an@x.com\nBinh,binh@x.com\n` và client gọi `GET /api/import-sessions/{id}/preview`
- **THEN** hệ thống trả `200` với `fileType` là `CSV`, `sheetName` là `null`, `previewLimit` là `50`, `totalRows` là `2`
- **AND** `columns` là `[{index 0, name "name"}, {index 1, name "email"}]`
- **AND** `rows[0]` là `{rowNumber 2, values ["An","an@x.com"]}`

#### Scenario: Giới hạn số row preview
- **WHEN** client gọi preview với `limit=1` trên session có 2 row
- **THEN** `rows` có đúng 1 phần tử, `previewLimit` là `1`, `totalRows` là `2`

#### Scenario: limit ngoài khoảng
- **WHEN** client gọi preview với `limit=201`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

#### Scenario: Session chưa đọc file
- **WHEN** client gọi preview trên một session đang ở `UPLOADED`
- **THEN** hệ thống trả `409` với `code` là `SESSION_STATE_INVALID`

#### Scenario: Session không tồn tại
- **WHEN** client gọi `GET /api/import-sessions/{id}/preview` với một UUID chưa từng được tạo
- **THEN** hệ thống trả `404` với `code` là `SESSION_NOT_FOUND`
