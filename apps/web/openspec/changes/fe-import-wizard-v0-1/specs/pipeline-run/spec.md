## ADDED Requirements

### Requirement: Nút Chạy xử lý chỉ bật khi cấu hình hợp lệ
Nút "Chạy xử lý" ở bước Transform & Validate MUST bị khoá, kèm lý do hiển thị, khi thuộc một trong các trường hợp:
- schema hoặc mapping chưa lưu;
- còn lỗi cấu hình ở bất kỳ bước nào (tên field, mapping, tham số transformation);
- đang có request chạy.

#### Scenario: Còn lỗi tham số
- **WHEN** một `dateFormat` còn thiếu `inputFormat`
- **THEN** nút "Chạy xử lý" bị khoá, kèm lý do chỉ ra field có lỗi

#### Scenario: Cấu hình hợp lệ
- **WHEN** schema và mapping đã lưu, và không còn lỗi cấu hình
- **THEN** nút "Chạy xử lý" bấm được

### Requirement: Trình tự lưu và chạy pipeline
Khi bấm "Chạy xử lý", FE SHALL thực hiện tuần tự và dừng ngay ở bước đầu tiên bị lỗi:
1. `PUT .../transformations` nếu transformation chưa lưu;
2. `PUT .../validations` nếu validation chưa lưu;
3. `POST .../process`;
4. `GET .../result`: `view=invalid` nếu `invalid > 0`, ngược lại `view=valid`; `page=0`.

Khi process thành công, wizard sang bước Result & Export, và bước đó gửi request 4. Nếu lỗi ở bước 1–3, wizard ở lại bước hiện tại và hiển thị lỗi; phần đã lưu thành công trước đó vẫn được giữ trạng thái đã lưu. Nếu request 4 lỗi, lỗi hiện ở bước Result & Export kèm nút "Thử lại" chỉ gửi lại request 4, không chạy lại process.

#### Scenario: Chạy thành công
- **WHEN** transformation và validation đều chưa lưu, user bấm "Chạy xử lý" và mọi request thành công
- **THEN** FE gọi lần lượt PUT transformations, PUT validations, POST process, GET result, rồi hiển thị bước Result & Export

#### Scenario: Chỉ gọi PUT phần chưa lưu
- **WHEN** transformation đã lưu và chưa sửa, validation chưa lưu
- **THEN** FE bỏ qua PUT transformations, và chỉ gọi PUT validations, POST process, GET result

#### Scenario: Lỗi khi lưu validation
- **WHEN** PUT transformations thành công nhưng PUT validations trả `422`
- **THEN** FE không gọi process, hiển thị lỗi, transformation ở trạng thái đã lưu, validation vẫn chưa lưu, và wizard ở lại bước Transform & Validate

#### Scenario: BE báo session chưa sẵn sàng
- **WHEN** POST process trả `409` với `code: "SESSION_NOT_READY"` và `errors: [{field: "email", code: "TARGET_FIELD_REQUIRED", message: "..."}]`
- **THEN** FE hiển thị lỗi kèm danh sách readiness issue (field `email`: field bắt buộc chưa map), và ở lại bước hiện tại

#### Scenario: Session đã hỏng
- **WHEN** POST process trả `409` với `code: "SESSION_STATE_INVALID"`
- **THEN** FE xử lý theo requirement "Session không dùng được nữa" của spec `import-wizard`

#### Scenario: File nguồn sai cấu trúc khi process
- **WHEN** POST process trả `422` với `code: "FILE_PARSE_ERROR"` (session đã chuyển sang `FAILED`)
- **THEN** FE hiển thị thông điệp của `FILE_PARSE_ERROR` kèm `detail` ở dòng phụ, và hiện ngay nút "Upload lại" như requirement "Session không dùng được nữa"

#### Scenario: Lỗi máy chủ khi process, session vẫn dùng được
- **WHEN** POST process trả `500` với `code: "INTERNAL_ERROR"`, và `GET /api/import-sessions/{id}` cho biết session không ở trạng thái `FAILED`
- **THEN** FE hiển thị lỗi chung, ở lại bước hiện tại, và nút "Chạy xử lý" bấm lại được

#### Scenario: Lỗi máy chủ khi process, session đã hỏng
- **WHEN** POST process trả `500` với `code: "INTERNAL_ERROR"`, và `GET /api/import-sessions/{id}` cho biết session đã `FAILED`
- **THEN** FE hiển thị lỗi chung kèm nút "Upload lại"

#### Scenario: Process lỗi khi đã có kết quả
- **WHEN** đã có kết quả, user chạy lại và POST process trả `422 FILE_PARSE_ERROR`
- **THEN** kết quả bị đánh dấu cũ, vì BE đã xoá nó

#### Scenario: Tải trang kết quả đầu lỗi
- **WHEN** POST process thành công nhưng GET result trả `503`
- **THEN** wizard ở bước Result & Export, hiển thị lỗi kèm nút "Thử lại"; bấm "Thử lại" chỉ gửi lại GET result

### Requirement: Trạng thái đang xử lý
Trong lúc trình tự lưu và chạy đang diễn ra, FE SHALL hiển thị chỉ báo "Đang xử lý…" và MUST khoá nút "Chạy xử lý", các nút điều hướng và stepper, để không gửi trùng request.

#### Scenario: Bấm đúp
- **WHEN** user bấm "Chạy xử lý" hai lần liên tiếp
- **THEN** FE chỉ gửi một lượt trình tự request

### Requirement: Chạy lại sau khi sửa cấu hình
Sau khi đã có kết quả, mọi thay đổi cấu hình MUST đánh dấu kết quả là cũ (stale). Chạy lại thành công MUST thay kết quả cũ bằng kết quả mới và bỏ đánh dấu cũ.

#### Scenario: Sửa rule rồi chạy lại
- **WHEN** đã có kết quả, user quay lại bước Transform & Validate, bật `unique` cho một field rồi bấm "Chạy xử lý"
- **THEN** FE gọi PUT validations, POST process và GET result; kết quả mới thay kết quả cũ và không còn đánh dấu cũ
