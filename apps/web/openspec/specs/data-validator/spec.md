# data-validator Specification

## Purpose
Công cụ "Kiểm tra dữ liệu" (tool 05 của Universal Data Tools): user kiểm một file CSV, XLSX hoặc JSON theo một schema tự soạn hoặc mở từ file, xem lỗi theo từng dòng và field, rồi tải dòng hợp lệ, dòng lỗi hoặc báo cáo lỗi. Contract: tool-05 của BE.
## Requirements
### Requirement: Công cụ Kiểm tra dữ liệu ba bước
FE SHALL có công cụ "Kiểm tra dữ liệu" trên sidebar, với stepper 3 bước: "Dữ liệu" → "Schema" → "Kết quả". Mỗi bước khoá cho tới khi đủ điều kiện:
- bước Schema cần preview đã tải xong;
- bước Kết quả cần đã có một lần chạy.

#### Scenario: Chưa có file
- **WHEN** user mở công cụ lần đầu
- **THEN** bước "Dữ liệu" đang mở, còn "Schema" và "Kết quả" bị khoá

### Requirement: Soạn schema
Bước Schema SHALL có ô nhập tên schema (mặc định là tên file bỏ đuôi) và danh sách field. Mỗi field có tên, kiểu (`string|number|boolean|date|email`), "Bắt buộc", "Không trùng" (`unique`), cùng các ràng buộc hợp với kiểu:
- `number`: min, max;
- `string` và `email`: độ dài tối thiểu, độ dài tối đa, pattern;
- `date`: định dạng ngày.

Field MUST thêm, xoá và đổi thứ tự được. Lần đầu vào bước Schema mà chưa có field nào thì FE MUST tự sinh mỗi cột một field, kiểu theo `inferredType` (`empty` thành `string`), không bắt buộc. Nút "Tạo lại từ cột của file" MUST hỏi xác nhận trước khi thay các field đang có.

#### Scenario: Tự sinh field
- **WHEN** preview có cột `ma` (`inferredType` là string) và `gia` (`inferredType` là number), rồi user vào bước Schema
- **THEN** schema có hai field `ma` kiểu string và `gia` kiểu number, cả hai không bắt buộc

### Requirement: Kiểm schema trước khi chạy
FE SHALL báo lỗi ngay tại ô nhập khi:
- tên schema rỗng;
- tên field rỗng, hoặc trùng (sau khi trim, không phân biệt hoa thường);
- ô số không đọc được, hoặc độ dài âm;
- min lớn hơn max, hoặc độ dài tối thiểu lớn hơn độ dài tối đa.

Còn lỗi thì nút "Chạy kiểm tra" MUST bị khoá. BE trả `422 SCHEMA_INVALID` thì FE MUST hiện từng lỗi ở đúng ô theo `pointer`; lỗi không map được thì hiện ở banner.

#### Scenario: min lớn hơn max
- **WHEN** field `gia` kiểu number có min 10 và max 5
- **THEN** ô max báo "Phải lớn hơn hoặc bằng min", và "Chạy kiểm tra" bị khoá

#### Scenario: Pattern sai cú pháp
- **WHEN** BE trả `SCHEMA_INVALID` với pointer `/schema/fields/0/constraints/pattern`
- **THEN** ô pattern của field đầu tiên hiện message của lỗi đó

### Requirement: Khớp field với cột
Bước Schema SHALL hiện cột khớp với từng field theo luật của BE:
1. tên giống hệt trước;
2. không có thì lấy cột đầu tiên còn trống mà giống khi trim và không phân biệt hoa thường;
3. mỗi cột chỉ khớp một field.

Field không có cột MUST được đánh dấu: là cảnh báo nếu field không bắt buộc, là lỗi và khoá "Chạy kiểm tra" nếu field bắt buộc. Cột không thuộc field nào MUST được liệt kê là "bỏ qua khi kiểm".

