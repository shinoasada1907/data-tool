## Context

- **Nguồn yêu cầu**: Notion "Universal Importer — Agent Project Pack", đọc theo thứ tự 00 → 03. Change này là BE-F01.
- **Vai trò của file này**: đây là **tài liệu thiết kế nền cho cả BE V0.1**. Các change `be-f02` → `be-f11` dẫn chiếu quyết định theo mã `D1`…`D14` thay vì chép lại. Mục **API contract V0.1** là contract chính thức giữa BE và FE.
- **Hiện trạng `apps/api`**: khung Spring Boot 4.1.1, chưa có endpoint. Đã có: Java 21, Web MVC, Validation, Data JPA, Flyway, Actuator, PostgreSQL driver.
  - Boot 4 dùng Jackson 3 (`tools.jackson`). Classpath không có Jackson 2 databind.
  - BOM quản lý JUnit Jupiter 6.0.3 và Testcontainers 2.0.5.
- **FE**: làm song song ở `apps/web` (change `fe-import-wizard-v0-1`), có contract đề xuất riêng. Contract dưới đây đã được hợp nhất: dùng shape của FE, giữ các phần BE bổ sung.
- **Nguồn gốc quyết định**: các quyết định trong file này đã được duyệt trong buổi brainstorming ngày 2026-09-25, gồm các mục review A1–A6, B1–B9, C1–C6, D.

## Goals / Non-Goals

**Goals:**
- Chốt nền dùng chung: kiến trúc, API contract, định dạng lỗi, vòng đời session, lưu trữ, đồng thời, cách test. Mười change sau chỉ việc thêm tính năng.
- F01: upload CSV/XLSX tạo session; đọc lại session; báo lỗi rõ khi file sai loại, quá lớn hoặc rỗng.

**Non-Goals:**
- Theo pack, V0.1 không làm: auth, AI, team, template, connector, xử lý import bất đồng bộ, deploy cloud.
- Không cấu hình CORS vì FE gọi qua Vite proxy (D3).
- Không lưu row đã parse vào PostgreSQL.
- F01 chưa parse nội dung file. Việc đọc file bắt đầu từ F02 (D9).

## Decisions

### D1. Một module Maven, 4 package, chiều phụ thuộc kiểm bằng ArchUnit

```
com.universalimporter
├── api             @RestController, DTO (record), @RestControllerAdvice
├── application     use case; @Transactional (nếu cần) đặt ở đây
├── domain          Java thuần: model, state machine, contract engine, port (interface)
└── infrastructure  parser, storage, persistence (JPA), export, config
```

- **Chiều phụ thuộc cho phép**:
  - `api → application, domain`
  - `application → domain`
  - `infrastructure → domain, application`
  - Không package nào được import `infrastructure`; Spring tự nối các bean.
- **`domain`** chỉ được phụ thuộc `java..` và chính nó. Có một test ArchUnit (bản core, gọi trong test Jupiter thường) kiểm các luật này.
- *Phương án khác*:
  - Multi-module Maven (`importer-core` + `api`): loại, vì phá khung đã có mà V0.1 không cần tới.
  - Package-by-feature: loại, vì trái với cây package pack đã chốt.

### D2. Vòng đời session

| Từ | Sự kiện | Sang |
|---|---|---|
| — | Upload thành công, file đã lưu | `UPLOADED` |
| `UPLOADED` | Đọc file thành công (từ F02, chạy trong cùng request upload) | `CONFIGURING` |
| `CONFIGURING` / `READY` | Một PUT config bất kỳ | `READY` nếu đủ điều kiện, ngược lại `CONFIGURING` |
| `PROCESSED` | PUT làm config thay đổi (so `configHash`) | như dòng trên, và **xoá kết quả** |
| `PROCESSED` | PUT không làm config thay đổi | giữ `PROCESSED` và kết quả |
| `READY` / `PROCESSED` | `POST /process` thành công | `PROCESSED` |
| `READY` / `PROCESSED` | `POST /process` gặp lỗi đọc file (IO hoặc cấu trúc) | `FAILED` |
| `FAILED` | Bất kỳ lệnh ghi nào | 409 `SESSION_STATE_INVALID`, trạng thái giữ nguyên |

- **`READY`** nghĩa là: schema có ít nhất một field, mọi field `required` đã được map, và mọi tham chiếu trong config đều hợp lệ.
- **Upload thất bại không tạo session**: file hỏng hay không hỗ trợ đều trả 4xx, và không để lại session hay file nào.
- **Cài đặt**:
  - `SessionStatus` giữ bảng chuyển trạng thái; `ImportSession.transitionTo(target, now)` kiểm tra theo bảng đó.
  - Chuyển trạng thái sai sinh `DomainException(SESSION_STATE_INVALID)`.
  - Cho phép chuyển về chính nó (`CONFIGURING`, `READY`, `PROCESSED`), vì PUT lại hay process lại đều hợp lệ.

