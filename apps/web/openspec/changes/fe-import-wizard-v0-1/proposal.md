## Why

Universal Importer V0.1 cần một giao diện web để user đi hết luồng: upload CSV/XLSX → xem dữ liệu nguồn → định nghĩa schema đích → map → cấu hình transformation/validation → chạy → xem kết quả → export. Hiện `apps/web` mới là template Vite. Backend được phát triển riêng. Lúc bắt đầu chưa có JSON contract, nên FE viết một bản đề xuất. Ngày 2026-09-25, BE đã chốt **API contract V0.1** dựa trên bản đề xuất đó. FE cần bản kế hoạch bám contract này, để làm song song với BE (dùng mock) mà không phải đoán.

Nguồn yêu cầu: Notion "Universal Importer — Agent Project Pack" (00 Context, 01 Requirements, 02 System Design, 03 Feature Breakdown), phần FE-F01 → FE-F11.

## What Changes

- Thay template Vite bằng **Import Wizard 6 bước**: Upload → Source Preview → Target Schema → Mapping → Transform & Validate (nút "Chạy xử lý") → Result & Export.
- Thêm lớp API client cho 10 trong 11 endpoint `/api/import-sessions/...` của **API contract V0.1** mà BE đã chốt (DTO, error envelope, mã lỗi). Nguồn chuẩn là `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md` ở gốc repo.
- Thêm cơ chế theo dõi cấu hình: bước nào cần lưu lại và kết quả nào đã cũ (stale), để sửa config rồi chạy lại mà không lệch dữ liệu.
- Thêm mock BE bằng MSW, dùng cho cả test lẫn chế độ `pnpm dev:mock` (chạy FE không cần BE).
- Thêm bộ test Vitest + Testing Library.
- Chốt hai mâu thuẫn trong tài liệu Notion (chi tiết ở design.md):
  - Wizard có **6 bước** theo System Design §12, không phải 7 bước như F11.
  - Thêm trường `rule` vào `ImportError`, để lỗi transformation truy được đúng transformation gây lỗi như FR-06 yêu cầu. BE đã nhận, và bổ sung thêm `stage`, `step`.

## Capabilities

### New Capabilities

- `import-upload`: chọn/kéo-thả file CSV/XLSX, kiểm tra phía client, upload có tiến độ và huỷ được, tạo import session (FE-F01).
- `source-preview`: bảng xem trước dữ liệu nguồn, giữ đúng thứ tự cột, số dòng, thông tin file/sheet, dùng chung cho CSV và XLSX (FE-F02/F03).
- `target-schema`: tạo schema đích (thêm/xoá/sắp xếp field, 5 kiểu dữ liệu, required), kiểm tên trùng/rỗng, lưu lên BE (FE-F04).
- `field-mapping`: map mỗi target field với một cột nguồn hoặc một giá trị cố định; cảnh báo field chưa map, chặn khi field required chưa map (FE-F05).
- `rule-config`: cấu hình transformation có thứ tự và validation rule theo từng field, kiểm lỗi cấu hình trước khi chạy (FE-F06/F07).
- `pipeline-run`: lưu cấu hình còn thiếu rồi chạy pipeline; trạng thái đang xử lý; chạy lại sau khi sửa (FE-F08).
- `result-review`: tóm tắt total/valid/invalid, xem dòng hợp lệ và dòng lỗi có phân trang, lọc lỗi, xử lý khi kết quả đã cũ (FE-F09).
- `result-export`: tải valid data (JSON/CSV) và error report, quy tắc khoá nút, hiển thị lỗi khi tải (FE-F10).
- `import-wizard`: khung wizard, chặn bước khi thiếu phụ thuộc, đánh dấu cần lưu lại/kết quả cũ, hiển thị lỗi API nhất quán, xử lý session không dùng được nữa, cảnh báo khi rời trang (FE-F11).

### Modified Capabilities

(không có — `apps/web` chưa có spec nào)

## Impact

- **Code**: thay toàn bộ nội dung template trong `apps/web/src`; sửa `vite.config.ts` (proxy `/api`, cấu hình test), `index.html`, `tsconfig.app.json` (`strict`), `package.json` (script `test`, `dev:mock`); thêm `public/mockServiceWorker.js`, `.env.example`, ~~`.env.mock`~~ (bỏ ở review FE-F11: chế độ mock đi theo mode của dev server, design D15); viết lại `README.md`.
- **Dependencies**: chỉ thêm devDependencies (`vitest`, `jsdom`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `msw`). Không thêm runtime dependency.
- **API (phụ thuộc BE)**: 10 endpoint theo Notion §10, cộng `GET /api/import-sessions/{id}` mà V0.1 chưa dùng. Contract V0.1 đã chốt ngày 2026-09-25; BE đã trả lời đủ Q1–Q10 (design.md → Open Questions). FE gọi BE cùng origin qua Vite proxy, không cần CORS.
- **Không đụng** `apps/api` và các file ở gốc repo.
