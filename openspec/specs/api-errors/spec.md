# api-errors Specification

## Purpose
Chuẩn hoá lỗi của mọi endpoint: `application/problem+json` có `code`, status HTTP theo bảng D4, và mỗi endpoint chỉ trả những mã lỗi đã công bố.
## Requirements
### Requirement: Mọi lỗi API trả ProblemDetail có mã lỗi
Mọi response lỗi (4xx, 5xx) của API SHALL có `Content-Type: application/problem+json` và body theo RFC 9457 gồm `type`, `title`, `status`, `detail`, `instance`. Body MUST có thêm thuộc tính `code` ở top-level, là một mã trong bảng mã lỗi API.

#### Scenario: Lỗi nghiệp vụ có mã
- **WHEN** một thao tác thất bại với mã `FILE_UNSUPPORTED` khi gọi `POST /api/import-sessions`
- **THEN** response có status `415`, `Content-Type` là `application/problem+json`
- **AND** body có `status` là `415`, `code` là `FILE_UNSUPPORTED`, `instance` là `/api/import-sessions`

#### Scenario: Lỗi của framework cũng có mã
- **WHEN** client gọi một endpoint không tồn tại, ví dụ `GET /api/khong-ton-tai`
- **THEN** response có status `404`, và body có `code` là `REQUEST_INVALID`

### Requirement: Bảng mã lỗi API và HTTP status
Mỗi mã lỗi API SHALL luôn đi kèm đúng HTTP status sau:

| HTTP | Code |
|---|---|
| 400 | `REQUEST_INVALID` |
| 401 | `GUEST_TOKEN_INVALID` |
| 404 | `SESSION_NOT_FOUND`, `DATASET_NOT_FOUND`, `RUN_NOT_FOUND`, `SCHEMA_NOT_FOUND` |
| 409 | `SESSION_NOT_READY`, `SESSION_STATE_INVALID`, `RESULT_NOT_AVAILABLE`, `SCHEMA_VERSION_CONFLICT` |
| 413 | `FILE_TOO_LARGE` |
| 415 | `FILE_UNSUPPORTED` |
| 422 | `FILE_EMPTY`, `FILE_PARSE_ERROR`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `SCHEMA_INVALID`, `SCHEMA_INCOMPATIBLE`, `MAPPING_INVALID`, `SOURCE_COLUMN_NOT_FOUND`, `CONFIG_INVALID`, `DIFF_KEY_INVALID` |
| 429 | `RATE_LIMITED` |
| 500 | `EXPORT_FAILED`, `INTERNAL_ERROR` |
| 503 | `SERVER_BUSY` |

Có hai ngoại lệ, chỉ về status:
- Lỗi 4xx do framework sinh ra (endpoint không tồn tại, sai method, sai Content-Type) SHALL mang `code` là `REQUEST_INVALID` nhưng giữ status gốc của framework.
- Body JSON quá lớn SHALL mang `code` là `REQUEST_INVALID` với status `413`.

Response `429` và `503` SHALL luôn có header `Retry-After` (số giây).

Tên mã lỗi MUST duy nhất trong toàn hệ thống, và mọi mã mà code có thể trả MUST có mặt trong bảng này.

#### Scenario: Mã nghiệp vụ quyết định status
- **WHEN** một thao tác thất bại với mã `SESSION_STATE_INVALID`
- **THEN** response có status `409` và `code` là `SESSION_STATE_INVALID`

#### Scenario: Sai method giữ status gốc
- **WHEN** client gọi `DELETE /api/import-sessions`
- **THEN** response có status `405` và `code` là `REQUEST_INVALID`

#### Scenario: Server bận có Retry-After
- **WHEN** một thao tác thất bại với mã `SERVER_BUSY`
- **THEN** response có status `503`, `code` là `SERVER_BUSY`, và có header `Retry-After`

### Requirement: Lỗi chi tiết theo field
Khi lỗi gắn với các target field cụ thể, body SHALL có mảng `errors`. Mỗi phần tử có dạng `{ field, code, message }`, trong đó `field` là tên target field, hoặc `null` nếu lỗi không gắn với field nào. Khi lỗi không gắn với field cụ thể, body MUST NOT có thuộc tính `errors`.

