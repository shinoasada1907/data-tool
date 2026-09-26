# result-export Specification

## Purpose
TBD - created by archiving change fe-import-wizard-v0-1. Update Purpose after archive.
## Requirements
### Requirement: Tải dữ liệu hợp lệ dạng JSON và CSV
FE SHALL có nút "Tải JSON" và "Tải CSV". Mỗi nút gọi `GET /api/import-sessions/{id}/export?format=json|csv` và lưu file về máy. Tên file lấy từ header `Content-Disposition`: ưu tiên `filename*` (BE luôn gửi dạng này), sau đó `filename`. Khi không có header này, FE dùng tên dự phòng `<tên-file-gốc-bỏ-đuôi>-valid.<json|csv>`, cùng mẫu tên mà BE dùng. FE MUST loại ký tự đường dẫn (`/`, `\`) khỏi tên file.

#### Scenario: Tải CSV có Content-Disposition UTF-8
- **WHEN** response có `Content-Disposition: attachment; filename*=UTF-8''kh%C3%A1ch-valid.csv`
- **THEN** file được lưu với tên `khách-valid.csv`

#### Scenario: Không có Content-Disposition
- **WHEN** file gốc là `customers.xlsx` và response export JSON không có `Content-Disposition`
- **THEN** file được lưu với tên `customers-valid.json`

### Requirement: Tải error report
FE SHALL có nút "Tải báo cáo lỗi" gọi `GET /api/import-sessions/{id}/errors/export`. Tên dự phòng là `<tên-file-gốc-bỏ-đuôi>-errors.csv`.

#### Scenario: Tải báo cáo lỗi
- **WHEN** có `invalid > 0` và user bấm "Tải báo cáo lỗi"
- **THEN** FE gọi GET errors/export và lưu file trả về

### Requirement: Khoá nút tải khi không phù hợp
FE MUST khoá nút tải, kèm lý do hiển thị, trong các trường hợp:
- kết quả đã cũ: khoá cả ba nút;
- `valid = 0`: khoá "Tải JSON" và "Tải CSV";
- `invalid = 0`: khoá "Tải báo cáo lỗi";
- một nút đang tải: khoá chính nút đó và hiển thị trạng thái đang tải.

#### Scenario: Kết quả đã cũ
- **WHEN** kết quả bị đánh dấu cũ
- **THEN** cả ba nút bị khoá, kèm lý do "Chạy lại để tải kết quả khớp cấu hình hiện tại"

#### Scenario: Không có dòng hợp lệ
- **WHEN** `valid: 0`
- **THEN** "Tải JSON" và "Tải CSV" bị khoá, còn "Tải báo cáo lỗi" vẫn bấm được nếu `invalid > 0`

### Requirement: Lỗi khi tải file
Khi request tải trả về mã lỗi, FE MUST đọc body lỗi (ProblemDetail) và hiển thị lỗi. FE MUST NOT lưu body lỗi thành file tải về. Khi lỗi mạng, FE MUST báo không kết nối được máy chủ.

#### Scenario: BE báo lỗi export
- **WHEN** GET export trả `500` với `code: "EXPORT_FAILED"`
- **THEN** FE hiển thị thông điệp ứng với `EXPORT_FAILED` và không có file nào được lưu

#### Scenario: Mất kết nối khi tải
- **WHEN** request tải thất bại vì lỗi mạng
- **THEN** FE hiển thị "Không kết nối được máy chủ" và nút tải bấm lại được

#### Scenario: BE báo kết quả không còn
- **WHEN** GET export trả `409` với `code: "RESULT_NOT_AVAILABLE"`
- **THEN** không có file nào được lưu; FE đánh dấu kết quả là cũ (spec `result-review`), nên cả ba nút tải bị khoá và nút "Chạy lại" hiện ra

