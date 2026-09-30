> Viết test trước cho hành vi mới (TDD). Xong thì `pnpm test`, `pnpm lint`, `pnpm build` phải xanh.

## 1. Thu gọn sidebar

- [x] 1.1 TDD trong `src/app/AppShell.test.tsx`: mặc định mở; bấm thì thu gọn (tên nút, `aria-expanded`, công cụ vẫn có tên và `aria-current`); chuyển công cụ khi đang thu gọn; nhớ trạng thái sau khi render lại; `localStorage` ném lỗi thì vẫn chạy.
- [x] 1.2 `AppShell.tsx`: state thu gọn đọc/ghi `localStorage` (bọc try/catch); nút bật/tắt với `aria-expanded` và `aria-controls`; nút công cụ hiện `Icon` khi thu gọn, tên công cụ vẫn nằm trong nút (ẩn về mặt hiển thị) và có `title` để hiện khi di chuột.
- [x] 1.3 `AppShell.module.css`: dải thu gọn khoảng 64px; ẩn tên app, "Công cụ" và nhãn phiên bản về mặt hiển thị; co giãn bằng transition, tắt khi `prefers-reduced-motion`.
- [x] 1.4 Icon "panel" trong `src/shared/ui/icons.tsx`; chuỗi giao diện trong `messages.ts`.

## 2. Hoàn tất

- [x] 2.1 Chạy app thật, chụp sidebar ở hai trạng thái.
  - Chrome headless ở 1440×900: khi mở, nút bật/tắt nằm góc phải cạnh tên app. Khi thu gọn, dải rộng 64px gồm nút bật/tắt, ô vuông vàng và icon công cụ nền vàng. Tải lại trang vẫn thu gọn. Console sạch.
  - 252 test, lint và build xanh. Đã làm mutation check (không lưu trạng thái, không bắt lỗi `localStorage`, thiếu `title` khi thu gọn, `aria-expanded` ngược): đều có test fail.
- [x] 2.2 Archive change, dồn delta vào `openspec/specs/app-shell`.

> `src/test/setup.ts` xoá `localStorage` sau mỗi test. **LÝ DO:** trạng thái thu gọn lưu trong trình duyệt, không xoá thì test sau kế thừa sidebar thu gọn của test trước.
