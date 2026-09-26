# import-pipeline Specification

## Purpose
Chạy pipeline map → transform → validate → ép kiểu trên toàn bộ file của session (`POST /process`), ghi kết quả `valid.ndjson`/`invalid.ndjson`/`summary.json` vào result store một cách nguyên khối, và giữ kết quả khớp với config hiện tại.
## Requirements
### Requirement: Pipeline xử lý từng row theo thứ tự map, transform, validate, ép kiểu
Với mỗi row nguồn, hệ thống SHALL xử lý từng target field theo thứ tự trong schema qua 4 bước:
1. **Map**: lấy giá trị từ cột nguồn hoặc hằng số; field chưa map nhận `null`.
2. **Transform**: chạy transformation theo `order`.
3. **Validate**: `required → type → email → unique`.
4. **Ép kiểu**: rule `type` đổi giá trị sang kiểu thật.

Row không có lỗi nào SHALL được tính là hợp lệ, và `values` của nó chứa giá trị đã ép kiểu cho mọi field của schema, theo thứ tự schema.

Bộ dữ liệu mẫu dùng trong các scenario của capability này:
- **File CSV** có cột `Họ tên, Email, Tuổi, Ngày sinh`, gồm 6 row:
  - row 2: `"  An ", "AN@X.COM", "30", "25/12/1990"`
  - row 3: `"Bình", "binh@x", "abc", "31/02/1990"`
  - row 4: `"", "an@x.com", "", ""`
  - row 5: `"Cường", "cuong@x.com", "", ""`
  - row 6: `"Dũng", "dung@x.com", "abc", ""`
  - row 7: `"Dũng 2", "dung@x.com", "40", ""`
- **Schema**: `name` (string, required), `email` (email, required), `age` (number), `dob` (date).
- **Mapping**: `Họ tên → name`, `Email → email`, `Tuổi → age`, `Ngày sinh → dob`.
- **Transformation**:
  - `name`: `trim(0)`
  - `email`: `trim(0)`, `lowercase(1)`
  - `dob`: `dateFormat(0){inputFormat: "dd/MM/yyyy"}`
- **Validation**: `email` có `unique`.

#### Scenario: Row hợp lệ được ép kiểu
- **WHEN** pipeline chạy bộ dữ liệu mẫu
- **THEN** row 2 hợp lệ với `values = {name: "An", email: "an@x.com", age: 30, dob: 1990-12-25}`
- **AND** `age` là số `30` và `dob` là ngày `1990-12-25`

#### Scenario: Field optional để trống cho null
- **WHEN** pipeline chạy bộ dữ liệu mẫu
- **THEN** row 5 hợp lệ với `values = {name: "Cường", email: "cuong@x.com", age: null, dob: null}`

### Requirement: Kết quả row lỗi có cấu trúc
Row có ít nhất một lỗi SHALL được tính là không hợp lệ.
- `values` của row lỗi là chuỗi sau transformation của mọi field. Field có transformation lỗi nhận `null`.
- Mỗi lỗi SHALL là một `ImportError` gồm:
  - `rowNumber`, `fieldName`;
  - `stage`: `TRANSFORMATION` hoặc `VALIDATION`;
  - `rule`;
  - `step`: `order` của transformation, `null` với validation;
  - `code`, `message`;
  - `sourceValue`: giá trị trước mọi transformation.
- Các lỗi của một row SHALL theo thứ tự field trong schema.

#### Scenario: Row có ba lỗi ở ba field
- **WHEN** pipeline chạy bộ dữ liệu mẫu
- **THEN** row 3 không hợp lệ với `values = {name: "Bình", email: "binh@x", age: "abc", dob: null}`, và có đúng 3 lỗi theo thứ tự:
  - `{fieldName: "email", stage: VALIDATION, rule: "email", step: null, code: VALIDATION_EMAIL, sourceValue: "binh@x"}`
  - `{fieldName: "age", stage: VALIDATION, rule: "type", step: null, code: VALIDATION_TYPE, sourceValue: "abc"}`
  - `{fieldName: "dob", stage: TRANSFORMATION, rule: "dateFormat", step: 0, code: TRANSFORMATION_FAILED, sourceValue: "31/02/1990"}`

