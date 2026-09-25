# BE-F02 Source Preview & CSV Parser — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** Upload CSV đọc hết file, lưu source schema và chuyển sang `CONFIGURING`; `GET /preview` trả header cùng dữ liệu mẫu đúng contract.

**Architecture:** Port `SourceParser` (`inspect` + `read`) và model nguồn nằm trong `domain.source`. `CsvSourceParser` (Commons CSV) nằm trong `infrastructure.parser`. `ImportSessionService.upload` gọi `inspect` và lưu `SourceSchema` vào cột jsonb. `SourcePreviewService` đọc lại file bằng `read().limit(n)`.

**Tech Stack:** Như F01, thêm `org.apache.commons:commons-csv:1.14.1`.

**Spec:** `openspec/changes/be-f02-csv-preview/specs/source-parsing/spec.md`. Thiết kế: `design.md` cùng thư mục (P1–P8) và `openspec/changes/be-f01-import-session/design.md` (D1–D14).

## Global Constraints

- Mọi ràng buộc chung của F01 (`openspec/changes/be-f01-import-session/tasks.md`, mục Global Constraints) vẫn áp dụng: `MAIN`/`TEST`, Jackson 3, D1, D4, micro giây, UTC, commit có `Co-Authored-By`.
- F01 phải đã xong và nằm trong nhánh này (tạo nhánh `feature/be-f02-csv-preview` từ nhánh có F01).
- Message lỗi không chứa giá trị ô (D13).
- `limit` của preview: mặc định 50, trong khoảng 1–200 (D3).

---

## 1. Model nguồn và luật đặt tên cột

**Files:**
- Create: `MAIN/domain/source/SourceColumn.java`, `SourceSchema.java`, `ImportRow.java`, `SourceParser.java`, `ColumnNames.java`
- Test: `TEST/domain/source/ColumnNamesTest.java`, `TEST/domain/source/ImportRowTest.java`

**Interfaces:**
- Produces:
  ```java
  public record SourceColumn(int index, String name) {}
  public record SourceSchema(List<SourceColumn> columns, long totalRows, String sheetName) {}   // sheetName null với CSV
  public record ImportRow(long rowNumber, List<String> values) {
      public String value(int index);           // index >= values.size() → null
  }
  public interface SourceParser {
      boolean supports(SourceFileType type);
      SourceSchema inspect(InputStream input);
      Stream<ImportRow> read(InputStream input);
  }
  public final class ColumnNames {
      public static List<SourceColumn> normalize(List<String> rawHeaders);
      public static String excelLetters(int index);   // 0 → "A", 25 → "Z", 26 → "AA", 701 → "ZZ", 702 → "AAA"
      public static boolean isBlankRow(List<String> values);   // mọi phần tử null hoặc isBlank()
  }
  ```

- [ ] 1.1 Viết `ColumnNamesTest`:
  | Input `normalize` | Tên mong đợi |
  |---|---|
  | `["name","email"]` | `["name","email"]` |
  | `[" name "]` | `["name"]` |
  | `["Email","email"]` | `["Email","email (2)"]` |
  | `["", " ", "x"]` | `["Column A","Column B","x"]` |
  | `["a","a","a"]` | `["a","a (2)","a (3)"]` |
  | `["a (2)","a","a"]` | `["a (2)","a","a (3)"]` |
  | 27 chuỗi rỗng | phần tử cuối là `"Column AA"` |
  | `[null, "b"]` | `["Column A","b"]` |

  `index` của từng cột bằng vị trí của nó. Thêm các case: `excelLetters` với 0 → `A`, 25 → `Z`, 26 → `AA`, 701 → `ZZ`, 702 → `AAA`; `isBlankRow` với `[null,"  "]` → true, `[null,"x"]` → false, `[]` → true.
