# BE-F01 Import Session & Upload — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task; mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** Upload CSV/XLSX tạo import session, đọc lại session; định dạng lỗi thống nhất cho mọi API.

**Architecture:** 4 package theo D1. Domain là Java thuần: `ImportSession`, state machine, `FileTypeDetector`, `OriginalFileName`, và port `FileStorage` / `ImportSessionRepository`. Infrastructure cài đặt port: lưu file trên đĩa local, persistence bằng JPA. Application điều phối upload. API có controller và `GlobalExceptionHandler`, trả ProblemDetail kèm `code`.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Jackson 3, PostgreSQL 17, Flyway, JUnit Jupiter 6, AssertJ, Mockito, Testcontainers 2.0.5, ArchUnit 1.4.1.

**Spec:** `openspec/changes/be-f01-import-session/specs/{import-session,api-errors}/spec.md`; thiết kế ở `design.md` cùng thư mục (D1–D14).

## Global Constraints

- Chạy mọi lệnh Maven từ `apps/api` trong worktree `D:\Code\Product\universal-importer-be`.
- Viết tắt đường dẫn: `MAIN` = `apps/api/src/main/java/com/universalimporter`, `TEST` = `apps/api/src/test/java/com/universalimporter`.
- Jackson 3 (`tools.jackson.*`). Không thêm `com.fasterxml.jackson.core:jackson-databind`.
- Chiều phụ thuộc theo D1. `domain` chỉ import `java.*` và `com.universalimporter.domain.*`.
- Mã lỗi và HTTP status đúng bảng D4. Body lỗi luôn là `application/problem+json` và có `code` ở top-level.
- Mọi `Instant` ghi xuống DB được cắt còn micro giây (`truncatedTo(ChronoUnit.MICROS)`), vì PostgreSQL chỉ lưu tới micro giây.
- Biến môi trường và giá trị mặc định: `IMPORTER_MAX_FILE_SIZE=20MB`, `IMPORTER_MAX_REQUEST_SIZE=21MB`, `IMPORTER_STORAGE_DIR=${java.io.tmpdir}/universal-importer`.
- Test chạy với `-Duser.timezone=UTC` (Surefire).
- Commit trên nhánh `feature/be-f01-import-session`, cuối commit message có dòng `Co-Authored-By`. Không push, không merge khi chưa hỏi.

---

## 1. Nền build và test: Testcontainers, ArchUnit, timezone

**Files:**
- Rename: `compose.yaml` → `docker-compose.yml`
- Modify: `apps/api/pom.xml`
- Create: `TEST/support/TestcontainersConfiguration.java`, `TEST/ArchitectureTest.java`
- Modify: `TEST/ApiApplicationTests.java`

**Interfaces:**
- Produces: `TestcontainersConfiguration` (`@TestConfiguration`, bean `PostgreSQLContainer` gắn `@ServiceConnection`, image `postgres:17`). Mọi test cần DB đều `@Import` class này.

- [x] 1.1 Chạy `git mv compose.yaml docker-compose.yml`, rồi `docker compose config -q` (không báo lỗi là đạt).
  - Làm thêm: đặt `name: universal-importer` ở đầu `docker-compose.yml`. **LÝ DO**: nếu không đặt, Compose lấy tên thư mục làm project name; chạy từ worktree (`universal-importer-be`) sẽ tạo stack thứ hai và đụng `container_name` với DB đang chạy.
- [x] 1.2 Chạy `./mvnw -q test` để xác nhận lỗi hiện tại: `ApiApplicationTests` FAIL với `FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"`.
- [x] 1.3 Sửa `pom.xml`: thêm các dependency scope `test` sau (version do BOM của Boot quản lý, trừ ArchUnit):
  ```xml
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-junit-jupiter</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-postgresql</artifactId><scope>test</scope></dependency>
  <dependency><groupId>com.tngtech.archunit</groupId><artifactId>archunit</artifactId><version>1.4.1</version><scope>test</scope></dependency>
  ```
  Và thêm plugin Surefire:
  ```xml
  <plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration><argLine>-Duser.timezone=UTC</argLine></configuration>
  </plugin>
  ```
- [x] 1.4 Tạo `TestcontainersConfiguration` rồi gắn vào `ApiApplicationTests` (`@Import(TestcontainersConfiguration.class)`):
  ```java
  @TestConfiguration(proxyBeanMethods = false)
  public class TestcontainersConfiguration {
      @Bean
      @ServiceConnection
      PostgreSQLContainer postgresContainer() {
          return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
      }
  }
  ```
