## Why

Export là bước cuối của luồng V0.1 (pack F10, FR-09, FR-10). Người dùng phải tải được dữ liệu hợp lệ dạng JSON hoặc CSV, và tải được báo cáo lỗi để sửa file nguồn. FE-F10 tải file thật qua `fetch` + blob, nên cần tên file an toàn, `Content-Disposition` đúng chuẩn, và lỗi rõ ràng khi chưa có gì để tải.

## What Changes

- `GET /api/import-sessions/{id}/export?format=json|csv` trả **chỉ** các row hợp lệ, dạng stream:
  - **JSON**: một mảng object, key theo thứ tự schema, giá trị đúng kiểu, số ghi dạng plain.
  - **CSV**: UTF-8 có BOM, header theo thứ tự schema, định dạng RFC 4180.
- `GET /api/import-sessions/{id}/errors/export` trả báo cáo lỗi dạng CSV. Mỗi lỗi một dòng, gồm các cột `rowNumber,fieldName,stage,rule,step,code,message,sourceValue`.
- Chống formula injection trong CSV đúng phạm vi D13:
  - CSV dữ liệu: ô dữ liệu kiểu `string`/`email`, và ô header;
  - báo cáo lỗi: các cột `fieldName`, `rule`, `message`, `sourceValue`.
- `Content-Disposition: attachment` có `filename*` theo RFC 5987. Tên file là `<tên-gốc>-valid.json`, `<tên-gốc>-valid.csv` hoặc `<tên-gốc>-errors.csv`.
- Mọi kiểm tra được làm **trước khi bắt đầu stream**:
  - `409 RESULT_NOT_AVAILABLE` khi chưa có kết quả hiện hành;
  - `500 EXPORT_FAILED` khi không mở được nguồn dữ liệu;
  - `400 REQUEST_INVALID` khi `format` sai.
- Không có row nào: JSON trả `[]`, CSV chỉ có BOM và header.
- Thêm `spring.mvc.async.request-timeout: 5m` để stream không bị cắt bởi timeout async mặc định của Tomcat (30 giây).

## Capabilities

### New Capabilities
- `data-export`: export row hợp lệ dạng JSON/CSV và báo cáo lỗi dạng CSV; gồm định dạng file, chống formula injection, tên file tải về, điều kiện export và cách xử lý lỗi khi export.

### Modified Capabilities
(không có)

## Impact

- **Code**:
  - `MAIN/domain/export/`: định dạng export, port exporter, `CsvFormulaGuard`, `ExportFileName`.
  - `MAIN/infrastructure/export/`: các writer JSON và CSV.
  - `MAIN/application/export/`: `ExportService`.
  - `MAIN/api/export/`: `ExportController`.
  - `application.yaml`.
- **API**: endpoint #10 và #11 của contract V0.1.
- **Dependencies**: dùng lại Apache Commons CSV đã có từ be-f02 (parser CSV). Không thêm dependency mới.
- **Phụ thuộc**: be-f08 (result store); be-f09 (`ResultQueryService.requireCurrentSummary`); be-f04 (schema hiện tại, gồm tên, kiểu và thứ tự field).
- **FE**: không phải đổi gì. FE đã đọc `filename*` trước rồi mới tới `filename` (D10 của FE).
