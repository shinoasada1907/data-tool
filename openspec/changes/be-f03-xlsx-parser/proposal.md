## Why

Pack yêu cầu hỗ trợ XLSX ngang với CSV. Sau F02, file XLSX vẫn dừng ở `UPLOADED` và chưa preview được. Excel lưu số, ngày, công thức và sheet ẩn theo cách mà đọc ngây thơ sẽ ra sai: ngày thành số serial, số điện thoại thành `8.4901234567E10`, sheet ẩn bị đọc nhầm. Thư viện đọc XLSX cũng chưa được kiểm chứng (review A4), nên change này bắt đầu bằng một spike.

## What Changes

- Spike có tiêu chí đạt/không đạt rõ ràng: `fastexcel-reader` 0.20.2 so với Apache POI 5.5.1 (SAX). Kết quả ghi vào design.md trước khi viết parser thật.
- `XlsxSourceParser` cài port `SourceParser` của F02 (`inspect` + `read`), dùng chung `ColumnNames`, luật số dòng, luật dòng trống và contract preview với CSV.
- Đọc sheet **hiển thị đầu tiên**; `sheetName` trả trong preview.
- Chuyển giá trị ô thành chuỗi (D9):
  - số: chuỗi raw → `BigDecimal.toPlainString()`;
  - ngày: ISO `yyyy-MM-dd`, hoặc `yyyy-MM-dd'T'HH:mm:ss` nếu có phần giờ;
  - boolean: `TRUE`/`FALSE`;
  - công thức: giá trị đã cache;
  - ô lỗi: chuỗi lỗi (`#N/A`);
  - merged cell: chỉ ô trên cùng bên trái có giá trị.
- Chống zip bomb: giới hạn tổng dung lượng sau giải nén, tỉ lệ nén và số entry, trước khi parse.
- XLSX hỏng → `FILE_PARSE_ERROR`; sheet đầu trống hoặc dòng 1 trống → `FILE_EMPTY`.
- Fixture test: một phần là file thật lưu từ LibreOffice Calc hoặc Excel, phần còn lại sinh bằng `org.dhatim:fastexcel` (writer, scope test).

## Capabilities

### New Capabilities
(không có)

### Modified Capabilities
(không có — `source-parsing` được bổ sung bằng các requirement mới dành riêng cho XLSX, không sửa requirement của F02)

Ghi chú: `source-parsing` do F02 tạo. Change này chỉ **thêm** requirement (ADDED) vào capability đó, không có MODIFIED.

## Impact

- **Code**: `MAIN/infrastructure/parser/xlsx/*` (parser, guard, nhận diện định dạng ngày).
- **Dependency** (chốt sau spike, một trong hai):
  - `org.dhatim:fastexcel-reader:0.20.2`
  - `org.apache.poi:poi-ooxml:5.5.1`

  Test: `org.dhatim:fastexcel:0.20.2` (writer).
- **Cấu hình mới**:
  - `IMPORTER_XLSX_MAX_UNCOMPRESSED_SIZE` (mặc định `200MB`)
  - `IMPORTER_XLSX_MAX_INFLATE_RATIO` (mặc định `100`)
  - `IMPORTER_XLSX_MAX_ENTRIES` (mặc định `10000`)
- **API**: không có endpoint mới. Upload XLSX giờ trả `status` là `CONFIGURING`; preview XLSX có `sheetName`.
- **DB**: không có migration.