#### Scenario: Lỗi required và unique trên cùng một row
- **WHEN** pipeline chạy bộ dữ liệu mẫu
- **THEN** row 4 có đúng 2 lỗi:
  - `{fieldName: "name", code: VALIDATION_REQUIRED, sourceValue: null}`
  - `{fieldName: "email", code: VALIDATION_UNIQUE, message: "Duplicate value; first seen in row 2.", sourceValue: "an@x.com"}`

### Requirement: Pipeline chỉ ghi nhận giá trị unique của row hợp lệ
Sau khi xử lý xong mỗi row, hệ thống SHALL ghi nhận các giá trị `unique` của row đó nếu row hợp lệ. Nếu row có bất kỳ lỗi nào, kể cả lỗi transformation hay lỗi ở field khác, hệ thống SHALL bỏ các giá trị đó. Mỗi lần chạy pipeline SHALL bắt đầu với một tập giá trị `unique` rỗng.

#### Scenario: Row lỗi ở field khác không giữ chỗ
- **WHEN** pipeline chạy bộ dữ liệu mẫu, trong đó row 6 có `email = "dung@x.com"` nhưng lỗi `age`
- **THEN** row 7 có `email = "dung@x.com"` vẫn hợp lệ và không có lỗi `VALIDATION_UNIQUE`

#### Scenario: Chạy lại không bị ảnh hưởng bởi lần chạy trước
- **WHEN** pipeline chạy bộ dữ liệu mẫu hai lần liên tiếp
- **THEN** lần chạy thứ hai cho row 2 hợp lệ (không bị báo trùng với chính lần chạy thứ nhất)

### Requirement: Một row lỗi không làm dừng pipeline
Khi mapping, transformation hay validation rule ném exception không lường trước ở một row, hệ thống SHALL chuyển exception đó thành lỗi của field tương ứng và tiếp tục xử lý các row sau. Exception của mapping được ghi thành lỗi `code = TRANSFORMATION_FAILED`, `rule = "mapping"`, `message = "Unexpected error while mapping the value."`. Message của exception MUST NOT xuất hiện trong kết quả.

#### Scenario: Transformation có bug ở một row
- **WHEN** một transformation ném `IllegalStateException("boom")` với giá trị của row 3, trong khi file có 5 row
- **THEN** pipeline trả `total = 5`; row 3 có lỗi `TRANSFORMATION_FAILED`; 4 row còn lại được xử lý như bình thường
- **AND** không có kết quả nào chứa chuỗi `boom`

### Requirement: Tổng hợp summary của pipeline
Hệ thống SHALL trả summary gồm:
- `total`: số row đã xử lý;
- `valid`, `invalid`, với `total = valid + invalid`;
- `errorCountsByCode`: số **lỗi** theo từng code, key sắp theo tên code, chỉ gồm code có lỗi;
- `errorCountsByField`: số lỗi theo từng field, key theo thứ tự schema, chỉ gồm field có lỗi.

#### Scenario: Summary của bộ dữ liệu mẫu
- **WHEN** pipeline chạy bộ dữ liệu mẫu
- **THEN** summary là:
  - `total = 6`, `valid = 3`, `invalid = 3`
  - `errorCountsByCode = {TRANSFORMATION_FAILED: 1, VALIDATION_EMAIL: 1, VALIDATION_REQUIRED: 1, VALIDATION_TYPE: 2, VALIDATION_UNIQUE: 1}`
  - `errorCountsByField = {name: 1, email: 2, age: 2, dob: 1}`

### Requirement: Lưu kết quả vào result store
Hệ thống SHALL ghi kết quả vào `{storageRoot}/{sessionId}/result/`, gồm 3 file:
- `valid.ndjson`: mỗi row hợp lệ một dòng `{"rowNumber", "values"}`.
- `invalid.ndjson`: mỗi row lỗi một dòng `{"rowNumber", "values", "errors"}`.
- `summary.json`: summary cùng `processedAt` và `configHash`.

Định dạng:
- File dùng UTF-8 không BOM và xuống dòng bằng `\n`.
- Số ghi dạng plain, không có ký hiệu `E`. Ngày ghi dạng chuỗi `yyyy-MM-dd`.

Kết quả SHALL được ghi vào một thư mục tạm rồi mới thay cho `result/`. Hệ thống MUST NOT để lại thư mục tạm, dù chạy thành công hay thất bại. Nếu hệ điều hành đang khoá một file bên trong nên chưa xoá được, lần process kế tiếp SHALL dọn nó.

