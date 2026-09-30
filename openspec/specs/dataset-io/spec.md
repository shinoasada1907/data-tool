# dataset-io Specification

## Purpose
Đọc dataset CSV/XLSX/JSON theo tuỳ chọn (encoding, dấu phân cách, header, sheet) thành bảng trung lập: cột, row, kiểu gốc của ô, profile, giới hạn. Ghi bảng ra CSV/JSON/XLSX có typing, chống formula injection và không mất giá trị khi chuyển qua lại. Importer dùng cùng lớp này mà giữ nguyên hành vi V0.1. Tạo bởi change `core-02-dataset-io` (2026-09-28).
## Requirements
### Requirement: Đọc CSV với encoding chọn được hoặc tự nhận
Khi đọc dataset CSV, hệ thống SHALL dùng encoding theo luật sau:
- Tuỳ chọn `encoding` có thể là `UTF-8`, `UTF-16`, `WINDOWS-1258` hoặc `WINDOWS-1252`.
- Khi không chọn:
  - có BOM UTF-8 → đọc UTF-8;
  - có BOM UTF-16 (`FF FE` hoặc `FE FF`) → đọc UTF-16;
  - còn lại → đọc UTF-8 chặt, và hệ thống MUST NOT tự đoán encoding khác.
- BOM MUST bị bỏ khỏi dữ liệu.
- Byte không hợp lệ với encoding đang dùng SHALL làm việc đọc thất bại với `FILE_PARSE_ERROR`, và `detail` nêu số dòng gần chỗ lỗi.
- Khi encoding được tự nhận mà không nhờ BOM, `detail` SHALL kèm gợi ý chọn encoding.

#### Scenario: File Windows-1258 đọc được khi chọn đúng encoding
- **WHEN** file CSV chứa header `ten` và dòng 2 là chữ `Nguyễn` mã hoá bằng windows-1258, và tuỳ chọn `encoding` là `WINDOWS-1258`
- **THEN** ô đầu của row 2 là `Nguyễn`

#### Scenario: File không phải UTF-8 khi để tự nhận
- **WHEN** file CSV không có BOM, dòng 3 chứa byte `C3 28`, và không chọn `encoding`
- **THEN** việc đọc thất bại với `FILE_PARSE_ERROR`, và `detail` là `File is not valid UTF-8 (near row 3). Choose the file's encoding.`

#### Scenario: UTF-16 có BOM được tự nhận
- **WHEN** file CSV bắt đầu bằng `FF FE`, theo sau là `a,b\n1,2\n` mã hoá UTF-16LE, và không chọn `encoding`
- **THEN** cột là `a`, `b`; row 2 là `["1","2"]`; `options.encoding` là `UTF-16`; và `autoDetected` không chứa `encoding`

### Requirement: Tự nhận dấu phân cách CSV
Khi không chọn `delimiter`, hệ thống SHALL chọn một trong `COMMA`, `SEMICOLON`, `TAB`, `PIPE` bằng cách xét tối đa 50 record đầu. Delimiter được chọn SHALL là cái cho nhiều hơn một cột và có số cột ổn định trên nhiều record nhất. Dấu nằm trong quote không được tính. Hoà thì chọn theo thứ tự `COMMA`, `SEMICOLON`, `TAB`, `PIPE`. Không delimiter nào cho quá một cột thì chọn `COMMA`. Delimiter đã chọn SHALL được báo lại cùng `autoDetected` có `delimiter`.

#### Scenario: File dùng chấm phẩy
- **WHEN** CSV có nội dung `ma;ten;gia\n1;Bút;5000\n2;Vở;12000\n`
- **THEN** delimiter là `SEMICOLON`, và có 3 cột `ma`, `ten`, `gia`

#### Scenario: Dấu chấm phẩy trong quote không gây nhầm
- **WHEN** CSV có nội dung `name,note\n"An","a; b; c"\n"Binh","x"\n`
- **THEN** delimiter là `COMMA`, và ô `note` của row 2 là `a; b; c`

