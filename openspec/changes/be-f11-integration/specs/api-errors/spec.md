## ADDED Requirements

### Requirement: Mỗi endpoint chỉ trả mã lỗi đã công bố
Mỗi endpoint SHALL chỉ trả các `code` được liệt kê cho nó trong bảng dưới. Ngoài bảng, chỉ có hai ngoại lệ:
- `INTERNAL_ERROR` (500) cho lỗi không lường trước, được phép ở mọi endpoint;
- `REQUEST_INVALID` với status gốc cho lỗi 4xx của framework.

| Endpoint | Mã lỗi được phép |
|---|---|
| `POST /api/import-sessions` | `REQUEST_INVALID`, `FILE_TOO_LARGE`, `FILE_UNSUPPORTED`, `FILE_EMPTY`, `FILE_PARSE_ERROR` |
| `GET /api/import-sessions/{id}` | `REQUEST_INVALID`, `SESSION_NOT_FOUND` |
| `GET /api/import-sessions/{id}/preview` | `REQUEST_INVALID`, `SESSION_NOT_FOUND` |
| `PUT /api/import-sessions/{id}/schema` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `SESSION_STATE_INVALID`, `SCHEMA_INVALID` |
| `PUT /api/import-sessions/{id}/mapping` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `SESSION_STATE_INVALID`, `MAPPING_INVALID`, `SOURCE_COLUMN_NOT_FOUND` |
| `PUT /api/import-sessions/{id}/transformations` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `SESSION_STATE_INVALID`, `CONFIG_INVALID` |
| `PUT /api/import-sessions/{id}/validations` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `SESSION_STATE_INVALID`, `CONFIG_INVALID` |
| `POST /api/import-sessions/{id}/process` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `SESSION_NOT_READY`, `SESSION_STATE_INVALID`, `FILE_PARSE_ERROR` |
| `GET /api/import-sessions/{id}/result` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `RESULT_NOT_AVAILABLE` |
| `GET /api/import-sessions/{id}/export` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `RESULT_NOT_AVAILABLE`, `EXPORT_FAILED` |
| `GET /api/import-sessions/{id}/errors/export` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `RESULT_NOT_AVAILABLE`, `EXPORT_FAILED` |

Mọi response lỗi của các endpoint trên SHALL là `application/problem+json` và có `code`.

#### Scenario: Process khi field required chưa map
- **WHEN** schema có field `email` là required nhưng mapping không có `email`, và client gọi `POST /api/import-sessions/{id}/process`
- **THEN** hệ thống trả `409` với `code` là `SESSION_NOT_READY`
- **AND** `errors` chứa phần tử có `field` là `email` và `code` là `TARGET_FIELD_REQUIRED`

#### Scenario: JSON hỏng ở PUT schema
- **WHEN** client gọi `PUT /api/import-sessions/{id}/schema` với body `{"fields": [`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

#### Scenario: Ghi vào session FAILED
- **WHEN** session đang `FAILED`, và client gọi `PUT /api/import-sessions/{id}/schema` với một schema hợp lệ
- **THEN** hệ thống trả `409` với `code` là `SESSION_STATE_INVALID`

#### Scenario: Process khi file nguồn đã mất
- **WHEN** session đang `READY`, file `{storageRoot}/{id}/source.bin` đã bị xoá, và client gọi `POST /api/import-sessions/{id}/process`
- **THEN** hệ thống trả `500` dạng `application/problem+json` với `code` là `INTERNAL_ERROR`
- **AND** sau đó `GET /api/import-sessions/{id}` trả `status` là `FAILED`

#### Scenario: CSV không phải UTF-8
- **WHEN** client upload `bad.csv` có nội dung byte `a,b\n` tiếp theo là `0xC3 0x28` và `\n`
- **THEN** hệ thống trả `422` với `code` là `FILE_PARSE_ERROR`, và không tạo session nào

#### Scenario: Mọi lỗi trong bộ kiểm contract đều có mã hợp lệ
- **WHEN** bộ integration test gọi lần lượt mọi tình huống lỗi trong bảng contract của F11
- **THEN** mỗi response có `Content-Type` chứa `application/problem+json`, và `code` thuộc tập mã được phép của endpoint tương ứng
