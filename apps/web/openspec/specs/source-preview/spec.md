# source-preview Specification

## Purpose
TBD - created by archiving change fe-import-wizard-v0-1. Update Purpose after archive.
## Requirements
### Requirement: Tải preview một lần cho mỗi session
Lần đầu vào bước Source Preview với một session, FE SHALL gọi `GET /api/import-sessions/{id}/preview?limit=50` và giữ kết quả trong wizard state. Quay lại bước này về sau MUST NOT gọi lại API, trừ khi user bấm "Thử lại" sau một lần lỗi.

#### Scenario: Vào Preview lần đầu
- **WHEN** vừa upload thành công và bước Preview được hiển thị
- **THEN** FE gọi GET preview đúng một lần và hiển thị bảng dữ liệu

#### Scenario: Quay lại Preview
- **WHEN** preview đã tải xong, user sang bước Schema rồi quay lại Preview
- **THEN** FE hiển thị dữ liệu đã có và không gọi lại API

### Requirement: Bảng preview giữ đúng thứ tự cột gốc
FE MUST hiển thị cột theo đúng thứ tự của mảng `columns[]` trong response, và lấy giá trị ô theo vị trí (`values[i]` ứng với `columns[i]`). Thứ tự cột MUST NOT phụ thuộc vào thứ tự key của object JavaScript. Cột đầu tiên của bảng SHALL là "Dòng", hiển thị `rowNumber` do BE trả về. Giá trị ô được hiển thị nguyên như BE trả về, FE không trim hay định dạng lại.

#### Scenario: Tên cột dạng số
- **WHEN** `columns` là `["2024", "Tên", "1"]`
- **THEN** header bảng hiển thị theo đúng thứ tự "Dòng", "2024", "Tên", "1"

#### Scenario: Hiển thị số dòng nguồn
- **WHEN** một dòng preview có `rowNumber: 7`
- **THEN** ô "Dòng" của dòng đó hiển thị `7`

#### Scenario: Ô trống
- **WHEN** một ô có giá trị `null`
- **THEN** ô hiển thị placeholder mờ, không bao giờ hiện chữ `null`

### Requirement: Thông tin tổng quan file
FE SHALL hiển thị tên file, loại file, số dòng đang xem trước và tổng số dòng dữ liệu (`totalRows`, BE luôn trả về). Nếu response có `sheetName` thì FE SHALL hiển thị tên sheet.

#### Scenario: Hiển thị tổng số dòng
- **WHEN** response có `rows` gồm 50 phần tử và `totalRows: 1200`
- **THEN** FE hiển thị "Xem trước 50 / 1.200 dòng"

#### Scenario: File XLSX
- **WHEN** response có `fileType: "XLSX"` và `sheetName: "Sheet1"`
- **THEN** FE hiển thị "Sheet: Sheet1"

#### Scenario: File CSV
- **WHEN** response có `sheetName: null`
- **THEN** FE không hiển thị dòng thông tin sheet

### Requirement: Dùng chung một màn preview cho CSV và XLSX
FE MUST dùng cùng một component và cùng một contract preview cho CSV và XLSX. Không có UI riêng cho XLSX, ngoài dòng thông tin sheet.

#### Scenario: Preview XLSX
- **WHEN** session là file XLSX
- **THEN** bảng preview dùng đúng component của CSV và các quy tắc thứ tự cột, số dòng như CSV

### Requirement: Trạng thái đang tải, rỗng và lỗi của preview
FE SHALL hiển thị trạng thái đang tải trong lúc gọi API, và trạng thái rỗng khi file không có dòng dữ liệu. Khi lỗi mạng hoặc 5xx, FE SHALL cho thử lại. Lỗi về nội dung file (`FILE_EMPTY`, `FILE_PARSE_ERROR`) không xảy ra ở bước này, vì BE đã kiểm file lúc upload (spec `import-upload`).

#### Scenario: Đang tải
- **WHEN** GET preview chưa trả về
- **THEN** FE hiển thị chỉ báo đang tải và nút "Tiếp" bị khoá

#### Scenario: Chỉ có header, không có dòng dữ liệu
- **WHEN** response có `columns` nhưng `rows` rỗng và `totalRows: 0`
- **THEN** FE hiển thị header và thông báo "File không có dòng dữ liệu"; user vẫn sang được bước Schema

#### Scenario: Lỗi mạng hoặc 5xx
- **WHEN** GET preview thất bại vì lỗi mạng hoặc HTTP 5xx
- **THEN** FE hiển thị lỗi và nút "Thử lại", khoá nút "Tiếp"; bấm "Thử lại" thì gọi lại GET preview