- [ ] 1.2 Viết `ImportRowTest`: `new ImportRow(2, List.of("a")).value(0)` → `"a"`; `.value(3)` → `null`. `values` là bản sao bất biến: sửa list gốc sau khi tạo không làm row thay đổi. Dùng `Collections.unmodifiableList(new ArrayList<>(values))` để giữ được phần tử `null`.
- [ ] 1.3 Chạy `./mvnw -q test -Dtest=ColumnNamesTest,ImportRowTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 1.4 Tạo 5 file ở phần Interfaces.
- [ ] 1.5 Chạy lại lệnh ở 1.3. Mong đợi: PASS. Chạy thêm `./mvnw -q test -Dtest=ArchitectureTest`: vẫn PASS, vì `domain.source` chỉ dùng `java.*`.
- [ ] 1.6 Commit: `feat(domain): neutral source model and column naming rules`

## 2. CsvSourceParser

**Files:**
- Modify: `apps/api/pom.xml` (thêm `commons-csv`)
- Create: `MAIN/infrastructure/parser/CsvSourceParser.java`
- Test: `TEST/infrastructure/parser/CsvSourceParserTest.java`

**Interfaces:**
- Consumes: `SourceParser`, `SourceSchema`, `ImportRow`, `ColumnNames` (task 1); `DomainException`, `ErrorCode` (F01).
- Produces: `@Component public class CsvSourceParser implements SourceParser`, với `supports(CSV) == true`, `supports(XLSX) == false`.

- [ ] 2.1 Thêm dependency `<dependency><groupId>org.apache.commons</groupId><artifactId>commons-csv</artifactId><version>1.14.1</version></dependency>`. Kiểm version resolve được: `./mvnw -q dependency:tree -Dincludes=org.apache.commons:commons-csv`, mong đợi thấy `org.apache.commons:commons-csv:jar:1.14.1:compile`.
- [ ] 2.2 Viết `CsvSourceParserTest`. Input là `ByteArrayInputStream` của chuỗi UTF-8 (trừ case có ghi rõ byte). `rows(x)` là `read(x)` gom vào list, trong `try (var s = parser.read(...))`.
  | # | Input | Mong đợi |
  |---|---|---|
  | 1 | `name,email\nAn,an@x.com\nBinh,binh@x.com\n` | `inspect`: columns `[{0,name},{1,email}]`, `totalRows` 2, `sheetName` null. `rows`: `(2,[An,an@x.com])`, `(3,[Binh,binh@x.com])` |
  | 2 | byte `EF BB BF` + `name\nAn\n` | cột 0 tên `name` |
  | 3 | `a,b\n"x,1","l1\nl2"\nc,d\n` | `rows`: `(2,["x,1","l1\nl2"])`, `(3,["c","d"])` |
  | 4 | `a,b\n1,2\n\n3,4\n` | `rows`: `(2,[1,2])`, `(4,[3,4])`; `totalRows` 2 |
  | 5 | `a,b\n1,2\n , \n` | `rows` chỉ có `(2,[1,2])`; `totalRows` 1 |
  | 6 | `a,b,c\n1\n1,2,3,4\n` | `rows`: `(2,["1",null,null])`, `(3,["1","2","3"])` |
  | 7 | `a,b\n  ,x\n,y\n` | `rows`: `(2,["  ","x"])`, `(3,[null,"y"])` |
  | 8 | `name,email\n` | `totalRows` 0; `rows` rỗng |
  | 9 | `""` (0 byte) | `inspect` ném `DomainException(FILE_EMPTY)` |
  | 10 | `\n1,2\n` | `inspect` ném `FILE_EMPTY`, message `The first row must contain column headers.` |
  | 11 | `,b\n1,2\n` | columns `["Column A","b"]` |
  | 12 | `a,b\n1,2\n` + byte `C3 28` + `\n` | `inspect` ném `FILE_PARSE_ERROR`, message `File is not valid UTF-8 (near row 3).` |
  | 13 | `a,b\n"unterminated,1\n` | `inspect` ném `FILE_PARSE_ERROR`, message bắt đầu bằng `CSV syntax error near row` |
  | 14 | `a,b\n1,2\n` và gọi `read` rồi `close()` stream | `InputStream` gốc đã bị đóng (dùng một `InputStream` kiểm được cờ `closed`) |
