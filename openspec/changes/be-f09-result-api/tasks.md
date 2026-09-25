# BE-F09 Result Preview & Error Report — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task; mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** `GET /api/import-sessions/{id}/result` trả summary và một trang row hợp lệ hoặc row lỗi, có lọc, đúng API contract V0.1.

**Architecture:** `ResultController` (api) parse và kiểm query param, gọi `ResultQueryService` (application). Service kiểm điều kiện có kết quả (F09-D4), rồi đọc stream từ `ResultStore` của F08, lọc và phân trang (F09-D2, F09-D3). Cuối cùng DTO trả `values` theo thứ tự schema, số ghi dạng plain (F09-D5).

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Web MVC), Jackson 3, JUnit Jupiter 6, AssertJ, Mockito, Testcontainers 2.0.5.

**Spec:** `openspec/changes/be-f09-result-api/specs/import-result/spec.md`. Thiết kế ở `design.md` cùng thư mục (F09-D1…D5) và `openspec/changes/be-f01-import-session/design.md` (D1–D14, API contract V0.1).

## Global Constraints

- Chạy mọi lệnh Maven từ `apps/api`. Viết tắt đường dẫn: `MAIN` = `apps/api/src/main/java/com/universalimporter`, `TEST` = `apps/api/src/test/java/com/universalimporter`.
- Chiều phụ thuộc theo D1. `application` không import `api`; `domain` là Java thuần.
- Mã lỗi và status đúng D4: 400 `REQUEST_INVALID`, 404 `SESSION_NOT_FOUND`, 409 `RESULT_NOT_AVAILABLE`.
- Mặc định `view=valid`, `page=0`, `size=50`. `size` trong khoảng 1–200; `page` ≥ 0.
- Lệnh đọc không lấy khoá theo session (D11). Mọi `Stream` đọc từ `ResultStore` phải đóng bằng try-with-resources.
- Jackson 3 (`tools.jackson.*`); số `BigDecimal` ghi dạng plain.
- Commit trên nhánh `feature/be-f09-result-api`, cuối commit message có dòng `Co-Authored-By`. Không push, không merge khi chưa hỏi.

---

## 1. Đối chiếu với F08 và cấu hình ghi số dạng plain

**Files:**
- Modify (nếu cần): `openspec/changes/be-f09-result-api/design.md`, `openspec/changes/be-f09-result-api/tasks.md`
- Create (nếu F08 chưa làm): `MAIN/infrastructure/config/JacksonConfig.java`
- Test (nếu F08 chưa làm): `TEST/infrastructure/config/JacksonConfigTest.java`

**Interfaces:**
- Consumes (giả định, từ F08): `ResultStore`, `ResultView`, `ResultSummary`, `RowResult`, `ImportError`, `PipelineSummaryDto`, và cách lấy hash config hiện tại (trong tasks này gọi là `ConfigHashProvider#currentConfigHash(UUID)`). Chữ ký đầy đủ ở design.md, mục "Giả định".
- Produces: `JacksonConfig` với `@Bean JsonMapperBuilderCustomizer` bật `StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN`.

- [ ] 1.1 Đọc code F08 đã merge. So tên và chữ ký của 7 thành phần ở phần Consumes với design.md. Chỗ nào khác thì sửa design.md và file này theo tên thật (gạch dòng cũ, ghi LÝ DO).
- [ ] 1.2 Kiểm xem mapper JSON của app đã bật `WRITE_BIGDECIMAL_AS_PLAIN` chưa: grep `WRITE_BIGDECIMAL_AS_PLAIN` trong `MAIN`. Đã có thì gạch các mục 1.3–1.6 và ghi "F08 đã làm".
- [ ] 1.3 Viết `JacksonConfigTest` với `@SpringBootTest` và `@Import(TestcontainersConfiguration.class)`. Inject `JsonMapper`:
  | Input | Mong đợi |
  |---|---|
  | `writeValueAsString(Map.of("v", new BigDecimal("0.0000001")))` | `{"v":0.0000001}` |
  | `writeValueAsString(Map.of("v", new BigDecimal("1E+3")))` | `{"v":1000}` |
- [ ] 1.4 Chạy `./mvnw -q test -Dtest=JacksonConfigTest`. Mong đợi: FAIL, vì ra `1E-7`.
- [ ] 1.5 Tạo `JacksonConfig`: `@Configuration`, `@Bean JsonMapperBuilderCustomizer plainBigDecimal()`, trong đó gọi `builder.enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)`. Chạy lại lệnh ở 1.4. Mong đợi: PASS.
- [ ] 1.6 Commit: `feat(infra): write BigDecimal as plain numbers in JSON`

