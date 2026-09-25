## ADDED Requirements

### Requirement: Wizard 6 bước
FE SHALL tổ chức luồng import thành 6 bước theo thứ tự:
1. Upload
2. Source Preview
3. Target Schema
4. Mapping
5. Transform & Validate
6. Result & Export

Stepper SHALL hiển thị trạng thái của từng bước: đã xong, đang ở, hoặc bị khoá.

#### Scenario: Mở ứng dụng lần đầu
- **WHEN** user mở ứng dụng
- **THEN** bước hiện tại là Upload; các bước 2 → 6 bị khoá

### Requirement: Chặn tiến tới bước khi thiếu phụ thuộc
FE MUST chỉ cho vào một bước khi các điều kiện sau thoả:

| Bước | Điều kiện |
|---|---|
| Source Preview | đã có session |
| Target Schema | preview đã tải thành công |
| Mapping | schema đã lưu và có ít nhất một field |
| Transform & Validate | điều kiện của Mapping, và mapping đã lưu |
| Result & Export | điều kiện của Transform & Validate, và đã có kết quả process |

Quay lại bước trước MUST luôn được phép và giữ nguyên dữ liệu đang nhập.

#### Scenario: Bấm vào bước bị khoá
- **WHEN** schema chưa lưu và user bấm "Mapping" trên stepper
- **THEN** wizard không chuyển bước và hiển thị lý do bị khoá

#### Scenario: Quay lại giữ dữ liệu đang nhập
- **WHEN** user đang sửa mapping (chưa lưu), bấm "Quay lại" sang bước Schema, rồi trở lại bước Mapping
- **THEN** các lựa chọn mapping đang sửa vẫn còn nguyên

### Requirement: Đánh dấu cần lưu lại và kết quả đã cũ
FE MUST theo dõi riêng trạng thái đã lưu của schema, mapping, transformations và validations, theo bảng sau:

| Khi user sửa | Chuyển về chưa lưu | Kết quả đã có |
|---|---|---|
| schema | schema, mapping, transformations, validations | bị đánh dấu cũ |
| mapping | mapping | bị đánh dấu cũ |
| transformations | transformations | bị đánh dấu cũ |
| validations | validations | bị đánh dấu cũ |

Sửa schema kéo theo mapping và rules phải lưu lại vì chúng gửi kèm tên và kiểu field.

#### Scenario: Sửa schema sau khi đã có kết quả
- **WHEN** đã có kết quả và user đổi tên một field
- **THEN** schema, mapping, transformations và validations đều ở trạng thái chưa lưu; kết quả bị đánh dấu cũ; các bước Transform & Validate và Result bị khoá cho đến khi mapping được lưu lại

#### Scenario: Sửa mapping sau khi đã có kết quả
- **WHEN** đã có kết quả và user đổi cột nguồn của một field
- **THEN** chỉ mapping chuyển về chưa lưu, và kết quả bị đánh dấu cũ

### Requirement: Khoá điều hướng khi đang gọi API
Trong lúc có request làm thay đổi state (upload, PUT, process, tải kết quả), FE MUST khoá stepper và các nút "Quay lại"/"Tiếp".

#### Scenario: Đang lưu schema
- **WHEN** PUT schema đang chạy
- **THEN** stepper và các nút điều hướng bị khoá cho đến khi request kết thúc

### Requirement: Hiển thị lỗi API nhất quán
Mọi lỗi từ API MUST hiển thị qua cùng một thành phần báo lỗi. **Dòng chính** được chọn theo thứ tự ưu tiên:
1. thông điệp tiếng Việt của FE ứng với `code`;
2. `detail` của BE;
3. `title` của BE;
4. thông điệp chung theo HTTP status.

