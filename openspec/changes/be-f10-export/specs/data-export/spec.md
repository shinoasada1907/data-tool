## ADDED Requirements

### Requirement: Export dữ liệu hợp lệ dạng JSON
Hệ thống SHALL trả file JSON qua `GET /api/import-sessions/{id}/export?format=json`. File là một mảng; mỗi phần tử là một row hợp lệ, theo `rowNumber` tăng dần. Header response là `Content-Type: application/json`.

Trong mỗi object:
- Key SHALL là tên target field, theo thứ tự schema hiện tại.
- Giá trị SHALL đúng kiểu: `number` là JSON number, ghi dạng plain, không dùng số mũ; `boolean` là `true`/`false`; `date` là chuỗi `yyyy-MM-dd`; `string`/`email` là chuỗi; giá trị rỗng là `null`.

Khi không có row hợp lệ nào, file SHALL là `[]`.

#### Scenario: Hai row hợp lệ
- **WHEN** schema là `name` (string), `score` (number), `active` (boolean), `dob` (date), `note` (string); row hợp lệ 2 có `An, 10, true, 1990-12-25, rỗng`; row hợp lệ 5 có `Em, 7.5, false, 1991-01-02, x`; client gọi `GET /export?format=json`
- **THEN** body là `[{"name":"An","score":10,"active":true,"dob":"1990-12-25","note":null},{"name":"Em","score":7.5,"active":false,"dob":"1991-01-02","note":"x"}]`

#### Scenario: Không có row hợp lệ
- **WHEN** mọi row đều lỗi và client gọi `GET /export?format=json`
- **THEN** hệ thống trả `200` với body là `[]`

#### Scenario: Số rất nhỏ ghi dạng plain
- **WHEN** row hợp lệ có `score` là số `0.0000001`
- **THEN** file JSON chứa đúng `0.0000001`, và không chứa `1E-7`

### Requirement: Export dữ liệu hợp lệ dạng CSV
Hệ thống SHALL trả file CSV qua `GET /api/import-sessions/{id}/export?format=csv`, với `Content-Type: text/csv;charset=UTF-8`. File SHALL:
- bắt đầu bằng BOM UTF-8 (`EF BB BF`);
- có dòng đầu là header, gồm tên target field theo thứ tự schema (đã áp quy tắc chống formula injection);
- mỗi dòng sau là một row hợp lệ, theo `rowNumber` tăng dần;
- theo RFC 4180: tách dòng bằng CRLF; giá trị chứa dấu phẩy, dấu nháy kép hoặc xuống dòng được đặt trong dấu nháy kép, và dấu nháy kép bên trong được nhân đôi.

Giá trị `number` SHALL ghi dạng plain, `boolean` là `true`/`false`, `date` là `yyyy-MM-dd`, rỗng là ô trống. Khi không có row hợp lệ nào, file SHALL chỉ gồm BOM và header.

#### Scenario: CSV cơ bản
- **WHEN** vẫn schema và row 2 như ở phần JSON, client gọi `GET /export?format=csv`
- **THEN** body bắt đầu bằng 3 byte `EF BB BF`, tiếp theo là dòng `name,score,active,dob,note` kết thúc bằng CRLF
- **AND** đọc lại theo RFC 4180, row đầu tiên có các giá trị `An`, `10`, `true`, `1990-12-25`, và ô trống

#### Scenario: Giá trị có dấu phẩy và dấu nháy kép
- **WHEN** row hợp lệ có `name` là `Nguyen, An` và `note` là `say "hi"`
- **THEN** file chứa `"Nguyen, An"` và `"say ""hi"""`
- **AND** đọc lại theo RFC 4180 ra đúng `Nguyen, An` và `say "hi"`

#### Scenario: Không có row hợp lệ
- **WHEN** mọi row đều lỗi và client gọi `GET /export?format=csv`
- **THEN** body là BOM, theo sau là đúng một dòng header