#### Scenario: File một cột
- **WHEN** CSV có nội dung `email\nan@x.com\nbinh@x.com\n`
- **THEN** delimiter là `COMMA`, và có một cột `email`

### Requirement: Dataset không có dòng header
Khi tuỳ chọn `hasHeader` là `false`, hệ thống SHALL coi mọi dòng là dữ liệu. Tên cột là `Column A`, `Column B`…, số cột bằng số ô của dòng đầu tiên khác trống, và row đầu tiên có số dòng `1`. `hasHeader` SHALL được bỏ qua với JSON.

#### Scenario: CSV không header
- **WHEN** CSV có nội dung `1,An\n2,Binh\n` và `hasHeader` là `false`
- **THEN** các cột là `Column A`, `Column B`; row đầu có số dòng `1` và giá trị `["1","An"]`

### Requirement: Chọn sheet của XLSX
Hệ thống SHALL liệt kê mọi sheet của workbook theo thứ tự trong file, kèm cờ `visible`. Tuỳ chọn `sheet` chọn sheet theo tên, khớp chính xác, và được chọn cả sheet ẩn. Không chọn thì đọc sheet hiển thị đầu tiên. Tên sheet không tồn tại SHALL làm việc đọc thất bại với `CONFIG_INVALID`, `detail` là `Sheet "<tên>" does not exist.`

#### Scenario: Đọc sheet thứ hai
- **WHEN** workbook có sheet `Data` (hiển thị, header `a`) và `Prices` (hiển thị, header `sku,price`), và `sheet` là `Prices`
- **THEN** các cột là `sku`, `price`, và `sheetName` là `Prices`

#### Scenario: Sheet không tồn tại
- **WHEN** `sheet` là `Khong co`
- **THEN** việc đọc thất bại với `CONFIG_INVALID`, và `detail` là `Sheet "Khong co" does not exist.`

### Requirement: Đọc JSON là mảng các object phẳng
Hệ thống SHALL đọc file JSON (UTF-8, có thể có BOM) theo luật sau:
- File là một mảng; mỗi phần tử là một row. Phần tử thứ N (đếm từ 1) có số dòng N.
- Tập cột là hợp các key theo thứ tự gặp lần đầu. Tên cột qua luật đặt tên cột duy nhất như header CSV.
- Key vắng trong một object cho ô `null`. Giá trị JSON `null` cũng cho ô `null`.
- String giữ nguyên văn. Number giữ **đúng chữ gốc** của token. Boolean thành `true`/`false`.
- Object rỗng `{}` vẫn là một row.

Hệ thống MUST từ chối:

| Trường hợp | `code` | `detail` |
|---|---|---|
| gốc không phải mảng | `FILE_PARSE_ERROR` | `JSON must be an array of objects.` |
| phần tử không phải object | `FILE_PARSE_ERROR` | `Element N of the array is not an object.` |
| key trùng trong một object | `FILE_PARSE_ERROR` | `Duplicate key "k" at row N.` |
| JSON sai cú pháp | `FILE_PARSE_ERROR` | `Invalid JSON near row N.` |
| mảng rỗng | `FILE_EMPTY` | `JSON array is empty.` |
| giá trị là object hoặc mảng | `JSON_NOT_FLAT` | `Nested value at row N, key "k" is not supported yet.` |

`detail` MUST NOT chứa giá trị trong file.

#### Scenario: Key khác nhau giữa các object
- **WHEN** JSON là `[{"id":1,"name":"An"},{"id":2,"email":"b@x.com"}]`
- **THEN** các cột là `id`, `name`, `email`; row 1 là `["1","An",null]`; row 2 là `["2",null,"b@x.com"]`

#### Scenario: Số giữ nguyên chữ gốc
- **WHEN** JSON là `[{"price":12.50,"big":12345678901234567890,"exp":1e3}]`
- **THEN** các ô của row 1 là `12.50`, `12345678901234567890`, `1e3`

