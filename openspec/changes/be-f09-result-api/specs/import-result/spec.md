## ADDED Requirements

### Requirement: Xem kết quả xử lý theo trang
Hệ thống SHALL trả kết quả xử lý của session qua `GET /api/import-sessions/{id}/result` dưới dạng `PipelineResultDto { summary, view, page { number, size, totalElements, totalPages }, rows[] }`.
- Mỗi phần tử của `rows` có dạng `{ rowNumber, valid, values, errors }`.
- `view=valid` chỉ trả row hợp lệ (`valid=true`, `errors=[]`); `view=invalid` chỉ trả row lỗi (`valid=false`, `errors` khác rỗng).
- Hệ thống SHALL trả tối đa `size` row của trang `page` (bắt đầu từ 0). `totalPages` bằng `ceil(totalElements / size)`, và bằng `0` khi `totalElements` bằng `0`.
- `page` vượt quá trang cuối SHALL trả `200` với `rows` rỗng.

#### Scenario: Trang đầu của row lỗi
- **WHEN** session có kết quả gồm row lỗi 3, 4, 6, và client gọi `GET /result?view=invalid&page=0&size=2`
- **THEN** hệ thống trả `200`, `view` là `invalid`, `rows` gồm row 3 và 4 theo đúng thứ tự đó
- **AND** `page` là `{ number: 0, size: 2, totalElements: 3, totalPages: 2 }`

#### Scenario: Trang cuối không đầy
- **WHEN** vẫn kết quả trên, client gọi `GET /result?view=invalid&page=1&size=2`
- **THEN** `rows` chỉ gồm row 6

#### Scenario: Trang vượt quá số trang
- **WHEN** vẫn kết quả trên, client gọi `GET /result?view=invalid&page=5&size=2`
- **THEN** hệ thống trả `200`, `rows` rỗng, `totalElements` là `3`, `totalPages` là `2`

#### Scenario: Tham số mặc định
- **WHEN** session có row hợp lệ 2 và 5, và client gọi `GET /result` không kèm query param
- **THEN** hệ thống dùng `view=valid`, `page=0`, `size=50`
- **AND** `rows` gồm row 2 và 5, mỗi row có `valid` là `true` và `errors` là mảng rỗng

### Requirement: Kiểm tra tham số truy vấn kết quả
Các query param không hợp lệ SHALL bị từ chối với `400` và `code` là `REQUEST_INVALID`:
- `view` không phải `valid`/`invalid` (không phân biệt hoa thường);
- `page` nhỏ hơn 0 hoặc không phải số;
- `size` nằm ngoài khoảng 1–200 hoặc không phải số.

`field` hoặc `code` là chuỗi rỗng hay toàn khoảng trắng SHALL được coi như không gửi.

#### Scenario: size vượt tối đa
- **WHEN** client gọi `GET /result?size=201`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

#### Scenario: size bằng 0
- **WHEN** client gọi `GET /result?size=0`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

#### Scenario: page âm
- **WHEN** client gọi `GET /result?page=-1`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

