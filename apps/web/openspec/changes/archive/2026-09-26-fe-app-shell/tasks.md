> Viết test trước cho hành vi mới (TDD). Xong mọi nhóm thì `pnpm test`, `pnpm lint`, `pnpm build` phải xanh. Phần restyle không có hành vi mới thì giữ xanh toàn bộ test hiện có.

## 1. Nền

- [x] 1.1 Cài `@fontsource/be-vietnam-pro` và `@fontsource/jetbrains-mono`, import các weight cần dùng trong `main.tsx`.
  - Be Vietnam Pro 400/500/600/700, JetBrains Mono 400/500.
- [x] 1.2 `src/index.css`: thay token theo design D4 (màu, bo góc, bóng đổ, font), thêm style cho `<progress>`.
- [x] 1.3 `src/shared/ui/icons.tsx`: icon SVG dạng nét, `aria-hidden` (upload, file, check, lỗi, cảnh báo, import).

## 2. Khung app (spec app-shell)

- [x] 2.1 TDD `src/app/AppShell.tsx` và `src/app/tools.tsx`:
  - sidebar có tên app và vùng "Công cụ" liệt kê công cụ từ danh sách đăng ký;
  - công cụ đang mở có `aria-current="page"`;
  - h1 là tên công cụ, kèm mô tả;
  - `document.title` lấy từ `messages.appName`.
  - File danh sách đặt tên `tools.ts` (không có JSX). Công cụ Import nằm ở `src/app/ImportTool.tsx`.
  - Test chạy với hai công cụ giả, chứng minh sidebar lấy từ danh sách đăng ký và đổi công cụ thì đổi trang. `AppShell` nhận danh sách qua prop `tools`; `App` truyền danh sách thật.
- [x] 2.2 `App.tsx` render AppShell. `WizardShell` bỏ header. `usePreventFileDrop` chuyển lên AppShell. Cập nhật smoke test.
  - `<main>` chuyển lên AppShell (bọc tiêu đề trang và nội dung công cụ). WizardShell chỉ còn stepper và thẻ nội dung.
  - Test "thả file ra ngoài vùng upload" chuyển từ `WizardShell.test.tsx` sang `AppShell.test.tsx`.

## 3. Giao diện theo mockup

- [x] 3.1 Stepper: vạch tiến độ trên mỗi bước, vòng số; bước đang ở tô màu nhấn; bước đã xong có dấu ✓; bước mở được tô nhạt; bước bị khoá mờ.
  - Bước đã xong hiện ✓ nhưng vẫn giữ số thứ tự cho screen reader, nên tên nút vẫn là "1 Upload file (đã xong)".
  - ~~Nhãn dài bị cắt bằng dấu "…"~~ Nhãn dài được xuống dòng. **LÝ DO:** ảnh chụp app thật cho thấy nhãn bị cắt thành "Xem trước dữ li…", mất chữ.
- [x] 3.2 Bước Upload:
  - vùng kéo-thả có icon và nút "Chọn file"; gợi ý định dạng đặt bên dưới;
  - thẻ "file đang dùng" có dấu ✓;
  - lúc đang upload, thay vùng kéo-thả bằng thẻ file: icon, tên, dung lượng và loại, nút Huỷ, thanh %.
  - Vùng kéo-thả được ẩn bằng `hidden`, input vẫn nằm trong DOM ở trạng thái khoá, nên các test về khoá và focus vẫn đúng.
  - Viền focus của vùng kéo-thả chỉ hiện khi dùng bàn phím (`:has(:focus-visible)`), không hiện khi focus được trả về sau lỗi.
- [x] 3.3 `ErrorBanner` (icon, chip mã lỗi) và `ConfirmPanel` (icon, nút chính và nút phụ) theo mockup.
- [x] 3.4 Chỗ giữ của các bước chưa làm: khung nét đứt.

## 5. Đổi sang hướng C "Khối Thuỵ Sĩ"

> **LÝ DO:** user thấy bản giao diện đầu tiên (thẻ trắng, một màu nhấn xanh) quá phổ biến. Mình vẽ 6 hướng khác trên canvas Claude Design (artboard A–F), user chọn **C · Khối Thuỵ Sĩ**. Token và bố cục của nhóm 1–3 được thay theo hướng này (design D4, D5).

- [x] 5.1 Font tiêu đề Bricolage Grotesque (`@fontsource/bricolage-grotesque` 700/800, có subset tiếng Việt).
- [x] 5.2 Token trong `index.css` theo hướng C:
  - nền giấy `#F2EFE8`, mực `#111111`, thẻ trắng, vàng nhấn `#FFD43B`;
  - viền 2 px màu mực, không bo góc, bóng đổ cứng;
  - thanh `<progress>` dày, có viền, phần đã chạy màu vàng.
- [x] 5.3 AppShell:
  - sidebar đen, ô vuông vàng làm dấu, tên app chữ đậm xếp hai dòng;
  - mục công cụ đang mở nền vàng, kèm số thứ tự và mũi tên; số thứ tự `aria-hidden`, nên tên nút vẫn là tên công cụ;
  - h1 chữ đậm 68 px.
- [x] 5.4 Stepper dạng 6 ô chung viền: số lớn và nhãn; ô đang ở nền vàng; ô đã xong có dấu ✓; ô bị khoá chữ mờ.
- [x] 5.5 Bước Upload:
  - vùng thả nằm ngang: ô icon vàng và câu hướng dẫn bên trái, nút "Chọn file" đen có bóng vàng bên phải;
  - gợi ý chia 3 cột, mỗi cột có nhãn nhỏ (CSV, XLSX, Dung lượng);
  - thẻ "file đang dùng", thẻ đang upload, báo lỗi, hộp xác nhận: cùng ngôn ngữ viền 2 px và bóng cứng.
- [x] 5.6 Chạy app thật, chụp màn chờ và màn lỗi để đối chiếu với artboard C.
  - Đã chụp (Chrome headless, 1440×900): màn chờ khớp artboard C; upload thật khi BE tắt ra khối lỗi "Máy chủ đang lỗi (502)" có nút "Upload lại"; console không có lỗi. 103/103 test, lint, build xanh.
  - Các trạng thái mockup C chưa vẽ (đang upload, thẻ file đang dùng, hộp xác nhận, ô bước đã xong) được suy ra theo cùng ngôn ngữ: viền mực 2 px, bóng cứng, vàng làm điểm nhấn; ghi ở design D4.

## 4. Hoàn tất

- [x] 4.1 Cập nhật `fe-import-wizard-v0-1/design.md`: D14 trỏ tới mockup và token mới, D17 thêm `src/app/`.
- [x] 4.2 `pnpm test`, `pnpm lint`, `pnpm build` xanh.
  - Ngoài test tự động: đã chạy app thật (Vite + Chrome headless điều khiển qua DevTools Protocol), chụp màn chờ, rồi upload thật một file CSV khi BE tắt.
    - Kết quả: "Máy chủ đang lỗi (502)" kèm nút "Upload lại"; console không có lỗi.
  - Ảnh chụp dẫn tới hai chỉnh sửa bố cục, ghi lý do ở design D5:
    - nhãn `V0.1` chuyển xuống chân sidebar;
    - vùng nội dung rộng tối đa 1120 px.
