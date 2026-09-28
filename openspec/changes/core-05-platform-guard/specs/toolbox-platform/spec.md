## ADDED Requirements

### Requirement: Giới hạn tần suất theo client
Hệ thống SHALL giới hạn số request của mỗi client theo ba nhóm. Client xác định theo địa chỉ IP (IPv6 gom theo prefix /64).

| Nhóm | Endpoint | Mặc định |
|---|---|---|
| `upload` | `POST /api/datasets`, `POST /api/import-sessions` | 30 request / 10 phút |
| `compute` | preview dataset, các endpoint chạy hoặc export của mọi tool, `POST /api/import-sessions/{id}/process`, các endpoint export của Importer | 60 request / 10 phút |
| `read` | mọi endpoint `/api/**` còn lại | 600 request / 10 phút |

Hạn mức nạp lại dần theo thời gian (token bucket). Vượt hạn mức, hệ thống SHALL trả `429` với `code` là `RATE_LIMITED` và header `Retry-After`: số giây (tối thiểu 1) tới khi client được gửi lại request thuộc nhóm đó. Request bị từ chối MUST NOT gây bất kỳ tác dụng phụ nào. Hạn mức cấu hình được qua `toolbox.rate-limit.*`.

#### Scenario: Vượt hạn mức upload
- **WHEN** hạn mức `upload` là 2 request / 10 phút, và một client gửi 3 upload liên tiếp
- **THEN** hai upload đầu trả `201`, upload thứ ba trả `429` với `code` là `RATE_LIMITED` và `Retry-After` là `300`
- **AND** không có dataset nào được tạo từ upload thứ ba

#### Scenario: Client khác không bị ảnh hưởng
- **WHEN** client A đã hết hạn mức `upload`, và client B (IP khác) upload
- **THEN** upload của B trả `201`

### Requirement: Giới hạn thao tác nặng chạy đồng thời
Hệ thống SHALL giới hạn số thao tác nặng chạy cùng lúc:
- toàn server: `toolbox.processing.max-concurrent`, mặc định bằng số CPU và tối thiểu 2;
- mỗi client: `toolbox.processing.per-client`, mặc định 2.

Thao tác nặng gồm:
- upload;
- preview dataset;
- convert;
- tạo run;
- export;
- so cột của diff;
- upload, process và export của Importer.

Với thao tác trả file, thao tác được tính là đang chạy **tới khi byte cuối được gửi**.

Khi vượt giới hạn:
- client đã có đủ số thao tác đang chạy: hệ thống SHALL trả ngay `429 RATE_LIMITED` với `Retry-After: 5`;
- toàn server đã đầy: hệ thống SHALL chờ tối đa `toolbox.processing.acquire-timeout` (mặc định 10 giây), rồi trả `503` với `code` là `SERVER_BUSY` và `Retry-After: 5`.

Thao tác bị từ chối MUST NOT gây tác dụng phụ.

#### Scenario: Server đang bận
- **WHEN** `max-concurrent` là 1, một convert đang stream file, và client khác gọi `POST /api/converter/convert`
- **THEN** sau khoảng `acquire-timeout`, lệnh thứ hai trả `503` với `code` là `SERVER_BUSY` và `Retry-After` là `5`

#### Scenario: Một client chạy quá nhiều
- **WHEN** `per-client` là 2, và một client đang có 2 thao tác nặng chạy, rồi gửi thao tác thứ ba
- **THEN** thao tác thứ ba trả ngay `429` với `code` là `RATE_LIMITED`

### Requirement: Không nhận thêm dữ liệu khi đĩa sắp đầy
Khi dung lượng trống của thư mục storage nhỏ hơn `toolbox.storage.min-free-space` (mặc định `2GB`), mọi upload (dataset và Importer) và mọi lệnh tạo run SHALL trả `503` với `code` là `SERVER_BUSY` và `Retry-After: 60`. Các lệnh đọc và `DELETE` SHALL vẫn hoạt động.

#### Scenario: Đĩa sắp đầy
- **WHEN** dung lượng trống nhỏ hơn ngưỡng, và client upload một dataset
- **THEN** hệ thống trả `503` với `code` là `SERVER_BUSY`
- **AND** `DELETE /api/datasets/{id}` của một dataset có sẵn vẫn trả `204`

### Requirement: Giới hạn kích thước body JSON
Request có body `application/json` lớn hơn 1 MB SHALL bị từ chối với `413` và `code` là `REQUEST_INVALID`, trước khi body được xử lý.

#### Scenario: Body quá lớn
- **WHEN** client gửi `POST /api/converter/convert` với body JSON 2 MB
- **THEN** hệ thống trả `413` với `code` là `REQUEST_INVALID`

### Requirement: Guest token cho tài nguyên có chủ
Các endpoint làm việc với tài nguyên có chủ SHALL đọc header `X-Guest-Token`. Token hợp lệ khớp `^[A-Za-z0-9_-]{32,128}$`. Thiếu hoặc sai định dạng SHALL trả `401` với `code` là `GUEST_TOKEN_INVALID`. Hệ thống SHALL chỉ lưu SHA-256 của token, và MUST NOT ghi token vào log. Tài nguyên không thuộc token đang gửi SHALL được trả lời như thể không tồn tại (`404`).

#### Scenario: Thiếu token
- **WHEN** client gọi một endpoint cần guest token mà không có header `X-Guest-Token`
- **THEN** hệ thống trả `401` với `code` là `GUEST_TOKEN_INVALID`

