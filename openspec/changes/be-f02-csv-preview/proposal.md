## Why

Sau F01, session mới chỉ biết file là CSV hay XLSX; nội dung file chưa được đọc. Người dùng cần thấy header và dữ liệu mẫu trước khi định nghĩa schema và map cột. BE cũng cần danh sách cột nguồn để kiểm mapping ở F05. Pack xếp CSV parser vào F02; XLSX để F03 và dùng chung contract.

## What Changes

- Thêm port `SourceParser` trong domain (`inspect` + `read`) cùng model nguồn trung lập: `SourceSchema`, `SourceColumn`, `ImportRow`. `ImportRow` có `rowNumber` và mảng giá trị theo `index` cột.
- Thêm `CsvSourceParser` dùng Apache Commons CSV 1.14.1. Chỉ nhận UTF-8 (có BOM thì bỏ), dấu phẩy, quote theo RFC 4180.
- **Upload đọc toàn bộ file** ngay trong request (D9):
  - lưu `source_schema` (cột, `totalRows`, `sheetName`);
  - chuyển session sang `CONFIGURING`;
  - file hỏng thì trả 422 `FILE_PARSE_ERROR` / `FILE_EMPTY`, xoá file và không tạo session.
- Luật tên cột (trống → `Column C`, trùng → `Email (2)`), số dòng như Excel, dòng trống, row thiếu/thừa ô.
- `GET /api/import-sessions/{id}/preview?limit=50` (tối đa 200) trả `SourcePreviewDto` đúng contract V0.1.
- Flyway V2: thêm cột `import_session.source_schema` (jsonb).

## Capabilities

### New Capabilities
- `source-parsing`: đọc file nguồn khi upload, đặt tên cột, đánh số dòng, luật đọc CSV, giá trị ô, file không có header, xem trước dữ liệu nguồn.

### Modified Capabilities
(không có — thay đổi hành vi upload được mô tả bằng requirement mới trong `source-parsing`; bảng trạng thái của `import-session` không đổi)

## Impact

- **Code**:
  - `MAIN/domain/source/*` (mới)
  - `MAIN/domain/importsession/ImportSession` (thêm `sourceSchema`, `markInspected`, tham số mới cho `restore`)
  - `MAIN/infrastructure/parser/CsvSourceParser`
  - `MAIN/infrastructure/persistence/*` (cột JSON)
  - `MAIN/application/importsession/*` (upload đọc file, preview)
  - `MAIN/api/importsession/SourcePreviewController`
- **Dependency**: `org.apache.commons:commons-csv:1.14.1`. Không nằm trong BOM của Boot nên ghi version cứng.
- **DB**: `V2__add_source_schema.sql`.
- **API**: endpoint #3 (preview). Response upload của CSV giờ có `status` = `CONFIGURING`.
- **Hành vi tạm thời**: XLSX vẫn ở `UPLOADED` và chưa preview được, cho tới khi có F03.