- [x] 1.5 Tạo `ArchitectureTest`. Import class của `com.universalimporter` bằng `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`. Viết 2 test, cả hai `.allowEmptyShould(true)` vì lúc này các package còn trống:
  - `domain_only_depends_on_jdk`: `classes().that().resideInAPackage("..domain..").should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..")`
  - `layers_respect_dependency_direction`: `layeredArchitecture().consideringOnlyDependenciesInLayers()`, layer `api`/`application`/`domain`/`infrastructure` theo package cùng tên. Cấu hình:
    - `api` không được layer nào truy cập;
    - `application` chỉ được `api` và `infrastructure` truy cập;
    - `infrastructure` không được layer nào truy cập;
    - `domain` chỉ được `api`, `application`, `infrastructure` truy cập.
- [x] 1.6 Chạy `./mvnw -q test` và xác nhận PASS: `contextLoads` xanh với Postgres chạy trong container, ArchitectureTest xanh.
- [x] 1.7 Commit: `chore(api): add Testcontainers, ArchUnit and UTC test timezone`

## 2. Mã lỗi và bảng HTTP status

**Files:**
- Create: `MAIN/domain/common/ErrorCode.java`, `MAIN/domain/common/ProblemItem.java`, `MAIN/domain/common/DomainException.java`, `MAIN/api/common/ErrorHttpStatus.java`
- Test: `TEST/api/common/ErrorHttpStatusTest.java`

**Interfaces:**
- Produces:
  ```java
  public enum ErrorCode { REQUEST_INVALID, SESSION_NOT_FOUND, SESSION_NOT_READY, SESSION_STATE_INVALID,
      RESULT_NOT_AVAILABLE, FILE_TOO_LARGE, FILE_UNSUPPORTED, FILE_EMPTY, FILE_PARSE_ERROR, SCHEMA_INVALID,
      MAPPING_INVALID, SOURCE_COLUMN_NOT_FOUND, CONFIG_INVALID, EXPORT_FAILED, INTERNAL_ERROR }
  public record ProblemItem(String field, String code, String message) {}
  public class DomainException extends RuntimeException {
      public DomainException(ErrorCode code, String message);
      public DomainException(ErrorCode code, String message, List<ProblemItem> items);
      public ErrorCode code();
      public List<ProblemItem> items();          // không bao giờ null; bản sao bất biến
  }
  public final class ErrorHttpStatus { public static HttpStatus of(ErrorCode code); }  // switch không có default → compiler bắt thiếu case
  ```

- [x] 2.1 Viết `ErrorHttpStatusTest` dạng `@ParameterizedTest @CsvSource`, mỗi dòng là `code,status`:
  - `REQUEST_INVALID,400`
  - `SESSION_NOT_FOUND,404`
  - `SESSION_NOT_READY,409`, `SESSION_STATE_INVALID,409`, `RESULT_NOT_AVAILABLE,409`
  - `FILE_TOO_LARGE,413`
  - `FILE_UNSUPPORTED,415`
  - `FILE_EMPTY,422`, `FILE_PARSE_ERROR,422`, `SCHEMA_INVALID,422`, `MAPPING_INVALID,422`, `SOURCE_COLUMN_NOT_FOUND,422`, `CONFIG_INVALID,422`
  - `EXPORT_FAILED,500`, `INTERNAL_ERROR,500`

  ~~Thêm test `every_code_has_a_status`: lặp qua `ErrorCode.values()`, `of()` không ném lỗi.~~ **LÝ DO bỏ**: `switch` không có `default` nên compiler đã bắt thiếu case; test này không bao giờ fail được.
- [x] 2.2 ~~Chạy `./mvnw -q test -Dtest=ErrorHttpStatusTest`. Mong đợi: FAIL vì lỗi compile (chưa có class).~~ Tạo trước `ErrorCode` và một `ErrorHttpStatus` cố tình trả sai (418), rồi chạy. Kết quả: 15/15 FAIL ở assertion. **LÝ DO**: phải thấy fail ở assertion thì mới chứng minh được test bắt được ánh xạ sai; fail vì lỗi compile không chứng minh điều đó.
- [x] 2.3 ~~Tạo 4 class ở phần Interfaces.~~ Viết bảng ánh xạ thật trong `ErrorHttpStatus`. **LÝ DO**: `ProblemItem` và `DomainException` chuyển sang task 3, vì chỉ test của task 3 mới dùng tới chúng (TDD: không viết code khi chưa có test cần nó).
- [x] 2.4 Chạy lại lệnh ở 2.2. Mong đợi: PASS (15 case; không phải 16 như ghi ban đầu, vì đã bỏ `every_code_has_a_status`).
- [x] 2.5 Commit: `feat(api): add error codes and HTTP status mapping`

## 3. Định dạng lỗi thống nhất (GlobalExceptionHandler)

**Files:**
- Create: `MAIN/api/common/GlobalExceptionHandler.java`
- Test: `TEST/api/common/GlobalExceptionHandlerTest.java`, gồm một controller lồng `ErrorProbeController` chỉ dùng cho test