#### Scenario: Khớp không phân biệt hoa thường
- **WHEN** file có cột ` Email ` và schema có field `email`
- **THEN** field `email` hiện khớp với cột ` Email `

### Requirement: Mở và lưu file schema
FE SHALL cho lưu schema ra file `<tên>.schema.json` theo định dạng `{ "format": "udt.schema", "version": 1, name, fields }`. FE cũng SHALL cho mở một file như vậy để thay schema đang có. File sai định dạng MUST được báo lỗi, và schema đang có giữ nguyên.

#### Scenario: Mở file schema sai
- **WHEN** user mở một file JSON không có `format: "udt.schema"`
- **THEN** FE báo file không phải schema của Universal Data Tools, và schema không đổi

### Requirement: Chạy kiểm tra
"Chạy kiểm tra" SHALL gửi `POST /api/validator/runs` gồm `source` (dataset và tuỳ chọn đọc BE đã dùng ở preview) cùng schema. Trong lúc chạy, nút MUST hiện "Đang kiểm tra…" và stepper MUST bị khoá.
- Chạy xong: FE mở bước Kết quả và xoá run trước đó (`DELETE`, không chờ).
- Chạy lỗi: FE ở lại bước Schema và hiện lỗi.

#### Scenario: Chạy thành công
- **WHEN** user bấm "Chạy kiểm tra" và BE trả 201
- **THEN** bước Kết quả mở, hiện tổng dòng, hợp lệ, không hợp lệ và số lỗi

### Requirement: Xem kết quả
Bước Kết quả SHALL hiện:
- tóm tắt `totalRows`, `validRows`, `invalidRows`, `errorCount`;
- thông tin khớp cột: số field khớp, field không bắt buộc bị thiếu cột, cột bị bỏ qua;
- hai tab "Hợp lệ (n)" và "Không hợp lệ (n)".

Mỗi trang 50 dòng, có phân trang. Bảng có cột số dòng và các field theo thứ tự `fields`. Ở tab "Không hợp lệ":
- ô có lỗi được đánh dấu;
- dưới mỗi dòng liệt kê lỗi: field, thông điệp tiếng Việt theo mã, message của BE và giá trị gốc;
- lọc được theo field và theo mã lỗi, dựa trên `errorCountsByField` và `errorCountsByCode`.

#### Scenario: Lọc theo mã lỗi
- **WHEN** user chọn mã "VALIDATION_UNIQUE" ở tab "Không hợp lệ"
- **THEN** FE gọi `rows?view=INVALID&code=VALIDATION_UNIQUE&page=0`

### Requirement: Kết quả cũ và hết hạn
Sửa schema, tuỳ chọn đọc hoặc file sau lần chạy thì kết quả MUST được đánh dấu là của lần chạy trước: có banner và nút "Chạy lại", nhưng vẫn xem và tải được. `404 RUN_NOT_FOUND` MUST được báo là kết quả đã hết hạn trên máy chủ, kèm nút chạy lại.

#### Scenario: Sửa schema sau khi chạy
- **WHEN** đã có kết quả và user đổi kiểu của một field
- **THEN** bước Kết quả hiện banner kết quả cũ cùng nút "Chạy lại"

### Requirement: Tải kết quả
Bước Kết quả SHALL cho chọn nội dung ("Dòng hợp lệ", "Dòng không hợp lệ", "Báo cáo lỗi") và định dạng (CSV, XLSX, JSON), rồi tải về bằng `POST /api/validator/runs/{id}/export`. Tên file lấy từ `Content-Disposition`. Nút tải MUST bị khoá khi nội dung đã chọn không có dòng nào.

#### Scenario: Tải báo cáo lỗi
- **WHEN** user chọn "Báo cáo lỗi", định dạng CSV, rồi bấm "Tải về"
- **THEN** FE gửi `{ content: "ERRORS", output: { format: "CSV" } }` và lưu file với tên BE trả

