## ADDED Requirements

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
- số: dùng dạng thập phân thường (không ký hiệu khoa học, không thêm `.0`);
- ô số có format ngày: `yyyy-MM-dd`, hoặc `yyyy-MM-dd'T'HH:mm:ss` khi phần giờ khác 0; đúng cho cả hệ ngày 1900 và 1904;
- boolean: `TRUE` hoặc `FALSE`;
- ô công thức: giá trị đã cache, chuyển theo đúng các luật trên;
- ô lỗi: chuỗi lỗi, ví dụ `#N/A`;
- ô rỗng hoặc chuỗi rỗng: `null`.

#### Scenario: Các kiểu ô thường gặp
- **WHEN** dòng 2 của sheet chứa lần lượt: chuỗi `An`; số `123`; số `-5.25`; số `0.001`; số `84901234567`; boolean TRUE; ngày 2024-02-29 với format có sẵn `m/d/yyyy`; ngày 2024-12-25 với format `dd/mm/yyyy`; ngày-giờ 2024-12-25 13:45:30 với format `yyyy-mm-dd hh:mm:ss`; công thức `=B2*2`; công thức `=A2&"!"`; công thức `=B2>100`; công thức `=NA()`
- **THEN** values của row dòng 2 là `["An","123","-5.25","0.001","84901234567","TRUE","2024-02-29","2024-12-25","2024-12-25T13:45:30","246","An!","TRUE","#N/A"]`

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
