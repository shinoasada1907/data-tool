# BE-F10 Export — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task; mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** Tải row hợp lệ dạng JSON/CSV và báo cáo lỗi dạng CSV, dạng stream, với tên file an toàn và lỗi rõ ràng.

**Architecture:**
- `domain/export` giữ port `ValidRowsExporter` / `ErrorReportExporter`, cùng hai lớp logic thuần `CsvFormulaGuard` và `ExportFileName`.
- `infrastructure/export` cài đặt writer JSON (bằng `JsonGenerator` của Jackson 3) và writer CSV (bằng Commons CSV, định dạng RFC 4180).
- `application/export/ExportService` kiểm điều kiện và **mở** nguồn dữ liệu trước (F10-D1), rồi trả `ExportDownload`.
- `api/export/ExportController` bọc kết quả thành `StreamingResponseBody`.

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Web MVC, StreamingResponseBody), Jackson 3, Apache Commons CSV (có sẵn từ be-f02), JUnit Jupiter 6, AssertJ, Mockito, Testcontainers 2.0.5.

**Spec:** `openspec/changes/be-f10-export/specs/data-export/spec.md`. Thiết kế ở `design.md` cùng thư mục (F10-D1…D8) và `openspec/changes/be-f01-import-session/design.md` (D1–D14, API contract V0.1).

## Global Constraints

- Chạy mọi lệnh Maven từ `apps/api`. Viết tắt đường dẫn: `MAIN` = `apps/api/src/main/java/com/universalimporter`, `TEST` = `apps/api/src/test/java/com/universalimporter`.
- Chiều phụ thuộc theo D1:
  - `domain/export` chỉ dùng `java.*` và `domain.*`;
  - `application` không import kiểu web của Spring (`StreamingResponseBody` chỉ có ở `api`).
- Mã lỗi và status: 400 `REQUEST_INVALID`, 404 `SESSION_NOT_FOUND`, 409 `RESULT_NOT_AVAILABLE`, 500 `EXPORT_FAILED`. Mọi kiểm tra xong **trước** byte đầu tiên của file.
- Content-Type: JSON là `application/json`; CSV là `text/csv;charset=UTF-8`. Mọi file CSV bắt đầu bằng BOM `EF BB BF` và tách dòng bằng CRLF.
- Chống formula injection (`= + - @ \t \r`) theo D13:
  - CSV dữ liệu: ô dữ liệu kiểu `STRING`/`EMAIL`, và ô header;
  - báo cáo lỗi: các cột `fieldName`, `rule`, `message`, `sourceValue`.

  JSON không bao giờ bị escape, kể cả key.
- File valid chỉ chứa row có `valid == true` và `errors` rỗng.
- Commit trên nhánh `feature/be-f10-export`, cuối commit message có dòng `Co-Authored-By`. Không push, không merge khi chưa hỏi.

---

## 1. Đối chiếu với F02, F04, F08, F09

**Files:**
- Modify (nếu cần): `openspec/changes/be-f10-export/design.md`, `openspec/changes/be-f10-export/tasks.md`

**Interfaces:**
- Consumes (giả định, chữ ký đầy đủ ở design.md mục "Giả định"):
  - `ResultStore`, `ResultView`, `RowResult`, `ImportError` (F08)
  - `ResultQueryService.requireCurrentSummary(UUID)` (F09)
  - `TargetField`, `FieldType`, `TargetSchemaProvider#currentFields(UUID)` (F04)
  - `org.apache.commons:commons-csv` (F02)
  - `ImportSessionRepository` (F01)
  - `FakeResultStore`, `ImportFlowClient` (TEST/support, từ F09)

- [ ] 1.1 Đọc code F02, F04, F08, F09 đã merge. So tên và chữ ký với phần Consumes. Chỗ nào khác thì sửa design.md và file này theo tên thật (gạch dòng cũ, ghi LÝ DO).
- [ ] 1.2 Xác nhận hai điều kiện:
  - `pom.xml` có `commons-csv`. Nếu thiếu, thêm `org.apache.commons:commons-csv` với version ghi rõ, và ghi LÝ DO.
  - Mapper JSON đã bật `WRITE_BIGDECIMAL_AS_PLAIN` (be-f09 task 1).

## 2. Domain: CsvFormulaGuard và ExportFileName