**Interfaces:**
- Consumes: `ErrorCode`, `DomainException`, `ProblemItem`, `ErrorHttpStatus` (task 2).
- Produces: `@RestControllerAdvice GlobalExceptionHandler extends ResponseEntityExceptionHandler`, gồm:
  - `@ExceptionHandler(DomainException.class)`: status lấy từ `ErrorHttpStatus.of(code)`; `detail` là `ex.getMessage()`; `code`; có `errors` khi `items` không rỗng.
  - `@ExceptionHandler(Exception.class)`: 500 `INTERNAL_ERROR`, `detail` là `"Unexpected server error."`; ghi stack trace bằng `log.error`.
  - Override `handleExceptionInternal`: gắn `code` cho lỗi framework. `MaxUploadSizeExceededException` thành `FILE_TOO_LARGE`; status 5xx thành `INTERNAL_ERROR`; còn lại là `REQUEST_INVALID`. Status giữ nguyên.
  - Mọi body có `instance` là request URI, `Content-Type` là `application/problem+json`.

- [x] 3.1 Viết `GlobalExceptionHandlerTest` với `@WebMvcTest(controllers = GlobalExceptionHandlerTest.ErrorProbeController.class)`. `ErrorProbeController` là `@RestController` lồng static, có:
  - Làm khác: gắn thêm `@Import(ErrorProbeController.class)`. **LÝ DO**: component scan không nhận controller lồng trong test (lần RED đầu mọi request đều 404, tức là fail sai lý do). `ProblemItem` và `DomainException` được tạo ở task này, cùng với test đầu tiên cần tới chúng.
  - `GET /test-errors/domain`: ném `DomainException(FILE_UNSUPPORTED, "Only .csv and .xlsx files are supported.")`
  - `GET /test-errors/items`: ném `DomainException(SCHEMA_INVALID, "Schema is invalid.", List.of(new ProblemItem("email", "SCHEMA_INVALID", "Duplicate field name")))`
  - `GET /test-errors/boom`: ném `IllegalStateException("secret-db-password")`
  - `GET /test-errors/uuid/{id}` với `@PathVariable UUID id`

  Các case (dùng `MockMvc` và `jsonPath`):
  | Request | Mong đợi |
  |---|---|
  | `GET /test-errors/domain` | 415; content type `application/problem+json`; `$.code`=`FILE_UNSUPPORTED`; `$.status`=415; `$.instance`=`/test-errors/domain`; không có `$.errors` |
  | `GET /test-errors/items` | 422; `$.code`=`SCHEMA_INVALID`; `$.errors[0].field`=`email`; `$.errors[0].code`=`SCHEMA_INVALID`; `$.errors[0].message`=`Duplicate field name` |
  | `GET /test-errors/boom` | 500; `$.code`=`INTERNAL_ERROR`; body không chứa `secret-db-password` và không chứa `IllegalStateException` |
  | `GET /test-errors/uuid/abc` | 400; `$.code`=`REQUEST_INVALID` |
  | `DELETE /test-errors/domain` | 405; `$.code`=`REQUEST_INVALID` |
  | `GET /test-errors/khong-ton-tai` | 404; `$.code`=`REQUEST_INVALID` |
- [x] 3.2 Chạy `./mvnw -q test -Dtest=GlobalExceptionHandlerTest`. Mong đợi: FAIL, vì chưa có handler nên lỗi không có `code`.
- [x] 3.3 Tạo `GlobalExceptionHandler` theo phần Interfaces.
- [x] 3.4 Chạy lại lệnh ở 3.2. Mong đợi: PASS (6 case). Nếu `$.code` bị lồng trong `properties` thay vì ở top-level (rủi ro với Jackson 3), sửa cách serialize để `code` và `errors` nằm ở top-level rồi chạy lại.
- [x] 3.5 Commit: `feat(api): unified ProblemDetail error envelope with error codes`

## 4. Domain: ImportSession và state machine

**Files:**
- Create: `MAIN/domain/importsession/SessionStatus.java`, `SourceFileType.java`, `SourceFile.java`, `ImportSession.java`
- Test: `TEST/domain/importsession/SessionStatusTest.java`, `TEST/domain/importsession/ImportSessionTest.java`

**Interfaces:**
- Consumes: `DomainException`, `ErrorCode.SESSION_STATE_INVALID`.
- Produces:
  ```java
  public enum SessionStatus { UPLOADED, CONFIGURING, READY, PROCESSED, FAILED;
      public boolean canTransitionTo(SessionStatus target); }
  public enum SourceFileType { CSV, XLSX }
  public record SourceFile(String originalFileName, SourceFileType fileType, long sizeBytes) {}
  public final class ImportSession {
      public static ImportSession create(UUID id, SourceFile sourceFile, Instant now);   // UPLOADED, createdAt = updatedAt = now, version null
      public static ImportSession restore(UUID id, SourceFile sourceFile, SessionStatus status,
                                          Instant createdAt, Instant updatedAt, Long version);
      public void transitionTo(SessionStatus target, Instant now);   // sai bảng → DomainException(SESSION_STATE_INVALID)
      public UUID id(); public SourceFile sourceFile(); public SessionStatus status();
      public Instant createdAt(); public Instant updatedAt(); public Long version();
  }
  ```