## 2. Use case: truy vấn kết quả (lọc, phân trang, điều kiện có kết quả)

**Files:**
- Create: `MAIN/application/result/ResultQuery.java`, `MAIN/application/result/ResultPage.java`, `MAIN/application/result/ResultQueryService.java`
- Test: `TEST/application/result/ResultQueryServiceTest.java`, `TEST/support/FakeResultStore.java`

**Interfaces:**
- Consumes: `ImportSessionRepository` (F01), `ResultStore`, `ResultView`, `ResultSummary`, `RowResult`, `ImportError`, `ConfigHashProvider` (F08, đã đối chiếu ở task 1), `DomainException`, `ErrorCode` (F01).
- Produces:
  ```java
  public record ResultQuery(ResultView view, int page, int size, String field, String code) {}
      // field/code: null nghĩa là không lọc; caller đã kiểm khoảng giá trị
  public record ResultPage(ResultSummary summary, SessionStatus status, ResultView view, int page, int size,
                           long totalElements, int totalPages, List<RowResult> rows) {}
  @Service public class ResultQueryService {
      public ResultQueryService(ImportSessionRepository sessions, ResultStore results, ConfigHashProvider hashes);
      public ResultPage query(UUID sessionId, ResultQuery query);
      public ResultSummary requireCurrentSummary(UUID sessionId);   // kiểm theo F09-D4; be-f10 dùng lại
      // không có session → SESSION_NOT_FOUND
      // status ≠ PROCESSED, không có summary, hoặc hash lệch → RESULT_NOT_AVAILABLE
  }
  ```

- [ ] 2.1 Viết `FakeResultStore`: giữ `summary`, `valid` và `invalid` là danh sách `RowResult` theo `rowNumber`. Có bộ đếm `rowsRead`, tăng mỗi khi stream phát ra một row. Viết `ResultQueryServiceTest` với dữ liệu mẫu:
  - Session `PROCESSED`; `ConfigHashProvider` trả `"h1"`.
  - Summary: total 5, valid 2, invalid 3; `errorCountsByCode` = `{VALIDATION_EMAIL:1, TRANSFORMATION_FAILED:1, VALIDATION_TYPE:1, VALIDATION_UNIQUE:1}`; `errorCountsByField` = `{email:2, dob:1, score:1}`; `configHash` = `"h1"`.
  - Row hợp lệ: 2 và 5.
  - Row lỗi:
    - row 3: `[email/VALIDATION_EMAIL]`
    - row 4: `[dob/TRANSFORMATION_FAILED step 0, score/VALIDATION_TYPE]`
    - row 6: `[email/VALIDATION_UNIQUE]`

  | Query | Mong đợi |
  |---|---|
  | `VALID, 0, 50, null, null` | `rows` = [2, 5]; `totalElements` = 2; `totalPages` = 1 |
  | `INVALID, 0, 2, null, null` | `rows` = [3, 4]; `totalElements` = 3; `totalPages` = 2 |
  | `INVALID, 1, 2, null, null` | `rows` = [6] |
  | `INVALID, 5, 2, null, null` | `rows` = []; `totalElements` = 3; `totalPages` = 2 |
  | `INVALID, 0, 1, null, null` | `rows` = [3]; `rowsRead` = 1 (dừng quét sớm; `totalElements` lấy từ summary) |
  | `INVALID, 0, 50, "email", null` | `rows` = [3, 6]; `totalElements` = 2 |
  | `INVALID, 0, 50, null, "VALIDATION_TYPE"` | `rows` = [4]; row 4 vẫn có đủ 2 lỗi |
  | `INVALID, 0, 50, "email", "VALIDATION_TYPE"` | `rows` = []; `totalElements` = 0; `totalPages` = 0 |
  | `INVALID, 0, 50, "dob", "TRANSFORMATION_FAILED"` | `rows` = [4] |
  | `INVALID, 0, 50, null, "NOPE"` | `rows` = []; không ném lỗi |
  | `VALID, 0, 50, "email", null` | `rows` = [2, 5] (bộ lọc bị bỏ qua) |
  | Session `READY` | `DomainException(RESULT_NOT_AVAILABLE)` |
  | Session `FAILED` | `DomainException(RESULT_NOT_AVAILABLE)` |
  | `PROCESSED` nhưng store không có summary | `DomainException(RESULT_NOT_AVAILABLE)` |
  | `PROCESSED`, summary có hash `"h1"`, provider trả `"h2"` | `DomainException(RESULT_NOT_AVAILABLE)` |
  | Không có session | `DomainException(SESSION_NOT_FOUND)` |
  | Gọi `INVALID, 0, 50, null, null` hai lần | hai `rows` bằng nhau, cùng thứ tự |
  | `requireCurrentSummary(id)` với dữ liệu mẫu | trả đúng summary; `rowsRead` = 0 (không đọc row nào) |
