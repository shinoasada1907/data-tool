## ADDED Requirements

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
