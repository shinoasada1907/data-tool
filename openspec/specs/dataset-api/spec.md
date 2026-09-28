# dataset-api Specification

## Purpose
Cho mọi tool của toolbox một nguồn dữ liệu chung: upload file CSV/XLSX/JSON một lần, xem trước theo tuỳ chọn đọc (delimiter, encoding, sheet, header) mà không upload lại, xoá ngay khi muốn, và tự hết hạn sau 24 giờ không dùng. Tạo bởi change `core-04-platform-services` (2026-09-28).
## Requirements
### Requirement: Upload dataset
Hệ thống SHALL nhận file qua `POST /api/datasets` (multipart, part tên `file`). Chỉ nhận:
- `.csv`: không có byte `0x00` trong 8 KB đầu, trừ khi file bắt đầu bằng BOM UTF-16;
- `.xlsx`: bắt đầu bằng `PK\x03\x04`, qua được zip guard, và đọc được danh sách sheet;
- `.json`: ký tự khác khoảng trắng đầu tiên (sau BOM UTF-8 nếu có) là `[`.

Đuôi file không phân biệt hoa thường. MIME do client gửi SHALL bị bỏ qua.

Khi được chấp nhận, hệ thống SHALL lưu file bằng tên do server sinh và trả `201` kèm header `Location: /api/datasets/{id}` và body `DatasetDto { id, originalFileName, format, sizeBytes, sheets, createdAt, expiresAt }`:
- `format` là `CSV`, `XLSX` hoặc `JSON`;
- `sheets` là `[{ name, visible }]` theo thứ tự trong workbook với XLSX, `null` với định dạng khác.

Lỗi:

| Trường hợp | Response |
|---|---|
| đuôi khác, hoặc magic bytes sai | `415 FILE_UNSUPPORTED` |
| file 0 byte | `422 FILE_EMPTY` |
| XLSX hỏng hoặc vượt zip guard | `422 FILE_PARSE_ERROR` |
| JSON không bắt đầu bằng `[` | `422 FILE_PARSE_ERROR`, `detail` là `JSON must be an array of objects.` |
| vượt dung lượng | `413 FILE_TOO_LARGE` |

Upload thất bại MUST NOT để lại file hay row nào.

#### Scenario: Upload CSV
- **WHEN** client upload `khách hàng.csv` có nội dung `ma;ten\n1;An\n`
- **THEN** hệ thống trả `201` với `format` là `CSV`, `originalFileName` là `khách hàng.csv`, `sheets` là `null`, và `expiresAt` cách `createdAt` 24 giờ

#### Scenario: Upload XLSX có sheet ẩn
- **WHEN** client upload workbook có sheet `Hidden` (ẩn) và `Data`
- **THEN** `sheets` là `[{ "name": "Hidden", "visible": false }, { "name": "Data", "visible": true }]`

#### Scenario: JSON không phải mảng
- **WHEN** client upload `data.json` có nội dung `{"a":1}`
- **THEN** hệ thống trả `422` với `code` là `FILE_PARSE_ERROR`, và không có dataset nào được tạo

#### Scenario: Đuôi không hỗ trợ
- **WHEN** client upload `data.xls`
- **THEN** hệ thống trả `415` với `code` là `FILE_UNSUPPORTED`

### Requirement: Xem trước dataset theo tuỳ chọn đọc
Hệ thống SHALL trả dữ liệu mẫu qua `GET /api/datasets/{id}/preview`. Query param, cái nào cũng tuỳ chọn:

| Param | Giá trị |
|---|---|
| `limit` | 1–200, mặc định 50 |
| `sheet` | tên sheet |
| `delimiter` | `COMMA`, `SEMICOLON`, `TAB`, `PIPE` |
| `encoding` | `UTF-8`, `UTF-16`, `WINDOWS-1258`, `WINDOWS-1252` |
| `hasHeader` | `true`, `false` |

Giá trị enum nhận cả hoa lẫn thường. Hệ thống SHALL đọc toàn bộ file theo tuỳ chọn và trả `DatasetPreviewDto`, gồm:
- `options`: giá trị thật đã dùng;
- `autoDetected`: tên các tuỳ chọn được tự nhận;
- `sheetName` và `sheets` (với XLSX);
- `columns[{ index, name, inferredType, emptyCount }]`;
- `totalRows`, `blankRowsSkipped`, `previewLimit`;
- `rows[{ rowNumber, values }]`, với `values` là mảng `string|null` theo thứ tự cột, dài tối đa `limit`.