- [ ] 2.3 Chạy `./mvnw -q test -Dtest=CsvSourceParserTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 2.4 Viết `CsvSourceParser` theo design P5:
  - `CSVFormat.RFC4180.builder().setIgnoreEmptyLines(false).get()`;
  - reader UTF-8 dùng decoder `REPORT`;
  - bỏ `﻿`;
  - `rowNumber = record.getRecordNumber()`;
  - bỏ row trống bằng `ColumnNames.isBlankRow`;
  - bù hoặc cắt ô theo số cột header;
  - `""` → `null`;
  - bắt `UncheckedIOException` rồi đổi sang `DomainException(FILE_PARSE_ERROR, …)`, N = số dòng đọc thành công gần nhất + 1.

  `read` trả `Stream` với `onClose` đóng `CSVParser`, tức là đóng luôn input.
- [ ] 2.5 Chạy lại lệnh ở 2.3. Mong đợi: PASS (14 case).
- [ ] 2.6 Commit: `feat(infra): CSV source parser (UTF-8, RFC 4180, Excel-style row numbers)`

## 3. Lưu source schema trên session (Flyway V2)

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V2__add_source_schema.sql`, `MAIN/infrastructure/persistence/SourceSchemaDocument.java`
- Modify: `MAIN/domain/importsession/ImportSession.java`, `MAIN/infrastructure/persistence/ImportSessionEntity.java`, `MAIN/infrastructure/persistence/JpaImportSessionRepository.java`, `TEST/domain/importsession/ImportSessionTest.java`, `TEST/infrastructure/persistence/JpaImportSessionRepositoryTest.java`, cùng mọi chỗ gọi `ImportSession.restore`

**Interfaces:**
- Consumes: `SourceSchema`, `SourceColumn` (task 1).
- Produces (thay đổi so với F01):
  ```java
  // ImportSession
  public static ImportSession restore(UUID id, SourceFile sourceFile, SessionStatus status, Instant createdAt,
                                      Instant updatedAt, Long version, SourceSchema sourceSchema);  // sourceSchema nullable
  public void markInspected(SourceSchema schema, Instant now);   // chỉ hợp lệ khi status UPLOADED; gán schema + transitionTo(CONFIGURING, now)
  public Optional<SourceSchema> sourceSchema();
  // infrastructure
  public record SourceSchemaDocument(List<ColumnDocument> columns, long totalRows, String sheetName) {
      public record ColumnDocument(int index, String name) {}
      public static SourceSchemaDocument from(SourceSchema schema);
      public SourceSchema toDomain();
  }
  ```
- Migration: `ALTER TABLE import_session ADD COLUMN source_schema JSONB;`
- Entity: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_schema") private String sourceSchemaJson;`. Adapter serialize và deserialize bằng `JsonMapper` (bean của Boot, được inject vào `JpaImportSessionRepository`).

- [ ] 3.1 Thêm test vào `ImportSessionTest`:
  | Case | Mong đợi |
  |---|---|
  | `create(...)` rồi `markInspected(schema, t1)` | `status`=`CONFIGURING`; `sourceSchema()` chứa `schema`; `updatedAt`=`t1` |
  | `restore(..., READY, ..., schema)` rồi `markInspected(schema, t1)` | ném `DomainException(SESSION_STATE_INVALID)`, message `Source file has already been inspected.` (`markInspected` chỉ hợp lệ khi đang `UPLOADED`, dù READY→CONFIGURING có trong bảng); `status` vẫn `READY` |
  | `create(...)` | `sourceSchema()` là `Optional.empty()` |
- [ ] 3.2 Thêm test vào `JpaImportSessionRepositoryTest`: lưu một session đã `markInspected` với schema `columns [{0,"name"},{1,"email"}], totalRows 2, sheetName null`, rồi `findById`. Mong đợi: `sourceSchema` đọc ra bằng đúng bản ghi vào.
- [ ] 3.3 Chạy `./mvnw -q test -Dtest=ImportSessionTest,JpaImportSessionRepositoryTest`. Mong đợi: FAIL.
- [ ] 3.4 Cài đặt:
  - migration V2;
  - `ImportSession`: thêm field, `markInspected`, tham số mới cho `restore`, và cập nhật mọi chỗ gọi `restore`;
  - `SourceSchemaDocument`;
  - `ImportSessionEntity`: thêm cột `sourceSchemaJson`;
  - adapter: dùng `JsonMapper` để map.
- [ ] 3.5 Chạy lại lệnh ở 3.3. Mong đợi: PASS. Nếu context lỗi vì Hibernate không có JSON FormatMapper (không có Jackson 2): đổi cột sang `@ColumnTransformer(write = "?::jsonb") @Column(name = "source_schema", columnDefinition = "jsonb") String`, chạy lại, rồi gạch ghi chú này và ghi LÝ DO.
- [ ] 3.6 Chạy `./mvnw -q test` để kiểm các test F01 vẫn xanh sau khi đổi `restore`.
- [ ] 3.7 Commit: `feat(infra): persist source schema as jsonb (Flyway V2)`