### D3. Quy ước API

- **Base path**: `/api/import-sessions`. JSON UTF-8. Thời gian dạng ISO-8601 UTC (`Instant`).
- **DTO**: là Java `record` trong package `api`; không trả entity JPA ra ngoài.
- **Enum trong JSON**: viết đúng như pack. `status`, `fileType`, `mappingType` viết HOA; field type viết thường (`string`…); transformation type theo camelCase (`defaultValue`).
- **PUT**:
  - Ghi đè toàn bộ phần config của endpoint đó, idempotent.
  - Trả `200 ConfigUpdateResponseDto { session, warnings }`. Session trả về đầy đủ, vì PUT `/schema` có thể prune cả mapping lẫn rule.
- **Phân trang**: `page` bắt đầu từ 0. `limit` (preview) và `size` (result) mặc định 50, tối đa 200. Ngoài khoảng đó trả 400 `REQUEST_INVALID`.
- **`message`**: viết tiếng Anh. `code` không bao giờ đổi, nên FE có thể Việt hoá theo `code`.
- **Không CORS**. Khi dev, FE gọi `/api` qua Vite proxy tới `http://localhost:8080`, tức là cùng origin. Nếu sau này deploy khác origin thì bật CORS và `Access-Control-Expose-Headers: Content-Disposition`.

### D4. Định dạng lỗi và bảng mã

Mọi lỗi API trả `application/problem+json`:

```json
{ "type": "about:blank", "title": "Unsupported Media Type", "status": 415,
  "detail": "Only .csv and .xlsx files are supported.",
  "instance": "/api/import-sessions",
  "code": "FILE_UNSUPPORTED",
  "errors": [ { "field": "email", "code": "TARGET_FIELD_REQUIRED", "message": "..." } ] }
```

- `code` là bắt buộc.
- `errors[]` chỉ có khi lỗi gắn với từng field. Mỗi phần tử có dạng `ProblemItemDto { field, code, message }`, với `field` là tên target field hoặc `null`.

**Mã lỗi API**:

| HTTP | Code | Khi nào |
|---|---|---|
| 400 | `REQUEST_INVALID` | JSON hoặc multipart hỏng, sai kiểu, thiếu part, id không phải UUID, query param sai. Các lỗi 4xx khác của framework cũng dùng mã này nhưng giữ status gốc (404 endpoint không tồn tại, 405, 415 sai Content-Type). |
| 404 | `SESSION_NOT_FOUND` | Không có session với id đó |
| 409 | `SESSION_NOT_READY` | Gọi process khi chưa `READY`; `errors[]` là danh sách readiness issue |
| 409 | `SESSION_STATE_INVALID` | Thao tác không hợp lệ ở trạng thái hiện tại (ví dụ session `FAILED`) |
| 409 | `RESULT_NOT_AVAILABLE` | Gọi result/export khi chưa process, hoặc kết quả đã cũ so với config |
| 413 | `FILE_TOO_LARGE` | File vượt `IMPORTER_MAX_FILE_SIZE` |
| 415 | `FILE_UNSUPPORTED` | Đuôi file hoặc magic bytes không phải CSV/XLSX |
| 422 | `FILE_EMPTY` | File 0 byte, không có header, workbook hoặc sheet trống |
| 422 | `FILE_PARSE_ERROR` | CSV sai cú pháp hoặc không phải UTF-8; XLSX hỏng |
| 422 | `SCHEMA_INVALID` | Tên field rỗng, trùng hoặc quá dài; `order` trùng; schema rỗng |
| 422 | `MAPPING_INVALID` | Target field không tồn tại, map trùng, thiếu `sourceColumn` hoặc `constantValue` |
| 422 | `SOURCE_COLUMN_NOT_FOUND` | `sourceColumn` không có trong cột nguồn |
| 422 | `CONFIG_INVALID` | Transformation hoặc rule sai: type lạ, thiếu hoặc sai params, pattern ngày sai, `email` trên field không phải string, … |
| 500 | `EXPORT_FAILED` | Lỗi khi tạo file export, xảy ra trước khi bắt đầu stream |
| 500 | `INTERNAL_ERROR` | Lỗi không lường trước. `detail` chỉ ghi chung chung; stack trace chỉ nằm trong log server |

**Các mã khác, không phải lỗi HTTP:**
- **Lỗi theo row** (trong `ImportErrorDto`): `TRANSFORMATION_FAILED`, `VALIDATION_REQUIRED`, `VALIDATION_TYPE`, `VALIDATION_EMAIL`, `VALIDATION_UNIQUE`.
- **Readiness issue**: `SCHEMA_EMPTY`, `TARGET_FIELD_REQUIRED`.
- **Warning kèm PUT**: `CONFIG_PRUNED`, `TARGET_FIELD_UNMAPPED`, `RULE_IMPLIED_BY_SCHEMA`.

