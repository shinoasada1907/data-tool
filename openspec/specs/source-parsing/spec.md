# source-parsing Specification

## Purpose
Đọc file nguồn CSV (UTF-8, dấu phẩy) và XLSX (sheet đầu tiên đang hiện) thành cột và row dạng text theo cùng một quy ước, và cho xem trước vài row đầu.
## Requirements
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

### Requirement: Đọc XLSX từ sheet hiển thị đầu tiên
Với file XLSX, hệ thống SHALL chỉ đọc sheet hiển thị đầu tiên theo thứ tự trong workbook, bỏ qua sheet có trạng thái `hidden` hoặc `veryHidden`. Tên sheet đó SHALL được lưu vào source schema và trả trong `sheetName` của preview.

#### Scenario: Sheet đầu bị ẩn
- **WHEN** client upload workbook có sheet 1 là `Hidden` (ẩn, header `x`) và sheet 2 là `Visible` (header `name`, dòng 2 là `An`)
- **THEN** upload trả `201` với `status` là `CONFIGURING`
- **AND** preview có `sheetName` là `Visible`, `columns` là `[{index 0, name "name"}]`, `rows[0]` là `{rowNumber 2, values ["An"]}`

### Requirement: XLSX dùng chung luật đọc với CSV
Với XLSX, hệ thống SHALL áp dụng cùng các luật như CSV:
- dòng 1 là header;
- đặt tên cột duy nhất;
- `rowNumber` là số dòng của sheet;
- row trống bị bỏ qua nhưng vẫn giữ chỗ trong cách đánh số;
- row thiếu ô được bù `null`;
- dùng chung contract `SourcePreviewDto`.

#### Scenario: Số dòng giữ khoảng trống và merged cell
- **WHEN** sheet có header `a,b,c`; dòng 2 có ô A2:B2 gộp với giá trị `M` và C2 là `c`; dòng 3 trống; dòng 4 là `1,2,3`
- **THEN** các row là `{rowNumber 2, values ["M",null,"c"]}` và `{rowNumber 4, values ["1","2","3"]}`, và `totalRows` là `2`

#### Scenario: Header trùng và trống trong XLSX
- **WHEN** dòng 1 của sheet là `Email`, `email`, và một ô trống ở cột C
- **THEN** các cột có tên `Email`, `email (2)`, `Column C`

### Requirement: Chuyển giá trị ô XLSX thành chuỗi
Mọi ô XLSX SHALL được chuyển thành chuỗi theo luật sau:
- số: dùng dạng thập phân thường (không ký hiệu khoa học, không thêm `.0`), làm tròn về 15 chữ số có nghĩa như Excel hiển thị;
- ô số có format ngày: `yyyy-MM-dd`, hoặc `yyyy-MM-dd'T'HH:mm:ss` khi phần giờ khác 0; đúng cho cả hệ ngày 1900 và 1904;
- ô số có format chỉ có giờ (có giờ/giây nhưng không có năm/ngày): `HH:mm:ss`;
- boolean: `TRUE` hoặc `FALSE`;
- ô công thức: giá trị đã cache, chuyển theo đúng các luật trên;
- ô lỗi: chuỗi lỗi, ví dụ `#N/A`;
- ô rỗng hoặc chuỗi rỗng: `null`.

#### Scenario: Các kiểu ô thường gặp
- **WHEN** dòng 2 của sheet chứa lần lượt: chuỗi `An`; số `123`; số `-5.25`; số `0.001`; số `84901234567`; boolean TRUE; ngày 2024-02-29 với format có sẵn `m/d/yyyy`; ngày 2024-12-25 với format `dd/mm/yyyy`; ngày-giờ 2024-12-25 13:45:30 với format `yyyy-mm-dd hh:mm:ss`; công thức `=B2*2`; công thức `=A2&"!"`; công thức `=B2>100`; công thức `=NA()`
- **THEN** values của row dòng 2 là `["An","123","-5.25","0.001","84901234567","TRUE","2024-02-29","2024-12-25","2024-12-25T13:45:30","246","An!","TRUE","#N/A"]`

#### Scenario: Số thập phân Excel lưu dạng double dài
- **WHEN** ô chứa 0.075 mà Excel ghi trong file là `0.074999999999999997`
- **THEN** giá trị ô là `0.075`

#### Scenario: Ô chỉ có giờ
- **WHEN** ô A2 chứa 13:30 với format `h:mm`, và ô B2 chứa 13:45:30 với format `hh:mm:ss`
- **THEN** giá trị hai ô lần lượt là `13:30:00` và `13:45:30`, không kèm ngày `1899-12-30`

#### Scenario: Hệ ngày 1904
- **WHEN** workbook dùng hệ ngày 1904 và ô A2 là ngày 2024-01-01 với format `dd/mm/yyyy`
- **THEN** giá trị ô là `2024-01-01`

### Requirement: XLSX hỏng hoặc trống bị từ chối
Khi upload, hệ thống SHALL từ chối các file XLSX sau:
- File không phải ZIP hợp lệ, hoặc thiếu phần workbook: `422` với `code` là `FILE_PARSE_ERROR`.
- Workbook không có sheet hiển thị, hoặc dòng 1 của sheet hiển thị đầu tiên không có ô nào có giá trị: `422` với `code` là `FILE_EMPTY`.

Trong cả hai trường hợp, hệ thống MUST NOT tạo session và SHALL xoá file đã lưu.

#### Scenario: Nội dung ZIP hỏng
- **WHEN** client upload `broken.xlsx` bắt đầu bằng các byte `50 4B 03 04`, theo sau là dữ liệu rác
- **THEN** hệ thống trả `422` với `code` là `FILE_PARSE_ERROR`

#### Scenario: Sheet hiển thị đầu tiên trống
- **WHEN** client upload workbook có sheet đầu (hiển thị) trống và sheet thứ hai có dữ liệu
- **THEN** hệ thống trả `422` với `code` là `FILE_EMPTY`

#### Scenario: Dòng 1 trống
- **WHEN** client upload workbook có dòng 1 của sheet đầu trống và dòng 2 là `a,b`
- **THEN** hệ thống trả `422` với `code` là `FILE_EMPTY`

### Requirement: Chống zip bomb khi đọc XLSX
Trước khi parse XLSX, hệ thống SHALL kiểm file theo các giới hạn sau, và MUST từ chối file vượt bất kỳ giới hạn nào với `422` và `code` là `FILE_PARSE_ERROR`:
- tổng dung lượng sau giải nén: `IMPORTER_XLSX_MAX_UNCOMPRESSED_SIZE`, mặc định `200MB`;
- tỉ lệ giải nén của entry có hơn 1MB sau giải nén: `IMPORTER_XLSX_MAX_INFLATE_RATIO`, mặc định `100`;
- số entry: `IMPORTER_XLSX_MAX_ENTRIES`, mặc định `10000`.

#### Scenario: Entry nén quá mức
- **WHEN** client upload `bomb.xlsx` có entry `xl/worksheets/sheet1.xml` gồm 50MB byte `0`, nén còn dưới 100KB
- **THEN** hệ thống trả `422` với `code` là `FILE_PARSE_ERROR`, và `detail` là `XLSX file expands beyond the allowed limits.`

#### Scenario: File bình thường không bị chặn
- **WHEN** client upload một workbook bình thường gồm 5.000 row × 10 cột chữ và số
- **THEN** hệ thống trả `201`

