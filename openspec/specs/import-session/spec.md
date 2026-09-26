# import-session Specification

## Purpose
TBD - created by archiving change be-f01-import-session. Update Purpose after archive.
## Requirements
### Requirement: Upload file tạo import session
Hệ thống SHALL nhận file qua `POST /api/import-sessions` (multipart, part tên `file`). Khi file được chấp nhận, hệ thống SHALL lưu file, tạo một import session, và trả `201 Created`. Response gồm header `Location: /api/import-sessions/{id}` và body `ImportSessionDto { id, status, originalFileName, fileType, sizeBytes, createdAt, updatedAt }`. Lúc tạo, `status` là trạng thái ban đầu `UPLOADED`.

#### Scenario: Upload CSV hợp lệ
- **WHEN** client gửi file `customers.csv` (UTF-8, có header và dữ liệu, 120 byte)
- **THEN** hệ thống trả `201`, header `Location` trỏ tới `/api/import-sessions/{id}`
- **AND** body có `id` là UUID, `fileType` là `CSV`, `originalFileName` là `customers.csv`, `sizeBytes` là `120`

#### Scenario: Upload XLSX hợp lệ
- **WHEN** client gửi file `customers.xlsx`, nội dung bắt đầu bằng chữ ký ZIP `PK\x03\x04`
- **THEN** hệ thống trả `201` và body có `fileType` là `XLSX`

