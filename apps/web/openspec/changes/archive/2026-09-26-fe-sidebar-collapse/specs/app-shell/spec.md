## ADDED Requirements

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
