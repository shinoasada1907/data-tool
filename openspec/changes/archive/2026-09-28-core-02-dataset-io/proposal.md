## Why

Converter, Validator, Cleaner và Diff đều đọc CSV/XLSX/JSON và ghi CSV/JSON/XLSX. Parser hiện tại chỉ phục vụ Importer: CSV bắt buộc UTF-8 và dấu phẩy, XLSX chỉ đọc sheet đầu, không có JSON, không mang kiểu của ô. Exporter thì gắn với kết quả pipeline. Notion Converter yêu cầu tuỳ chọn delimiter, encoding, header, chọn sheet, JSON phẳng, và "không tự thay đổi semantic value". Change này dựng lớp đọc/ghi bảng dùng chung trong `core`. Đây là nền cho mọi tool mới.

## What Changes

- **Mô hình bảng trung lập** (`core.table`):
  - Đổi tên: `ImportRow` → `Row`, `SourceColumn` → `Column`.
  - Thêm `CellKind`/`CellKinds` (kiểu gốc của ô), `TableInfo`, `ColumnProfile`, `ReadOptions`, `ReadLimits`, `DataFormat` (thay `SourceFileType`).
  - `SourceSchema` chuyển thành record riêng của Importer.
- **SPI `TableReader`** thay cho `SourceParser`: `inspect(in, options)` trả `TableInfo`; `read(in, info)` trả `Stream<Row>`.
  - CSV: tự nhận delimiter (`,` `;` tab `|`) và encoding (BOM, UTF-8 chặt); chọn được `UTF-16`, `WINDOWS-1258`, `WINDOWS-1252`; có `hasHeader`.
  - XLSX: chọn sheet theo tên, liệt kê sheet, `hasHeader`, mang kiểu ô.
  - **JSON mới**: mảng object phẳng, đọc stream bằng Jackson. Lồng nhau → `JSON_NOT_FLAT`.
- **Profile cột**: `inferredType`, `emptyCount`, `maxLength`, tính ngay trong `inspect`.
- **Giới hạn đọc** (`ReadLimits`): số row, số cột, độ dài ô → `LIMIT_EXCEEDED`. Chỉ áp cho dataset của toolbox; Importer truyền `ReadLimits.NONE`.
- **SPI `TableWriter`** với `CsvTableWriter`, `JsonTableWriter`, `XlsxTableWriter`.
  - Có typing `PRESERVE|STRING|INFER` và formula guard cho CSV.
  - `fastexcel` (writer) chuyển từ scope `test` sang `compile`.
- **Exporter của Importer viết lại trên `TableWriter`**. File ra giống hệt từng byte, có golden test canh.
- **`KeyHasher`/`KeyIndex`**: nhớ khoá của cả tập dữ liệu bằng hash 128-bit, dùng cho unique, dedupe và diff.
- `FileTypeDetector` nhận thêm JSON và CSV có BOM UTF-16. Upload của Importer vẫn chỉ nhận CSV/XLSX.
- Thêm mã lỗi `JSON_NOT_FLAT` và `LIMIT_EXCEEDED` (422). Chưa endpoint nào dùng; core-04 sẽ đưa vào API.

## Capabilities

### New Capabilities
- `dataset-io`: đọc CSV/XLSX/JSON theo tuỳ chọn thành bảng trung lập (cột, row, kiểu gốc của ô, profile, giới hạn), và ghi bảng ra CSV/JSON/XLSX (typing, formula guard, giới hạn XLSX, round-trip).

### Modified Capabilities
(không có. Hành vi đọc và export của Importer giữ nguyên theo `source-parsing` và `data-export`, có golden test canh)

## Impact

- **Code**:
  - `core.table`, `core.format.{csv, xlsx, json}`: mới, hoặc đổi tên từ core-01.
  - `tools.importer`: dùng `TableReader`/`TableWriter` qua adapter. `SourceParsers` thành `TableReaders`.
- **Dependencies**: `org.dhatim:fastexcel` 0.20.2 chuyển sang scope `compile`. Jackson 3 streaming đã có.
- **API**: không đổi. Hai mã lỗi mới được khai nhưng chưa endpoint nào trả.
- **DB**: không đổi. Cột `source_schema` của Importer giữ nguyên định dạng jsonb.
