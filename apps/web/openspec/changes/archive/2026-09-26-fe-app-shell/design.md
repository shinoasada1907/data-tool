## Context

- Wizard import (change `fe-import-wizard-v0-1`) hiện chiếm cả trang, header ghi cứng "Universal Importer".
- User muốn app là một dashboard: import chỉ là một công cụ trong danh sách, về sau thêm công cụ khác và có thể đổi tên dự án.
- Giao diện đã được duyệt qua mockup trên Claude Design: https://claude.ai/artifact/MCkwHpanZPv1TUiW6trSbP (5 màn của bước Upload, phong cách sạch, tối giản). Mockup chưa có sidebar; sidebar theo cùng ngôn ngữ hình ảnh.

## Goals / Non-Goals

**Goals:**
- Khung dashboard: sidebar và vùng nội dung. Thêm công cụ = thêm một phần tử vào danh sách công cụ.
- Tên app ở một chỗ.
- Áp đúng giao diện của mockup cho khung app, stepper và bước Upload.

**Non-Goals:**
- Router hay URL riêng cho từng công cụ. Làm khi có công cụ thứ hai (D1).
- Thu gọn sidebar, layout mobile, dark mode.
- Mục sidebar cho công cụ chưa có.

## Decisions

### D1. Danh sách công cụ + state, chưa dùng router
- `src/app/tools.tsx` export mảng `{ id, label, description, Icon, Component }`. AppShell giữ công cụ đang mở trong state, mặc định là công cụ đầu tiên.
- *Vì sao*: mới có một công cụ. Router chỉ đáng thêm khi có công cụ thứ hai, cần URL riêng hoặc nút Back giữa các công cụ.
- *Việc cần quyết khi có công cụ thứ hai*: đổi công cụ thì state của wizard (đang mount trong công cụ Import) mất, trừ khi giữ mount hoặc cảnh báo trước.

### D2. Tên app ở một chỗ
- `messages.appName` dùng cho sidebar, và AppShell đặt `document.title` bằng giá trị đó.
- `<title>` trong `index.html` chỉ hiện trong lúc JS chưa chạy. Đổi tên dự án: sửa `messages.appName`, và nếu muốn thì sửa cả `index.html`.

### D3. Font đóng gói cùng app (`@fontsource`), không tải từ Google Fonts
- *Vì sao*: công cụ nội bộ có thể chạy ở nơi không ra được internet, và không nên gửi request ra bên thứ ba mỗi lần mở app.
- `@fontsource` chỉ chứa file font và CSS, bundle cùng app. Đây là lệch so với ghi chú "không thêm runtime dependency" của `fe-import-wizard-v0-1`; phần lệch chỉ là font.
- Be Vietnam Pro có subset tiếng Việt, được thiết kế cho tiếng Việt. JetBrains Mono dùng cho mã lỗi và dung lượng file.
- Từ hướng C: thêm **Bricolage Grotesque** 700/800 (`@fontsource/bricolage-grotesque`, có subset tiếng Việt) cho tiêu đề, tên app và số bước. Be Vietnam Pro vẫn là font chữ thường.

### D4. Token hình ảnh theo hướng C "Khối Thuỵ Sĩ"
Tất cả nằm trong `src/index.css`, component chỉ dùng biến. Mockup chuẩn: artboard **C · Khối Thuỵ Sĩ** trên canvas.

~~Bộ token đầu tiên: nền `#F5F6F8`, thẻ trắng viền xám nhạt `#E2E5EA`, một màu nhấn xanh `#2D5BE3`, bo góc 8/12/16 px, bóng mềm.~~ **LÝ DO:** user thấy bản này quá phổ biến. Sau 6 hướng A–F vẽ trên canvas, user chọn hướng C.