## 4. Upload đọc file

**Files:**
- Create: `MAIN/application/importsession/SourceParsers.java`
- Modify: `MAIN/application/importsession/ImportSessionService.java`, `TEST/application/importsession/ImportSessionServiceTest.java`
- Test support: `TEST/support/FakeSourceParser.java` (parser giả, có thể cấu hình `SourceSchema` trả về hoặc exception ném ra)

**Interfaces:**
- Consumes: `SourceParser` (task 1), `ImportSession.markInspected` (task 3).
- Produces:
  ```java
  @Component public class SourceParsers {
      public SourceParsers(List<SourceParser> parsers);
      public Optional<SourceParser> find(SourceFileType type);
  }
  // ImportSessionService: constructor thêm tham số SourceParsers sourceParsers (vị trí cuối)
  ```

- [ ] 4.1 Thêm test vào `ImportSessionServiceTest`, dùng `FakeSourceParser`:
  | Case | Mong đợi |
  |---|---|
  | Parser CSV trả schema `[name,email]`, `totalRows` 2; `upload("customers.csv", "name,email\n…")` | `status`=`CONFIGURING`; `sourceSchema` chính là schema đó; repository có session |
  | Parser CSV ném `DomainException(FILE_PARSE_ERROR)` | ném lại đúng exception đó; storage và repository đều trống |
  | Parser CSV ném `IllegalStateException` | ném lại exception đó; storage trống |
  | Không có parser cho XLSX; `upload("a.xlsx", ZIP)` | `status`=`UPLOADED`; `sourceSchema` rỗng |
  | Parser CSV thành công nhưng repository `failOnSave` | ném lại lỗi; storage trống (nhánh dọn file của F01 vẫn đúng) |
- [ ] 4.2 Chạy `./mvnw -q test -Dtest=ImportSessionServiceTest`. Mong đợi: FAIL.
- [ ] 4.3 Cài `SourceParsers` và sửa `upload` theo design P6.
- [ ] 4.4 Chạy lại lệnh ở 4.2. Mong đợi: PASS (cả case cũ của F01).
- [ ] 4.5 Commit: `feat(app): inspect uploaded files and move sessions to CONFIGURING`

## 5. Preview: use case và endpoint

**Files:**
- Create: `MAIN/application/importsession/SourcePreview.java`, `MAIN/application/importsession/SourcePreviewService.java`, `MAIN/api/importsession/SourcePreviewController.java`, `MAIN/api/importsession/SourcePreviewDto.java`
- Test: `TEST/application/importsession/SourcePreviewServiceTest.java`, `TEST/api/importsession/SourcePreviewControllerTest.java`

**Interfaces:**
- Consumes: `ImportSessionRepository`, `FileStorage` (F01); `SourceParsers` (task 4); `ImportRow`, `SourceSchema` (task 1).
- Produces:
  ```java
  public record SourcePreview(UUID sessionId, SourceFileType fileType, String sheetName, List<SourceColumn> columns,
                              List<ImportRow> rows, int previewLimit, long totalRows) {}
  @Service public class SourcePreviewService { public SourcePreview preview(UUID sessionId, int limit); }
  public record SourcePreviewDto(UUID sessionId, SourceFileType fileType, String sheetName, List<ColumnDto> columns,
                                 List<RowDto> rows, int previewLimit, long totalRows) {
      public record ColumnDto(int index, String name) {}
      public record RowDto(long rowNumber, List<String> values) {}
      public static SourcePreviewDto from(SourcePreview preview);
  }
  // GET /api/import-sessions/{id}/preview?limit (mặc định 50), @Validated + @Min(1) @Max(200)
  ```

- [ ] 5.1 Viết `SourcePreviewServiceTest`. Dùng fake repository và storage của F01, cùng `FakeSourceParser` trả 3 row:
  | Case | Mong đợi |
  |---|---|
  | Session `CONFIGURING` có schema `[name,email]`, `totalRows` 3; `preview(id, 2)` | `rows` có 2 phần tử đầu; `previewLimit` 2; `totalRows` 3; `columns` lấy từ schema đã lưu |
  | `preview(id, 50)` | `rows` có 3 phần tử |
  | Session `UPLOADED` (không có schema) | `DomainException(SESSION_STATE_INVALID)`, message `Source file has not been inspected.` |
  | Id không tồn tại | `DomainException(SESSION_NOT_FOUND)` |
  | Sau khi `preview` xong | stream do parser trả về đã được `close()` |