**Files:**
- Create: `MAIN/domain/export/ExportFormat.java`, `MAIN/domain/export/CsvFormulaGuard.java`, `MAIN/domain/export/ExportFileName.java`
- Test: `TEST/domain/export/CsvFormulaGuardTest.java`, `TEST/domain/export/ExportFileNameTest.java`

**Interfaces:**
- Produces:
  ```java
  public enum ExportFormat { JSON, CSV;
      public static ExportFormat parse(String raw); }   // "json"/"csv", không phân biệt hoa thường;
                                                        // null hoặc giá trị khác → DomainException(REQUEST_INVALID)
  public final class CsvFormulaGuard { public static String escape(String value); }   // null → null
  public final class ExportFileName { public static String of(String originalFileName, String suffix); }
  ```

- [ ] 2.1 Viết `CsvFormulaGuardTest` (parameterized):
  | Input | Output |
  |---|---|
  | `=SUM(A1)` | `'=SUM(A1)` |
  | `+84901234567` | `'+84901234567` |
  | `-5` | `'-5` |
  | `@user` | `'@user` |
  | `"\tx"` | `"'\tx"` |
  | `"\rx"` | `"'\rx"` |
  | `a=b` | `a=b` |
  | `'quoted` | `'quoted` |
  | `""` | `""` |
  | `null` | `null` |
- [ ] 2.2 Viết `ExportFileNameTest` và phần test `ExportFormat.parse`:
  | Input | Output |
  |---|---|
  | `of("customers.csv", "-valid.json")` | `customers-valid.json` |
  | `of("khách hàng.xlsx", "-valid.csv")` | `khách hàng-valid.csv` |
  | `of("report.final.xlsx", "-errors.csv")` | `report.final-errors.csv` |
  | `of("a\"b.csv", "-errors.csv")` | `a_b-errors.csv` |
  | `of("data", "-valid.json")` | `data-valid.json` |
  | `of(".csv", "-valid.json")` | `export-valid.json` |
  | `of("", "-valid.csv")` | `export-valid.csv` |
  | `parse("JSON")`, `parse("csv")` | `JSON`, `CSV` |
  | `parse(null)`, `parse("xml")` | `DomainException(REQUEST_INVALID)` |
- [ ] 2.3 Chạy `./mvnw -q test -Dtest=CsvFormulaGuardTest,ExportFileNameTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 2.4 Tạo 3 class ở phần Interfaces:
  - `escape`: thêm `'` khi ký tự đầu nằm trong `{'=', '+', '-', '@', '\t', '\r'}`.
  - `of`: bỏ đuôi sau dấu `.` cuối cùng (nếu dấu `.` không nằm ở đầu chuỗi), rồi thay `["\\/\p{Cntrl}]` bằng `_`. Phần tên rỗng thì dùng `export`.
- [ ] 2.5 Chạy lại lệnh ở 2.3. Mong đợi: PASS.
- [ ] 2.6 Commit: `feat(domain): CSV formula guard and export file names`

## 3. Writer: JSON, CSV dữ liệu hợp lệ, CSV báo cáo lỗi

**Files:**
- Create:
  - `MAIN/domain/export/ValidRowsExporter.java`, `MAIN/domain/export/ErrorReportExporter.java`
  - `MAIN/infrastructure/export/JsonValidRowsExporter.java`, `MAIN/infrastructure/export/CsvValidRowsExporter.java`, `MAIN/infrastructure/export/CsvErrorReportExporter.java`
- Test: `TEST/infrastructure/export/JsonValidRowsExporterTest.java`, `TEST/infrastructure/export/CsvValidRowsExporterTest.java`, `TEST/infrastructure/export/CsvErrorReportExporterTest.java`, `TEST/support/CsvTestReader.java`