### Requirement: Chống formula injection trong file CSV
Khi ghi CSV, hệ thống SHALL thêm tiền tố `'` vào giá trị bắt đầu bằng `=`, `+`, `-`, `@`, tab (`\t`) hoặc CR (`\r`). Quy tắc chỉ áp cho:
- ô dữ liệu của field kiểu `string` và `email` trong CSV dữ liệu;
- ô header của CSV dữ liệu, tức tên target field do người dùng đặt;
- các cột `fieldName`, `rule`, `message`, `sourceValue` trong báo cáo lỗi.

Giá trị của field kiểu `number`, `boolean`, `date` MUST NOT bị đổi. File JSON MUST NOT bị đổi, kể cả key là tên field.

#### Scenario: Công thức trong cột chuỗi
- **WHEN** row hợp lệ có `note` (string) là `=HYPERLINK("http://x")`
- **THEN** đọc lại CSV, ô `note` là `'=HYPERLINK("http://x")`
- **AND** file JSON vẫn giữ nguyên `=HYPERLINK("http://x")`

#### Scenario: Số âm không bị đổi
- **WHEN** row hợp lệ có `score` (number) là `-3`
- **THEN** đọc lại CSV, ô `score` là `-3`

#### Scenario: Chuỗi bắt đầu bằng dấu trừ bị escape
- **WHEN** row hợp lệ có `note` (string) là `-abc`
- **THEN** đọc lại CSV, ô `note` là `'-abc`

#### Scenario: sourceValue trong báo cáo lỗi
- **WHEN** một lỗi có `sourceValue` là `+84901234567`
- **THEN** đọc lại báo cáo lỗi, ô `sourceValue` là `'+84901234567`

#### Scenario: Tên field nguy hiểm trong header
- **WHEN** schema có một field kiểu `string` tên `=cmd()`, và client tải CSV dữ liệu
- **THEN** đọc lại CSV, ô header của cột đó là `'=cmd()`
- **AND** trong file JSON, key của field đó vẫn là `=cmd()`

#### Scenario: Tên field nguy hiểm trong báo cáo lỗi
- **WHEN** một lỗi có `fieldName` là `=cmd()`
- **THEN** đọc lại báo cáo lỗi, ô `fieldName` là `'=cmd()`

### Requirement: File dữ liệu hợp lệ không chứa row lỗi
File export JSON và CSV MUST chỉ chứa row hợp lệ của kết quả hiện hành. Một row lỗi MUST NOT xuất hiện trong file dữ liệu hợp lệ, dù chỉ một phần.

#### Scenario: Kết quả có cả row hợp lệ và row lỗi
- **WHEN** row 2 và 4 hợp lệ, row 3 lỗi `VALIDATION_EMAIL` với email `not-an-email`; client tải cả JSON lẫn CSV
- **THEN** cả hai file chỉ có dữ liệu của row 2 và 4, và không chứa chuỗi `not-an-email`

### Requirement: Export báo cáo lỗi
Hệ thống SHALL trả báo cáo lỗi dạng CSV qua `GET /api/import-sessions/{id}/errors/export`, với `Content-Type: text/csv;charset=UTF-8`. Báo cáo gồm BOM UTF-8, header `rowNumber,fieldName,stage,rule,step,code,message,sourceValue`, và mỗi lỗi một dòng. Thứ tự dòng là `rowNumber` tăng dần, rồi theo thứ tự lỗi trong row. `step` và `sourceValue` bằng `null` SHALL thành ô trống. Khi không có lỗi nào, file SHALL chỉ gồm BOM và header.

#### Scenario: Báo cáo có nhiều lỗi
- **WHEN** row 3 có lỗi `VALIDATION/email/VALIDATION_EMAIL` với `sourceValue` là `" ABC "`; row 4 có lỗi `TRANSFORMATION/dob/dateFormat` (step 0, `sourceValue` là `31/02/2024`), sau đó là lỗi `VALIDATION/score/type/VALIDATION_TYPE` (`sourceValue` là `x`)
- **THEN** đọc lại file ra đúng 3 dòng dữ liệu, theo thứ tự:
  - `3,email,VALIDATION,email,,VALIDATION_EMAIL,<message>, ABC `
  - `4,dob,TRANSFORMATION,dateFormat,0,TRANSFORMATION_FAILED,<message>,31/02/2024`
  - `4,score,VALIDATION,type,,VALIDATION_TYPE,<message>,x`