- [x] 4.1 Viết `SessionStatusTest` dạng `@ParameterizedTest @CsvSource`, liệt kê đủ 25 cặp `from,to,allowed`. Chỉ các cặp sau là `true`, còn lại `false`:
  - `UPLOADED→CONFIGURING`
  - `CONFIGURING→CONFIGURING`, `CONFIGURING→READY`
  - `READY→CONFIGURING`, `READY→READY`, `READY→PROCESSED`, `READY→FAILED`
  - `PROCESSED→CONFIGURING`, `PROCESSED→READY`, `PROCESSED→PROCESSED`, `PROCESSED→FAILED`
- [x] 4.2 Viết `ImportSessionTest`, dùng `t0 = Instant.parse("2026-09-25T10:00:00Z")` và `t1 = t0 + 1 phút`:
  | Case | Mong đợi |
  |---|---|
  | `create(id, file, t0)` | `status`=`UPLOADED`; `createdAt`=`updatedAt`=`t0`; `version`=null |
  | `restore(…, READY, t0, t0, 3L)` rồi `transitionTo(PROCESSED, t1)` | `status`=`PROCESSED`; `updatedAt`=`t1`; `createdAt`=`t0` |
  | `restore(…, FAILED, …)` rồi `transitionTo(CONFIGURING, t1)` | ném `DomainException` có `code()`=`SESSION_STATE_INVALID`; `status` vẫn `FAILED`; `updatedAt` không đổi |
  | `create(…)` rồi `transitionTo(PROCESSED, t1)` | ném `DomainException` `SESSION_STATE_INVALID` |
- [x] 4.3 Chạy `./mvnw -q test -Dtest=SessionStatusTest,ImportSessionTest`. Mong đợi: FAIL vì lỗi compile.
  - Làm khác: tạo trước class khung (method ném `UnsupportedOperationException`, `canTransitionTo` trả `false`), để RED là 11 failure ở assertion cộng 4 error "not implemented", thay vì lỗi compile. Dùng dấu phẩy trong `-Dtest=A,B`. **LÝ DO**: Surefire 3.5.6 không nhận `A+B` (khi đó không chạy test nào mà cũng không báo gì); mọi lệnh `-Dtest` trong file này đã được sửa sang dấu phẩy.
- [x] 4.4 Tạo 4 class ở phần Interfaces. ~~`canTransitionTo` dùng `EnumMap<SessionStatus, EnumSet<SessionStatus>>`.~~ `canTransitionTo` dùng `switch` trên `this`. **LÝ DO**: `switch` không có `default` nên compiler bắt được khi thêm trạng thái mới mà quên xử lý; cách dùng map không có đảm bảo đó.
- [x] 4.5 Chạy lại lệnh ở 4.3. Mong đợi: PASS.
- [x] 4.6 Commit: `feat(domain): import session lifecycle state machine`

## 5. Chấp nhận file: làm sạch tên và nhận diện loại file

**Files:**
- Create: `MAIN/domain/importsession/OriginalFileName.java`, `MAIN/domain/importsession/FileTypeDetector.java`
- Test: `TEST/domain/importsession/OriginalFileNameTest.java`, `TEST/domain/importsession/FileTypeDetectorTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class OriginalFileName { public static String sanitize(String raw); }   // null hoặc blank → ""
  public final class FileTypeDetector {
      public static final int HEAD_SIZE = 8192;
      public static SourceFileType detect(String sanitizedName, byte[] head);
      // đuôi không hỗ trợ → FILE_UNSUPPORTED; head rỗng → FILE_EMPTY; sai magic bytes → FILE_UNSUPPORTED
  }
  ```

- [x] 5.1 Viết `OriginalFileNameTest` (parameterized):
  - Làm thêm 2 case: bỏ ký tự bidi override `U+202A–U+202E` và `U+2066–U+2069`; không cắt ngang cặp surrogate ở giới hạn 255. **LÝ DO**: bidi override làm tên hiển thị sai đuôi file (kỹ thuật giả mạo tên file); cắt ngang surrogate sinh chuỗi UTF-16 hỏng mà PostgreSQL không lưu được. Bước 5.2 thêm case `tiny.xlsx` (2 byte), để bắt lỗi đọc quá độ dài mảng khi so magic bytes.
  | Input | Output |
  |---|---|
  | `customers.csv` | `customers.csv` |
  | `../../etc/passwd.csv` | `passwd.csv` |
  | `C:\Users\an\Desktop\khách hàng.csv` | `khách hàng.csv` |
  | `"  report.xlsx  "` | `report.xlsx` |
  | `"a\u0000b\u001F.csv"` | `ab.csv` |
  | `"kha\u0301ch.csv"` (NFD) | `"khách.csv"` (NFC, độ dài 9) |
  | 300 chữ `a` + `.csv` | 251 chữ `a` + `.csv` (độ dài 255) |
  | `null` | `""` |
  | `"   "` | `""` |