**Cài đặt:**
- `domain.common.ErrorCode` là enum chứa mọi mã lỗi API.
- `domain.common.DomainException` mang `ErrorCode`, `message` và `List<ProblemItem>`.
- `api.common.ErrorHttpStatus` ánh xạ mã sang HTTP status.
- `api.common.GlobalExceptionHandler extends ResponseEntityExceptionHandler` gắn `code` cho cả lỗi của framework. Riêng `MaxUploadSizeExceededException` được gắn mã `FILE_TOO_LARGE`.

### D5. Nhận file upload

- **Đuôi file**: chỉ `.csv` và `.xlsx`, không phân biệt hoa thường.
- **Magic bytes**, đọc 8 KB đầu file:
  - XLSX phải bắt đầu bằng `PK\x03\x04` (ZIP).
  - CSV không được chứa byte `0x00`.
  - Sai thì trả 415 `FILE_UNSUPPORTED`. `.xls` và mọi đuôi khác cũng trả 415.
- **MIME do client gửi bị bỏ qua**, chỉ ghi log. Lý do: Windows có cài Excel gửi `.csv` là `application/vnd.ms-excel`; có trình duyệt gửi `application/octet-stream`.
- **File 0 byte**: 422 `FILE_EMPTY`.
- **Giới hạn dung lượng**:
  - `spring.servlet.multipart.max-file-size = ${IMPORTER_MAX_FILE_SIZE:20MB}`.
  - `max-request-size = ${IMPORTER_MAX_REQUEST_SIZE:21MB}`, cộng thêm phần overhead của multipart.
  - `server.tomcat.max-swallow-size = -1`, để Tomcat đọc hết body và luôn gửi được 413 thay vì reset kết nối.
