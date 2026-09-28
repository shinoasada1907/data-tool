## Why

Data Converter là tool thứ hai của Universal Data Tools, được Notion xếp nhóm "Next": đổi dataset giữa CSV, XLSX và JSON mà không phải viết code. Mọi phần nó cần đã có trong core: reader và writer ba định dạng (core-02), dataset upload một lần, preview theo tuỳ chọn, download chung (core-04). Change này chỉ ghép chúng lại thành một endpoint.

## What Changes

- `POST /api/converter/convert` nhận body `{ source: {datasetId, options}, output: OutputDto }` và trả **file** tải về: CSV, XLSX hoặc JSON.
  - Đọc dataset bằng đúng tuỳ chọn người dùng đã xem ở preview.
  - Ghi theo `output`: delimiter, header, BOM, formula guard cho CSV; `pretty` cho JSON; tên sheet cho XLSX; `typing` là `PRESERVE` (mặc định), `STRING` hoặc `INFER`.
  - Mọi lỗi được báo trước byte đầu tiên, gồm cả giới hạn XLSX: 1 048 575 row, 32 767 ký tự mỗi ô.
- Preview của Notion (`/api/converter/preview`) được thay bằng `GET /api/datasets/{id}/preview` (lệch N1).
- Package `tools.converter.{api, application}`. Nhóm OpenAPI `converter`. `ArchitectureTest` thêm tool `converter`.

## Capabilities

### New Capabilities
- `data-converter`: đổi một dataset sang CSV, XLSX hoặc JSON theo tuỳ chọn ghi, giữ nguyên số row và giá trị, không tự đổi nghĩa giá trị.

### Modified Capabilities
(không có)

## Impact

- **Code**: `tools.converter` (mới); `ArchitectureTest`; `OpenApiConfig`.
- **API**: 1 endpoint mới. FE dùng `/api/datasets` để upload và preview, rồi gọi convert.
- **DB**: không đổi.
- **Chưa có**: rate limit và giới hạn đồng thời, sẽ gắn ở core-05 trước khi mở public.