- [x] 5.2 Viết `FileTypeDetectorTest`. Hằng `ZIP = {0x50,0x4B,0x03,0x04,0x14,0x00}`.
  | Tên | Head | Mong đợi |
  |---|---|---|
  | `data.csv` | `"a,b\n1,2"` (UTF-8) | `CSV` |
  | `DATA.CSV` | `"a,b"` | `CSV` |
  | `data.xlsx` | `ZIP` | `XLSX` |
  | `data.xlsx` | `"a,b,c"` | `FILE_UNSUPPORTED` |
  | `data.csv` | `{'a',',',0x00}` | `FILE_UNSUPPORTED` |
  | `data.xls` | `ZIP` | `FILE_UNSUPPORTED` |
  | `data.json` | `"{}"` | `FILE_UNSUPPORTED` |
  | `data` | `"a,b"` | `FILE_UNSUPPORTED` |
  | `""` | `"a,b"` | `FILE_UNSUPPORTED` |
  | `empty.csv` | `{}` | `FILE_EMPTY` |
  | `empty.xls` | `{}` | `FILE_UNSUPPORTED` (đuôi được kiểm trước) |

  Các case lỗi dùng `assertThatThrownBy(...).isInstanceOf(DomainException.class)` và kiểm `code()`.
- [x] 5.3 Chạy `./mvnw -q test -Dtest=OriginalFileNameTest,FileTypeDetectorTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 5.4 Viết `OriginalFileName.sanitize` theo thứ tự:
  1. Cắt lấy phần sau `/` hoặc `\` cuối cùng.
  2. `Normalizer.normalize(NFC)`.
  3. Bỏ ký tự `\p{Cntrl}`.
  4. `strip()`.
  5. Nếu dài hơn 255: cắt phần tên, giữ đuôi.

  Viết `FileTypeDetector.detect` theo thứ tự: kiểm đuôi (lowercase), rồi `head.length == 0`, rồi magic bytes. Message: `Only .csv and .xlsx files are supported.`, `File is empty.`, `File content does not match the .xlsx format.`, `File content is not a text CSV.`
- [x] 5.5 Chạy lại lệnh ở 5.3. Mong đợi: PASS.
- [x] 5.6 Commit: `feat(domain): sanitize uploaded file names and detect CSV/XLSX by magic bytes`

## 6. Lưu file: port FileStorage và LocalFileStorage

**Files:**
- Create: `MAIN/domain/importsession/FileStorage.java`, `MAIN/infrastructure/storage/StorageProperties.java`, `MAIN/infrastructure/storage/LocalFileStorage.java`
- Test: `TEST/infrastructure/storage/LocalFileStorageTest.java`

**Interfaces:**
- Produces:
  ```java
  public interface FileStorage {
      long save(UUID sessionId, InputStream content);   // trả số byte đã ghi; lỗi IO → UncheckedIOException
      InputStream open(UUID sessionId);                 // file không tồn tại → UncheckedIOException
      void delete(UUID sessionId);                      // xoá cả thư mục {root}/{id}; không có thì bỏ qua
  }
  @ConfigurationProperties("importer.storage") public record StorageProperties(Path dir) {}
  @Component public class LocalFileStorage implements FileStorage { public LocalFileStorage(StorageProperties properties); }
  ```

- [x] 6.1 Viết `LocalFileStorageTest` với `@TempDir Path root` và `new LocalFileStorage(new StorageProperties(root))`:
  - Làm thêm 2 case: `delete` xoá được cả thư mục con `result/` (F08 sẽ tạo); `save` tự tạo thư mục gốc nếu chưa có.
  | Case | Mong đợi |
  |---|---|
  | `save(id, "a,b".bytes)` | trả `3`; `root/{id}/source.bin` chứa đúng `a,b`; không còn file `*.tmp` nào trong `root/{id}` |
  | `save` rồi `open(id)` | đọc ra đúng `a,b` |
  | `save` rồi `delete(id)` | `root/{id}` không còn; gọi `delete(id)` lần nữa không ném lỗi |
  | `open(randomUUID)` | ném `UncheckedIOException` |
- [x] 6.2 Chạy `./mvnw -q test -Dtest=LocalFileStorageTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 6.3 Tạo 3 class. `save` ghi ra `source.bin.tmp` rồi `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`. `delete` xoá đệ quy bằng `Files.walk` theo thứ tự ngược.
  - Làm khác: `@ConfigurationPropertiesScan` và thuộc tính `importer.storage.dir` được thêm ngay ở task này, không đợi tới task 9. **LÝ DO**: `LocalFileStorage` là bean cần `StorageProperties`; thiếu chúng thì `ApiApplicationTests` đỏ ở commit này. `save` không đóng stream đầu vào, người gọi đóng.
