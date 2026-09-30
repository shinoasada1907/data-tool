# result-review Specification

## Purpose
TBD - created by archiving change fe-import-wizard-v0-1. Update Purpose after archive.
## Requirements
### Requirement: Tóm tắt kết quả
FE SHALL hiển thị ba thẻ Tổng / Hợp lệ / Lỗi, lấy từ `summary.total`, `summary.valid`, `summary.invalid` trong response của BE.

#### Scenario: Hiển thị tóm tắt
- **WHEN** summary là `{total: 120, valid: 100, invalid: 20}`
- **THEN** FE hiển thị các thẻ Tổng 120, Hợp lệ 100, Lỗi 20

### Requirement: Xem dòng hợp lệ và dòng lỗi theo tab
FE SHALL có hai tab "Hợp lệ (n)" và "Lỗi (n)". Tab mặc định là "Lỗi" khi `invalid > 0`, ngược lại là "Hợp lệ". Bảng MUST có cột "Dòng" (`rowNumber`), tiếp theo là các target field theo đúng thứ tự schema; thứ tự cột MUST NOT phụ thuộc vào thứ tự key của object `values`. Giá trị hiển thị là giá trị BE trả về: row hợp lệ là giá trị đã ép kiểu; row lỗi là chuỗi sau transformation, hoặc `null` ở field có transformation lỗi (hiện placeholder).

#### Scenario: Tab mặc định khi có lỗi
- **WHEN** vào bước Result với `invalid: 20`
- **THEN** tab "Lỗi (20)" đang được chọn

#### Scenario: Thứ tự cột theo schema
- **WHEN** schema theo thứ tự là `["2024", "name"]` và `values` của một dòng là `{"name": "AN", "2024": 5}`
- **THEN** các cột hiển thị theo thứ tự "Dòng", "2024", "name"

#### Scenario: Dòng lỗi do validation
- **WHEN** dòng 7 có lỗi `{fieldName: "email", stage: "VALIDATION", rule: "email", step: null, code: "VALIDATION_EMAIL", message: "...", sourceValue: "abc"}`
- **THEN** ô `email` của dòng 7 được làm nổi bật, và dưới dòng hiển thị field `email`, nhãn mã lỗi, rule `email`, và giá trị nguồn `abc`

#### Scenario: Dòng lỗi do transformation
- **WHEN** dòng 9 có lỗi `{fieldName: "birthday", stage: "TRANSFORMATION", rule: "dateFormat", step: 1, code: "TRANSFORMATION_FAILED", message: "...", sourceValue: "31/02/2024"}`
- **THEN** ô `birthday` hiển thị placeholder và được làm nổi bật; lỗi ghi rõ transformation `dateFormat` ở bước 2 (`step` đếm từ 0, FE hiển thị `step + 1` cho khớp số thứ tự trên màn Transform & Validate), kèm giá trị nguồn `31/02/2024`

### Requirement: Phân trang kết quả
FE SHALL phân trang phía server, mỗi trang 50 dòng (`page` bắt đầu từ 0), có nút "Trước"/"Sau" và nhãn "Trang x / y". Mỗi lần đổi trang hoặc đổi tab, FE SHALL gọi lại `GET .../result` với `view` và `page` tương ứng.

#### Scenario: Sang trang sau
- **WHEN** đang ở trang 1 / 3 của tab "Lỗi" và user bấm "Sau"
- **THEN** FE gọi `GET .../result?view=invalid&page=1&size=50` và hiển thị "Trang 2 / 3"

#### Scenario: Đổi tab
- **WHEN** user chuyển từ tab "Lỗi" sang tab "Hợp lệ"
- **THEN** FE gọi `GET .../result?view=valid&page=0&size=50`

### Requirement: Lọc dòng lỗi theo field và mã lỗi
Ở tab "Lỗi", FE SHALL cho lọc theo target field và/hoặc mã lỗi. Bộ lọc được gửi lên BE qua query `field` và `code` (BE đã xác nhận ở Q3), và mỗi lần đổi bộ lọc FE quay về `page=0`. Danh sách lựa chọn kèm số lỗi lấy từ `summary.errorCountsByField` và `summary.errorCountsByCode`.

#### Scenario: Lọc theo mã lỗi
- **WHEN** user chọn mã lỗi `VALIDATION_EMAIL`
- **THEN** FE gọi `GET .../result?view=invalid&page=0&size=50&code=VALIDATION_EMAIL`

#### Scenario: Xoá bộ lọc
- **WHEN** user bấm "Xoá lọc"
- **THEN** FE gọi lại tab "Lỗi" không có `field`/`code`, tại `page=0`

### Requirement: Trạng thái rỗng của kết quả
Khi một tab không có dòng nào, FE SHALL hiển thị thông báo rõ ràng thay cho bảng trống.

#### Scenario: Không có dòng lỗi
- **WHEN** `invalid: 0` và user mở tab "Lỗi"
- **THEN** FE hiển thị "Không có dòng lỗi"

#### Scenario: Không có dòng hợp lệ
- **WHEN** `valid: 0` và user mở tab "Hợp lệ"
- **THEN** FE hiển thị "Không có dòng hợp lệ"

### Requirement: Kết quả đã cũ
Kết quả bị đánh dấu cũ, kèm lý do, khi:
- cấu hình đã sửa sau lần chạy gần nhất (spec `import-wizard`);
- BE trả `409 RESULT_NOT_AVAILABLE`;
- hoặc lần chạy sau đó bị lỗi ở process (spec `pipeline-run`).

Khi đó FE MUST:
- hiển thị cảnh báo theo lý do:
  - cấu hình đã sửa: "Cấu hình đã thay đổi — kết quả này là của lần chạy trước";
  - BE không còn kết quả: "Máy chủ không còn giữ kết quả này — chạy lại để có kết quả mới";
  - session không dùng được nữa: "Phiên import không dùng được nữa — kết quả này là của lần chạy trước, hãy upload lại file";
- giữ phần tóm tắt và trang đang xem;
- khoá đổi trang, đổi tab và bộ lọc, vì BE đã xoá kết quả cũ;
- hiện nút "Chạy lại", dùng đúng trình tự lưu và chạy của `pipeline-run`. Riêng khi session không dùng được nữa, hiện nút "Upload lại" thay cho "Chạy lại".

#### Scenario: Xem kết quả cũ
- **WHEN** user sửa mapping, bấm "Tiếp" để lưu, rồi bấm stepper sang bước Result
- **THEN** FE hiển thị cảnh báo kết quả cũ và nút "Chạy lại"; các nút đổi trang, tab và bộ lọc bị khoá

#### Scenario: Session hỏng sau khi đã có kết quả
- **WHEN** đã có kết quả, user chạy lại mà không sửa gì, và POST process trả `422 FILE_PARSE_ERROR`
- **THEN** bước Result hiển thị cảnh báo "Phiên import không dùng được nữa…" và nút "Upload lại", không có nút "Chạy lại"

#### Scenario: BE báo kết quả không còn
- **WHEN** user bấm "Sau" và `GET .../result` trả `409` với `code: "RESULT_NOT_AVAILABLE"`
- **THEN** FE đánh dấu kết quả là cũ, giữ trang đang xem, và hiển thị cảnh báo kèm nút "Chạy lại"

