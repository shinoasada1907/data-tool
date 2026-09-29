## ADDED Requirements

### Requirement: Upload file nguồn của công cụ
`DatasetSource` SHALL cho chọn hoặc kéo-thả **một** file `.csv`, `.xlsx` hoặc `.json`. FE MUST kiểm đuôi file, file rỗng và giới hạn dung lượng (`VITE_MAX_UPLOAD_MB`) trước khi gửi. File qua được kiểm tra thì upload lên `POST /api/datasets`: có thanh tiến độ, và có nút huỷ. Upload lỗi thì MUST hiện thông điệp tiếng Việt theo `code`. Bên dưới ô chọn file MUST ghi rõ "File và kết quả tự xoá sau 24 giờ không dùng tới."

#### Scenario: Upload thành công
- **WHEN** user chọn `khach.csv`
- **THEN** FE gửi file lên `POST /api/datasets`, rồi hiện tên file, định dạng và dung lượng

#### Scenario: Sai đuôi file
- **WHEN** user chọn `anh.png`
- **THEN** FE báo chỉ nhận CSV, XLSX hoặc JSON, và không gửi request nào

### Requirement: Tuỳ chọn đọc theo định dạng
Sau khi upload, FE SHALL gọi `GET /api/datasets/{id}/preview` và hiện tuỳ chọn đọc theo định dạng:
- CSV: dấu phân cách, bảng mã, dòng đầu là tiêu đề;
- XLSX: sheet (sheet ẩn có ghi "(ẩn)"), dòng đầu là tiêu đề;
- JSON: không có tuỳ chọn.

Tuỳ chọn chưa chọn MUST hiện là "Tự nhận" kèm giá trị BE thực dùng, lấy từ `options` của preview. Đổi một tuỳ chọn MUST gọi lại preview với tuỳ chọn mới.

#### Scenario: Dấu phân cách tự nhận
- **WHEN** preview trả `options.delimiter = SEMICOLON` và `autoDetected` có `delimiter`
- **THEN** ô "Dấu phân cách" hiện "Tự nhận (dấu chấm phẩy)"

#### Scenario: Đổi bảng mã
- **WHEN** user chọn bảng mã "Windows-1258"
- **THEN** FE gọi lại preview với `encoding=WINDOWS-1258` và thay bảng xem trước

### Requirement: Xem trước dữ liệu
FE SHALL hiện bảng xem trước gồm số dòng và các cột theo đúng thứ tự BE trả, kèm dòng tóm tắt số dòng, số cột và số dòng trống đã bỏ. Ô `null` MUST hiện là ô trống, không hiện chữ "null". Preview lỗi (`FILE_PARSE_ERROR`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `CONFIG_INVALID`…) MUST hiện thông điệp tiếng Việt kèm `detail` của BE, và vẫn cho đổi tuỳ chọn hoặc đổi file.

#### Scenario: File không phải UTF-8
- **WHEN** preview trả `422 FILE_PARSE_ERROR` với detail "File is not valid UTF-8…"
- **THEN** FE hiện lỗi đọc file kèm detail đó, và ô "Bảng mã" vẫn chọn được

### Requirement: Đổi hoặc xoá file
FE SHALL cho đổi file khác và xoá file hiện tại. Cả hai trường hợp MUST gọi `DELETE /api/datasets/{id}` cho dataset cũ mà không chặn giao diện; lỗi của lệnh xoá được bỏ qua. Dataset đã hết hạn (`404 DATASET_NOT_FOUND`) MUST được báo là "File đã hết hạn trên máy chủ", và user phải chọn lại file.

#### Scenario: Xoá file
- **WHEN** user bấm "Xoá file"
- **THEN** FE gửi `DELETE /api/datasets/{id}` và quay về ô chọn file