- [ ] 2.2 Chạy `./mvnw -q test -Dtest=ResultQueryServiceTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 2.3 Tạo `ResultQuery`, `ResultPage`, `ResultQueryService`:
  - `requireCurrentSummary` kiểm điều kiện theo đúng thứ tự F09-D4. `query` gọi nó trước tiên.
  - Không có bộ lọc hiệu lực (view `VALID`, hoặc cả `field` lẫn `code` là null): `totalElements` lấy từ summary, dùng `skip(page*size).limit(size)` trên stream.
  - Có bộ lọc: quét cả stream một lần, đếm tổng và gom đúng trang.
  - Stream đóng bằng try-with-resources.
- [ ] 2.4 Chạy lại lệnh ở 2.2. Mong đợi: PASS (18 case).
- [ ] 2.5 Commit: `feat(app): query processed results with paging and error filters`

## 3. API: controller, DTO và kiểm query param

**Files:**
- Create: `MAIN/api/result/ResultController.java`, `MAIN/api/result/PipelineResultDto.java`, `MAIN/api/result/PageDto.java`, `MAIN/api/result/ResultRowDto.java`, `MAIN/api/result/ImportErrorDto.java`
- Test: `TEST/api/result/ResultControllerTest.java`

**Interfaces:**
- Consumes: `ResultQueryService`, `ResultQuery`, `ResultPage` (task 2), `PipelineSummaryDto` (F08), `GlobalExceptionHandler` (F01).
- Produces:
  ```java
  public record PipelineResultDto(PipelineSummaryDto summary, String view, PageDto page, List<ResultRowDto> rows) {}
  public record PageDto(int number, int size, long totalElements, int totalPages) {}
  public record ResultRowDto(int rowNumber, boolean valid, Map<String, Object> values, List<ImportErrorDto> errors) {}
  public record ImportErrorDto(int rowNumber, String fieldName, String stage, String rule, Integer step,
                               String code, String message, String sourceValue) {}
  // GET /api/import-sessions/{id}/result?view=&page=&size=&field=&code=
  //   view: "valid" | "invalid" (không phân biệt hoa thường); trong JSON trả về viết thường
  ```

- [ ] 3.1 Viết `ResultControllerTest` với `@WebMvcTest(ResultController.class)` và `@MockitoBean ResultQueryService`. Stub mặc định trả một `ResultPage` có 1 row lỗi (row 3): `values` = `{email: "abc", score: new BigDecimal("0.0000001")}`, 1 lỗi `VALIDATION/email/null/VALIDATION_EMAIL`, `sourceValue` là `" ABC "`.
  | Request | Mong đợi |
  |---|---|
  | `GET /api/import-sessions/{id}/result` | service nhận `ResultQuery(VALID, 0, 50, null, null)` |
  | `…/result?view=INVALID&page=2&size=10&field=email&code=VALIDATION_EMAIL` | service nhận `ResultQuery(INVALID, 2, 10, "email", "VALIDATION_EMAIL")` |
  | `…/result?view=invalid&field=&code=%20` | service nhận `field` = null, `code` = null |
  | `…/result?size=0` | 400; `$.code` = `REQUEST_INVALID`; service không được gọi |
  | `…/result?size=201` | 400; `$.code` = `REQUEST_INVALID` |
  | `…/result?page=-1` | 400; `$.code` = `REQUEST_INVALID` |
  | `…/result?page=x` | 400; `$.code` = `REQUEST_INVALID` |
  | `…/result?view=all` | 400; `$.code` = `REQUEST_INVALID` |
  | `…/result?view=invalid` (stub mặc định) | 200; `$.view` = `invalid`; `$.page.number`, `$.page.size`, `$.page.totalElements`, `$.page.totalPages` khớp stub; `$.rows[0].rowNumber` = 3; `$.rows[0].valid` = false; `$.rows[0].values.email` = `abc`; `$.rows[0].errors[0].stage` = `VALIDATION`; `$.rows[0].errors[0].step` là null; `$.rows[0].errors[0].sourceValue` = `" ABC "`; `$.summary.errorCountsByCode.VALIDATION_EMAIL` khớp stub |
  | như trên | nội dung response chứa `0.0000001`, không chứa `1E-7` |
  | stub `values` = LinkedHashMap `name, email, score` | các key trong `$.rows[0].values` theo đúng thứ tự `name, email, score` |
  | service ném `DomainException(RESULT_NOT_AVAILABLE, …)` | 409; `$.code` = `RESULT_NOT_AVAILABLE` |
  | service ném `DomainException(SESSION_NOT_FOUND, …)` | 404; `$.code` = `SESSION_NOT_FOUND` |
- [ ] 3.2 Chạy `./mvnw -q test -Dtest=ResultControllerTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 3.3 Tạo controller và 4 DTO:
  - Controller nhận `page`/`size` là `int` có `defaultValue`. Nếu không phải số, F01 đã trả 400 `REQUEST_INVALID` qua lỗi type mismatch.
  - Kiểm khoảng giá trị và parse `view` thủ công; sai thì ném `DomainException(REQUEST_INVALID, …)`.
  - Chuỗi blank ở `field`/`code` được đổi thành `null`.
  - `ResultPage` được map sang `PipelineResultDto`; `summary` dùng mapper của F08.