**Interfaces:**
- Consumes: `RowResult`, `ImportError` (F08), `TargetField`, `FieldType` (F04), `CsvFormulaGuard`, `ExportFormat` (task 2).
- Produces:
  ```java
  public interface ValidRowsExporter {
      ExportFormat format();
      String contentType();        // "application/json" | "text/csv;charset=UTF-8"
      String fileSuffix();         // "-valid.json" | "-valid.csv"
      void write(List<TargetField> fields, Stream<RowResult> rows, OutputStream out) throws IOException;
  }
  public interface ErrorReportExporter {
      String contentType();        // "text/csv;charset=UTF-8"
      String fileSuffix();         // "-errors.csv"
      void write(Stream<RowResult> invalidRows, OutputStream out) throws IOException;
  }
  @Component public class JsonValidRowsExporter implements ValidRowsExporter { public JsonValidRowsExporter(JsonMapper mapper); }
  @Component public class CsvValidRowsExporter implements ValidRowsExporter { }
  @Component public class CsvErrorReportExporter implements ErrorReportExporter { }
  // TEST/support: kiểm 3 byte BOM rồi parse phần còn lại bằng CSVFormat.RFC4180
  public final class CsvTestReader { public static List<List<String>> read(byte[] csvWithBom); }
  ```
- Dữ liệu mẫu cho writer của row hợp lệ: `fields` = `name STRING, score NUMBER, active BOOLEAN, dob DATE, note STRING`.
  - `row2` = `RowResult(2, true, {name:"An", score:new BigDecimal("10"), active:true, dob:"1990-12-25", note:null}, [])`
  - `row5` = `RowResult(5, true, {name:"Em", score:new BigDecimal("7.5"), active:false, dob:"1991-01-02", note:"x"}, [])`

- [ ] 3.1 Viết `JsonValidRowsExporterTest`, dùng `JsonMapper.builder().enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).build()`:
  | Rows | Output (so khớp chính xác chuỗi UTF-8) |
  |---|---|
  | không có row | `[]` |
  | `row2` | `[{"name":"An","score":10,"active":true,"dob":"1990-12-25","note":null}]` |
  | `row2, row5` | `[{…row2…},{"name":"Em","score":7.5,"active":false,"dob":"1991-01-02","note":"x"}]` |
  | `row2` nhưng map theo thứ tự `note, dob, active, score, name` | key vẫn theo thứ tự `name, score, active, dob, note` |
  | `row2` thiếu hẳn key `note` | có `"note":null` |
  | `score` = `new BigDecimal("0.0000001")` | chứa `0.0000001`, không chứa `1E-7` |
  | fields `=cmd() STRING`, row `{"=cmd()": "x"}` | `[{"=cmd()":"x"}]` (key không bị escape) |
  | `row2`, `RowResult(3, false, …, [lỗi])`, `row5` | chỉ có row 2 và 5 |
  | stream ném `UncheckedIOException` ở phần tử thứ 2 | exception lan ra ngoài; phần đã ghi không chứa `"code"` |
- [ ] 3.2 Viết `CsvValidRowsExporterTest`. Đọc kết quả bằng `CsvTestReader`, và kiểm thêm chuỗi raw:
  | Rows | Mong đợi |
  |---|---|
  | không có row | raw đúng bằng BOM + `name,score,active,dob,note\r\n` |
  | `row2` | dòng đọc lại là `[An, 10, true, 1990-12-25, ""]`; raw kết thúc bằng `\r\n` |
  | `name` = `Nguyen, An`, `note` = `say "hi"` | raw chứa `"Nguyen, An"` và `"say ""hi"""`; đọc lại đúng giá trị gốc |
  | `name` = `=SUM(A1:A2)` | đọc lại ra `'=SUM(A1:A2)` |
  | `note` = `-abc` | đọc lại ra `'-abc` |
  | `score` = `new BigDecimal("-3")` | đọc lại ra `-3` |
  | `score` = `new BigDecimal("1E+3")` | đọc lại ra `1000` |
  | `note` = `"line1\nline2"` | đọc lại ra `line1\nline2` |
  | fields `email EMAIL`, giá trị `=cmd@x.io` | đọc lại ra `'=cmd@x.io` |
  | fields `=cmd() STRING, score NUMBER`, row `{"=cmd()": "x", score: new BigDecimal("1")}` | header đọc lại ra `['=cmd(), score]`; dòng dữ liệu ra `[x, 1]` |
  | có một `RowResult` với `valid=false` | row đó không có trong file |
