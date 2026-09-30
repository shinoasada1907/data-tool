## ADDED Requirements

### Requirement: Run được tạo nguyên khối
Với mọi tool dạng run (Validator, Cleaner, Diff), `POST /api/{tool}/runs` SHALL chạy đồng bộ. Khi thành công, hệ thống SHALL trả `201` kèm `Location: /api/{tool}/runs/{id}` và DTO của run. DTO có ít nhất `id`, `createdAt`, `expiresAt`, `sources` và `summary`. `sources` là `[{ role, datasetId, fileName, format, options }]`, với `options` là tuỳ chọn đọc thật đã dùng.

Một run MUST chỉ tồn tại khi nó đã chạy xong và toàn bộ kết quả đã được ghi. Khi run thất bại (lỗi cấu hình, lỗi đọc nguồn, lỗi ghi), hệ thống MUST NOT để lại row hay file nào của run đó.

#### Scenario: Lỗi giữa chừng không để lại run
- **WHEN** việc ghi kết quả của một run thất bại sau khi đã ghi một phần
- **THEN** request trả lỗi, không có row nào trong `tool_run` cho run đó, và storage không còn thư mục của run đó (thư mục tạm cũng bị dọn)

### Requirement: Xem và xoá run
- `GET /api/{tool}/runs/{id}` SHALL trả DTO của run.
- `DELETE /api/{tool}/runs/{id}` SHALL xoá row và file của run rồi trả `204`.
- id không tồn tại, đã hết hạn, đã bị xoá, hoặc thuộc tool khác SHALL trả `404` với `code` là `RUN_NOT_FOUND`.
- id không phải UUID SHALL trả `400 REQUEST_INVALID`.
- Response JSON của run SHALL có header `Cache-Control: no-store`.

#### Scenario: id của tool khác
- **WHEN** client có id của một run Validator và gọi `GET /api/diff/runs/{id}`
- **THEN** hệ thống trả `404` với `code` là `RUN_NOT_FOUND`

### Requirement: Run không phụ thuộc dataset nguồn
Sau khi được tạo, một run SHALL xem được và export được mà không cần dataset nguồn. Xoá dataset, hoặc dataset hết hạn, MUST NOT làm hỏng run.

#### Scenario: Xoá dataset sau khi chạy
- **WHEN** một run được tạo từ dataset D, rồi client gọi `DELETE /api/datasets/{D}`
- **THEN** `GET /api/{tool}/runs/{id}/rows` và `POST /api/{tool}/runs/{id}/export` vẫn trả `200` với nội dung như trước

### Requirement: Run tự hết hạn sau 24 giờ không dùng
Mỗi run SHALL có `expiresAt = lastUsedAt + TTL`, với TTL cấu hình qua `toolbox.retention.run-ttl` (mặc định `24h`). Mỗi lần xem run, xem trang row, hoặc export, `lastUsedAt` SHALL được cập nhật, tối đa một lần mỗi 10 phút. Run hết hạn SHALL trả `404 RUN_NOT_FOUND` và SHALL được dọn dẹp định kỳ.

#### Scenario: Run hết hạn
- **WHEN** một run không được dùng trong 25 giờ
- **THEN** `GET /api/{tool}/runs/{id}` trả `404` với `code` là `RUN_NOT_FOUND`

### Requirement: Phân trang row của run
Endpoint `GET /api/{tool}/runs/{id}/rows` của mọi tool SHALL nhận `page` (từ 0) và `size` (1–200, mặc định 50), và trả `page { number, size, totalElements, totalPages }`. Giá trị ngoài khoảng SHALL trả `400 REQUEST_INVALID`. Trang vượt quá trang cuối SHALL trả `rows` rỗng, kèm `totalElements` đúng.

#### Scenario: Trang vượt quá cuối
- **WHEN** run có 30 row ở view được chọn, và client gọi `rows?page=5&size=10`
- **THEN** hệ thống trả `200` với `rows` rỗng, `page.totalElements` là `30`, và `page.totalPages` là `3`