- [ ] 3.4 Chạy lại lệnh ở 3.2. Mong đợi: PASS.
- [ ] 3.5 Commit: `feat(api): result endpoint with paging and filters`

## 4. Integration test qua HTTP thật

**Files:**
- Create: `TEST/support/ImportFlowClient.java`. Nếu F08 đã có helper tương đương thì dùng lại, và gạch mục này kèm LÝ DO.
- Test: `TEST/api/result/ResultApiIntegrationTest.java`

**Interfaces:**
- Consumes: toàn bộ app (F01–F08), `TestcontainersConfiguration` (F01).
- Produces:
  ```java
  public final class ImportFlowClient {                       // bọc RestClient tới http://localhost:{port}
      public ImportFlowClient(int port);
      public UUID upload(String fileName, byte[] content);    // yêu cầu 201, trả id
      public void put(UUID id, String section, String json);  // section: schema|mapping|transformations|validations; yêu cầu 200
      public JsonNode process(UUID id);                       // yêu cầu 200
      public ResponseEntity<String> get(String path);         // không ném lỗi với 4xx/5xx
  }
  ```

- [ ] 4.1 Viết `ResultApiIntegrationTest` với `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Import(TestcontainersConfiguration.class)`, và `static @TempDir` cho `importer.storage.dir`. File CSV dùng trong test:
  ```
  name,email,score
  An,an@x.com,10
  Binh,not-an-email,5
  Chi,chi@x.com,abc
  ```
  Config:
  - schema `[{name, string, required, 0}, {email, email, required, 1}, {score, number, optional, 2}]`
  - mapping: 3 field `SOURCE_COLUMN`, trùng tên cột
  - transformations và validations đều rỗng

  | Case | Mong đợi |
  |---|---|
  | `GET /result` sau upload và cấu hình, trước khi process | 409; `code` = `RESULT_NOT_AVAILABLE` |
  | process, rồi `GET /result?view=valid` | 200; `rows` = [row 2]; `values` = `{"name":"An","email":"an@x.com","score":10}`; `summary.total` = 3, `valid` = 1, `invalid` = 2 |
  | `GET /result?view=invalid` | `rows` = [3, 4]; row 3 có lỗi `VALIDATION_EMAIL` ở `email`; row 4 có lỗi `VALIDATION_TYPE` ở `score` với `sourceValue` = `abc` |
  | `GET /result?view=invalid&code=VALIDATION_TYPE` | `rows` = [4]; `page.totalElements` = 1 |
  | PUT schema mới (đổi `score` thành required), rồi `GET /result` | 409; `code` = `RESULT_NOT_AVAILABLE` |
- [ ] 4.2 Chạy `./mvnw -q test -Dtest=ResultApiIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính (không nới lỏng test), rồi chạy lại.
- [ ] 4.3 Commit: `test(api): result endpoint end-to-end`

## 5. Kiểm tra toàn bộ và hoàn tất

- [ ] 5.1 Chạy `./mvnw -q verify`. Mong đợi: mọi test xanh, gồm cả ArchitectureTest.
- [ ] 5.2 Chạy app thật: upload, cấu hình, process với curl, rồi `curl "localhost:8080/api/import-sessions/{id}/result?view=invalid&size=1"`. Kiểm `page.totalPages` đúng, và JSON có `errors[].stage`, `rule`, `step`.
- [ ] 5.3 Tick đủ checkbox; chỗ nào làm khác kế hoạch thì gạch và ghi LÝ DO. Commit: `docs(openspec): complete be-f09 tasks`
- [ ] 5.4 Hỏi người dùng trước khi merge vào `main`. Sau khi merge: `openspec archive be-f09-result-api -y`, commit phần archive.
