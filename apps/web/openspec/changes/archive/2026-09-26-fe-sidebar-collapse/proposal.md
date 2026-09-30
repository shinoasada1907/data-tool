## Why

Người dùng muốn thu gọn sidebar để vùng làm việc rộng hơn, nhất là ở các bước có bảng nhiều cột (Xem trước, Schema). Sidebar hiện luôn rộng 280px và không tắt được.

## What Changes

- Thêm nút bật/tắt (icon "panel") ở đầu sidebar.
- Khi thu gọn, sidebar thành dải hẹp khoảng 64px, còn lại:
  - nút bật/tắt;
  - ô vuông vàng;
  - icon của từng công cụ, lấy `Icon` sẵn có trong danh sách công cụ. Công cụ đang mở vẫn nền vàng, di chuột vào thì hiện tên.
- Tên app, chữ "Công cụ" và nhãn phiên bản chỉ ẩn về mặt hiển thị; screen reader vẫn đọc được tên vùng điều hướng.
- Trạng thái thu gọn được nhớ qua lần tải lại trang (`localStorage`). Không lưu được thì mặc định là mở.
- Có hiệu ứng co giãn; tắt khi user chọn giảm chuyển động.

## Capabilities

### New Capabilities

(không có)

### Modified Capabilities

- `app-shell`: thêm requirement "Thu gọn và mở rộng sidebar".

## Impact

- `src/app/AppShell.tsx`, `src/app/AppShell.module.css`, `src/shared/ui/icons.tsx`, `src/shared/messages.ts`.
- Không đổi API, không đổi wizard.