- [ ] 3.3 Viết `CsvErrorReportExporterTest`. Row lỗi mẫu:
  - `row3`: lỗi `(3, "email", "VALIDATION", "email", null, "VALIDATION_EMAIL", "Not a valid email address", " ABC ")`
  - `row4`: lỗi `(4, "dob", "TRANSFORMATION", "dateFormat", 0, "TRANSFORMATION_FAILED", "Does not match pattern dd/MM/yyyy", "31/02/2024")`, rồi `(4, "score", "VALIDATION", "type", null, "VALIDATION_TYPE", "Not a number", "x")`

  | Rows | Mong đợi |
  |---|---|
  | không có row | raw đúng bằng BOM + `rowNumber,fieldName,stage,rule,step,code,message,sourceValue\r\n` |
  | `row3, row4` | đọc lại (bỏ header) ra đúng 3 dòng: `[3,email,VALIDATION,email,,VALIDATION_EMAIL,Not a valid email address, ABC ]`, `[4,dob,TRANSFORMATION,dateFormat,0,TRANSFORMATION_FAILED,Does not match pattern dd/MM/yyyy,31/02/2024]`, `[4,score,VALIDATION,type,,VALIDATION_TYPE,Not a number,x]` |
  | `sourceValue` = `+84901234567` | ô `sourceValue` đọc lại ra `'+84901234567` |
  | `message` = `-bad` | ô `message` đọc lại ra `'-bad` |
  | `fieldName` = `=cmd()` | ô `fieldName` đọc lại ra `'=cmd()` |
  | `rule` = `-rule` | ô `rule` đọc lại ra `'-rule` |
  | `sourceValue` = null | ô `sourceValue` đọc lại ra `""` |
- [ ] 3.4 Chạy `./mvnw -q test -Dtest=JsonValidRowsExporterTest,CsvValidRowsExporterTest,CsvErrorReportExporterTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 3.5 Tạo 2 port và 3 writer theo F10-D3, F10-D4, F10-D5:
  - Writer JSON dùng `mapper.createGenerator(out)` và đi theo `fields`.
  - Writer CSV ghi BOM trước, rồi dùng `new CSVPrinter(new OutputStreamWriter(out, UTF_8), CSVFormat.RFC4180)`, và `flush()` ở cuối.
  - Row không hợp lệ bị bỏ qua, kèm `log.warn` có `rowNumber`.
  - `CsvFormulaGuard.escape` áp cho: ô header và ô dữ liệu kiểu `STRING`/`EMAIL` của CSV dữ liệu; các cột `fieldName`, `rule`, `message`, `sourceValue` của báo cáo lỗi.
- [ ] 3.6 Chạy lại lệnh ở 3.4. Mong đợi: PASS.
- [ ] 3.7 Commit: `feat(infra): JSON and CSV exporters with formula-injection guard`

## 4. Use case: ExportService (kiểm và mở nguồn trước khi stream)

**Files:**
- Create: `MAIN/application/export/ExportBody.java`, `MAIN/application/export/ExportDownload.java`, `MAIN/application/export/ExportService.java`
- Test: `TEST/application/export/ExportServiceTest.java`. Mở rộng `TEST/support/FakeResultStore.java` (của F09) với cờ `failOnOpen` và biến `closed`.

**Interfaces:**
- Consumes: `ResultQueryService.requireCurrentSummary` (F09), `ResultStore`, `ResultView` (F08), `TargetSchemaProvider` (F04), `ImportSessionRepository` (F01), `ValidRowsExporter`, `ErrorReportExporter`, `ExportFileName` (tasks 2–3).
- Produces:
  ```java
  @FunctionalInterface public interface ExportBody { void writeTo(OutputStream out) throws IOException; }
  public record ExportDownload(String fileName, String contentType, ExportBody body) {}
  @Service public class ExportService {
      public ExportService(ResultQueryService results, ResultStore store, TargetSchemaProvider schema,
                           ImportSessionRepository sessions, List<ValidRowsExporter> validExporters,
                           ErrorReportExporter errorExporter);
      public ExportDownload prepareValidRows(UUID sessionId, ExportFormat format);   // đọc view VALID
      public ExportDownload prepareErrorReport(UUID sessionId);                      // đọc view INVALID
      // requireCurrentSummary ném 404/409 → ném lại nguyên vẹn
      // mở stream thất bại → DomainException(EXPORT_FAILED, "Export could not be started.")
      // body.writeTo luôn đóng stream nguồn trong finally
  }
  ```

- [ ] 4.1 Viết `ExportServiceTest`:
  - `ResultQueryService` là mock Mockito.
  - `FakeResultStore` có sẵn row 2 hợp lệ và row 3 lỗi.
  - Session có `originalFileName` = `customers.csv`.
  - Các writer là bản thật của task 3.

  | Case | Mong đợi |
  |---|---|
  | `prepareValidRows(id, JSON)` rồi `body.writeTo(buf)` | `fileName` = `customers-valid.json`; `contentType` = `application/json`; `buf` chỉ có row 2; sau đó `store.closed` = true |
  | `prepareValidRows(id, CSV)` | `fileName` = `customers-valid.csv`; `contentType` = `text/csv;charset=UTF-8` |
  | `requireCurrentSummary` ném `DomainException(RESULT_NOT_AVAILABLE)` | ném lại đúng exception đó; không gọi `store.readRows` |
  | `store.failOnOpen` = true | `DomainException(EXPORT_FAILED)` |
  | `prepareErrorReport(id)` rồi `writeTo` | `fileName` = `customers-errors.csv`; nội dung là báo cáo lỗi của row 3; store được đọc với view `INVALID` |
- [ ] 4.2 Chạy `./mvnw -q test -Dtest=ExportServiceTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 4.3 Tạo 3 class theo F10-D1. Chọn exporter theo `format()`. Gọi `readRows` ngay trong `prepare…()`, không để dành tới lúc chạy `writeTo`.
- [ ] 4.4 Chạy lại lệnh ở 4.2. Mong đợi: PASS.
- [ ] 4.5 Commit: `feat(app): prepare exports and fail fast before streaming`