#### Scenario: Giá trị lồng nhau bị từ chối
- **WHEN** JSON là `[{"id":1},{"id":2,"address":{"city":"HN"}}]`
- **THEN** việc đọc thất bại với `JSON_NOT_FLAT`, và `detail` là `Nested value at row 2, key "address" is not supported yet.`

#### Scenario: Gốc là object
- **WHEN** JSON là `{"data":[{"id":1}]}`
- **THEN** việc đọc thất bại với `FILE_PARSE_ERROR`, và `detail` là `JSON must be an array of objects.`

### Requirement: Kiểu gốc của ô
Khi nguồn có kiểu, mỗi ô SHALL mang kiểu gốc (`TEXT`, `NUMBER`, `BOOLEAN`, `DATE`) cùng với chữ của nó:
- JSON: kiểu theo token.
- XLSX:
  - ô số → `NUMBER`;
  - ô số có format ngày → `DATE`;
  - ô số có format chỉ giờ → `TEXT`;
  - boolean → `BOOLEAN`;
  - chuỗi và ô lỗi → `TEXT`;
  - công thức lấy kiểu của giá trị đã cache.
- CSV: không có kiểu gốc.

Chữ của ô XLSX SHALL giữ đúng luật chuyển ô XLSX thành chuỗi hiện hành.

#### Scenario: Kiểu ô XLSX
- **WHEN** dòng 2 của sheet có: chuỗi `00123`, số `42`, ngày 2024-02-29 (format `dd/mm/yyyy`), boolean TRUE, công thức `=B2*2`
- **THEN** các ô là `00123` (`TEXT`), `42` (`NUMBER`), `2024-02-29` (`DATE`), `TRUE` (`BOOLEAN`), `84` (`NUMBER`)

### Requirement: Profile của cột
Mỗi lần đọc toàn bộ dataset, hệ thống SHALL tính cho mỗi cột:
- `emptyCount`: số ô rỗng hoặc chỉ khoảng trắng.
- `maxLength`: số ký tự (code point) lớn nhất.
- `inferredType`:
  - Nguồn có kiểu gốc: dùng kiểu gốc. Cột trộn kiểu là `string`. Cột toàn chữ chỉ có thể là `email` hoặc `string`.
  - CSV: xét mọi ô khác rỗng, không trim, và lấy ứng viên đầu tiên khớp mọi ô theo thứ tự:

    | Ứng viên | Luật |
    |---|---|
    | `boolean` | chỉ `true`/`false`, không phân biệt hoa thường |
    | `number` | khớp `-?(0\|[1-9][0-9]{0,14})(\.[0-9]+)?` |
    | `date` | ngày ISO có thật |
    | `email` | email hợp lệ |
    | `string` | còn lại |

  - Cột không có ô khác rỗng nào: `empty`.

#### Scenario: Suy kiểu cột CSV
- **WHEN** CSV có header `id,code,active,joined,mail,note`, row 2 là `1,00123,true,2024-01-31,an@x.com,x` và row 3 là `2,00456,FALSE,2024-02-29,,y`
- **THEN** `inferredType` lần lượt là `number`, `string`, `boolean`, `date`, `email`, `string`; `emptyCount` của `mail` là `1`

#### Scenario: Số quá dài không bị coi là number
- **WHEN** một cột CSV có giá trị `1234567890123456`
- **THEN** `inferredType` của cột là `string`

### Requirement: Giới hạn khi đọc dataset
Khi đọc dataset của toolbox, hệ thống SHALL dừng ngay khi file vượt một trong các giới hạn (cấu hình được) và báo `LIMIT_EXCEEDED`:

| Giới hạn | Mặc định | `detail` |
|---|---|---|
| số row dữ liệu | 500 000 | `File has more than 500000 rows.` |
| số cột | 1 000 | `File has more than 1000 columns.` |
| độ dài một ô | 32 767 ký tự | `Value at row N, column "c" is longer than 32767 characters.` |

Giới hạn này MUST NOT áp cho file của Importer.

