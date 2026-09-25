## ADDED Requirements

### Requirement: Dọn session hết hạn theo TTL
Hệ thống SHALL tự động dọn dẹp: một lần ngay khi ứng dụng khởi động, sau đó mỗi giờ. Mỗi lần chạy SHALL xoá mọi session có `updatedAt` cũ hơn TTL; TTL cấu hình qua `IMPORTER_SESSION_TTL`, mặc định `24h`. Với mỗi session:
1. xoá thư mục storage `{storageRoot}/{sessionId}` trước;
2. rồi xoá session, cùng config của nó, khỏi database.

Quy tắc khi gặp sự cố:
- Lỗi khi xoá một session MUST NOT làm dừng việc xoá các session khác.
- Session xoá storage thất bại SHALL được giữ lại trong database để lần chạy sau thử lại.
- Session đang có lệnh ghi (đang giữ khoá theo session) SHALL được bỏ qua ở lần chạy đó.

Chỉ lệnh ghi mới làm mới `updatedAt`; lệnh đọc (GET) MUST NOT kéo dài thời hạn của session.

#### Scenario: Session quá hạn bị xoá, session còn hạn được giữ
- **WHEN** TTL là `24h`, session A có `updatedAt` cách đây 25 giờ, session B cách đây 23 giờ, và cleanup chạy
- **THEN** A không còn trong database và thư mục `{storageRoot}/{A}` không còn tồn tại
- **AND** B vẫn còn nguyên, cả trong database lẫn trong storage

#### Scenario: Session đã bị dọn trả 404
- **WHEN** session A đã bị cleanup xoá, và client gọi `GET /api/import-sessions/{A}`
- **THEN** hệ thống trả `404` với `code` là `SESSION_NOT_FOUND`

#### Scenario: Lỗi ở một session không chặn session khác
- **WHEN** A và C đều quá hạn, và việc xoá thư mục storage của A ném lỗi
- **THEN** C bị xoá hoàn toàn
- **AND** A vẫn còn trong database, và report của lần chạy ghi `failures` là `1`, `deletedSessions` là `1`

#### Scenario: Session đang bận được bỏ qua
- **WHEN** A quá hạn nhưng đang có một lệnh ghi giữ khoá của A
- **THEN** A không bị xoá ở lần chạy này, và report ghi `skipped` là `1`

#### Scenario: Chạy ngay khi khởi động
- **WHEN** ứng dụng khởi động xong
- **THEN** lần dọn đầu tiên chạy trong vòng 5 giây, không phải chờ đủ một giờ

### Requirement: Dọn thư mục lưu trữ mồ côi
Mỗi lần dọn dẹp, hệ thống SHALL xoá những thư mục con của storage root thoả đồng thời:
- có tên là một UUID;
- không có session tương ứng trong database;
- có thời điểm sửa cuối cũ hơn TTL.

Thư mục hay file có tên không phải UUID MUST NOT bị xoá. Thư mục mồ côi còn mới hơn TTL MUST NOT bị xoá, vì có thể là upload đang dở.

#### Scenario: Mồ côi quá hạn bị xoá
- **WHEN** storage root có thư mục `{X}` (X là UUID), database không có session X, và thư mục được sửa lần cuối cách đây 25 giờ
- **THEN** cleanup xoá thư mục `{X}`, và report ghi `deletedOrphans` là `1`

#### Scenario: Mồ côi còn mới được giữ
- **WHEN** storage root có thư mục `{Y}` (Y là UUID), database không có session Y, và thư mục được sửa lần cuối cách đây 1 giờ
- **THEN** thư mục `{Y}` vẫn còn sau cleanup

#### Scenario: Thư mục không phải UUID không bị đụng tới
- **WHEN** storage root có thư mục `backup` được sửa lần cuối cách đây 30 ngày
- **THEN** thư mục `backup` vẫn còn sau cleanup
