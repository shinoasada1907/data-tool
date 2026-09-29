## Why

Notion đã tổ chức lại dự án thành "Universal Data Tools — Data Toolbox Platform". Universal Importer giờ chỉ là Tool 01. BE đã chạy xong **Data Validator** (tool 05) trên `dev`, nên cần một màn hình để user kiểm tra một file có đúng schema hay không mà không phải gọi API tay.

Contract lấy từ BE, không đoán:
- Contract chung (dataset, `SourceDto`, `OutputDto`, mã lỗi): mục "API contract Toolbox v1" trong `openspec/changes/archive/2026-09-28-core-01-toolbox-restructure/design.md` ở gốc repo.
- Contract riêng của Validator: `openspec/changes/archive/2026-09-29-tool-05-data-validator/design.md`.

## What Changes

- **Công cụ mới "Kiểm tra dữ liệu"** trên sidebar, gồm 3 bước:
  - **Dữ liệu**: upload CSV/XLSX/JSON; chọn tuỳ chọn đọc (sheet, dấu phân cách, bảng mã, có dòng tiêu đề hay không); xem trước.
  - **Schema**: tự sinh field từ các cột của file; sửa kiểu, bắt buộc, unique và ràng buộc; xem field nào khớp cột nào; mở hoặc lưu file schema `.json`; chạy kiểm tra.
  - **Kết quả**: tóm tắt; tab dòng hợp lệ và dòng lỗi (có lọc theo field và mã lỗi, có phân trang); tải dòng hợp lệ, dòng lỗi hoặc báo cáo lỗi dạng CSV/XLSX/JSON.
- **Nguồn dữ liệu dùng chung (`DatasetSource`)**: component upload, tuỳ chọn đọc và xem trước, gọi `/api/datasets`. Converter, Cleaner và Diff sau này dùng lại.
- **Khung app**:
  - Tên app đổi thành "Universal Data Tools".
  - Công cụ đã mở được giữ nguyên trạng thái khi chuyển sang công cụ khác, vì từ nay có hơn một công cụ.

## Capabilities

### New Capabilities

- `dataset-source`: upload file vào `/api/datasets`, tuỳ chọn đọc theo định dạng, xem trước, đổi hoặc xoá file.
- `data-validator`: công cụ Kiểm tra dữ liệu, gồm schema, chạy, xem kết quả và tải file.

### Modified Capabilities

- `app-shell`: tên app mới; giữ trạng thái công cụ khi chuyển công cụ.

## Impact

- **Code**:
  - Thêm `src/shared/dataset/`, `src/shared/output/` và `src/tools/validator/`.
  - Sửa `src/app/` (danh sách công cụ và AppShell), `src/api/` (upload tổng quát, tải file bằng POST, `pointer` trong lỗi), `messages.ts` và `index.html`.
- **API**: chỉ dùng endpoint BE đã có trên `dev`, gồm dataset và `/api/validator/runs/**`. Không đổi contract.
- **Không làm ở change này**:
  - URL riêng cho từng công cụ, trang chủ.
  - Schema đã lưu trên máy chủ (thuộc Schema Builder).
  - Đếm ngược theo `Retry-After`, vì BE chưa có rate limit.
  - BE giả cho `pnpm dev:mock` (BE thật đã chạy được).