#### Scenario: Ô quá dài
- **WHEN** row 7 của một CSV có ô ở cột `note` dài 40 000 ký tự
- **THEN** việc đọc thất bại với `LIMIT_EXCEEDED`, và `detail` là `Value at row 7, column "note" is longer than 32767 characters.`

### Requirement: Dòng trống của CSV và XLSX
Row của CSV hoặc XLSX có mọi ô rỗng hoặc chỉ khoảng trắng SHALL bị bỏ qua, nhưng vẫn giữ chỗ trong cách đánh số, và SHALL được đếm vào `blankRowsSkipped`. Với JSON, mọi phần tử đều là row, và `blankRowsSkipped` là `0`.

#### Scenario: Đếm dòng trống
- **WHEN** CSV có nội dung `a,b\n1,2\n\n , \n3,4\n`
- **THEN** có 2 row (số dòng 2 và 5), và `blankRowsSkipped` là `2`

### Requirement: Ghi bảng ra CSV
Hệ thống SHALL ghi CSV theo RFC 4180, UTF-8, xuống dòng `\r\n`, quote khi cần. Các tuỳ chọn:
- `delimiter`, mặc định `COMMA`;
- `header`, mặc định `true`, ghi tên cột ở dòng đầu;
- `bom`, mặc định `true`, ghi `EF BB BF` ở đầu file;
- `formulaGuard`, mặc định `true`: thêm `'` trước giá trị bắt đầu bằng `=`, `+`, `-`, `@`, tab hoặc CR. Chỉ áp cho header và cho ô có kiểu thật là chữ.

Ô rỗng SHALL được ghi là trường rỗng.

#### Scenario: Chấm phẩy, không BOM
- **WHEN** ghi 2 cột `a`, `b` với một row `["x;y","1"]`, `delimiter` là `SEMICOLON`, `bom` là `false`
- **THEN** file là `a;b\r\n"x;y";1\r\n`

#### Scenario: Formula guard chỉ áp cho chữ
- **WHEN** ghi một row có ô chữ `=SUM(A1)` và ô số `-5` (kiểu thật `NUMBER`), với `formulaGuard` là `true`
- **THEN** hai ô được ghi là `'=SUM(A1)` và `-5`

### Requirement: Ghi bảng ra JSON
Hệ thống SHALL ghi JSON là một mảng object, key là tên cột theo thứ tự cột, UTF-8, không BOM. `pretty`, mặc định `false`, bật thụt lề. Mỗi ô được ghi theo kiểu thật (luật typing):

| Kiểu thật | Ghi thành |
|---|---|
| `TEXT` | string |
| `NUMBER` | literal số **đúng chữ của ô**, nếu chữ là số JSON hợp lệ; không thì string |
| `BOOLEAN` | `true` hoặc `false` |
| `DATE` | string |
| ô rỗng | `null` |

#### Scenario: JSON giữ nguyên số
- **WHEN** đọc JSON `[{"p":12.50,"ok":true,"n":null,"s":"00123"}]` rồi ghi lại JSON với `typing` là `PRESERVE`
- **THEN** file ghi ra là `[{"p":12.50,"ok":true,"n":null,"s":"00123"}]`

### Requirement: Typing khi ghi JSON và XLSX
Tuỳ chọn `typing` SHALL quyết định kiểu thật của mỗi ô:

| `typing` | Ô có kiểu gốc | Ô không có kiểu gốc (CSV) |
|---|---|---|
| `PRESERVE` (mặc định) | kiểu gốc | chữ |
| `STRING` | chữ | chữ |
| `INFER` | kiểu gốc | `inferredType` của cột, nếu là `number`, `boolean` hoặc `date` và ô khớp luật của kiểu đó; còn lại là chữ |

#### Scenario: CSV sang JSON mặc định không đổi kiểu
- **WHEN** CSV `id,code\n1,00123\n` được ghi ra JSON với `typing` mặc định
- **THEN** file là `[{"id":"1","code":"00123"}]`