#### Scenario: Không có lỗi
- **WHEN** mọi row đều hợp lệ và client gọi `GET /errors/export`
- **THEN** body là BOM, theo sau là đúng một dòng header

### Requirement: Tên file tải về an toàn
Mọi response export thành công SHALL có header `Content-Disposition: attachment` kèm `filename*` theo RFC 5987 (UTF-8, percent-encoding). Tên file SHALL là tên gốc bỏ đuôi cuối cùng, trong đó `"`, `\`, `/` và ký tự điều khiển được thay bằng `_`, rồi nối thêm hậu tố:
- `-valid.json` cho export JSON;
- `-valid.csv` cho export CSV;
- `-errors.csv` cho báo cáo lỗi.

Phần tên còn lại rỗng thì dùng `export`.

#### Scenario: Tên tiếng Việt có dấu cách
- **WHEN** session có `originalFileName` là `khách hàng.xlsx` và client gọi `GET /export?format=csv`
- **THEN** header `Content-Disposition` chứa `attachment` và `filename*=UTF-8''kh%C3%A1ch%20h%C3%A0ng-valid.csv`

#### Scenario: Tên có dấu nháy kép
- **WHEN** session có `originalFileName` là `a"b.csv` và client gọi `GET /errors/export`
- **THEN** tên file tải về là `a_b-errors.csv`

#### Scenario: Chỉ bỏ đuôi cuối cùng
- **WHEN** session có `originalFileName` là `report.final.xlsx` và client gọi `GET /export?format=json`
- **THEN** tên file tải về là `report.final-valid.json`

### Requirement: Điều kiện để export
- `format` SHALL bắt buộc có, và nhận `json` hoặc `csv` (không phân biệt hoa thường). Thiếu hoặc giá trị khác SHALL bị từ chối với `400`, `code` là `REQUEST_INVALID`.
- Khi chưa có kết quả hiện hành (theo đúng điều kiện của `GET /result`), cả hai endpoint export SHALL trả `409`, `code` là `RESULT_NOT_AVAILABLE`.
- Session không tồn tại SHALL trả `404`, `code` là `SESSION_NOT_FOUND`.

Mọi kiểm tra này MUST xong trước khi gửi byte đầu tiên của file.

#### Scenario: Thiếu format
- **WHEN** client gọi `GET /export` không có `format`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

#### Scenario: Format không hỗ trợ
- **WHEN** client gọi `GET /export?format=xml`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

#### Scenario: Export trước khi process
- **WHEN** session đang `READY` và client gọi `GET /export?format=json` hoặc `GET /errors/export`
- **THEN** hệ thống trả `409` với `code` là `RESULT_NOT_AVAILABLE`

#### Scenario: Session không tồn tại
- **WHEN** client gọi `GET /api/import-sessions/{uuid-chưa-tạo}/export?format=csv` hoặc `GET /api/import-sessions/{uuid-chưa-tạo}/errors/export`
- **THEN** hệ thống trả `404` với `code` là `SESSION_NOT_FOUND`

### Requirement: Xử lý lỗi khi export
- Không mở được nguồn dữ liệu của kết quả (ví dụ thiếu file `valid.ndjson`) SHALL trả `500` dạng `application/problem+json`, `code` là `EXPORT_FAILED`, trước khi gửi byte nào của file.
- Lỗi xảy ra **sau khi** đã bắt đầu gửi file SHALL được ghi log phía server (có `sessionId`, không có dữ liệu row) và làm kết nối bị huỷ. Hệ thống MUST NOT chèn body lỗi JSON vào giữa file.

#### Scenario: Thiếu file dữ liệu hợp lệ
- **WHEN** session đã `PROCESSED`, file `result/valid.ndjson` bị xoá khỏi storage, và client gọi `GET /export?format=json`
- **THEN** hệ thống trả `500` dạng `application/problem+json` với `code` là `EXPORT_FAILED`

#### Scenario: Lỗi giữa chừng không chèn JSON lỗi vào file
- **WHEN** writer đã ghi xong row đầu tiên, và việc đọc row thứ hai ném `UncheckedIOException`
- **THEN** exception được ném ra khỏi writer, và phần đã ghi không chứa chuỗi `"code"`