- [x] 6.4 Chạy lại lệnh ở 6.2. Mong đợi: PASS.
- [x] 6.5 Commit: `feat(infra): local file storage keyed by session id`

## 7. Persistence: Flyway V1 và repository JPA

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V1__create_import_session.sql`, `MAIN/domain/importsession/ImportSessionRepository.java`, `MAIN/infrastructure/persistence/ImportSessionEntity.java`, `MAIN/infrastructure/persistence/ImportSessionJpaRepository.java`, `MAIN/infrastructure/persistence/JpaImportSessionRepository.java`
- Test: `TEST/infrastructure/persistence/JpaImportSessionRepositoryTest.java`

**Interfaces:**
- Consumes: `ImportSession.restore(...)`, `ImportSession` getters (task 4).
- Produces:
  ```java
  public interface ImportSessionRepository {
      ImportSession save(ImportSession session);   // trả bản đã lưu (có version mới)
      Optional<ImportSession> findById(UUID id);
  }
  @Repository public class JpaImportSessionRepository implements ImportSessionRepository { … }
  ```
- Migration:
  ```sql
  CREATE TABLE import_session (
      id                 UUID         PRIMARY KEY,
      original_file_name VARCHAR(255) NOT NULL,
      file_type          VARCHAR(10)  NOT NULL CHECK (file_type IN ('CSV', 'XLSX')),
      size_bytes         BIGINT       NOT NULL CHECK (size_bytes >= 0),
      status             VARCHAR(20)  NOT NULL
                         CHECK (status IN ('UPLOADED', 'CONFIGURING', 'READY', 'PROCESSED', 'FAILED')),
      version            BIGINT       NOT NULL,
      created_at         TIMESTAMPTZ  NOT NULL,
      updated_at         TIMESTAMPTZ  NOT NULL
  );
  ```

- [x] 7.1 Viết `JpaImportSessionRepositoryTest`, với các annotation `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)` và `@Import({TestcontainersConfiguration.class, JpaImportSessionRepository.class})`:
  - Làm khác: case thứ ba đổi thành "lưu → flush/clear → sửa → lưu lại chính session vừa nhận về, hai lần" (mong đợi `version` = 2). Test dùng `TestEntityManager.flush()/clear()` để thật sự đọc lại từ DB. **LÝ DO**: bắt lỗi version cũ sau `merge` (xem 7.3).
  | Case | Mong đợi |
  |---|---|
  | `save(create(id, file("a.csv", CSV, 7), t0))` rồi `findById(id)` | các field giống hệt; `status`=`UPLOADED`; `version`=`0` |
  | `findById(randomUUID)` | `Optional.empty()` |
  | lưu, `transitionTo(CONFIGURING, t1)` trên bản đã lưu, rồi `save` | `status`=`CONFIGURING`; `updatedAt`=`t1`; `version`=`1` |

  (`t0`, `t1` là `Instant` chính xác tới micro giây.)
- [x] 7.2 Chạy `./mvnw -q test -Dtest=JpaImportSessionRepositoryTest`. Mong đợi: FAIL vì lỗi compile hoặc thiếu bảng.
- [x] 7.3 Tạo migration, entity, Spring Data repository và adapter:
  - Làm khác: adapter dùng `saveAndFlush` thay vì `save`. **LÝ DO**: sau `merge`, version chỉ tăng lúc flush, nên session trả về mang version cũ và lần lưu kế tiếp bị `ObjectOptimisticLockingFailureException`. Đã kiểm mutation: đổi về `save` thì case ở 7.1 fail đúng lỗi này. Entity và Spring Data repository để package-private, bên ngoài chỉ thấy port `ImportSessionRepository`.
  - Entity: `@Version Long version`; `status` và `fileType` là `@Enumerated(STRING)`; `createdAt` và `updatedAt` là `Instant`.
  - Adapter: map domain sang entity (detached, mang id và version), gọi `save`, rồi map ngược về domain.
- [x] 7.4 Chạy lại lệnh ở 7.2. Mong đợi: PASS (Flyway áp V1; `ddl-auto: validate` không báo lỗi).
- [x] 7.5 Commit: `feat(infra): persist import sessions with Flyway V1`

## 8. Use case: upload và đọc session

**Files:**
- Create: `MAIN/application/importsession/ImportSessionService.java`, `MAIN/infrastructure/config/ClockConfig.java`
- Test: `TEST/application/importsession/ImportSessionServiceTest.java`, `TEST/support/InMemoryImportSessionRepository.java`, `TEST/support/InMemoryFileStorage.java`

**Interfaces:**
- Consumes: `OriginalFileName`, `FileTypeDetector`, `FileStorage`, `ImportSessionRepository`, `ImportSession` (tasks 4–7).
- Produces:
  ```java
  @Service public class ImportSessionService {
      public ImportSessionService(ImportSessionRepository repository, FileStorage storage, Clock clock);
      public ImportSession upload(String originalFileName, InputStreamSource content);
      public ImportSession get(UUID id);   // không có → DomainException(SESSION_NOT_FOUND, "Import session not found.")
  }
  @Configuration public class ClockConfig { @Bean Clock clock() { return Clock.systemUTC(); } }
  ```
- Luồng `upload`:
  1. `sanitize`.
  2. Đọc tối đa `HEAD_SIZE` byte đầu.
  3. `detect`.
  4. `id = UUID.randomUUID()`.
  5. `size = storage.save(id, content.getInputStream())`.
  6. `repository.save(ImportSession.create(...))`. Nếu ném lỗi thì `storage.delete(id)` rồi ném lại.
  7. Log `Created import session {} ({}, {} bytes)`, không log nội dung file.

  `now = Instant.now(clock).truncatedTo(MICROS)`.

- [x] 8.1 Viết 2 fake, dùng map trong bộ nhớ. `InMemoryImportSessionRepository` có cờ `failOnSave` để giả lập lỗi DB. Viết `ImportSessionServiceTest` với `Clock.fixed(t0, UTC)` và `ByteArrayResource`:
  - Làm thêm: clock cố định có phần nano giây (`…123456789Z`), mong đợi `createdAt` là `…123456Z`, để test bắt được trường hợp quên cắt về micro giây. Case "không lưu gì" kiểm cả storage lẫn repository.
  | Case | Mong đợi |
  |---|---|
  | `upload("customers.csv", "a,b\n1,2")` | `status`=`UPLOADED`; `fileType`=`CSV`; `sizeBytes`=7; `originalFileName`=`customers.csv`; `createdAt`=`t0`; storage có đúng 7 byte dưới id đó; repository có session |
  | `upload("../x.csv", "a")` | `originalFileName`=`x.csv` |
  | `upload("data.xls", "a")` | `DomainException(FILE_UNSUPPORTED)`; storage và repository đều trống |
  | `upload("e.csv", "")` | `DomainException(FILE_EMPTY)`; không lưu gì |
  | `failOnSave=true`, `upload("a.csv", "a")` | ném lại lỗi của repository; storage không còn file nào |
  | `get(id)` sau khi upload | trả đúng session |
  | `get(randomUUID)` | `DomainException(SESSION_NOT_FOUND)` |
- [x] 8.2 Chạy `./mvnw -q test -Dtest=ImportSessionServiceTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 8.3 Tạo `ImportSessionService` và `ClockConfig` theo phần Interfaces.
- [x] 8.4 Chạy lại lệnh ở 8.2. Mong đợi: PASS.
- [x] 8.5 Commit: `feat(app): upload and read import sessions`

