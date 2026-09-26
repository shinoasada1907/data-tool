# import-session Specification

## Purpose
Quản lý vòng đời của một phiên import: upload và đọc file, trạng thái `UPLOADED` → `CONFIGURING`/`READY` → `PROCESSED` hoặc `FAILED`, và tự dọn session hết hạn cùng thư mục lưu trữ mồ côi.
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

### Requirement: Dọn session hết hạn theo TTL
Hệ thống SHALL tự động dọn dẹp: một lần ngay khi ứng dụng khởi động, sau đó mỗi giờ. Mỗi lần chạy SHALL xoá mọi session có `updatedAt` cũ hơn TTL; TTL cấu hình qua `IMPORTER_SESSION_TTL`, mặc định `24h`. Với mỗi session:
1. xoá session, cùng config của nó, khỏi database. Chỉ xoá nếu `updatedAt` vẫn cũ hơn mốc cắt; việc kiểm và việc xoá là một lệnh duy nhất trên database;
2. rồi xoá thư mục storage `{storageRoot}/{sessionId}`.

Quy tắc khi gặp sự cố:
- Lỗi khi xoá một session MUST NOT làm dừng việc xoá các session khác.
- Xoá trong database thất bại: session SHALL được giữ nguyên vẹn, cả row lẫn file, để lần chạy sau thử lại.
- Xoá storage thất bại sau khi row đã mất: thư mục SHALL trở thành mồ côi và được dọn ở lần chạy sau. Hệ thống MUST NOT để lộ ra một session còn row mà mất file.
- Session đang có lệnh ghi (đang giữ khoá theo session) SHALL được bỏ qua ở lần chạy đó. Session vừa có lệnh ghi sau lúc được liệt kê MUST NOT bị xoá.

`IMPORTER_SESSION_TTL` là số trần thì được hiểu là giờ. TTL dưới 1 phút SHALL làm ứng dụng không khởi động được.

Chỉ lệnh ghi mới làm mới `updatedAt`; lệnh đọc (GET) MUST NOT kéo dài thời hạn của session.

#### Scenario: Session quá hạn bị xoá, session còn hạn được giữ
- **WHEN** TTL là `24h`, session A có `updatedAt` cách đây 25 giờ, session B cách đây 23 giờ, và cleanup chạy
- **THEN** A không còn trong database và thư mục `{storageRoot}/{A}` không còn tồn tại
- **AND** B vẫn còn nguyên, cả trong database lẫn trong storage

#### Scenario: Session đã bị dọn trả 404
- **WHEN** session A đã bị cleanup xoá, và client gọi `GET /api/import-sessions/{A}`
- **THEN** hệ thống trả `404` với `code` là `SESSION_NOT_FOUND`

#### Scenario: Lỗi ở một session không chặn session khác
- **WHEN** A và C đều quá hạn, và việc xoá A trong database ném lỗi
- **THEN** C bị xoá hoàn toàn
- **AND** A vẫn còn nguyên (row và file), và report của lần chạy ghi `failures` là `1`, `deletedSessions` là `1`

#### Scenario: File không xoá được thành mồ côi
- **WHEN** A quá hạn, và việc xoá thư mục storage của A ném lỗi
- **THEN** A không còn trong database, thư mục của A còn lại
- **AND** ở lần chạy sau, thư mục đó được xoá như một mồ côi

#### Scenario: TTL không đơn vị
- **WHEN** `IMPORTER_SESSION_TTL` là `24`
- **THEN** TTL là 24 giờ

#### Scenario: Session đang bận được bỏ qua
- **WHEN** A quá hạn nhưng đang có một lệnh ghi giữ khoá của A
- **THEN** A không bị xoá ở lần chạy này, và report ghi `skipped` là `1`

#### Scenario: Chạy ngay khi khởi động
- **WHEN** ứng dụng khởi động xong
- **THEN** lần dọn đầu tiên chạy trong vòng 5 giây, không phải chờ đủ một giờ

### Requirement: Dọn thư mục lưu trữ mồ côi
Mỗi lần dọn dẹp, hệ thống SHALL xoá những thư mục con của storage root thoả đồng thời:
- có tên là một UUID ở dạng chuẩn;
- là thư mục thật, không phải link hay junction;
- không có session tương ứng trong database;
- có thời điểm sửa cuối cũ hơn TTL, và cũ hơn ít nhất 1 giờ.

Thư mục hay file có tên không phải UUID MUST NOT bị xoá. Thư mục mồ côi còn mới MUST NOT bị xoá, vì có thể là upload đang dở. Việc xoá MUST NOT đi xuyên link hay junction ra ngoài thư mục đang xoá.

Storage root thuộc về một database duy nhất:
- Lần dọn đầu tiên SHALL ghi id của database (bảng `installation`) vào `{storageRoot}/.owner`, nếu storage còn trống hoặc có chứa ít nhất một session mà database biết.
- Storage có `.owner` của database khác, hoặc chưa có `.owner` mà chỉ chứa session lạ, SHALL không bị dọn mồ côi. Hệ thống SHALL ghi log lỗi.
- Một lượt thấy hơn 10 mồ côi, và số mồ côi nhiều hơn một nửa số thư mục session, SHALL không xoá mồ côi nào và ghi log lỗi.

#### Scenario: Mồ côi quá hạn bị xoá
- **WHEN** storage root có thư mục `{X}` (X là UUID), database không có session X, và thư mục được sửa lần cuối cách đây 25 giờ
- **THEN** cleanup xoá thư mục `{X}`, và report ghi `deletedOrphans` là `1`

#### Scenario: Mồ côi còn mới được giữ
- **WHEN** storage root có thư mục `{Y}` (Y là UUID), database không có session Y, và thư mục được sửa lần cuối cách đây 1 giờ
- **THEN** thư mục `{Y}` vẫn còn sau cleanup

#### Scenario: Storage của database khác
- **WHEN** `{storageRoot}/.owner` ghi id của một database khác, và storage có thư mục `{X}` cũ 3 ngày mà database hiện tại không biết
- **THEN** thư mục `{X}` vẫn còn sau cleanup

#### Scenario: Junction không bị đi xuyên qua
- **WHEN** bên trong thư mục của một session có một junction trỏ ra một thư mục ngoài storage, và session đó bị xoá
- **THEN** junction bị gỡ, còn thư mục bên ngoài và file trong đó vẫn còn nguyên

#### Scenario: Thư mục không phải UUID không bị đụng tới
- **WHEN** storage root có thư mục `backup` được sửa lần cuối cách đây 30 ngày
- **THEN** thư mục `backup` vẫn còn sau cleanup

