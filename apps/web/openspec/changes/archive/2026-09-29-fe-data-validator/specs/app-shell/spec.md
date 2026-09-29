## MODIFIED Requirements

### Requirement: Sidebar liệt kê công cụ
FE SHALL hiển thị sidebar bên trái, gồm tên app ở trên cùng và danh sách công cụ trong một vùng điều hướng tên "Công cụ". Danh sách MUST lấy từ danh sách công cụ đã đăng ký, không ghi cứng trong sidebar. Công cụ đang mở MUST được đánh dấu `aria-current="page"`. Sidebar MUST NOT có mục cho công cụ chưa tồn tại.

#### Scenario: Mở app
- **WHEN** user mở app
- **THEN** sidebar hiển thị "Universal Data Tools", có hai mục "Import dữ liệu" và "Kiểm tra dữ liệu", và "Import dữ liệu" đang được chọn (`aria-current="page"`)

### Requirement: Tên app nằm ở một chỗ
Tên app MUST được lấy từ đúng một nguồn (`messages.appName`) cho cả sidebar lẫn tiêu đề tab trình duyệt (`document.title`), để đổi tên dự án chỉ phải sửa một chỗ. Riêng `<title>` trong `index.html` là giá trị lúc trang chưa chạy JS.

#### Scenario: Tiêu đề tab
- **WHEN** app đã chạy
- **THEN** `document.title` là "Universal Data Tools", trùng tên trên sidebar

## ADDED Requirements

### Requirement: Giữ trạng thái khi chuyển công cụ
Công cụ đã mở ít nhất một lần SHALL giữ nguyên trạng thái (file, cấu hình, kết quả, bước đang đứng) khi user chuyển sang công cụ khác rồi quay lại. Công cụ không đang mở MUST bị ẩn khỏi màn hình và khỏi cây truy cập (`hidden`). Công cụ chưa mở lần nào MUST NOT được mount.

#### Scenario: Quay lại Import
- **WHEN** user đã upload file ở "Import dữ liệu", chuyển sang "Kiểm tra dữ liệu", rồi quay lại
- **THEN** wizard Import vẫn ở bước cũ với file đã upload