- **Tên file gốc** chỉ dùng làm metadata:
  - Lấy phần sau `/` hoặc `\` cuối cùng.
  - Chuẩn hoá Unicode NFC, vì macOS gửi tên tiếng Việt ở dạng NFD.
  - Bỏ ký tự điều khiển, trim.
  - Cắt còn tối đa 255 ký tự, giữ nguyên đuôi.

### D6. Lưu file

- **Port**: `domain.importsession.FileStorage` với ba hàm `save(UUID, InputStream) → long bytes`, `open(UUID) → InputStream`, `delete(UUID)`.
- **Cài đặt**: `infrastructure.storage.LocalFileStorage`.
  - Thư mục gốc là `importer.storage.dir = ${IMPORTER_STORAGE_DIR:${java.io.tmpdir}/universal-importer}`.
  - Layout: `{root}/{sessionId}/source.bin`. Từ F08 có thêm `{root}/{sessionId}/result/`.
  - Ghi ra file `.tmp` rồi `Files.move(ATOMIC_MOVE)`.
- **Khoá lưu trữ chính là `sessionId`**, không lưu đường dẫn trong DB.
  - Đây là lệch so với cột `storage_path` trong bản nháp DB của pack.
  - Lý do: không thể path traversal; đổi thư mục gốc không cần migrate; sau này chuyển sang object storage chỉ cần ánh xạ key sang object.

### D7. Lưu kết quả pipeline (dùng từ F08)

- **Layout** `{root}/{sessionId}/result/`:
  - `summary.json`: `total`, `valid`, `invalid`, `errorCountsByCode`, `errorCountsByField`, `processedAt`, `configHash`.
  - `valid.ndjson`: mỗi dòng là một row đã ép kiểu.
  - `invalid.ndjson`: mỗi dòng là một row kèm `errors[]`.
- **Ghi**: `/process` ghi vào `result.tmp-{uuid}/`, xong thì đổi tên nguyên khối thành `result/`.
- **Đọc**: `/result` và `/export` đọc stream từ file. Nếu thiếu `result/`, hoặc `configHash` khác config hiện tại, trả 409 `RESULT_NOT_AVAILABLE`.
- **`configHash`**: SHA-256 của JSON config đã chuẩn hoá (thứ tự key cố định).

### D8. Persistence

- **Bảng theo migration**:
  - `import_session` (V1, F01).
  - Cột `source_schema jsonb` (V2, F02).
  - Bảng `import_configuration` (V3, F04). Cột `session_id` vừa là PK vừa là FK; các cột `target_schema_json`, `mapping_json`, `transformations_json`, `validations_json` đều là `jsonb`.
- **JSON do app tự serialize** bằng Jackson 3 thành `String`, map vào cột qua `@JdbcTypeCode(SqlTypes.JSON)`. Không dựa vào FormatMapper của Hibernate: đường Hibernate + Jackson 3 chưa được kiểm chứng (review A5).
- **Khoá lạc quan** qua cột `version` với `@Version Long`. Kiểu wrapper để Spring Data nhận ra entity mới khi id do app gán.
- **Migration**: mỗi feature có migration riêng, không sửa migration đã merge. `spring.jpa.hibernate.ddl-auto: validate`.
- **Enum** lưu dạng `VARCHAR` có `CHECK` constraint.

### D9. Ngữ nghĩa dữ liệu nguồn (F02, F03)

- **Đọc file lúc upload**: sau khi lưu file, upload đọc stream toàn bộ file một lần để:
  - kiểm cấu trúc;
  - lấy header;
  - đếm `totalRows` (số dòng dữ liệu không trống);
  - lưu `source_schema` (cột, `sheetName`, `totalRows`).

  Nếu đọc lỗi thì xoá file, trả 422, và không tạo session. Đọc xong, session sang `CONFIGURING`.
- **Header** luôn là dòng 1. Tên cột được trim.
  - Header trống được đặt tên `Column <chữ cột Excel>`, ví dụ `Column C`.
  - Tên trùng (không phân biệt hoa thường) thì bản xuất hiện sau thêm hậu tố ` (2)`, ` (3)`, …
  - Cột có `index` bắt đầu từ 0 và `name` duy nhất; mapping tham chiếu theo `name`.
- **`rowNumber`** tính như khi mở file bằng Excel: header là dòng 1.
  - CSV: một giá trị nhiều dòng trong dấu quote vẫn tính là một row.
  - Dòng trống bị bỏ qua nhưng vẫn được đếm số.
- **Row thiếu ô**: ô thiếu coi là rỗng. **Row thừa ô**: phần thừa bị bỏ qua.
- **Giá trị** luôn là chuỗi. Ô rỗng thành `null`. Parser không trim.
- **CSV**: chỉ nhận UTF-8, có BOM thì bỏ BOM. Delimiter là dấu phẩy; quote theo RFC 4180. Byte không phải UTF-8 hoặc quote hỏng trả 422 `FILE_PARSE_ERROR`, kèm số dòng.
- **XLSX**: đọc sheet hiển thị đầu tiên.
  - Ô công thức lấy giá trị đã cache.
  - Ô số lấy từ chuỗi raw trong XML qua `BigDecimal.toPlainString()`, không đi qua `double`.
  - Ô ngày thành ISO `yyyy-MM-dd`. Nếu có phần giờ khác 0 thì thành `yyyy-MM-dd'T'HH:mm:ss`.
  - Ô boolean thành `TRUE`/`FALSE`.
  - Workbook hoặc sheet trống trả `FILE_EMPTY`.
  - Thư viện: spike `fastexcel-reader` trước; nếu không được thì dùng Apache POI SAX (review A4).
  - Giới hạn dung lượng sau giải nén để chống zip bomb.

### D10. Ngữ nghĩa pipeline (F05 → F08)

- **Thứ tự xử lý** với mỗi row, mỗi field (theo thứ tự schema):
  1. **Map**. `SOURCE_COLUMN` lấy ô tương ứng; `CONSTANT` lấy `constantValue`; field chưa map có giá trị `null`.
  2. **Transformation**, chạy theo `order`.
  3. **Validation.**
  4. **Ép kiểu.**
- **"Rỗng"** nghĩa là `null` hoặc chuỗi toàn khoảng trắng.
- **Transformation**:
  - Mọi transformation trừ `defaultValue` giữ nguyên giá trị rỗng. `defaultValue` thay giá trị rỗng bằng `params.value`.
  - `trim` bỏ khoảng trắng ở hai đầu, kể cả NBSP `U+00A0`.
  - `uppercase` và `lowercase` dùng `Locale.ROOT`.
  - `dateFormat`:
    - Parse theo `params.inputFormat` với `ResolverStyle.STRICT`. Trong pattern, BE tự đổi `y` thành `u` ở ngoài phần được quote.
    - Output theo `params.outputFormat`, mặc định `yyyy-MM-dd`.
    - Không khớp định dạng thì sinh `TRANSFORMATION_FAILED` với `rule=dateFormat` và `step=order`.
  - Khi một transformation lỗi, các transformation và validation còn lại của field đó bị bỏ qua.
- **Validation** của mỗi field chạy theo thứ tự:
  1. `required`, nếu `field.required`.
  2. `type`, luôn chạy.
  3. `email`, rule do user thêm, chỉ trên field kiểu `string`.
  4. `unique`, rule do user thêm.

  Mỗi field **chỉ báo lỗi đầu tiên** gặp phải; một row vẫn có thể có nhiều lỗi ở nhiều field. Field optional mà rỗng thì bỏ qua mọi kiểm tra và output `null`.
- **Luật `type`**:
  - `string`: mọi giá trị.
  - `number`: `-?\d+(\.\d+)?`, ép sang `BigDecimal`. Không nhận dấu phân cách hàng nghìn.
  - `boolean`: `true`/`false`/`1`/`0`, không phân biệt hoa thường.
  - `date`: ISO `uuuu-MM-dd` STRICT, ép sang `LocalDate`.
  - `email`: regex `^[^@\s]+@[^@\s]+\.[^@\s]+$`. Lỗi của nó mang `code=VALIDATION_EMAIL`, `rule=email`.
- **`unique`**:
  - So trên giá trị đã ép kiểu (`BigDecimal.stripTrailingZeros()`, `LocalDate`, `Boolean`, `String` so khớp chính xác).
  - Giá trị rỗng không tính.
  - Bản gặp đầu tiên được giữ.
  - Một giá trị chỉ được **ghi nhận** khi row của nó hợp lệ hoàn toàn: kiểm trong lúc xử lý row, xác nhận sau khi row đã hợp lệ. Nhờ vậy output hợp lệ không bao giờ có giá trị trùng.
- **`sourceValue`** là giá trị trước transformation: giá trị gốc của ô, hoặc `constantValue`; `null` nếu field chưa map.
- **Giá trị trong kết quả**:
  - Row hợp lệ: `values` đã ép kiểu, giống hệt file export.
  - Row lỗi: `values` là chuỗi sau transformation, hoặc `null` ở field có transformation lỗi.
- **Rule suy ra từ schema**:
  - Payload `/validations` chỉ gồm `email` và `unique`.
  - Nếu nhận `required` hoặc `type`: bỏ qua, kèm warning `RULE_IMPLIED_BY_SCHEMA`.
  - `email` trên field kiểu `email`: bỏ qua, kèm warning cùng mã.
  - `email` trên field kiểu `number`, `boolean` hoặc `date`: 422 `CONFIG_INVALID`.
  - `dateFormat` trên field kiểu `date` mà `outputFormat` khác ISO: 422 `CONFIG_INVALID`.
- **Tên field**:
  - Trim, dài 1–100 ký tự, không trùng (không phân biệt hoa thường). `order` không trùng; BE chuẩn hoá lại thành 0..n-1.
  - Đổi tên field được xử lý như xoá rồi thêm.
  - PUT `/schema` prune mọi config của field đã bị xoá, kèm warning `CONFIG_PRUNED`.
  - Đổi kiểu field khỏi `string` thì bỏ rule `email`.
- **Field `required` chưa map**: sinh readiness issue `TARGET_FIELD_REQUIRED`. Gọi `/process` khi đó trả 409 `SESSION_NOT_READY`.
- **Pipeline**: `ImportPipeline` nhận các row dạng stream và một `RowResultSink`, chỉ trả về summary. Không gom row vào RAM (review B1).

### D11. Đồng thời

- Mọi lệnh ghi (PUT, process) lấy một `ReentrantLock` theo session, nằm trong map của JVM (V0.1 chỉ chạy một instance). Các lệnh ghi vào cùng một session vì thế chạy lần lượt.
- Lệnh đọc không lấy khoá. Việc đổi tên nguyên khối ở D7 đảm bảo người đọc không bao giờ thấy kết quả đang ghi dở.
- Cột `version` là lớp bảo vệ thứ hai ở mức DB.

### D12. Dọn dẹp (F11)

- Chạy `@Scheduled` mỗi giờ, và một lần khi khởi động. Xoá session có `updated_at` cũ hơn `IMPORTER_SESSION_TTL` (mặc định `24h`): xoá thư mục storage trước, rồi xoá row trong DB.
- Xoá cả thư mục storage mồ côi (không có row DB) cũ hơn TTL.
- Việc này không vi phạm non-goal "background jobs": non-goal đó nói về xử lý import bất đồng bộ, không phải việc dọn dẹp (review C5).

### D13. Bảo mật và log

- Tên file lưu trữ do server sinh; tên gốc chỉ là metadata đã làm sạch (D5).
- **Log không chứa nội dung file.** Chỉ log `sessionId`, loại file và dung lượng.
- `message` của lỗi không lặp lại giá trị ô; giá trị chỉ nằm trong `sourceValue`.
- **CSV export chống formula injection**: thêm tiền tố `'` khi giá trị bắt đầu bằng `=`, `+`, `-`, `@`, `\t` hoặc `\r`. Áp cho:
  - cột kiểu `string`/`email`;
  - **ô header** (tên field do người dùng đặt);
  - các cột `fieldName`, `rule`, `message`, `sourceValue` của error report.

  Cột số, boolean, ngày đã được validate nên không cần (review B4). Header được thêm vào sau khi fork F10 phát hiện: tên field như `=cmd()` sẽ lọt vào hàng đầu của file CSV.