## 5. API: ExportController và timeout async

**Files:**
- Create: `MAIN/api/export/ExportController.java`
- Modify: `apps/api/src/main/resources/application.yaml` (thêm `spring.mvc.async.request-timeout: 5m`)
- Test: `TEST/api/export/ExportControllerTest.java`

**Interfaces:**
- Consumes: `ExportService`, `ExportDownload`, `ExportFormat.parse` (tasks 2, 4), `GlobalExceptionHandler` (F01).
- Produces:
  ```java
  // GET /api/import-sessions/{id}/export?format=json|csv → ResponseEntity<StreamingResponseBody>
  // GET /api/import-sessions/{id}/errors/export         → ResponseEntity<StreamingResponseBody>
  // Header: Content-Type = download.contentType();
  //         Content-Disposition = ContentDisposition.attachment().filename(download.fileName(), UTF_8).build()
  ```

- [ ] 5.1 Viết `ExportControllerTest` với `@WebMvcTest(ExportController.class)` và `@MockitoBean ExportService`. Với response 200 thì dùng `request().asyncStarted()` rồi `asyncDispatch(...)`.
  | Request | Stub | Mong đợi |
  |---|---|---|
  | `GET …/export?format=json` | trả `ExportDownload("customers-valid.json", "application/json", out -> out.write("[]".getBytes()))` | 200; `Content-Type` = `application/json`; `Content-Disposition` chứa `attachment` và `filename*=UTF-8''customers-valid.json`; body = `[]` |
  | `GET …/export?format=CSV` | trả download CSV | service được gọi với `ExportFormat.CSV` |
  | `GET …/export` | — | 400; `$.code` = `REQUEST_INVALID`; service không được gọi |
  | `GET …/export?format=xml` | — | 400; `$.code` = `REQUEST_INVALID` |
  | `GET …/export?format=json` | ném `DomainException(RESULT_NOT_AVAILABLE, …)` | 409; `$.code` = `RESULT_NOT_AVAILABLE` |
  | `GET …/export?format=json` | ném `DomainException(EXPORT_FAILED, …)` | 500; `$.code` = `EXPORT_FAILED` |
  | `GET …/errors/export` | trả `ExportDownload("khách hàng-errors.csv", "text/csv;charset=UTF-8", …)` | 200; `Content-Disposition` chứa `filename*=UTF-8''kh%C3%A1ch%20h%C3%A0ng-errors.csv` |
  | `GET …/errors/export` | ném `DomainException(SESSION_NOT_FOUND, …)` | 404; `$.code` = `SESSION_NOT_FOUND` |