- [ ] 5.2 Viết `SourcePreviewControllerTest` (`@WebMvcTest(SourcePreviewController.class)`, `@MockitoBean SourcePreviewService`):
  | Request | Mong đợi |
  |---|---|
  | `GET /api/import-sessions/{id}/preview` | service được gọi với `limit` 50. JSON có `$.sessionId`, `$.fileType`=`CSV`, `$.sheetName` null, `$.columns[1].name`=`email`, `$.rows[0].rowNumber`=2, `$.rows[0].values[0]`=`An`, `$.previewLimit`=50, `$.totalRows`=2 |
  | `?limit=0` | 400, `$.code`=`REQUEST_INVALID` |
  | `?limit=201` | 400, `$.code`=`REQUEST_INVALID` |
  | `?limit=abc` | 400, `$.code`=`REQUEST_INVALID` |
  | Service ném `SESSION_STATE_INVALID` | 409, `$.code`=`SESSION_STATE_INVALID` |
  | Service ném `SESSION_NOT_FOUND` | 404, `$.code`=`SESSION_NOT_FOUND` |
  | Một row có `values` `["x", null]` | JSON `$.rows[0].values[1]` là `null` (giữ đúng vị trí) |
- [ ] 5.3 Chạy `./mvnw -q test -Dtest=SourcePreviewServiceTest,SourcePreviewControllerTest`. Mong đợi: FAIL.
- [ ] 5.4 Cài 4 class ở phần Interfaces, theo design P8.
- [ ] 5.5 Chạy lại lệnh ở 5.3. Mong đợi: PASS.
- [ ] 5.6 Commit: `feat(api): source preview endpoint`

## 6. Integration test qua HTTP thật

**Files:**
- Test: `TEST/api/importsession/SourcePreviewIntegrationTest.java` (cùng setup với `ImportSessionApiIntegrationTest` của F01: RANDOM_PORT, Testcontainers, `@TempDir` cho storage, `RestClient`)

- [ ] 6.1 Viết các case:
  | Case | Mong đợi |
  |---|---|
  | Upload `customers.csv` = `name,email\nAn,an@x.com\nBinh,binh@x.com\n` | 201 với `status`=`CONFIGURING`. `GET preview` → 200, `totalRows`=2, `columns[0].name`=`name`, `rows[1]` = `{rowNumber 3, values ["Binh","binh@x.com"]}` |
  | Upload `broken.csv` = `a,b\n"unterminated,1\n` | 422, `code`=`FILE_PARSE_ERROR`; `storageDir` không có thư mục con nào mới |
  | Upload `header-only.csv` = `name,email\n` | 201; preview `totalRows`=0, `rows`=[] |
  | Upload `dup.csv` = `Email,email,\n1,2,3\n` | preview `columns` có tên `Email`, `email (2)`, `Column C` |
  | `GET /api/import-sessions/{UUID chưa từng tạo}/preview` | 404, `code`=`SESSION_NOT_FOUND` |
- [ ] 6.2 Chạy `./mvnw -q test -Dtest=SourcePreviewIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính, không nới lỏng test.
- [ ] 6.3 Commit: `test(api): CSV upload and preview over real HTTP`

## 7. Kiểm tra toàn bộ và hoàn tất

- [ ] 7.1 Chạy `./mvnw -q verify`. Mong đợi: mọi test xanh, gồm ArchitectureTest.
- [ ] 7.2 Chạy app thật (`docker compose up -d`, `./mvnw spring-boot:run`):
  - upload một CSV tiếng Việt có dấu (UTF-8 có BOM, lưu từ Excel bằng "CSV UTF-8");
  - `curl localhost:8080/api/import-sessions/{id}/preview?limit=5` hiển thị đúng dấu và số dòng;
  - upload một CSV lưu bằng "CSV (Comma delimited)" (Windows-1258/1252), nhận 422 `FILE_PARSE_ERROR` nếu file có ký tự không phải UTF-8.
- [ ] 7.3 Tick đủ checkbox, ghi LÝ DO cho mọi chỗ làm khác kế hoạch. Commit: `docs(openspec): complete be-f02 tasks`
- [ ] 7.4 Hỏi người dùng trước khi merge. Sau khi merge: `openspec archive be-f02-csv-preview -y`.