Việc thay `result/` thất bại giữa chừng SHALL trả lại kết quả cũ nguyên vẹn. Kết quả không ghi được (ví dụ đầy đĩa) SHALL trả `500` với `code = INTERNAL_ERROR`, và MUST NOT đổi trạng thái session hay kết quả cũ.

#### Scenario: Nội dung file kết quả
- **WHEN** pipeline chạy xong bộ dữ liệu mẫu
- **THEN** `valid.ndjson` có đúng 3 dòng, dòng đầu là `{"rowNumber":2,"values":{"name":"An","email":"an@x.com","age":30,"dob":"1990-12-25"}}`
- **AND** `invalid.ndjson` có đúng 3 dòng, lần lượt cho row 3, 4, 6
- **AND** `summary.json` có `total = 6` và `configHash` bằng hash của config hiện tại

#### Scenario: Chạy thất bại không để lại rác
- **WHEN** việc đọc file nguồn thất bại giữa chừng
- **THEN** thư mục `{sessionId}` không còn thư mục nào tên bắt đầu bằng `result.tmp-`

#### Scenario: Ghi kết quả thất bại giữ kết quả cũ
- **WHEN** session đã `PROCESSED`, và lần process sau không ghi được kết quả
- **THEN** hệ thống trả `500` với `code = INTERNAL_ERROR`
- **AND** session vẫn `PROCESSED`, và `result/` vẫn là kết quả cũ

### Requirement: Kết quả pipeline có tính xác định
Với cùng file nguồn và cùng config, hệ thống SHALL tạo `valid.ndjson` và `invalid.ndjson` giống hệt nhau từng byte giữa các lần chạy. `summary.json` SHALL giống nhau ở mọi field, trừ `processedAt`.

#### Scenario: Chạy lại cho cùng kết quả
- **WHEN** client gọi `POST /process` hai lần cho cùng một session mà không đổi config
- **THEN** `valid.ndjson` và `invalid.ndjson` của lần chạy thứ hai giống hệt từng byte lần chạy thứ nhất

### Requirement: Endpoint chạy pipeline
Hệ thống SHALL nhận `POST /api/import-sessions/{id}/process` (không có body). Endpoint chạy pipeline đồng bộ trên toàn bộ file, lưu kết quả, chuyển session sang `PROCESSED`, rồi trả `200` với `PipelineSummaryDto { sessionId, status, total, valid, invalid, errorCountsByCode, errorCountsByField, processedAt }`. Gọi lại endpoint trên session đã `PROCESSED` SHALL chạy lại pipeline và thay kết quả cũ.

#### Scenario: Process thành công
- **WHEN** session `READY` được cấu hình theo bộ dữ liệu mẫu, và client gọi `POST /process`
- **THEN** hệ thống trả `200` với `status = PROCESSED`, `total = 6`, `valid = 3`, `invalid = 3`
- **AND** `GET /api/import-sessions/{id}` trả `status = PROCESSED`

#### Scenario: Process lại
- **WHEN** session đang `PROCESSED` và client gọi `POST /process` lần nữa
- **THEN** hệ thống trả `200` và session vẫn `PROCESSED`

### Requirement: Không process khi session chưa sẵn sàng
Hệ thống SHALL từ chối process:
- với `409` và `code = SESSION_NOT_READY` khi session chưa `READY`, kèm `errors[]` là các readiness issue (ví dụ `TARGET_FIELD_REQUIRED`);
- với `409` và `code = SESSION_STATE_INVALID` khi session đang `FAILED`;
- với `404` và `code = SESSION_NOT_FOUND` khi session không tồn tại.

Khi bị từ chối, hệ thống MUST NOT tạo hay thay đổi kết quả.

#### Scenario: Field required chưa map
- **WHEN** field `email` là required nhưng chưa được map, và client gọi `POST /process`
- **THEN** hệ thống trả `409`, `code = SESSION_NOT_READY`, và `errors` chứa `{field: "email", code: "TARGET_FIELD_REQUIRED"}`

#### Scenario: Session FAILED
- **WHEN** session đang `FAILED` và client gọi `POST /process`
- **THEN** hệ thống trả `409` với `code = SESSION_STATE_INVALID`

#### Scenario: Session không tồn tại
- **WHEN** client gọi `POST /api/import-sessions/{uuid-chưa-tạo}/process`
- **THEN** hệ thống trả `404` với `code = SESSION_NOT_FOUND` (FE nhận biết session đã mất nhờ `code`, không nhờ status)

