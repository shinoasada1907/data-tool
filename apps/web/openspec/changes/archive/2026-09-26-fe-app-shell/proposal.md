## Why

Universal Importer sẽ không chỉ có một công cụ: import là tính năng đầu tiên trong một bộ công cụ, và tên dự án có thể đổi về sau. Hiện app chỉ là một màn wizard, header ghi cứng tên sản phẩm. Cần một khung dashboard có sidebar để thêm công cụ mới mà không phải dựng lại bố cục, và tên app nằm ở đúng một chỗ.

Giao diện đi kèm theo mockup đã được duyệt trên Claude Design (https://claude.ai/artifact/MCkwHpanZPv1TUiW6trSbP), phong cách sạch, tối giản.

## What Changes

- Thêm **khung app (AppShell)**:
  - sidebar bên trái: tên app và danh sách công cụ;
  - vùng nội dung bên phải: tiêu đề trang (tên công cụ và mô tả ngắn) và nội dung của công cụ.
- Thêm **danh sách công cụ** (`src/app/tools.tsx`). Thêm công cụ mới = thêm một phần tử vào danh sách này.
- Tên app nằm ở một chỗ (`messages.appName`), dùng cho sidebar và tiêu đề tab trình duyệt.
- Wizard import thành công cụ đầu tiên, "Import dữ liệu". Header riêng của wizard bị bỏ, vì khung app đã lo phần này.
- Áp giao diện theo mockup:
  - font Be Vietnam Pro và JetBrains Mono, đóng gói cùng app;
  - bộ token màu, bo góc, bóng đổ mới;
  - stepper dạng vạch tiến độ, bước đã xong có dấu ✓;
  - vùng kéo-thả có icon và nút "Chọn file";
  - lúc đang upload, vùng kéo-thả được thay bằng thẻ file có thanh %;
  - báo lỗi và hộp xác nhận có icon.

## Capabilities

### New Capabilities

- `app-shell`: khung dashboard gồm sidebar liệt kê công cụ, công cụ đang mở, tiêu đề trang, và tên app ở một chỗ.

### Modified Capabilities

(không có — change `fe-import-wizard-v0-1` chưa archive nên chưa có spec chính; hành vi của wizard không đổi, chỉ đổi giao diện)

## Impact

- **Code**:
  - thêm `src/app/` (AppShell, danh sách công cụ, trang công cụ);
  - `App.tsx` chuyển sang render AppShell;
  - `WizardShell` bỏ header;
  - restyle `index.css`, Stepper, ErrorBanner, ConfirmPanel, UploadStep;
  - thêm `shared/ui/icons.tsx`.
- **Dependencies**: thêm `@fontsource/be-vietnam-pro` và `@fontsource/jetbrains-mono` làm runtime dependency (chỉ là file font). Đây là lệch so với ghi chú "không thêm runtime dependency" của `fe-import-wizard-v0-1`; lý do nằm ở design.md.
- **Test**: smoke test đổi heading (h1 giờ là tên công cụ), thêm test cho AppShell. Mọi test hành vi của wizard giữ nguyên.
- Không đụng `apps/api`, không đổi contract.