- `Content-Disposition` dùng `filename*` theo RFC 5987 (qua `ContentDisposition.builder(...).filename(name, UTF_8)`).

### D14. Test

- **Unit**: domain và application, JUnit Jupiter thuần, dùng fake thay cho port.
- **Slice**: `@WebMvcTest` cho controller và định dạng lỗi; `@DataJpaTest` với `@AutoConfigureTestDatabase(replace = NONE)` cho persistence.
- **Integration**: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, Testcontainers `postgres:17` qua `@ServiceConnection`, và `RestClient` để gửi HTTP thật (multipart, 413).
- **ArchUnit**: dùng `com.tngtech.archunit:archunit` 1.4.1 (core) trong test Jupiter thường. Không dùng engine JUnit 5 của ArchUnit, vì dự án đang chạy JUnit 6.
- **Surefire** chạy với `-Duser.timezone=UTC`, vì image `postgres:17` (Debian 13) không có alias `Asia/Saigon` (review A1).
- **Fixture** nằm ở `src/test/resources/fixtures/`.

## API contract V0.1 (chính thức)

| # | Method + path | Request | Thành công | Lỗi chính | Feature |
|---|---|---|---|---|---|
| 1 | `POST /api/import-sessions` | multipart, part `file` | `201` + `Location`, `ImportSessionDto` | 400, 413, 415, 422 `FILE_EMPTY`/`FILE_PARSE_ERROR` | F01 (đọc file từ F02) |
| 2 | `GET /api/import-sessions/{id}` | — | `200 ImportSessionDto` (có `config`/`readiness` từ F04) | 400, 404 | F01 |
| 3 | `GET /api/import-sessions/{id}/preview?limit=50` | — | `200 SourcePreviewDto` | 400, 404 | F02 |
| 4 | `PUT /api/import-sessions/{id}/schema` | `TargetSchemaDto` | `200 ConfigUpdateResponseDto` | 404, 409, 422 `SCHEMA_INVALID` | F04 |
| 5 | `PUT /api/import-sessions/{id}/mapping` | `MappingConfigDto` | `200 ConfigUpdateResponseDto` | 404, 409, 422 `MAPPING_INVALID`/`SOURCE_COLUMN_NOT_FOUND` | F05 |
| 6 | `PUT /api/import-sessions/{id}/transformations` | `TransformationConfigDto` | `200 ConfigUpdateResponseDto` | 404, 409, 422 `CONFIG_INVALID` | F06 |
| 7 | `PUT /api/import-sessions/{id}/validations` | `ValidationConfigDto` | `200 ConfigUpdateResponseDto` | 404, 409, 422 `CONFIG_INVALID` | F07 |
| 8 | `POST /api/import-sessions/{id}/process` | — | `200 PipelineSummaryDto` | 404, 409 `SESSION_NOT_READY`/`SESSION_STATE_INVALID`; đọc file lỗi thì trả 422 `FILE_PARSE_ERROR` (sai cấu trúc) hoặc 500 `INTERNAL_ERROR` (IO, mất file), và session chuyển sang `FAILED` | F08 |
| 9 | `GET /api/import-sessions/{id}/result?view=valid\|invalid&page=0&size=50&field=&code=` | — | `200 PipelineResultDto` | 400, 404, 409 `RESULT_NOT_AVAILABLE` | F09 |
| 10 | `GET /api/import-sessions/{id}/export?format=json\|csv` | — | `200` file stream + `Content-Disposition` | 400, 404, 409, 500 `EXPORT_FAILED` | F10 |
| 11 | `GET /api/import-sessions/{id}/errors/export` | — | `200` file CSV + `Content-Disposition` | 404, 409, 500 `EXPORT_FAILED` | F10 |

