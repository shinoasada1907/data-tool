## Context

- **Nguồn yêu cầu**: Notion "02 — Data Converter" (MVP scope, activity flow, DoD).
- **Nền đã có**:
  - core-02: `TableReader`/`TableWriter` cho CSV, XLSX, JSON; typing; formula guard.
  - core-04: `DatasetSources`, `OutputSpec`, `Downloads`, `DownloadNames`.
- **Contract**: mục "API contract Toolbox v1 → Converter" trong design của core-01 (đã chốt với FE, C4).
- **Chưa có** (core-05): rate limit, `ProcessingGate`, metrics. Converter sẽ được gắn vào khi core-05 xong.

## Goals / Non-Goals

**Goals:** đạt đủ DoD của Notion với một endpoint. Nguồn và đích là bất kỳ cặp nào trong {CSV, XLSX, JSON}.

**Non-Goals:**
- JSON lồng nhau (Notion để stage sau; reader trả `JSON_NOT_FLAT`).
- Ghi CSV bằng encoding khác UTF-8.
- Gộp hay tách nhiều sheet.

## Decisions

### CV1. Một endpoint, không trạng thái

`POST /api/converter/convert` nhận `{ source: {datasetId, options?}, output: OutputDto }` và trả `200` kèm file. Server không lưu gì thêm ngoài việc cập nhật lần dùng của dataset. Upload và preview đi qua `/api/datasets` (lệch Notion N1).

### CV2. Mọi kiểm tra xảy ra trước byte đầu tiên

Theo thứ tự:
1. Parse body. Thiếu `source` hoặc `output` thì trả `400 REQUEST_INVALID`.
2. `OutputSpec.from(output, tên sheet mặc định)` → có thể trả `CONFIG_INVALID`.
3. `DatasetSources.open(source)` → có thể trả `DATASET_NOT_FOUND`, hoặc các lỗi đọc: `FILE_PARSE_ERROR`, `FILE_EMPTY`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `CONFIG_INVALID`.
4. Đích là XLSX thì kiểm giới hạn bằng `TableInfo`:
   - `rowCount > 1 048 575` → `422 LIMIT_EXCEEDED`, `detail` là `XLSX holds at most 1048575 rows; the dataset has N.`;
   - có cột với `maxLength > 32 767` → `422 LIMIT_EXCEEDED`, `detail` là `Column "c" has a value longer than 32767 characters, more than an XLSX cell holds.`
5. Rồi mới stream qua `Downloads.send`.

Sau byte đầu tiên chỉ còn lỗi I/O. Khi đó kết nối bị huỷ, như mọi download.

### CV3. Tên sheet và tên file

- Tên sheet XLSX mặc định là tên sheet nguồn nếu nguồn là XLSX, không thì `Sheet1`.
- Tên file là `DownloadNames.of(originalFileName, "." + extension)`. Ví dụ `khách hàng.csv` → `khách hàng.xlsx`. Cùng định dạng thì tên vẫn theo luật đó: `data.csv` → `data.csv`.

### CV4. Giá trị và kiểu

- Mỗi row của nguồn thành một row của đích, theo cùng thứ tự; tên cột giữ nguyên.
- Mỗi ô được ghi thành `TypedCell(text, kind)` với kiểu gốc của nguồn, rồi writer áp typing (TD7):
  - `PRESERVE`: JSON number giữ literal, XLSX giữ kiểu ô, CSV thành chữ;
  - `STRING`: mọi ô thành chữ;
  - `INFER`: ô chưa có kiểu thì lấy theo `inferredType` của cột.
- Profile cột (từ inspect) được truyền vào `OutputColumn`, để `INFER` dùng.

### CV5. Package và đăng ký

- `tools.converter.api.ConverterController` + `ConvertRequest` (record gồm `SourceDto` và `OutputDto`).
- `tools.converter.application.ConverterService`.
- `SourceDto { datasetId, options: {sheet, delimiter, encoding, hasHeader} }` là DTO dùng chung, đặt ở `platform.dataset` để các tool sau dùng lại. Parse delimiter/encoding bằng `DatasetController.delimiter/encoding`. Giá trị sai → `CONFIG_INVALID` (trong body), khác với query của preview (`REQUEST_INVALID`).
- Thêm `converter` vào `TOOL_NAMES` của `ArchitectureTest`. Thêm nhóm OpenAPI `converter` (`/api/converter/**`).

## Ánh xạ DoD Notion

| DoD | Requirement / test |
|---|---|
| CSV → JSON/XLSX | "Đổi dataset sang định dạng khác" (scenario CSV→JSON, CSV→XLSX) |
| XLSX → CSV/JSON | như trên (scenario XLSX chọn sheet → CSV) |
| JSON flat → CSV/XLSX | như trên (scenario JSON→CSV) |
| Preview động | `GET /api/datasets/{id}/preview` (spec `dataset-api`) |
| Giữ đúng số row và giá trị | "Giữ nguyên row và giá trị"; round-trip của core-02 |
| Round-trip parser/exporter | `RoundTripTest` (core-02) |
| File lỗi trả structured error | "Lỗi trước khi tải về" |

## Risks / Trade-offs

- **Chưa có giới hạn đồng thời** (core-05). Một file lớn chiếm một thread trong vài giây. Chấp nhận khi dev; phải xong core-05 trước khi mở public.
- **Mỗi lần convert đọc file hai lượt** (inspect rồi read). Nếu người dùng vừa preview với cùng tuỳ chọn thì cache inspect bỏ được lượt đầu.

## Open Questions

(không có)