#### Scenario: CSV sang JSON với INFER
- **WHEN** CSV `id,code,ok\n1,00123,true\n2,00456,false\n` được ghi ra JSON với `typing` là `INFER`
- **THEN** file là `[{"id":1,"code":"00123","ok":true},{"id":2,"code":"00456","ok":false}]`

### Requirement: Ghi bảng ra XLSX
Hệ thống SHALL ghi XLSX có một sheet:
- dòng 1 là header (ô chữ);
- mỗi row là một dòng;
- ô được ghi theo kiểu thật:

  | Kiểu thật | Ô ghi ra |
  |---|---|
  | `NUMBER` | ô số, chỉ khi giá trị có tối đa 15 chữ số có nghĩa; không thì ô chữ |
  | `BOOLEAN` | ô boolean |
  | `DATE` | ô ngày, format `yyyy-mm-dd` |
  | `TEXT` | ô chữ; MUST NOT bao giờ là công thức |

Tên sheet lấy từ tuỳ chọn `sheetName`, mặc định `Sheet1`. Các ký tự `[]:*?/\` bị thay bằng `_`, và tên bị cắt còn 31 ký tự.

#### Scenario: Chữ trông như công thức vẫn là chữ
- **WHEN** ghi XLSX một ô chữ `=1+1`
- **THEN** đọc lại workbook, ô đó có kiểu chuỗi với giá trị `=1+1`, không phải công thức

#### Scenario: Tên sheet không hợp lệ được làm sạch
- **WHEN** ghi XLSX với `sheetName` là `Q1/2024: [draft]`
- **THEN** sheet có tên `Q1_2024_ _draft_`

### Requirement: Round-trip không làm mất dữ liệu
Đọc một bảng rồi ghi lại với `typing` là `PRESERVE`, rồi đọc file vừa ghi, SHALL cho cùng tên cột, cùng số row, và cùng giá trị của từng ô:
- Khi nguồn hoặc đích là CSV: cùng **chữ** của từng ô.
- Giữa hai định dạng có kiểu (JSON, XLSX): cùng **giá trị**. Số so theo giá trị, vì XLSX hiển thị `12.50` thành `12.5`. Boolean không phân biệt hoa thường, vì XLSX hiển thị `TRUE`/`FALSE`. Chữ và ngày giống từng chữ.
- Giữa hai định dạng có kiểu, kiểu gốc của ô SHALL giữ nguyên, với hai ngoại lệ:
  - ngày đi vào JSON thành `TEXT`, vì JSON không có kiểu ngày;
  - số có hơn 15 chữ số có nghĩa đi vào XLSX thành `TEXT` (chữ vẫn giữ nguyên).

#### Scenario: CSV sang XLSX rồi về CSV
- **WHEN** CSV có 3 cột và 100 row, với chữ tiếng Việt, ô rỗng, ô `=x` và ô có xuống dòng, được ghi ra XLSX rồi đọc lại và ghi ra CSV với `formulaGuard` là `false`
- **THEN** cột và mọi ô của CSV cuối giống CSV ban đầu

### Requirement: Importer giữ nguyên hành vi đọc và export
Việc đọc file upload của Importer và các file export của Importer (JSON, CSV, báo cáo lỗi) SHALL giống hệt từng byte so với trước change này, với cùng input và cấu hình. Upload `.json` vào Importer SHALL vẫn bị từ chối với `415 FILE_UNSUPPORTED`.

#### Scenario: Export Importer giống bản chụp
- **WHEN** chạy luồng e2e `customers.csv` rồi tải `export?format=json`, `export?format=csv` và `errors/export`
- **THEN** ba file giống hệt các file golden đã chụp trước change này

#### Scenario: Importer không nhận JSON
- **WHEN** client upload `data.json` có nội dung `[{"a":1}]` vào `POST /api/import-sessions`
- **THEN** hệ thống trả `415` với `code` là `FILE_UNSUPPORTED`