## 9. API: controller, DTO, cấu hình multipart

**Files:**
- Create: `MAIN/api/importsession/ImportSessionController.java`, `MAIN/api/importsession/ImportSessionDto.java`
- Modify: `MAIN/ApiApplication.java` (thêm `@ConfigurationPropertiesScan`), `apps/api/src/main/resources/application.yaml`
- Test: `TEST/api/importsession/ImportSessionControllerTest.java`

**Interfaces:**
- Consumes: `ImportSessionService` (task 8), `GlobalExceptionHandler` (task 3).
- Produces:
  ```java
  public record ImportSessionDto(UUID id, SessionStatus status, String originalFileName, SourceFileType fileType,
                                 long sizeBytes, Instant createdAt, Instant updatedAt) {
      public static ImportSessionDto from(ImportSession session);
  }
  // POST /api/import-sessions (multipart, @RequestPart("file") MultipartFile) → 201 + Location
  // GET  /api/import-sessions/{id} → 200
  ```
- Thêm vào `application.yaml`:
  ```yaml
  spring:
    servlet:
      multipart:
        max-file-size: ${IMPORTER_MAX_FILE_SIZE:20MB}
        max-request-size: ${IMPORTER_MAX_REQUEST_SIZE:21MB}
  server:
    tomcat:
      max-swallow-size: -1
  importer:
    storage:
      dir: ${IMPORTER_STORAGE_DIR:${java.io.tmpdir}/universal-importer}
  ```

