# app-shell Specification

## Purpose
Khung dashboard của FE: sidebar liệt kê các công cụ từ một danh sách đăng ký, và vùng nội dung có tiêu đề trang. Thêm công cụ mới không phải dựng lại bố cục, và tên app nằm ở đúng một chỗ (`messages.appName`). Import dữ liệu là công cụ đầu tiên.
## Requirements
### Requirement: Sidebar liệt kê công cụ
FE SHALL hiển thị sidebar bên trái, gồm tên app ở trên cùng và danh sách công cụ trong một vùng điều hướng tên "Công cụ". Danh sách MUST lấy từ danh sách công cụ đã đăng ký, không ghi cứng trong sidebar. Công cụ đang mở MUST được đánh dấu `aria-current="page"`. Sidebar MUST NOT có mục cho công cụ chưa tồn tại.

#### Scenario: Mở app
- **WHEN** user mở app
- **THEN** sidebar hiển thị "Universal Importer" và mục "Import dữ liệu" đang được chọn (`aria-current="page"`)

### Requirement: Trang của công cụ có tiêu đề
Vùng nội dung SHALL hiển thị tiêu đề trang (h1) là tên công cụ đang mở, kèm mô tả ngắn, rồi tới nội dung của công cụ đó.

#### Scenario: Trang Import dữ liệu
- **WHEN** công cụ "Import dữ liệu" đang mở
- **THEN** h1 của trang là "Import dữ liệu" và bên dưới là wizard import với stepper 6 bước

### Requirement: Tên app nằm ở một chỗ
Tên app MUST được lấy từ đúng một nguồn (`messages.appName`) cho cả sidebar lẫn tiêu đề tab trình duyệt (`document.title`), để đổi tên dự án chỉ phải sửa một chỗ. Riêng `<title>` trong `index.html` là giá trị lúc trang chưa chạy JS.

#### Scenario: Tiêu đề tab
- **WHEN** app đã chạy
- **THEN** `document.title` là "Universal Importer", trùng tên trên sidebar

