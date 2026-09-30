## ADDED Requirements

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
