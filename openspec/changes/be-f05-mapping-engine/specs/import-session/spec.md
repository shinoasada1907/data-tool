## ADDED Requirements

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