Lỗi đọc file SHALL được trả tại đây: `FILE_PARSE_ERROR`, `FILE_EMPTY`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `CONFIG_INVALID` (sheet không tồn tại). Tham số sai giá trị SHALL trả `400 REQUEST_INVALID`.

#### Scenario: Tự nhận chấm phẩy
- **WHEN** dataset là CSV `ma;ten\n1;An\n2;Bình\n`, và client gọi preview không có tham số
- **THEN** `options.delimiter` là `SEMICOLON`, `autoDetected` chứa `delimiter`, `columns` là `ma`, `ten`, `totalRows` là `2`, và `rows[0]` là `{ rowNumber: 2, values: ["1","An"] }`

#### Scenario: Đổi delimiter không cần upload lại
- **WHEN** client gọi preview với `delimiter=COMMA` trên cùng dataset
- **THEN** `columns` là một cột tên `ma;ten`, và `autoDetected` không chứa `delimiter`

#### Scenario: limit ngoài khoảng
- **WHEN** client gọi preview với `limit=500`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

### Requirement: Đọc thông tin và xoá dataset
`GET /api/datasets/{id}` SHALL trả `DatasetDto`. `DELETE /api/datasets/{id}` SHALL xoá row và file của dataset rồi trả `204`. Sau khi bị xoá, mọi lệnh trên id đó SHALL trả `404 DATASET_NOT_FOUND`. Khi dataset đang được đọc và không xoá được trong 5 giây, `DELETE` SHALL trả `503 SERVER_BUSY` kèm `Retry-After`. id không phải UUID SHALL trả `400 REQUEST_INVALID`.

#### Scenario: Xoá rồi đọc lại
- **WHEN** client gọi `DELETE /api/datasets/{id}`, rồi `GET /api/datasets/{id}/preview`
- **THEN** lệnh đầu trả `204`, lệnh sau trả `404` với `code` là `DATASET_NOT_FOUND`, và thư mục storage của dataset không còn

### Requirement: Dataset tự hết hạn sau 24 giờ không dùng
Mỗi dataset SHALL có `expiresAt = lastUsedAt + TTL`, với TTL cấu hình qua `toolbox.retention.dataset-ttl` (mặc định `24h`). Mỗi lần dataset được dùng (đọc thông tin, preview, hoặc làm nguồn cho một tool), `lastUsedAt` SHALL được cập nhật, tối đa một lần mỗi 10 phút. Dataset đã hết hạn SHALL được coi như không tồn tại (`404 DATASET_NOT_FOUND`), kể cả khi việc dọn dẹp chưa kịp xoá nó. Việc dọn dẹp định kỳ SHALL xoá row và file của dataset hết hạn.

#### Scenario: Dùng lại thì kéo dài hạn
- **WHEN** dataset tạo lúc 08:00, được preview lúc 20:00, và TTL là 24 giờ
- **THEN** `GET /api/datasets/{id}` lúc 20:00 trả `expiresAt` là 20:00 ngày hôm sau

#### Scenario: Hết hạn thì không đọc được
- **WHEN** dataset không được dùng trong 25 giờ, và cleanup chưa chạy
- **THEN** `GET /api/datasets/{id}` trả `404` với `code` là `DATASET_NOT_FOUND`

### Requirement: Mã lỗi của các endpoint dataset
Mỗi endpoint dataset SHALL chỉ trả các `code` sau, ngoài các ngoại lệ chung của spec `api-errors`:

| Endpoint | Mã lỗi được phép |
|---|---|
| `POST /api/datasets` | `REQUEST_INVALID`, `FILE_TOO_LARGE`, `FILE_UNSUPPORTED`, `FILE_EMPTY`, `FILE_PARSE_ERROR`, `SERVER_BUSY` |
| `GET /api/datasets/{id}` | `REQUEST_INVALID`, `DATASET_NOT_FOUND` |
| `GET /api/datasets/{id}/preview` | `REQUEST_INVALID`, `DATASET_NOT_FOUND`, `FILE_PARSE_ERROR`, `FILE_EMPTY`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `CONFIG_INVALID`, `SERVER_BUSY` |
| `DELETE /api/datasets/{id}` | `REQUEST_INVALID`, `DATASET_NOT_FOUND`, `SERVER_BUSY` |

#### Scenario: Dataset không tồn tại
- **WHEN** client gọi `GET /api/datasets/{id}` với một UUID ngẫu nhiên
- **THEN** hệ thống trả `404` dạng `application/problem+json` với `code` là `DATASET_NOT_FOUND`

