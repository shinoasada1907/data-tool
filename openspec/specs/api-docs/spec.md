# api-docs Specification

## Purpose
Cung cấp tài liệu API sinh từ code đang chạy: Swagger UI tại `/swagger-ui.html` và OpenAPI 3 tại `/v3/api-docs`.
## Requirements
### Requirement: Tài liệu API trực tuyến
Hệ thống SHALL cung cấp giao diện Swagger UI tại `/swagger-ui.html` và tài liệu OpenAPI 3 dạng JSON tại `/v3/api-docs`. Tài liệu SHALL được sinh từ code đang chạy, nên luôn liệt kê đủ mọi endpoint `/api/...` hiện có. Tài liệu MUST có tiêu đề `Universal Data Tools API`.

Tài liệu SHALL được chia nhóm theo tool:
- nhóm `all` gồm mọi path `/api/**`, tại `/v3/api-docs` và `/v3/api-docs/all`;
- nhóm `importer` gồm `/api/import-sessions/**`, tại `/v3/api-docs/importer`;
- mỗi tool thêm về sau có nhóm riêng mang tên tool.

#### Scenario: Tài liệu liệt kê mọi endpoint hiện có
- **WHEN** client gọi `GET /v3/api-docs`
- **THEN** hệ thống trả `200` với JSON có `info.title` là `Universal Data Tools API`
- **AND** `paths` có `/api/import-sessions`, `/api/import-sessions/{id}` và `/api/import-sessions/{id}/preview`

#### Scenario: Nhóm importer chỉ chứa endpoint của importer
- **WHEN** client gọi `GET /v3/api-docs/importer`
- **THEN** hệ thống trả `200`, và mọi key trong `paths` đều bắt đầu bằng `/api/import-sessions`

#### Scenario: Mở giao diện Swagger UI
- **WHEN** client mở `/swagger-ui.html`
- **THEN** hệ thống dẫn tới trang Swagger UI và trang đó tải thành công (`200`)

#### Scenario: Upload thử được ngay trên tài liệu
- **WHEN** client đọc mô tả của `POST /api/import-sessions` trong `/v3/api-docs`
- **THEN** request body có kiểu `multipart/form-data`, với field `file` dạng nhị phân (`format: binary`), để Swagger UI hiện nút chọn file