```ts
type SessionStatus = 'UPLOADED' | 'CONFIGURING' | 'READY' | 'PROCESSED' | 'FAILED'
type SourceFileType = 'CSV' | 'XLSX'
type FieldType = 'string' | 'number' | 'boolean' | 'date' | 'email'
type MappingType = 'SOURCE_COLUMN' | 'CONSTANT'
type TransformationType = 'trim' | 'uppercase' | 'lowercase' | 'defaultValue' | 'dateFormat'
type UserValidationType = 'email' | 'unique'           // required/type do BE suy ra từ schema

interface ProblemItemDto { field: string | null; code: string; message: string }

interface ApiProblemDto {                               // application/problem+json
  type: string; title: string; status: number; detail?: string; instance?: string
  code: string                                          // luôn có
  errors?: ProblemItemDto[]
}

interface ImportSessionDto {
  id: string
  status: SessionStatus
  originalFileName: string
  fileType: SourceFileType
  sizeBytes: number
  createdAt: string
  updatedAt: string
  config?: { schema: TargetSchemaDto; mapping: MappingConfigDto;
             transformations: TransformationConfigDto; validations: ValidationConfigDto }   // từ F04
  readiness?: { ready: boolean; issues: ProblemItemDto[] }                                 // từ F04
}

interface ConfigUpdateResponseDto { session: ImportSessionDto; warnings: ProblemItemDto[] }  // FE được phép bỏ qua

interface SourcePreviewDto {
  sessionId: string
  fileType: SourceFileType
  sheetName: string | null                              // CSV: null
  columns: { index: number; name: string }[]            // đúng thứ tự gốc; name duy nhất, không rỗng
  rows: { rowNumber: number; values: (string | null)[] }[]   // values[i] ứng với columns[i]; giá trị gốc
  previewLimit: number
  totalRows: number
}

interface TargetSchemaDto { fields: { name: string; type: FieldType; required: boolean; order: number }[] }

interface MappingConfigDto {
  mappings: { targetField: string; mappingType: MappingType;
              sourceColumn: string | null; constantValue: string | null }[]   // chỉ gồm field đã map
}

interface TransformationConfigDto {
  transformations: { targetField: string; order: number; type: TransformationType;
                     params?: Record<string, string> }[]
  // defaultValue: { value }; dateFormat: { inputFormat, outputFormat? = 'yyyy-MM-dd' }; loại khác: không có params
  // order: bắt đầu từ 0, không trùng trong cùng một targetField
}

interface ValidationConfigDto {
  validations: { targetField: string; type: UserValidationType; params?: Record<string, string> }[]
}

interface PipelineSummaryDto {
  sessionId: string; status: SessionStatus
  total: number; valid: number; invalid: number         // total = valid + invalid
  errorCountsByCode: Record<string, number>
  errorCountsByField: Record<string, number>
  processedAt: string
}

interface PipelineResultDto {
  summary: PipelineSummaryDto
  view: 'valid' | 'invalid'
  page: { number: number; size: number; totalElements: number; totalPages: number }
  rows: { rowNumber: number; valid: boolean; values: Record<string, unknown>; errors: ImportErrorDto[] }[]
  // values: key theo thứ tự schema. Row hợp lệ: đã ép kiểu. Row lỗi: chuỗi sau transformation.
}

interface ImportErrorDto {
  rowNumber: number
  fieldName: string
  stage: 'TRANSFORMATION' | 'VALIDATION'
  rule: string                                          // ví dụ 'dateFormat', 'required', 'type', 'email', 'unique'
  step: number | null                                   // order của transformation; null với validation
  code: string                                          // TRANSFORMATION_FAILED | VALIDATION_*
  message: string
  sourceValue: string | null                            // giá trị trước transformation
}
```

