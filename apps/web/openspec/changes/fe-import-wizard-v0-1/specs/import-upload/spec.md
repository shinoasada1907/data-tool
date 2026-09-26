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

### Requirement: Thả file ra ngoài vùng upload không làm rời khỏi app
Trình duyệt mặc định mở hoặc tải file khi user thả file ra ngoài vùng nhận, tức là rời khỏi app. FE MUST chặn hành vi mặc định này ở mọi chỗ trong trang, và báo cho trình duyệt rằng chỗ đó không nhận file.

#### Scenario: Thả file lệch ra ngoài khung
- **WHEN** user thả một file xuống phần header của trang
- **THEN** trình duyệt không mở file, và app giữ nguyên trạng thái

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
Trong lúc upload, FE SHALL:
- hiển thị phần trăm đã gửi;
- khoá thao tác chọn file;
- cho phép huỷ, và đưa focus tới nút "Huỷ" vì ô chọn file vừa bị khoá.

Screen reader MUST được báo lúc bắt đầu upload và lúc đã gửi xong, nhưng MUST NOT bị đọc lại theo từng phần trăm. Mỗi lúc chỉ có một upload chạy.

#### Scenario: Đang upload
- **WHEN** file đang được gửi lên BE
- **THEN** FE hiển thị tiến độ theo phần trăm, khoá vùng chọn file và hiện nút "Huỷ" đang giữ focus

#### Scenario: Đã gửi xong nhưng BE chưa trả lời
- **WHEN** đã gửi 100% file mà BE vẫn đang đọc file
- **THEN** FE hiển thị "Đang đọc file trên máy chủ…" thay cho "Đang upload… 100%"

#### Scenario: Huỷ upload
- **WHEN** user bấm "Huỷ" trong lúc upload
- **THEN** request bị huỷ, không có session nào được lưu, FE quay về trạng thái chọn file, không hiển thị lỗi, và focus về ô chọn file

#### Scenario: Thả thêm file trong lúc đang upload
- **WHEN** đang upload `khach-hang.csv` và user thả thêm một file (kể cả file sai đuôi) vào vùng upload
- **THEN** FE bỏ qua file đó, không tạo request thứ hai, và vẫn hiển thị tiến độ của upload đang chạy

### Requirement: Tạo session rồi chuyển sang Preview
Khi BE tạo session thành công, FE SHALL lưu thông tin session (id, tên file gốc, loại file, dung lượng) vào wizard state và tự chuyển sang bước Source Preview. Response 2xx mà thiếu những field này MUST được coi là lỗi, với mã `INVALID_RESPONSE` do FE sinh ra, chứ không được chạy tiếp với dữ liệu thiếu.

#### Scenario: Upload thành công
- **WHEN** `POST /api/import-sessions` trả `201` kèm `ImportSessionDto`
- **THEN** wizard state có `session.id` bằng `id` trong response và bước hiện tại là Source Preview

#### Scenario: BE trả 2xx nhưng body sai dạng
- **WHEN** `POST /api/import-sessions` trả `201` mà body không có `id`
- **THEN** FE báo "Máy chủ trả về dữ liệu không đúng định dạng" và ở lại bước Upload

### Requirement: Hiển thị lỗi upload có cấu trúc
BE đọc và kiểm toàn bộ file ngay lúc upload, nên mọi lỗi về file (sai loại, quá lớn, rỗng, không đọc được) đều trả về ở bước này. FE MUST hiển thị lỗi qua `ErrorBanner` theo `code`, kèm `detail` của BE ở dòng phụ khi có. Sau lỗi, focus MUST về ô chọn file, và user MUST chọn lại được file khác. Upload lỗi không tạo session, nên wizard vẫn ở bước Upload.

Nút "Upload lại" (gửi lại đúng file đó) chỉ hiện khi lỗi mạng hoặc lỗi 5xx. Với lỗi 4xx, gửi lại đúng file đó vẫn lỗi y hệt, nên không có nút này.

#### Scenario: BE từ chối loại file
- **WHEN** BE trả `415` với `code: "FILE_UNSUPPORTED"`
- **THEN** FE hiển thị thông điệp ứng với `FILE_UNSUPPORTED` kèm mã lỗi, không có nút "Upload lại", và cho chọn file khác

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

#### Scenario: Máy chủ lỗi 5xx
- **WHEN** BE trả `500 INTERNAL_ERROR`, hoặc proxy trả `502` vì BE đang khởi động lại
- **THEN** FE hiển thị lỗi và nút "Upload lại" để gửi lại đúng file đó

### Requirement: Upload file mới khi đã có session
Khi đã có session, việc upload file khác MUST được user xác nhận. Câu hỏi xác nhận MUST nêu tên file sắp upload, và focus MUST chuyển tới nút "Tiếp tục".

State của session cũ (preview, schema, mapping, rules, result) MUST chỉ bị thay khi session mới đã được tạo. Nếu upload thay thế lỗi hoặc bị huỷ, session và toàn bộ cấu hình hiện tại MUST được giữ nguyên.

#### Scenario: Xác nhận upload file mới
- **WHEN** đã có session và user chọn `don-hang.csv`
- **THEN** FE hỏi "Upload “don-hang.csv” sẽ xoá toàn bộ cấu hình của phiên hiện tại." và focus vào nút "Tiếp tục"

#### Scenario: Upload thay thế thành công
- **WHEN** user bấm "Tiếp tục" và BE tạo session mới
- **THEN** wizard chỉ còn dữ liệu của session mới

#### Scenario: Upload thay thế bị từ chối
- **WHEN** user bấm "Tiếp tục" và BE trả `415 FILE_UNSUPPORTED`
- **THEN** FE báo lỗi; session cũ vẫn còn, và bước Source Preview vẫn vào được

#### Scenario: Huỷ upload thay thế
- **WHEN** user bấm "Tiếp tục" rồi bấm "Huỷ" trong lúc upload
- **THEN** session cũ và toàn bộ cấu hình vẫn còn nguyên

#### Scenario: Huỷ xác nhận
- **WHEN** user bấm "Huỷ" ở hộp xác nhận
- **THEN** session và toàn bộ cấu hình hiện tại được giữ nguyên, không gọi API, và focus về ô chọn file