- [x] 9.1 Viết `ImportSessionControllerTest` với `@WebMvcTest(ImportSessionController.class)` và `@MockitoBean ImportSessionService`:
  - Làm khác: ~~case service ném `FILE_UNSUPPORTED` → 415~~ và ~~case `DELETE` → 405~~ đã bỏ. **LÝ DO**: cả hai chỉ kiểm lại `GlobalExceptionHandler`, vốn đã có test riêng ở task 3; luồng 415 thật được kiểm end-to-end ở task 10. Thêm vào đó: dùng `ArgumentCaptor` kiểm controller đưa đúng tên file và đúng nội dung file cho service.
  | Request | Stub | Mong đợi |
  |---|---|---|
  | multipart `file`=`customers.csv` | `upload` trả session `UPLOADED`, CSV, 7 byte, `t0` | 201; header `Location`=`/api/import-sessions/{id}`; `$.id`, `$.status`=`UPLOADED`, `$.fileType`=`CSV`, `$.sizeBytes`=7, `$.originalFileName`=`customers.csv`, `$.createdAt`=`2026-09-25T10:00:00Z` |
  | multipart chỉ có part `other` | — | 400; `$.code`=`REQUEST_INVALID` |
  | multipart `file` | `upload` ném `DomainException(FILE_UNSUPPORTED, …)` | 415; `$.code`=`FILE_UNSUPPORTED` |
  | `GET /api/import-sessions/{id}` | `get` trả session | 200; các field như trên |
  | `GET /api/import-sessions/{uuid}` | `get` ném `DomainException(SESSION_NOT_FOUND, …)` | 404; `$.code`=`SESSION_NOT_FOUND` |
  | `GET /api/import-sessions/abc` | — | 400; `$.code`=`REQUEST_INVALID` |
  | `DELETE /api/import-sessions` | — | 405; `$.code`=`REQUEST_INVALID` |
- [x] 9.2 Chạy `./mvnw -q test -Dtest=ImportSessionControllerTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 9.3 Tạo controller và DTO, sửa `ApiApplication` và `application.yaml`.
- [x] 9.4 Chạy lại lệnh ở 9.2. Mong đợi: PASS.
- [x] 9.5 Commit: `feat(api): import session upload and read endpoints`

## 10. Integration test qua HTTP thật

**Files:**
- Test: `TEST/api/importsession/ImportSessionApiIntegrationTest.java`, `TEST/api/importsession/UploadSizeLimitIntegrationTest.java`

**Interfaces:**
- Consumes: toàn bộ app, `TestcontainersConfiguration` (task 1).
- Cả hai class dùng `@SpringBootTest(webEnvironment = RANDOM_PORT)` và `@Import(TestcontainersConfiguration.class)`. `static @TempDir Path storageDir` được đăng ký qua `@DynamicPropertySource` cho `importer.storage.dir`. Gọi HTTP bằng `RestClient.create("http://localhost:" + port)`, đọc status và body bằng `exchange(...)` để không bị ném lỗi khi status là 4xx.
- File XLSX dùng trong test được tạo trong bộ nhớ bằng `ZipOutputStream`, với một entry `xl/workbook.xml`.

- [ ] 10.1 Viết `ImportSessionApiIntegrationTest`:
  | Case | Mong đợi |
  |---|---|
  | POST `customers.csv` (`"name,email\nAn,an@x.com\n"`) | 201; theo `Location` gọi GET được 200, cùng `id`, `status`=`UPLOADED`; file `storageDir/{id}/source.bin` có đúng các byte đã gửi |
  | POST `customers.xlsx` (zip tạo trong bộ nhớ) | 201; `fileType`=`XLSX` |
  | POST `data.xls` | 415; `Content-Type` chứa `application/problem+json`; `code`=`FILE_UNSUPPORTED` |
- [ ] 10.2 Viết `UploadSizeLimitIntegrationTest` với `@TestPropertySource(properties = {"spring.servlet.multipart.max-file-size=1KB", "spring.servlet.multipart.max-request-size=2KB"})`:
  | Case | Mong đợi |
  |---|---|
  | POST `big.csv` 5KB | nhận được response (không bị reset kết nối); status 413; `code`=`FILE_TOO_LARGE` |
- [ ] 10.3 Chạy `./mvnw -q test -Dtest=ImportSessionApiIntegrationTest,UploadSizeLimitIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính (không nới lỏng test), rồi chạy lại.
- [ ] 10.4 Commit: `test(api): end-to-end upload tests over real HTTP`

## 11. Kiểm tra toàn bộ và hoàn tất

- [ ] 11.1 Chạy `./mvnw -q verify`. Mong đợi: mọi test xanh, gồm cả ArchitectureTest (lúc này các package đã có class).
- [ ] 11.2 Chạy app thật: `docker compose up -d`, rồi `./mvnw spring-boot:run`. Dùng `curl -F "file=@<csv>" localhost:8080/api/import-sessions` và kiểm:
  - Nhận 201 kèm JSON.
  - `curl localhost:8080/api/import-sessions/{id}` trả 200.
  - Gửi file `.xls` nhận 415 với `code`.
  - Log không chứa nội dung file.
- [ ] 11.3 Tick đủ các checkbox trong file này. Chỗ nào làm khác kế hoạch thì gạch ngang và ghi LÝ DO. Commit: `docs(openspec): complete be-f01 tasks`
- [ ] 11.4 Hỏi người dùng trước khi merge vào `main`. Sau khi merge: `openspec archive be-f01-import-session -y`, commit phần archive.