**Export (F10):**
- **JSON**: một mảng object; key theo thứ tự schema; giá trị đúng kiểu (number, boolean, ngày dạng chuỗi `yyyy-MM-dd`, `null`).
- **CSV**: header theo thứ tự schema; UTF-8 **có BOM**; chống formula injection theo D13.
- **Error report**: file CSV, mỗi lỗi một dòng, gồm các cột `rowNumber,fieldName,stage,rule,step,code,message,sourceValue`.
- **Tên file**: `<tên-gốc>-valid.json`, `<tên-gốc>-valid.csv`, `<tên-gốc>-errors.csv`.

## Lệch khỏi pack (đã được duyệt ngày 2026-09-25)

1. `ImportPipeline` ghi row qua `RowResultSink` và chỉ trả summary, thay vì gom mọi row vào `PipelineResult` (B1).
2. `ImportError` có thêm `stage`, `rule`, `step`, để đáp ứng FR-06 (B2).
3. Upload đọc toàn bộ file để kiểm và đếm row; thêm cột `source_schema` (C2).
4. Thêm `GET /api/import-sessions/{id}` (C3).
5. Dọn file tạm bằng `@Scheduled` (C5).
6. Làm error envelope từ F01 thay vì F11. Không cấu hình CORS; FE dùng Vite proxy (B8).
7. Bảng mã lỗi: tách lỗi API khỏi lỗi theo row, và bổ sung mã mới (B9, D4).
8. Không có cột `storage_path` (D6).
9. `required` và `type` được suy ra từ schema, không cấu hình như rule riêng (D10).
10. Shape DTO theo bản FE đề xuất.

## Trả lời Open Questions của FE (`apps/web/openspec/changes/fe-import-wizard-v0-1/design.md`)

- **Q1**: Có. Payload validations chỉ gồm `email` và `unique`; BE tự áp `required` và `type` từ schema. Nếu gửi `required` hoặc `type` thì BE bỏ qua và trả warning `RULE_IMPLIED_BY_SCHEMA`.
- **Q2**:
  - Config sai thì trả 422, kèm `code` và `errors[{ field, code, message }]`.
  - JSON hỏng hoặc sai kiểu thì trả 400 `REQUEST_INVALID`.
  - PUT thành công trả `200 { session, warnings }`; FE được phép bỏ qua body.
