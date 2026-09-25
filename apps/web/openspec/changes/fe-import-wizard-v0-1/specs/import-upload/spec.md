## ADDED Requirements

### Requirement: Chọn đúng một file CSV hoặc XLSX
FE SHALL cho user chọn file qua hộp chọn hoặc kéo-thả, và chỉ nhận **đúng một** file có đuôi `.csv` hoặc `.xlsx` (không phân biệt hoa thường). FE MUST kiểm theo đuôi file; MIME type chỉ để tham khảo, không dùng để từ chối. FE MUST từ chối file 0 byte. Khi file bị từ chối, FE MUST NOT gọi API.

#### Scenario: CSV hợp lệ có MIME của Excel trên Windows
- **WHEN** user chọn `khach-hang.csv` mà trình duyệt báo MIME là `application/vnd.ms-excel`
- **THEN** FE nhận file và hiển thị tên file, dung lượng dễ đọc (ví dụ `1,2 MB`) và loại `CSV`

#### Scenario: Đuôi file không được hỗ trợ
- **WHEN** user chọn `data.json`
- **THEN** FE hiển thị lỗi "Chỉ hỗ trợ file .csv hoặc .xlsx" và không gọi API

#### Scenario: Kéo-thả nhiều file
- **WHEN** user thả 2 file vào vùng upload
- **THEN** FE từ chối cả hai, báo "Chỉ chọn một file" và không gọi API

#### Scenario: File rỗng
- **WHEN** user chọn một file `.csv` dung lượng 0 byte
- **THEN** FE báo file rỗng và không gọi API

### Requirement: Gợi ý định dạng file
Ở bước Upload, FE SHALL hiển thị gợi ý:
- CSV phải mã hoá UTF-8 và phân cách bằng dấu phẩy (từ Excel thì lưu dạng "CSV UTF-8");
- với XLSX, hệ thống đọc sheet hiển thị đầu tiên;
- dung lượng tối đa bằng giá trị `VITE_MAX_UPLOAD_MB`.

#### Scenario: Mở bước Upload
- **WHEN** user mở bước Upload với giới hạn 20 MB
- **THEN** FE hiển thị gợi ý về CSV UTF-8 phân cách dấu phẩy, về việc đọc sheet hiển thị đầu tiên của XLSX, và "Tối đa 20 MB"

### Requirement: Kiểm dung lượng trước khi upload
FE MUST so dung lượng file với giới hạn cấu hình `VITE_MAX_UPLOAD_MB` (mặc định 20, khớp giới hạn của BE) trước khi gửi. Đây chỉ là lớp UX; BE vẫn là nơi quyết định cuối cùng.

#### Scenario: File vượt giới hạn
- **WHEN** giới hạn là 20 MB và user chọn file 25 MB
- **THEN** FE báo "File vượt giới hạn 20 MB" và không gọi API

### Requirement: Upload có tiến độ và huỷ được
Trong lúc upload, FE SHALL hiển thị phần trăm đã gửi, khoá thao tác chọn file và cho phép huỷ.

#### Scenario: Đang upload
- **WHEN** file đang được gửi lên BE
- **THEN** FE hiển thị tiến độ theo phần trăm, khoá vùng chọn file và hiện nút "Huỷ"

#### Scenario: Huỷ upload
- **WHEN** user bấm "Huỷ" trong lúc upload
- **THEN** request bị huỷ, không có session nào được lưu, FE quay về trạng thái chọn file và không hiển thị lỗi

### Requirement: Tạo session rồi chuyển sang Preview
Khi BE tạo session thành công, FE SHALL lưu thông tin session (id, tên file gốc, loại file, dung lượng) vào wizard state và tự chuyển sang bước Source Preview.

#### Scenario: Upload thành công
- **WHEN** `POST /api/import-sessions` trả `201` kèm `ImportSessionDto`
- **THEN** wizard state có `session.id` bằng `id` trong response và bước hiện tại là Source Preview

### Requirement: Hiển thị lỗi upload có cấu trúc
BE đọc và kiểm toàn bộ file ngay lúc upload, nên mọi lỗi về file (sai loại, quá lớn, rỗng, không đọc được) đều trả về ở bước này. FE MUST hiển thị lỗi qua `ErrorBanner` theo `code`, kèm `detail` của BE ở dòng phụ khi có. Sau lỗi, user MUST chọn lại được file khác. Upload lỗi không tạo session, nên wizard vẫn ở bước Upload.

#### Scenario: BE từ chối loại file
- **WHEN** BE trả `415` với `code: "FILE_UNSUPPORTED"`
- **THEN** FE hiển thị thông điệp ứng với `FILE_UNSUPPORTED`, kèm mã lỗi, và cho chọn file khác

#### Scenario: File vượt giới hạn của máy chủ
- **WHEN** BE trả `413` với `code: "FILE_TOO_LARGE"`
- **THEN** FE hiển thị "File vượt giới hạn của máy chủ"

#### Scenario: Workbook hoặc sheet trống, file không có header
- **WHEN** BE trả `422` với `code: "FILE_EMPTY"`
- **THEN** FE hiển thị thông điệp ứng với `FILE_EMPTY` và cho chọn file khác

#### Scenario: File không đọc được
- **WHEN** BE trả `422` với `code: "FILE_PARSE_ERROR"` và `detail` có số dòng bị lỗi
- **THEN** FE hiển thị thông điệp ứng với `FILE_PARSE_ERROR` ở dòng chính và `detail` của BE ở dòng phụ, rồi cho chọn file khác

#### Scenario: Mất kết nối trong lúc upload
- **WHEN** request upload thất bại vì lỗi mạng
- **THEN** FE báo "Không kết nối được máy chủ" và hiện nút "Upload lại" để gửi lại đúng file đó

### Requirement: Upload file mới khi đã có session
Khi đã có session, việc upload file khác MUST được user xác nhận. Nếu user xác nhận, FE MUST xoá toàn bộ state của session cũ (preview, schema, mapping, rules, result) trước khi dùng session mới.

#### Scenario: Xác nhận upload file mới
- **WHEN** đã có session và user chọn file mới rồi bấm "Tiếp tục" ở hộp xác nhận
- **THEN** FE xoá state cũ, upload file mới, và wizard chỉ còn dữ liệu của session mới

#### Scenario: Huỷ xác nhận
- **WHEN** user bấm "Huỷ" ở hộp xác nhận
- **THEN** session và toàn bộ cấu hình hiện tại được giữ nguyên, không gọi API