#### Scenario: Lỗi kèm danh sách field
- **WHEN** một thao tác thất bại với mã `SCHEMA_INVALID`, kèm chi tiết `{ field: "email", code: "SCHEMA_INVALID", message: "Duplicate field name" }`
- **THEN** body có `errors` là một mảng một phần tử, với `field` là `email`, `code` là `SCHEMA_INVALID`, `message` là `Duplicate field name`

#### Scenario: Lỗi không có chi tiết field
- **WHEN** một thao tác thất bại với mã `SESSION_NOT_FOUND`
- **THEN** body không có thuộc tính `errors`

### Requirement: Lỗi không lường trước không lộ chi tiết nội bộ
Lỗi không lường trước SHALL trả `500` với `code` là `INTERNAL_ERROR` và `detail` là một thông điệp chung. Body MUST NOT chứa message của exception, stack trace, tên class, hay dữ liệu từ file người dùng. Chi tiết lỗi SHALL được ghi vào log phía server.

#### Scenario: Exception bất ngờ
- **WHEN** xử lý request ném ra `IllegalStateException("secret-db-password")`
- **THEN** response có status `500` và `code` là `INTERNAL_ERROR`
- **AND** body không chứa chuỗi `secret-db-password` và không chứa `IllegalStateException`

### Requirement: Mỗi endpoint chỉ trả mã lỗi đã công bố
Mỗi endpoint SHALL chỉ trả các `code` được liệt kê cho nó: endpoint của Importer trong bảng dưới, endpoint dataset trong spec `dataset-api`, và endpoint của mỗi tool trong spec của tool đó. Ngoài các danh sách đó, chỉ có ba ngoại lệ:
- `INTERNAL_ERROR` (500) cho lỗi không lường trước, được phép ở mọi endpoint;
- `REQUEST_INVALID` với status gốc cho lỗi 4xx của framework, và với status `413` cho body JSON quá lớn;
- `RATE_LIMITED` (429), được phép ở mọi endpoint `/api/**`.

| Endpoint | Mã lỗi được phép |
|---|---|
| `POST /api/import-sessions` | `REQUEST_INVALID`, `FILE_TOO_LARGE`, `FILE_UNSUPPORTED`, `FILE_EMPTY`, `FILE_PARSE_ERROR`, `SERVER_BUSY` |
| `GET /api/import-sessions/{id}` | `REQUEST_INVALID`, `SESSION_NOT_FOUND` |
| `GET /api/import-sessions/{id}/preview` | `REQUEST_INVALID`, `SESSION_NOT_FOUND` |
| `PUT /api/import-sessions/{id}/schema` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `SESSION_STATE_INVALID`, `SCHEMA_INVALID` |
| `PUT /api/import-sessions/{id}/mapping` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `SESSION_STATE_INVALID`, `MAPPING_INVALID`, `SOURCE_COLUMN_NOT_FOUND` |
| `PUT /api/import-sessions/{id}/transformations` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `SESSION_STATE_INVALID`, `CONFIG_INVALID` |
| `PUT /api/import-sessions/{id}/validations` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `SESSION_STATE_INVALID`, `CONFIG_INVALID` |
| `POST /api/import-sessions/{id}/process` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `SESSION_NOT_READY`, `SESSION_STATE_INVALID`, `FILE_PARSE_ERROR`, `FILE_EMPTY`, `SERVER_BUSY` |
| `GET /api/import-sessions/{id}/result` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `RESULT_NOT_AVAILABLE` |
| `GET /api/import-sessions/{id}/export` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `RESULT_NOT_AVAILABLE`, `EXPORT_FAILED`, `SERVER_BUSY` |
| `GET /api/import-sessions/{id}/errors/export` | `REQUEST_INVALID`, `SESSION_NOT_FOUND`, `RESULT_NOT_AVAILABLE`, `EXPORT_FAILED`, `SERVER_BUSY` |

Mọi response lỗi của mọi endpoint SHALL là `application/problem+json` và có `code`.

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