Khi dòng chính lấy từ bảng của FE và BE có `detail`, thành phần báo lỗi MUST hiện `detail` ở **dòng phụ** (BE viết `detail` bằng tiếng Anh). Thành phần báo lỗi MUST hiển thị kèm `code` khi có. Với lỗi mạng hoặc 5xx của request đọc (GET), FE SHALL có nút "Thử lại".

#### Scenario: Mã đã biết, có detail
- **WHEN** BE trả `code: "FILE_PARSE_ERROR"` kèm `detail: "Malformed quote at line 12"`
- **THEN** dòng chính là thông điệp tiếng Việt của `FILE_PARSE_ERROR`, dòng phụ là "Malformed quote at line 12", kèm mã `FILE_PARSE_ERROR`

#### Scenario: Lỗi không phải JSON
- **WHEN** proxy trả `502` với body HTML
- **THEN** FE hiển thị "Máy chủ đang lỗi (502)", không hiển thị nội dung HTML

#### Scenario: Mã lỗi FE chưa biết
- **WHEN** BE trả `code: "SOMETHING_NEW"` kèm `detail: "Chi tiết"`
- **THEN** FE hiển thị "Chi tiết" kèm mã `SOMETHING_NEW`

### Requirement: Session không dùng được nữa
FE MUST coi session là không dùng được nữa khi response mang một trong hai mã sau, và nhận biết theo `code` chứ không theo HTTP status:
- `code: "SESSION_NOT_FOUND"` (404): BE xoá session không hoạt động sau 24 giờ. FE báo "Không tìm thấy phiên import (có thể đã hết hạn)".
- `code: "SESSION_STATE_INVALID"` (409): session đã hỏng, ví dụ do đọc file lỗi lúc process. FE báo "Phiên import không dùng được nữa".

Cả hai trường hợp đều hiện nút "Upload lại". FE MUST NOT tự xoá state; state chỉ bị reset khi user bấm nút này. Một `404` mang mã khác (ví dụ `REQUEST_INVALID` khi gọi sai endpoint) MUST được hiển thị như lỗi thường.

#### Scenario: Session hết hạn khi đang cấu hình
- **WHEN** PUT mapping trả `404` với `code: "SESSION_NOT_FOUND"`
- **THEN** FE hiển thị thông báo hết hạn và nút "Upload lại"; bấm nút thì wizard reset về bước Upload với state trống

#### Scenario: Session đã hỏng
- **WHEN** PUT validations trả `409` với `code: "SESSION_STATE_INVALID"`
- **THEN** FE hiển thị "Phiên import không dùng được nữa" và nút "Upload lại"; state giữ nguyên cho đến khi user bấm nút

#### Scenario: 404 do sai endpoint
- **WHEN** một request trả `404` với `code: "REQUEST_INVALID"`
- **THEN** FE hiển thị lỗi thường qua `ErrorBanner`, không hiện nút "Upload lại" và không đổi state

### Requirement: Cảnh báo khi rời trang
Khi đã có session, FE SHALL đăng ký cảnh báo `beforeunload`, vì V0.1 không khôi phục được state sau khi tải lại trang. Khi chưa có session, hoặc sau khi reset, FE MUST gỡ cảnh báo này.

#### Scenario: Tải lại trang khi đang cấu hình
- **WHEN** đã có session và user bấm F5
- **THEN** trình duyệt hiện hộp xác nhận rời trang

#### Scenario: Chưa có session
- **WHEN** chưa upload file nào và user bấm F5
- **THEN** trang tải lại ngay, không có hộp xác nhận

### Requirement: Bố cục cho desktop và laptop
FE SHALL dùng được ở độ rộng màn hình từ 1024px trở lên. Bảng rộng MUST cuộn ngang bên trong khung bảng; toàn trang MUST NOT cuộn ngang.

#### Scenario: File có nhiều cột
- **WHEN** preview có 40 cột trên màn hình rộng 1280px
- **THEN** chỉ khung bảng cuộn ngang, header và các nút điều hướng vẫn nằm trong khung nhìn