### Requirement: Lỗi đọc file khi process chuyển session sang FAILED
Khi đọc file nguồn thất bại trong lúc process, hệ thống SHALL:
- chuyển session sang `FAILED`;
- xoá thư mục tạm và mọi kết quả cũ của session;
- trả lỗi của parser như chính nó (`422` với `code = FILE_PARSE_ERROR` nếu file sai cấu trúc), hoặc `500` với `code = INTERNAL_ERROR` nếu lỗi IO hay thiếu file.

Chỉ lỗi của file nguồn mới làm session `FAILED`. Lỗi khi đóng file sau khi đã đọc hết mọi row MUST NOT làm hỏng lần chạy. Lỗi bất ngờ của chính hệ thống (bug) trả `500` và MUST NOT đổi trạng thái session.

Session đã `FAILED` là trạng thái cuối (D2). Mọi lệnh ghi sau đó (`PUT /schema`, `/mapping`, `/transformations`, `/validations`, và `POST /process`) SHALL bị từ chối với `409` và `code = SESSION_STATE_INVALID`.

#### Scenario: File sai cấu trúc giữa chừng
- **WHEN** parser ném lỗi `FILE_PARSE_ERROR` ở row 4 trong lúc process
- **THEN** hệ thống trả `422` với `code = FILE_PARSE_ERROR`, và session chuyển sang `FAILED`
- **AND** không còn thư mục `result/` hay `result.tmp-*` nào của session

#### Scenario: Đóng file lỗi sau khi đã đọc hết
- **WHEN** mọi row đã được đọc, nhưng việc đóng file nguồn ném lỗi IO
- **THEN** hệ thống trả `200` với `status = PROCESSED`, và `result/` là kết quả của lần chạy này

#### Scenario: Thiếu file nguồn
- **WHEN** file `source.bin` của session không còn trên đĩa, và client gọi `POST /process`
- **THEN** hệ thống trả `500` với `code = INTERNAL_ERROR`, và session chuyển sang `FAILED`

#### Scenario: Lỗi IO khi đang đọc
- **WHEN** việc đọc file nguồn ném lỗi IO ở row 3 trong lúc process
- **THEN** hệ thống trả `500` với `code = INTERNAL_ERROR`, session chuyển sang `FAILED`, và không còn thư mục `result.tmp-*` nào

#### Scenario: Lệnh ghi sau khi FAILED bị từ chối
- **WHEN** session vừa chuyển sang `FAILED` vì lỗi đọc file, và client gửi `PUT /transformations` hợp lệ, rồi gọi `POST /process`
- **THEN** cả hai request đều trả `409` với `code = SESSION_STATE_INVALID`, và `GET /api/import-sessions/{id}` vẫn trả `status = FAILED`

### Requirement: Đổi config thì kết quả cũ bị xoá
Khi một lệnh PUT config làm `configHash` thay đổi, hệ thống SHALL xoá thư mục `result/` nếu có (dù session đang ở trạng thái nào), rồi chuyển session sang `READY` hoặc `CONFIGURING` tuỳ readiness. Việc xoá chạy sau khi thay đổi đã được lưu. Nếu xoá không được thì thay đổi vẫn giữ nguyên, vì kết quả có `configHash` cũ không bao giờ được trả cho client. Khi lệnh PUT không làm `configHash` thay đổi, hệ thống SHALL giữ kết quả và giữ `PROCESSED`.

#### Scenario: Sửa transformation sau khi process
- **WHEN** session đang `PROCESSED` và client gửi `PUT /transformations` với cấu hình khác cấu hình đã dùng khi process
- **THEN** `result/` của session bị xoá, và `GET /api/import-sessions/{id}` trả `status = READY`

#### Scenario: Gửi lại đúng cấu hình cũ
- **WHEN** session đang `PROCESSED` và client gửi lại `PUT /validations` với đúng cấu hình đang lưu
- **THEN** `result/` được giữ nguyên và `status` vẫn là `PROCESSED`

### Requirement: Process giữ khoá session
Hệ thống SHALL giữ khoá của session (D11) trong suốt thời gian process. Các lệnh process và PUT config của cùng một session SHALL chạy lần lượt.

#### Scenario: Hai lệnh process đồng thời
- **WHEN** hai request `POST /process` cho cùng một session tới gần như cùng lúc
- **THEN** cả hai đều trả `200`
- **AND** thư mục `result/` cuối cùng đầy đủ 3 file, và không còn thư mục `result.tmp-*` hay `result.old-*` nào

