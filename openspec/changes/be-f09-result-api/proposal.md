## Why

F08 chạy pipeline và ghi kết quả ra đĩa (D7 của be-f01), nhưng người dùng chưa có cách xem kết quả đó. Pack F09 và FR-08 yêu cầu hiển thị tổng số row, số hợp lệ và không hợp lệ, cùng chi tiết từng row lỗi, để debug dữ liệu trước khi export. FE-F09 cần endpoint này có phân trang và lọc ngay phía server, vì lọc phía client trên dữ liệu đã phân trang chỉ lọc được trang đang xem.

## What Changes

- Thêm `GET /api/import-sessions/{id}/result?view=valid|invalid&page=0&size=50&field=&code=`, trả `PipelineResultDto` đúng API contract V0.1:
  - `summary` là `PipelineSummaryDto`, có đủ `errorCountsByCode` và `errorCountsByField`.
  - `page { number, size, totalElements, totalPages }`.
  - `rows[] { rowNumber, valid, values, errors }`.
- Phân trang phía server. `size` mặc định 50, tối đa 200; `page` bắt đầu từ 0.
- Lọc row lỗi theo `field` và/hoặc `code`: một row khớp khi có **cùng một lỗi** thoả mọi bộ lọc được gửi.
- Kết quả chỉ trả khi session đã `PROCESSED` và `configHash` trong `summary.json` khớp config hiện tại. Ngược lại trả `409 RESULT_NOT_AVAILABLE`.
- Giá trị trả về: row hợp lệ đã ép kiểu (number, boolean, ngày ISO, `null`); row lỗi là chuỗi sau transformation. Số luôn ghi dạng plain, không dùng số mũ.
- Thứ tự row ổn định, tăng dần theo `rowNumber`.

## Capabilities

### New Capabilities
- `import-result`: truy vấn kết quả xử lý của một session, gồm summary, row hợp lệ, row lỗi kèm `errors[]`; cùng với phân trang, lọc, cách hiển thị giá trị và điều kiện để có kết quả.

### Modified Capabilities
(không có)

## Impact

- **Code**:
  - `MAIN/api/result/` (controller, DTO), `MAIN/application/result/` (use case).
  - Đọc qua result store của F08; config lấy từ F04–F07.
  - Có thể thêm `MAIN/infrastructure/config/JacksonConfig.java` nếu F08 chưa bật ghi số dạng plain.
- **API**: endpoint #9 của contract V0.1. Không đổi endpoint nào khác.
- **DB**: không có migration mới.
- **Phụ thuộc**: be-f08-pipeline (result store, `summary.json`, `configHash`, `PipelineSummaryDto`); be-f04 → be-f07 (config hiện tại của session).