- [ ] 5.2 Chạy `./mvnw -q test -Dtest=ExportControllerTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 5.3 Tạo `ExportController`:
  - `format` nhận dạng `@RequestParam(required = false) String`, rồi gọi `ExportFormat.parse`.
  - `StreamingResponseBody` gọi `download.body().writeTo(out)`. Gặp `IOException` hoặc `RuntimeException` thì `log.error("Export stream failed for session {}", id, e)` rồi ném lại.

  Thêm `spring.mvc.async.request-timeout: 5m` vào `application.yaml`.
- [ ] 5.4 Chạy lại lệnh ở 5.2. Mong đợi: PASS.
- [ ] 5.5 Commit: `feat(api): export and error report download endpoints`

## 6. Integration test qua HTTP thật

**Files:**
- Test: `TEST/api/export/ExportApiIntegrationTest.java`

**Interfaces:**
- Consumes: toàn bộ app (F01–F09), `TestcontainersConfiguration` (F01), `ImportFlowClient` (F09), `CsvTestReader` (task 3).
- Dùng `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Import(TestcontainersConfiguration.class)`, và `static @TempDir storageDir` gắn vào `importer.storage.dir`.
- File `customers.csv` dùng trong test:
  ```
  name,email,score,note
  An,an@x.com,10,"=HYPERLINK(""http://x"")"
  Binh,not-an-email,5,
  Chi,chi@x.com,-3,ok
  ```
- Config:
  - schema `[{name,string,true,0},{email,email,true,1},{score,number,false,2},{note,string,false,3}]`
  - mapping: 4 field `SOURCE_COLUMN`
  - transformations và validations rỗng

- [ ] 6.1 Viết `ExportApiIntegrationTest`:
  | Case | Mong đợi |
  |---|---|
  | `GET /export?format=json` trước khi process | 409; `code` = `RESULT_NOT_AVAILABLE` |
  | `GET /errors/export` trước khi process | 409; `code` = `RESULT_NOT_AVAILABLE` |
  | process, rồi `GET /export?format=json` | 200; `Content-Type` = `application/json`; `Content-Disposition` chứa `filename*=UTF-8''customers-valid.json`; body parse ra 2 object: `{name:"An",email:"an@x.com",score:10,note:"=HYPERLINK(\"http://x\")"}` và `{name:"Chi",email:"chi@x.com",score:-3,note:"ok"}` |
  | `GET /export?format=csv` | 200; body bắt đầu bằng BOM; `CsvTestReader` đọc ra: header `[name,email,score,note]`, `[An,an@x.com,10,'=HYPERLINK("http://x")]`, `[Chi,chi@x.com,-3,ok]`; body không chứa `not-an-email` |
  | `GET /errors/export` | 200; `Content-Disposition` chứa `customers-errors.csv`; đọc ra đúng 1 dòng dữ liệu: `[3,email,VALIDATION,email,,VALIDATION_EMAIL,<message>,not-an-email]` |
  | `GET /export?format=xml` | 400; `code` = `REQUEST_INVALID` |
  | `GET /api/import-sessions/{randomUUID}/export?format=csv` | 404; `code` = `SESSION_NOT_FOUND` |
  | `GET /api/import-sessions/{randomUUID}/errors/export` | 404; `code` = `SESSION_NOT_FOUND` |
  | xoá `storageDir/{id}/result/valid.ndjson`, rồi `GET /export?format=json` | 500; `Content-Type` chứa `application/problem+json`; `code` = `EXPORT_FAILED` |
- [ ] 6.2 Chạy `./mvnw -q test -Dtest=ExportApiIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính (không nới lỏng test), rồi chạy lại.
- [ ] 6.3 Commit: `test(api): export endpoints end-to-end`

## 7. Kiểm tra toàn bộ và hoàn tất

- [ ] 7.1 Chạy `./mvnw -q verify`. Mong đợi: mọi test xanh, gồm cả ArchitectureTest (`domain/export` chỉ dùng JDK).
- [ ] 7.2 Chạy app thật. Upload, cấu hình, process, rồi:
  - `curl -OJ "localhost:8080/api/import-sessions/{id}/export?format=csv"`: mở file bằng Excel hoặc LibreOffice, kiểm tiếng Việt hiển thị đúng và ô `'=…` không chạy công thức;
  - `curl -OJ ".../errors/export"`.
- [ ] 7.3 Tick đủ checkbox; chỗ nào làm khác kế hoạch thì gạch và ghi LÝ DO. Commit: `docs(openspec): complete be-f10 tasks`
- [ ] 7.4 Hỏi người dùng trước khi merge vào `main`. Sau khi merge: `openspec archive be-f10-export -y`, commit phần archive.
