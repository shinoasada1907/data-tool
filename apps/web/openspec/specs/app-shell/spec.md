# app-shell Specification

## Purpose
Khung dashboard của FE: sidebar liệt kê các công cụ từ một danh sách đăng ký, và vùng nội dung có tiêu đề trang. Thêm công cụ mới không phải dựng lại bố cục, và tên app nằm ở đúng một chỗ (`messages.appName`). Import dữ liệu là công cụ đầu tiên.
## Requirements
### Requirement: Sidebar liệt kê công cụ
FE SHALL hiển thị sidebar bên trái, gồm tên app ở trên cùng và danh sách công cụ trong một vùng điều hướng tên "Công cụ". Danh sách MUST lấy từ danh sách công cụ đã đăng ký, không ghi cứng trong sidebar. Công cụ đang mở MUST được đánh dấu `aria-current="page"`. Sidebar MUST NOT có mục cho công cụ chưa tồn tại.

#### Scenario: Mở app
- **WHEN** user mở app
- **THEN** sidebar hiển thị "Universal Data Tools", có hai mục "Import dữ liệu" và "Kiểm tra dữ liệu", và "Import dữ liệu" đang được chọn (`aria-current="page"`)

### Requirement: Trang của công cụ có tiêu đề
Vùng nội dung SHALL hiển thị tiêu đề trang (h1) là tên công cụ đang mở, kèm mô tả ngắn, rồi tới nội dung của công cụ đó.

#### Scenario: Trang Import dữ liệu
- **WHEN** công cụ "Import dữ liệu" đang mở
- **THEN** h1 của trang là "Import dữ liệu" và bên dưới là wizard import với stepper 6 bước

### Requirement: Tên app nằm ở một chỗ
Tên app MUST được lấy từ đúng một nguồn (`messages.appName`) cho cả sidebar lẫn tiêu đề tab trình duyệt (`document.title`), để đổi tên dự án chỉ phải sửa một chỗ. Riêng `<title>` trong `index.html` là giá trị lúc trang chưa chạy JS.

#### Scenario: Tiêu đề tab
- **WHEN** app đã chạy
- **THEN** `document.title` là "Universal Data Tools", trùng tên trên sidebar

### Requirement: Thu gọn và mở rộng sidebar
Sidebar SHALL có một nút bật/tắt để chuyển giữa hai trạng thái: mở (như mặc định) và thu gọn thành một dải hẹp. Nút MUST có tên đọc được, là "Thu gọn thanh bên" khi sidebar đang mở và "Mở rộng thanh bên" khi đang thu gọn, và MUST báo trạng thái qua `aria-expanded`.

Khi thu gọn, sidebar SHALL vẫn liệt kê mọi công cụ đã đăng ký, mỗi công cụ là một nút chỉ có icon. Tên đọc được của nút MUST vẫn là tên công cụ, và công cụ đang mở MUST vẫn có `aria-current="page"`. Chọn công cụ khi đang thu gọn MUST mở trang của công cụ đó như khi sidebar mở.

Trạng thái thu gọn SHALL được nhớ qua lần tải lại trang. Nếu trình duyệt không cho lưu hoặc đọc, sidebar MUST mặc định là mở và app vẫn chạy bình thường.

#### Scenario: Mặc định là mở
- **WHEN** user mở app lần đầu
- **THEN** sidebar ở trạng thái mở, và nút bật/tắt có tên "Thu gọn thanh bên" với `aria-expanded="true"`

#### Scenario: Thu gọn sidebar
- **WHEN** user bấm "Thu gọn thanh bên"
- **THEN** nút đổi tên thành "Mở rộng thanh bên" với `aria-expanded="false"`
- **AND** nút công cụ "Import dữ liệu" vẫn có tên đó và vẫn có `aria-current="page"`

#### Scenario: Chuyển công cụ khi đang thu gọn
- **WHEN** sidebar đang thu gọn và user bấm icon của một công cụ khác
- **THEN** trang của công cụ đó được mở

#### Scenario: Nhớ trạng thái
- **WHEN** user thu gọn sidebar rồi tải lại trang
- **THEN** sidebar vẫn ở trạng thái thu gọn

#### Scenario: Không lưu được trạng thái
- **WHEN** trình duyệt chặn `localStorage`
- **THEN** sidebar mặc định là mở và nút bật/tắt vẫn dùng được

### Requirement: Giữ trạng thái khi chuyển công cụ
Công cụ đã mở ít nhất một lần SHALL giữ nguyên trạng thái (file, cấu hình, kết quả, bước đang đứng) khi user chuyển sang công cụ khác rồi quay lại. Công cụ không đang mở MUST bị ẩn khỏi màn hình và khỏi cây truy cập (`hidden`). Công cụ chưa mở lần nào MUST NOT được mount.

#### Scenario: Quay lại Import
- **WHEN** user đã upload file ở "Import dữ liệu", chuyển sang "Kiểm tra dữ liệu", rồi quay lại
- **THEN** wizard Import vẫn ở bước cũ với file đã upload