#### Scenario: view lạ
- **WHEN** client gọi `GET /result?view=all`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`

#### Scenario: Bộ lọc rỗng coi như không lọc
- **WHEN** client gọi `GET /result?view=invalid&field=&code=`
- **THEN** kết quả giống hệt khi gọi `GET /result?view=invalid`

### Requirement: Lọc row lỗi theo field và mã lỗi
Với `view=invalid`, hệ thống SHALL chỉ trả những row có ít nhất **một** phần tử trong `errors[]` thoả đồng thời mọi bộ lọc được gửi:
- `fieldName` bằng đúng `field`;
- `code` bằng đúng `code`.

Row khớp SHALL được trả kèm đầy đủ `errors[]` của nó. `totalElements` SHALL là số row khớp. Với `view=valid`, `field` và `code` SHALL bị bỏ qua.

#### Scenario: Lọc theo field
- **WHEN** row lỗi 3 có lỗi ở `email`, row 4 có lỗi ở `dob` và `score`, row 6 có lỗi ở `email`, và client gọi `GET /result?view=invalid&field=email`
- **THEN** `rows` gồm row 3 và 6, và `totalElements` là `2`

#### Scenario: Lọc theo mã lỗi
- **WHEN** chỉ row 4 có lỗi `VALIDATION_TYPE`, và client gọi `GET /result?view=invalid&code=VALIDATION_TYPE`
- **THEN** `rows` chỉ gồm row 4, và `errors` của row 4 vẫn chứa cả lỗi `TRANSFORMATION_FAILED` ở `dob`

#### Scenario: Hai bộ lọc phải khớp cùng một lỗi
- **WHEN** row 3 có lỗi `VALIDATION_EMAIL` ở `email`, không row nào có lỗi `VALIDATION_TYPE` ở `email`, và client gọi `GET /result?view=invalid&field=email&code=VALIDATION_TYPE`
- **THEN** `rows` rỗng và `totalElements` là `0`

#### Scenario: Bộ lọc bị bỏ qua ở view hợp lệ
- **WHEN** session có row hợp lệ 2 và 5, và client gọi `GET /result?view=valid&field=email`
- **THEN** `rows` gồm row 2 và 5

### Requirement: Hiển thị giá trị trong kết quả
`values` của mỗi row SHALL là một object có key là tên target field, theo thứ tự schema.
- Với row hợp lệ, giá trị SHALL đã được ép kiểu: `number` là JSON number, ghi dạng plain, không dùng số mũ; `boolean` là `true`/`false`; `date` là chuỗi `yyyy-MM-dd`; giá trị rỗng là `null`.
- Với row lỗi, giá trị SHALL là chuỗi sau transformation, hoặc `null` ở field có transformation thất bại.
- Mỗi phần tử của `errors[]` SHALL có đủ `rowNumber`, `fieldName`, `stage`, `rule`, `step`, `code`, `message`, `sourceValue`.

#### Scenario: Row hợp lệ đã ép kiểu
- **WHEN** row 2 hợp lệ có `name` là `An`, `score` là `10`, `active` là `true`, `dob` là `1990-12-25`, và `note` rỗng
- **THEN** `values` của row 2 là `{"name":"An","score":10,"active":true,"dob":"1990-12-25","note":null}`, với các key theo đúng thứ tự đó

#### Scenario: Số rất nhỏ không bị ghi dạng số mũ
- **WHEN** row hợp lệ có `score` là số `0.0000001`
- **THEN** JSON response chứa đúng `0.0000001`, và không chứa `1E-7`

#### Scenario: Row lỗi hiển thị chuỗi sau transformation
- **WHEN** row 3 có `email` gốc là ` ABC `, config có transformation `trim` rồi `lowercase`, và `email` bị lỗi `VALIDATION_EMAIL`
- **THEN** `values.email` của row 3 là `"abc"`
- **AND** lỗi có `stage` là `VALIDATION`, `rule` là `email`, `step` là `null`, `code` là `VALIDATION_EMAIL`, `sourceValue` là `" ABC "`

### Requirement: Thứ tự row ổn định
Trong mọi view, row SHALL được trả theo `rowNumber` tăng dần. Gọi lại cùng tham số khi config không đổi SHALL trả đúng cùng dữ liệu, cùng thứ tự.

#### Scenario: Gọi lại cho cùng kết quả
- **WHEN** client gọi `GET /result?view=invalid&page=0&size=50` hai lần liên tiếp, và giữa hai lần không có thay đổi nào
- **THEN** hai response có cùng `rows`, cùng thứ tự `rowNumber` tăng dần

### Requirement: Kết quả chỉ có khi đã process và còn khớp config
Hệ thống SHALL trả `409` với `code` là `RESULT_NOT_AVAILABLE` khi một trong các điều kiện sau đúng:
- session không ở trạng thái `PROCESSED` (kể cả khi đang `FAILED`);
- không có `summary.json` của kết quả;
- `configHash` trong `summary.json` khác hash của config hiện tại.

Session không tồn tại SHALL trả `404` với `code` là `SESSION_NOT_FOUND`.

#### Scenario: Chưa process
- **WHEN** session đang `READY` và client gọi `GET /result`
- **THEN** hệ thống trả `409` với `code` là `RESULT_NOT_AVAILABLE`

#### Scenario: Config đã đổi sau khi process
- **WHEN** session đã được process, sau đó client PUT `/transformations` với config khác, rồi gọi `GET /result`
- **THEN** hệ thống trả `409` với `code` là `RESULT_NOT_AVAILABLE`

#### Scenario: Session không tồn tại
- **WHEN** client gọi `GET /api/import-sessions/{uuid-chưa-tạo}/result`
- **THEN** hệ thống trả `404` với `code` là `SESSION_NOT_FOUND`

### Requirement: Summary kèm số đếm lỗi
`summary` trong response SHALL gồm `sessionId`, `status`, `total`, `valid`, `invalid`, `errorCountsByCode`, `errorCountsByField`, `processedAt`, với `total = valid + invalid`.
- Các số đếm lỗi SHALL tính trên toàn bộ kết quả, không phụ thuộc trang hay bộ lọc.
- `status` SHALL là trạng thái hiện tại của session.

#### Scenario: Summary không đổi theo bộ lọc
- **WHEN** kết quả có `total` 5, `valid` 2, `invalid` 3, với các lỗi `VALIDATION_EMAIL` 1, `TRANSFORMATION_FAILED` 1, `VALIDATION_TYPE` 1, `VALIDATION_UNIQUE` 1; và client gọi `GET /result?view=invalid&code=VALIDATION_TYPE&size=1`
- **THEN** `summary` có `total` 5, `valid` 2, `invalid` 3, `status` là `PROCESSED`
- **AND** `errorCountsByCode` là `{VALIDATION_EMAIL: 1, TRANSFORMATION_FAILED: 1, VALIDATION_TYPE: 1, VALIDATION_UNIQUE: 1}`