- **Q3**:
  - Result có phân trang (`view`, `page`, `size`) và lọc (`field`, `code`).
  - Summary có `errorCountsByCode` và `errorCountsByField`.
  - Gọi result trước khi process trả **409 `RESULT_NOT_AVAILABLE`**, không phải `SESSION_NOT_READY`.
- **Q4**: `rowNumber` là số dòng như khi mở file bằng Excel. Header là dòng 1; dòng trống bị bỏ qua nhưng vẫn được đếm số; với XLSX là số dòng của sheet.
- **Q5**:
  - Pattern theo cú pháp `DateTimeFormatter` của Java, parse ở chế độ STRICT (BE tự đổi `yyyy` thành `uuuu`).
  - Sau transformation, kiểu `date` chỉ nhận ISO `yyyy-MM-dd`.
  - `outputFormat` mặc định là ISO. Field kiểu `date` mà đặt output khác ISO thì trả 422 `CONFIG_INVALID`.
- **Q6**: Error report là file CSV với các cột `rowNumber,fieldName,stage,rule,step,code,message,sourceValue`.
- **Q7**:
  - Part có tên `file`.
  - Giới hạn **20MB** qua `IMPORTER_MAX_FILE_SIZE`. FE đặt `VITE_MAX_UPLOAD_MB=20`.
  - Vượt giới hạn trả `413 FILE_TOO_LARGE`. Đã đặt `max-swallow-size=-1` nên FE luôn nhận được 413, không bị reset kết nối.
- **Q8**:
  - Có trường `rule`, và thêm `stage` và `step`.
  - Có đủ các mã FE đề xuất. Thêm các mã `FILE_EMPTY`, `REQUEST_INVALID`, `SESSION_STATE_INVALID`, `RESULT_NOT_AVAILABLE`, `INTERNAL_ERROR` (xem D4).
- **Q9**:
  - Header trùng tên hoặc rỗng được tự đặt lại tên (`Email (2)`, `Column C`), nên `columns[].name` luôn duy nhất và không rỗng.
  - Ô XLSX: số trả về dạng chuỗi plain; ngày trả về dạng ISO `yyyy-MM-dd`; boolean trả về `TRUE`/`FALSE`.
- **Q10**: BE chạy cổng 8080. FE gọi cùng origin qua Vite proxy, nên V0.1 không có CORS.
- **Ngoài 10 câu hỏi**: BE có `GET /api/import-sessions/{id}` trả `config` và `readiness`. FE có thể dùng nó để khôi phục state sau khi tải lại trang (hiện đang là non-goal của FE).

## Risks / Trade-offs

- [Thư viện XLSX chưa được kiểm với ô ngày và sheet ẩn] → F03 bắt đầu bằng spike với file thật xuất từ Excel, LibreOffice và Google Sheets. Nếu không đạt thì dùng Apache POI SAX.
- [`unique` giữ các giá trị đã gặp trong RAM] → Bị chặn trên bởi giới hạn upload 20MB. Về sau có thể chuyển sang lưu trên đĩa hoặc DB.
- [Process chạy đồng bộ, file lớn có thể mất vài giây] → Thời gian bị chặn trên bởi giới hạn upload. Xử lý nền là non-goal của V0.1.
- [Chỉ nhận CSV UTF-8 dùng dấu phẩy; `number` không nhận dấu phân cách hàng nghìn] → Đây là giới hạn đã biết, ghi trong README. Khi lưu từ Excel, chọn "CSV UTF-8".
- [ProblemDetail với Jackson 3 có thể không đưa `code` ra top-level] → Test của F01 kiểm `$.code` ở top-level. Nếu hỏng thì tự serialize.
- [Testcontainers với Docker Desktop trên Windows] → Kiểm ngay ở task đầu của F01.
- [Hai phiên (BE, FE) làm trên cùng một repo] → BE làm trong git worktree `../universal-importer-be`.

## Migration Plan

- Hệ thống mới, chưa có dữ liệu. Flyway tạo schema.
- Mỗi feature làm trên một nhánh `feature/be-fxx-*` theo pack. Merge vào `main` phải hỏi trước.
- Rollback: revert nhánh. Không sửa migration đã merge; có thay đổi thì thêm migration mới.

## Open Questions

- Quy tắc "mỗi field chỉ báo lỗi đầu tiên" (D10) là quyết định mới, chưa được trình bày riêng trong phần brainstorming. Cần xác nhận khi review spec. Nếu đổi sang báo mọi lỗi của field, chỉ phải sửa trong F07.