| Nhóm | Giá trị |
|---|---|
| Nền | giấy `#F2EFE8`; thẻ `#FFFFFF`; nền phụ `#FAF8F3` |
| Chữ | mực `#111111`; chữ phụ `#4D4A43`; chữ bị khoá `#6B6860` |
| Viền, bóng | viền mực 2 px (liền hoặc nét đứt), **không bo góc**; bóng cứng `8px 8px 0` màu mực (thẻ), `4px 4px 0` (nút, khối báo) |
| Màu nhấn | vàng `#FFD43B`, chữ trên vàng là mực. Nút chính: nền mực, chữ trắng, bóng vàng `4px 4px 0` |
| Sidebar | nền mực `#111111`, chữ `#F2EFE8`, chữ phụ `#A9A49A`; viền focus màu vàng |
| Lỗi | chữ `#8A1C0B`, icon `#C4320A`, nền `#FFE3DC` |
| Xác nhận / cảnh báo | nền `#FFF1C2`, icon mực |
| Thành công | nền `#D9F7C4`, dấu ✓ màu mực |
| Focus | viền 3 px màu mực, lệch 3 px (trong sidebar là màu vàng) |

Các trạng thái mockup C chưa vẽ đều suy ra theo cùng ngôn ngữ trên:
- thẻ đang upload: viền mực, thanh % 16 px có viền, phần đã chạy màu vàng;
- thẻ "file đang dùng": ô ✓ nền xanh nhạt có viền;
- hộp xác nhận;
- ô bước đã xong: dấu ✓ thay cho số.

### D5. Bố cục
- Sidebar ~~rộng 248 px~~ rộng 280 px, nền mực (hướng C), cố định bên trái, cao bằng màn hình:
  - trên cùng: ô vuông vàng và tên app chữ Bricolage 34 px, mỗi từ một dòng (`width: min-content`, nên tên nào cũng xếp gọn);
  - danh sách công cụ: mục đang mở nền vàng, có số thứ tự `aria-hidden` và mũi tên;
  - chân: nhãn phiên bản `V0.1`.

  Vùng nội dung cuộn riêng, nội dung rộng tối đa 1120 px. h1 chữ Bricolage 68 px. Stepper là 6 ô chung khung viền mực. Thẻ nội dung có bóng cứng.
  - ~~Nhãn phiên bản cạnh tên app; nội dung rộng tối đa 960 px~~. **LÝ DO:** ảnh chụp app thật ở 1440 px cho thấy nhãn phiên bản đẩy "Universal Importer" xuống hai dòng. Với 960 px, nhãn của 6 bước bị cắt ("Xem trước dữ li…"). Ở màn hẹp hơn, nhãn bước tự xuống dòng thay vì bị cắt.
- Khung tối thiểu vẫn 1024 px (design D14 của wizard).
- Trang công cụ: h1 là tên công cụ, kèm mô tả một dòng. Nội dung của công cụ nằm bên dưới.
- Heading "Universal Importer" không còn là h1, mà là tên app trong sidebar; h1 của trang là tên công cụ.
- Việc chặn thả file ra ngoài vùng nhận (`usePreventFileDrop`) chuyển lên AppShell, vì nó áp cho cả trang chứ không riêng wizard.

## Risks / Trade-offs

- [Đổi công cụ làm mất state của wizard] → Chưa xảy ra vì mới có một công cụ. Ghi lại để quyết khi thêm công cụ thứ hai (D1).
- ~~[`color-mix()` cần trình duyệt mới]~~ Không còn áp dụng: token của hướng C không dùng `color-mix()`. Vẫn cần trình duyệt có `:has()` (Chrome 105+, Safari 15.4+, Firefox 121+) cho viền focus của vùng thả.
- [Viền dày và bóng cứng dễ rối ở màn nhiều bảng và field (Mapping, Kết quả)] → Ở các bước đó, bảng dùng đường kẻ 1 px bên trong, chỉ khung ngoài mới viền 2 px và có bóng. Quyết định cụ thể khi làm từng bước.
- [Mockup C chỉ vẽ màn chờ của bước Upload] → Các trạng thái khác suy ra theo D4. Nếu cần, bổ sung artboard cho chúng trên canvas.