#### Scenario: Thiếu part `file`
- **WHEN** client gửi multipart không có part tên `file`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`, và không tạo session nào

### Requirement: Chỉ nhận file CSV và XLSX
Hệ thống SHALL chấp nhận một file khi và chỉ khi file thoả cả hai điều kiện:
- Đuôi file là `.csv` hoặc `.xlsx`, không phân biệt hoa thường.
- Nội dung khớp loại file: với XLSX, 4 byte đầu là `PK\x03\x04`; với CSV, 8 KB đầu không có byte `0x00`.

Hệ thống MUST NOT dựa vào MIME type client gửi để quyết định. File không đạt SHALL bị từ chối với `415` và `code` là `FILE_UNSUPPORTED`, và không tạo session nào.

#### Scenario: File `.xls` bị từ chối
- **WHEN** client gửi file `data.xls`
- **THEN** hệ thống trả `415` với `code` là `FILE_UNSUPPORTED`

#### Scenario: Đuôi `.xlsx` nhưng nội dung không phải ZIP
- **WHEN** client gửi file `fake.xlsx` có nội dung là văn bản `a,b,c`
- **THEN** hệ thống trả `415` với `code` là `FILE_UNSUPPORTED`

#### Scenario: Đuôi `.csv` nhưng nội dung là nhị phân
- **WHEN** client gửi file `data.csv` có byte `0x00` trong 8 KB đầu
- **THEN** hệ thống trả `415` với `code` là `FILE_UNSUPPORTED`

#### Scenario: CSV gửi với MIME của Excel vẫn được nhận
- **WHEN** client gửi file `data.csv` hợp lệ với `Content-Type: application/vnd.ms-excel`
- **THEN** hệ thống trả `201` và `fileType` là `CSV`

#### Scenario: Đuôi viết hoa vẫn được nhận
- **WHEN** client gửi file `DATA.CSV` hợp lệ
- **THEN** hệ thống trả `201` và `fileType` là `CSV`

#### Scenario: File không có đuôi
- **WHEN** client gửi file tên `data`
- **THEN** hệ thống trả `415` với `code` là `FILE_UNSUPPORTED`

### Requirement: Giới hạn dung lượng file upload
Giới hạn upload SHALL cấu hình được qua biến môi trường `IMPORTER_MAX_FILE_SIZE`, mặc định `20MB`.
- File vượt giới hạn SHALL bị từ chối với `413` và `code` là `FILE_TOO_LARGE`. Hệ thống MUST gửi được response này về client, không được đóng kết nối giữa chừng.
- File 0 byte SHALL bị từ chối với `422` và `code` là `FILE_EMPTY`.

#### Scenario: File vượt giới hạn
- **WHEN** giới hạn là 1KB và client gửi file CSV 5KB
- **THEN** client nhận được response `413` dạng `application/problem+json`, với `code` là `FILE_TOO_LARGE`

#### Scenario: File rỗng
- **WHEN** client gửi file `empty.csv` dài 0 byte
- **THEN** hệ thống trả `422` với `code` là `FILE_EMPTY`, và không tạo session nào

### Requirement: Lưu file bằng tên do server sinh
Hệ thống SHALL lưu file tại vị trí chỉ phụ thuộc vào `sessionId` do server sinh, là `{storageRoot}/{sessionId}/source.bin`. Hệ thống MUST NOT dùng tên file client gửi để tạo đường dẫn.

Tên file gốc SHALL chỉ được lưu làm metadata, sau khi làm sạch:
- chỉ giữ phần sau dấu `/` hoặc `\` cuối cùng;
- chuẩn hoá Unicode NFC;
- bỏ ký tự điều khiển và trim;
- tối đa 255 ký tự, giữ nguyên đuôi file.

Nếu không tạo được session sau khi đã lưu file, hệ thống SHALL xoá file vừa lưu.

#### Scenario: Tên file chứa đường dẫn
- **WHEN** client gửi file hợp lệ với tên `../../etc/passwd.csv`
- **THEN** file được lưu tại `{storageRoot}/{sessionId}/source.bin`
- **AND** `originalFileName` của session là `passwd.csv`

#### Scenario: Tên file từ Windows kèm đường dẫn
- **WHEN** client gửi file hợp lệ với tên `C:\Users\an\Desktop\khách hàng.csv`
- **THEN** `originalFileName` của session là `khách hàng.csv`

#### Scenario: Lưu session thất bại thì dọn file
- **WHEN** file đã được lưu nhưng việc ghi session vào database thất bại
- **THEN** file vừa lưu bị xoá khỏi storage, và client nhận `500` với `code` là `INTERNAL_ERROR`

### Requirement: Vòng đời trạng thái session
Session SHALL chỉ chuyển trạng thái theo bảng sau:

| Từ | Sang |
|---|---|
| `UPLOADED` | `CONFIGURING` |
| `CONFIGURING` | `CONFIGURING`, `READY` |
| `READY` | `CONFIGURING`, `READY`, `PROCESSED`, `FAILED` |
| `PROCESSED` | `CONFIGURING`, `READY`, `PROCESSED`, `FAILED` |
| `FAILED` | (không có) |

Chuyển trạng thái nằm ngoài bảng SHALL bị từ chối với mã `SESSION_STATE_INVALID`, và trạng thái giữ nguyên. Mỗi lần chuyển trạng thái thành công SHALL cập nhật `updatedAt`.

#### Scenario: Session mới ở trạng thái UPLOADED
- **WHEN** một session được tạo từ file vừa lưu
- **THEN** `status` là `UPLOADED` và `updatedAt` bằng `createdAt`

#### Scenario: Chuyển trạng thái hợp lệ
- **WHEN** session đang `READY` và chuyển sang `PROCESSED`
- **THEN** `status` là `PROCESSED` và `updatedAt` được cập nhật

#### Scenario: FAILED là trạng thái cuối
- **WHEN** session đang `FAILED` và có yêu cầu chuyển sang `CONFIGURING`
- **THEN** yêu cầu bị từ chối với mã `SESSION_STATE_INVALID` và `status` vẫn là `FAILED`

#### Scenario: Không nhảy cóc trạng thái
- **WHEN** session đang `UPLOADED` và có yêu cầu chuyển sang `PROCESSED`
- **THEN** yêu cầu bị từ chối với mã `SESSION_STATE_INVALID`

### Requirement: Đọc thông tin session
Hệ thống SHALL trả `ImportSessionDto` của session qua `GET /api/import-sessions/{id}`.

#### Scenario: Session tồn tại
- **WHEN** client gọi `GET /api/import-sessions/{id}` với id của một session đã tạo
- **THEN** hệ thống trả `200`, với `id`, `status`, `originalFileName`, `fileType`, `sizeBytes`, `createdAt`, `updatedAt` đúng như lúc tạo

#### Scenario: Session không tồn tại
- **WHEN** client gọi `GET /api/import-sessions/{id}` với một UUID chưa từng được tạo
- **THEN** hệ thống trả `404` với `code` là `SESSION_NOT_FOUND`

#### Scenario: Id không phải UUID
- **WHEN** client gọi `GET /api/import-sessions/abc`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

### Requirement: Session trả kèm cấu hình và readiness
Mọi response trả `ImportSessionDto` (upload `201`, `GET /api/import-sessions/{id}`, và `session` trong response của PUT cấu hình) SHALL có thêm:
- `config`: chứa ít nhất `schema.fields`;
- `readiness`: `{ ready, issues[{field, code, message}] }`, trong đó `ready` là `true` khi và chỉ khi `issues` rỗng.

#### Scenario: Session vừa upload
- **WHEN** client upload một CSV hợp lệ
- **THEN** response có `config.schema.fields` là mảng rỗng
- **AND** `readiness` là `{ready false, issues [{field null, code "SCHEMA_EMPTY", message "Target schema has no fields."}]}`

#### Scenario: GET trả cấu hình đã lưu
- **WHEN** client đã PUT schema gồm field `email`, rồi gọi `GET /api/import-sessions/{id}`
- **THEN** response có `config.schema.fields[0].name` là `email`

### Requirement: Readiness khi schema chưa có field
Khi schema của session chưa có field nào, readiness SHALL có issue `{field null, code "SCHEMA_EMPTY", message "Target schema has no fields."}`, và session MUST NOT ở trạng thái `READY`.

#### Scenario: Có schema thì hết issue SCHEMA_EMPTY
- **WHEN** session đang `CONFIGURING` và client PUT schema gồm một field optional `note`
- **THEN** `readiness.issues` không còn `SCHEMA_EMPTY`

### Requirement: Cập nhật cấu hình theo trạng thái session
Mọi PUT cấu hình (`/schema`, `/mapping`, `/transformations`, `/validations`) SHALL tuân theo các luật sau:
- Session không tồn tại → `404` với `code` là `SESSION_NOT_FOUND`.
- Session ở `UPLOADED` hoặc `FAILED` → `409` với `code` là `SESSION_STATE_INVALID`, và không lưu gì.
- Session ở `CONFIGURING`, `READY` hoặc `PROCESSED` → lưu cấu hình, rồi chuyển session sang `READY` nếu `readiness.ready`, ngược lại sang `CONFIGURING`.
- Ngoại lệ: session đang `PROCESSED` mà cấu hình sau khi PUT giống hệt trước khi PUT (cùng `configHash`) thì SHALL giữ nguyên `PROCESSED`.

#### Scenario: Session không tồn tại
- **WHEN** client PUT schema cho một UUID chưa từng được tạo
- **THEN** hệ thống trả `404` với `code` là `SESSION_NOT_FOUND`

#### Scenario: Session FAILED không nhận cấu hình
- **WHEN** session đang `FAILED` và client PUT schema hợp lệ
- **THEN** hệ thống trả `409` với `code` là `SESSION_STATE_INVALID`, và cấu hình không đổi

#### Scenario: Session chưa đọc file
- **WHEN** session đang `UPLOADED` và client PUT schema hợp lệ
- **THEN** hệ thống trả `409` với `code` là `SESSION_STATE_INVALID`

#### Scenario: Chuyển sang READY
- **WHEN** session đang `CONFIGURING` và client PUT một schema hợp lệ, khiến readiness không còn issue nào
- **THEN** `session.status` trong response là `READY`

#### Scenario: PUT giống hệt khi đã PROCESSED
- **WHEN** session đang `PROCESSED` và client PUT lại đúng schema đang có
- **THEN** `session.status` vẫn là `PROCESSED`

### Requirement: Các lệnh ghi trên cùng session chạy tuần tự
Các lệnh ghi (PUT cấu hình và process) trên cùng một session SHALL chạy lần lượt: lệnh sau chỉ bắt đầu khi lệnh trước đã commit xong. Lệnh ghi trên các session khác nhau MAY chạy song song.

#### Scenario: Hai PUT schema đồng thời
- **WHEN** hai request PUT schema cho cùng một session được gửi cùng lúc, một request với field `a` và một request với field `b`
- **THEN** cả hai đều trả `200`, không có request nào trả `500`
- **AND** `GET /api/import-sessions/{id}` sau đó trả `config.schema` khớp với `session.config.schema` trong response của request hoàn tất sau

### Requirement: Readiness khi field required chưa được map
Với mỗi field `required` trong schema chưa có mapping, readiness SHALL có một issue `{field, code "TARGET_FIELD_REQUIRED", message "Required field is not mapped."}`, theo thứ tự schema. Khi còn issue này, session MUST NOT ở trạng thái `READY`. Nếu gọi process khi đó, hệ thống SHALL trả `409` với `code` là `SESSION_NOT_READY`, và `errors` là danh sách issue.

#### Scenario: Field required chưa map
- **WHEN** schema có `email` (required) và `note` (optional), và client PUT mapping chỉ cho `note`
- **THEN** `session.status` là `CONFIGURING`
- **AND** `session.readiness` là `{ready false, issues [{field "email", code "TARGET_FIELD_REQUIRED", message "Required field is not mapped."}]}`

#### Scenario: Map đủ field required
- **WHEN** tiếp theo client PUT mapping cho cả `email` và `note`
- **THEN** `session.status` là `READY` và `session.readiness.issues` rỗng

#### Scenario: Thêm field required vào schema đã READY
- **WHEN** session đang `READY`, và client PUT schema thêm field required `phone` chưa được map
- **THEN** `session.status` là `CONFIGURING`, và `readiness.issues` chứa `{field "phone", code "TARGET_FIELD_REQUIRED", …}`

