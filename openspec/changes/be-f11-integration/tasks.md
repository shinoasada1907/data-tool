# BE-F11 Integration & UX Polish — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task; mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** Khép kín luồng V0.1:
- dọn session và file tạm theo TTL;
- chứng minh bằng integration test rằng mọi endpoint trả lỗi đúng contract, vòng đời đúng D2, và happy path CSV/XLSX chạy end-to-end;
- có README chạy được demo.

**Architecture:**
- `SessionCleanupService` (application) dùng các method mới của port `ImportSessionRepository` và `FileStorage`, cùng khoá theo session của F08.
- `SessionCleanupScheduler` (infrastructure) gọi service qua `@Scheduled`.
- Phần còn lại là integration test trên toàn bộ app (Testcontainers), và README.

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Scheduling, Web MVC, Data JPA), Flyway, PostgreSQL 17, JUnit Jupiter 6, AssertJ, Mockito, Testcontainers 2.0.5.

**Spec:** `openspec/changes/be-f11-integration/specs/{import-session,api-errors}/spec.md`. Thiết kế ở `design.md` cùng thư mục (F11-D1…D7) và `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md` (D1–D14, API contract V0.1).

## Global Constraints

- Chạy mọi lệnh Maven từ `apps/api`. Viết tắt đường dẫn:
  - `MAIN` = `apps/api/src/main/java/com/universalimporter`
  - `TEST` = `apps/api/src/test/java/com/universalimporter`
  - `RES` = `apps/api/src/test/resources`
- Chiều phụ thuộc theo D1. `application` không import `infrastructure`; `@EnableScheduling` và `@Scheduled` chỉ nằm ở `infrastructure`.
- TTL lấy từ `importer.cleanup.session-ttl: ${IMPORTER_SESSION_TTL:24h}`; interval từ `importer.cleanup.interval: PT1H`; bật/tắt qua `importer.cleanup.enabled: true`.
- Xoá storage trước, rồi mới xoá DB. Không bao giờ xoá thư mục có tên không phải UUID. Log dọn dẹp chỉ gồm số đếm và `sessionId`.
- Migration của F11 là `V10`. Không sửa migration đã có.
- Integration test dùng `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Import(TestcontainersConfiguration.class)`, `static @TempDir storageDir` cho `importer.storage.dir`, và `ImportFlowClient` (F09).
- Commit trên nhánh `feature/be-f11-integration`, cuối commit message có dòng `Co-Authored-By`. Không push, không merge khi chưa hỏi.

---

## 1. Đối chiếu với F02–F10

**Files:**
- Modify (nếu cần): `openspec/changes/be-f11-integration/design.md`, `openspec/changes/be-f11-integration/tasks.md`

**Interfaces:**
- Consumes (giả định, xem design.md mục "Giả định"):
  - khoá theo session của F08 (trong tasks gọi là `SessionLocks#tryRun(UUID, Runnable) → boolean`);
  - FK của `import_configuration` (F04);
  - `ImportFlowClient` (F09);
  - fixture XLSX của F03;
  - nhánh `FAILED` của `POST /process`: thiếu file trả 500 `INTERNAL_ERROR`, file sai cấu trúc trả 422 `FILE_PARSE_ERROR`.

- [x] 1.1 Đọc code F02–F10 đã merge. So các giả định trên với code thật. Chỗ nào khác thì sửa design.md và file này theo tên thật (gạch dòng cũ, ghi LÝ DO).
  - Kết quả đối chiếu:
    - ~~`SessionLocks#tryRun` có sẵn~~ → F08 chỉ có `withLock(UUID, Supplier)` (chờ tới khi lấy được khoá). F11 thêm `tryRun(UUID, Runnable) → boolean` dùng `ReentrantLock.tryLock()`.
      - Khoá chia theo 1024 stripe, nên một session khác cùng stripe đang bận cũng làm session này bị `skipped`; lần chạy sau sẽ xử lý.
    - ~~`ImportFlowClient` (F09)~~ → `support/HttpTestClient` (`upload`, `putJson`, `post`, `get`, và `download` của F10). **LÝ DO**: F09 không tạo `ImportFlowClient` mà dùng lại helper sẵn có.
    - ~~Fixture XLSX của F03 là file nhị phân~~ → F03 sinh XLSX bằng code (`support/XlsxFixtures`, fastexcel). F11 cũng sinh `customers.xlsx` bằng code (thêm `XlsxFixtures.e2eCustomers`), không commit file nhị phân.
      - **LÝ DO**: đọc test là thấy từng ô và kiểu của ô, không phải unzip ra xem `t="b"`. Sinh lại được, và diff được khi sửa.
    - `ResultQueryService.requireCurrentSummary` đã được F09 đổi thành `openCurrent`. F11 không dùng tới.
    - Nhánh FAILED của `POST /process`: thiếu file → 500 `INTERNAL_ERROR` → `FAILED`, đúng như giả định.
      - Sau review F08: 500 chỉ làm session `FAILED` khi chính file không đọc được; lỗi ghi kết quả hoặc bug thì không đổi trạng thái session.
    - Migration hiện có: V1–V3. V10 để lại khoảng trống, Flyway chấp nhận.
    - Chưa có `README.md` ở gốc repo; FE có `apps/web/README.md`, phiên FE sẽ viết tiếp nó ở FE-F11. README gốc link sang đó, không chép lại.
- [x] 1.2 ~~Chạy `\d import_configuration` trong psql~~ Đọc thẳng `V3__create_import_configuration.sql`: `session_id UUID PRIMARY KEY REFERENCES import_session (id) ON DELETE CASCADE`. Có cascade, nên `deleteById` chỉ cần xoá `import_session`. Test 2.1 vẫn kiểm việc này trên DB thật.

## 2. Mở rộng port cho việc dọn dẹp, kèm migration V10

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V10__index_import_session_updated_at.sql`, `MAIN/domain/importsession/StoredEntry.java`
- Modify:
  - `MAIN/domain/importsession/ImportSessionRepository.java`, `MAIN/domain/importsession/FileStorage.java`
  - `MAIN/infrastructure/persistence/JpaImportSessionRepository.java`, `MAIN/infrastructure/persistence/ImportSessionJpaRepository.java`
  - `MAIN/infrastructure/storage/LocalFileStorage.java`
  - `TEST/support/InMemoryImportSessionRepository.java`, `TEST/support/InMemoryFileStorage.java`
- Test: `TEST/infrastructure/persistence/JpaImportSessionRepositoryTest.java` (thêm case), `TEST/infrastructure/storage/LocalFileStorageTest.java` (thêm case)

**Interfaces:**
- Produces:
  ```java
  // thêm vào ImportSessionRepository
  List<UUID> findIdsUpdatedBefore(Instant cutoff, int limit);   // sắp theo updatedAt tăng dần
  boolean existsById(UUID id);
  void deleteById(UUID id);                                     // xoá cả config; không có thì bỏ qua
  // thêm vào FileStorage
  List<StoredEntry> listEntries();                              // chỉ các thư mục con có tên là UUID
  public record StoredEntry(UUID sessionId, Instant lastModified) {}
  ```
- Migration:
  ```sql
  CREATE INDEX idx_import_session_updated_at ON import_session (updated_at);
  ```

- [x] 2.1 Thêm case vào `JpaImportSessionRepositoryTest`. `t0` là `Instant` chính xác tới micro giây; các session được lưu bằng `ImportSession.restore(…, updatedAt, …)`.
  | Case | Mong đợi |
  |---|---|
  | A (`t0−25h`), B (`t0−23h`), C (`t0−48h`); `findIdsUpdatedBefore(t0−24h, 10)` | `[C, A]` |
  | như trên với `limit` = 1 | `[C]` |
  | `existsById(A)` / `existsById(randomUUID)` | `true` / `false` |
  | `deleteById(A)` rồi `findById(A)` | `Optional.empty()`; gọi `deleteById(randomUUID)` không ném lỗi |
  | A có row `import_configuration` (PUT schema qua repository của F04), rồi `deleteById(A)` | không vi phạm FK; không còn config của A |
  | native query `select indexname from pg_indexes where tablename = 'import_session'` | có `idx_import_session_updated_at` |
- [x] 2.2 Thêm case vào `LocalFileStorageTest`:
  | Case | Mong đợi |
  |---|---|
  | `root` có thư mục `{u1}`, `{u2}`, thư mục `backup` và file `x.txt` | `listEntries()` trả đúng 2 phần tử `u1`, `u2`; `lastModified` bằng `Files.getLastModifiedTime` của từng thư mục |
  | `root` chưa tồn tại | `listEntries()` trả danh sách rỗng |
- [x] 2.3 Chạy `./mvnw -q test -Dtest=JpaImportSessionRepositoryTest,LocalFileStorageTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 2.4 Tạo `V10`, `StoredEntry`; thêm method vào 2 port.
- [x] 2.5 Cài đặt method mới:
  - Adapter JPA: dùng query derived hoặc `@Query("select s.id from ImportSessionEntity s where s.updatedAt < :cutoff order by s.updatedAt")` kèm ~~`Limit`~~ `Pageable` (`PageRequest.of(0, limit)`). `deleteById` xoá config trước nếu kết quả 1.2 là không có cascade.
    - Kết quả 1.2: có cascade. `deleteById` là bulk JPQL `delete … where s.id = :id` (`@Modifying @Transactional`): không cần nạp entity, và id không tồn tại thì chỉ xoá 0 dòng.
  - `listEntries` chỉ nhận tên ở dạng chuẩn (`UUID.toString()` phải bằng đúng tên thư mục). **LÝ DO**: `UUID.fromString` nhận cả dạng rút gọn như `1-2-3-4-5`, nên thư mục tên đó sẽ bị coi là thư mục của session.
  - Test tạo session bằng `ImportSession.create(id, file, t)` (khi đó `updatedAt = t`), ~~`restore`~~. **LÝ DO**: merge một entity đã có version, với id chưa có trong DB, thì Hibernate báo optimistic lock.
  - `LocalFileStorage.listEntries`: `Files.list(root)` → chỉ thư mục có tên parse được bằng `UUID.fromString`, bỏ qua các tên khác.
  - Cập nhật 2 fake:
    - `InMemoryFileStorage` có `lastModified` đặt được, và tập `failDeleteFor` để giả lập lỗi xoá.
    - `InMemoryImportSessionRepository` có tập `failDeleteFor` để giả lập lỗi xoá DB.
- [x] 2.6 Chạy lại lệnh ở 2.3. Mong đợi: PASS.
- [x] 2.7 Commit: `feat(infra): cleanup queries, storage listing and updated_at index (V10)`

## 3. Use case: SessionCleanupService

**Files:**
- Create: `MAIN/application/importsession/SessionCleanupService.java`, `MAIN/application/importsession/CleanupProperties.java`, `MAIN/application/importsession/CleanupReport.java`
- Test: `TEST/application/importsession/SessionCleanupServiceTest.java`, ~~`TEST/support/FakeSessionLocks.java`~~ (dùng `SessionLocks` thật; một thread khác giữ khoá bằng latch), `TEST/application/common/SessionLocksTest.java` (thêm case `tryRun`).

**Interfaces:**
- Consumes: 3 method mới của repository và `listEntries` (task 2), `FileStorage.delete` (F01), `SessionLocks` (F08).
- Produces:
  ```java
  @ConfigurationProperties("importer.cleanup")
  public record CleanupProperties(boolean enabled, Duration sessionTtl, Duration interval) {}
  public record CleanupReport(int deletedSessions, int deletedOrphans, int skipped, int failures) {}
  @Service public class SessionCleanupService {
      public SessionCleanupService(ImportSessionRepository sessions, FileStorage storage, SessionLocks locks,
                                   CleanupProperties properties, Clock clock);
      public CleanupReport cleanupExpired();              // now = Instant.now(clock)
      public CleanupReport cleanupExpired(Instant now);
  }
  ```

- [x] 3.1 Viết `SessionCleanupServiceTest`. Dùng `t0 = 2026-09-25T12:00:00Z`, TTL 24h, các fake của task 2, và `FakeSessionLocks` (có tập `busy`).
  | Bối cảnh | Mong đợi |
  |---|---|
  | A (`t0−25h`, có file) và B (`t0−23h`, có file) | report `(1,0,0,0)`; A không còn trong repo lẫn storage; B còn |
  | A, C quá hạn; `storage.failDeleteFor` = {A} | C bị xoá hết; A còn trong repo; report `(1,0,0,1)` |
  | A quá hạn; `locks.busy` = {A} | A còn; report `(0,0,1,0)` |
  | A quá hạn; `repository.failDeleteFor` = {A} (file đã xoá) | report `(0,0,0,1)`; không ném exception; các session khác vẫn được xử lý |
  | thư mục mồ côi X (không có trong repo), `lastModified` = `t0−25h` | X bị xoá; `deletedOrphans` = 1 |
  | thư mục mồ côi Y, `lastModified` = `t0−1h` | Y còn |
  | thư mục của session B (còn hạn), `lastModified` = `t0−30 ngày` | B còn (vì có trong repo) |
  | repo và storage đều rỗng | report `(0,0,0,0)` |
  | 600 session quá hạn | lần chạy này xoá 500; còn 100 |
- [x] 3.2 Chạy `./mvnw -q test -Dtest=SessionCleanupServiceTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 3.3 Tạo 3 class theo F11-D1 và F11-D2. Mỗi session được xử lý trong `try/catch` riêng; `log.warn("Cleanup failed for session {}", id, e)`.
  - **Thêm**: trong khoá, đọc lại session và chỉ xoá khi `updatedAt` vẫn trước `cutoff`.
    - **LÝ DO**: giữa lúc liệt kê và lúc lấy được khoá, một PUT có thể vừa sửa session. Nếu không kiểm lại thì xoá mất một session đang dùng.
    - Test: `a_session_changed_after_it_was_listed_is_kept`. Kiểm ngược: bỏ bước kiểm lại thì test đỏ.
  - Log chỉ ghi tên class của exception, không ghi message.
  - Thêm `SessionLocks.tryRun(UUID, Runnable)`, dùng `tryLock()`, có test: rảnh thì chạy, đang bận thì bỏ qua, lỗi thì vẫn nhả khoá.
  - `CleanupProperties` có `@DefaultValue` (`true`, `24h`, `1h`).
- [x] 3.4 Chạy lại lệnh ở 3.2. Mong đợi: PASS.
- [x] 3.5 Commit: `feat(app): delete expired sessions and orphan storage`

## 4. Lịch chạy: SessionCleanupScheduler

**Files:**
- Create: `MAIN/infrastructure/scheduling/SchedulingConfig.java`, `MAIN/infrastructure/scheduling/SessionCleanupScheduler.java`
- Modify: `apps/api/src/main/resources/application.yaml`
- Test: `TEST/infrastructure/scheduling/SessionCleanupSchedulerTest.java`, `TEST/infrastructure/scheduling/SessionCleanupSchedulerStartupTest.java`

**Interfaces:**
- Consumes: `SessionCleanupService`, `CleanupProperties` (task 3).
- Produces:
  ```java
  @Configuration @EnableScheduling public class SchedulingConfig {}
  @Component @ConditionalOnProperty(name = "importer.cleanup.enabled", havingValue = "true", matchIfMissing = true)
  public class SessionCleanupScheduler {
      @Scheduled(initialDelayString = "PT0S", fixedDelayString = "${importer.cleanup.interval:PT1H}")
      public void run();   // bắt mọi exception → log.error; log.info kèm các số đếm trong report
  }
  ```
- Thêm vào `application.yaml`:
  ```yaml
  importer:
    cleanup:
      enabled: true
      session-ttl: ${IMPORTER_SESSION_TTL:24h}
      interval: PT1H
  ```

- [x] 4.1 Viết `SessionCleanupSchedulerTest` (unit, service là mock):
  | Case | Mong đợi |
  |---|---|
  | service trả `CleanupReport(2,1,0,0)` | `run()` gọi `cleanupExpired()` đúng 1 lần |
  | service ném `RuntimeException("db down")` | `run()` không ném exception |
- [x] 4.2 Viết `SessionCleanupSchedulerStartupTest` với `@SpringBootTest`, `@Import(TestcontainersConfiguration.class)` và `@MockitoSpyBean SessionCleanupService`:
  | Case | Mong đợi |
  |---|---|
  | context vừa khởi động | `verify(service, timeout(5000).atLeastOnce()).cleanupExpired()` |
  | inject `CleanupProperties` | `sessionTtl` = `Duration.ofHours(24)`; `interval` = `Duration.ofHours(1)`; `enabled` = true |
- [x] 4.3 Chạy `./mvnw -q test -Dtest=SessionCleanupSchedulerTest,SessionCleanupSchedulerStartupTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 4.4 Tạo `SchedulingConfig` và `SessionCleanupScheduler`, sửa `application.yaml`.
  - **Thêm `src/test/resources/config/application.yaml`**: tắt cleanup và đổi storage sang `${java.io.tmpdir}/universal-importer-tests` cho **mọi** test context.
    - **LÝ DO**: `ApiApplicationTests` và `ApiDocsIntegrationTest` không đặt `importer.storage.dir`, nên dùng đúng thư mục storage mặc định của app dev. DB của test thì trống, nên cleanup chạy lúc khởi động sẽ coi mọi thư mục cũ hơn 24h ở đó là mồ côi và **xoá dữ liệu dev của người dùng**.
    - Đặt ở `config/` vì Spring Boot nạp nó **thêm** vào `application.yaml` chính, không thay thế.
    - Test nào cần cleanup (4.2) thì tự bật, trên `@TempDir` riêng.
  - Cũng vì vậy, `application.yaml` ghi chú: mỗi instance cần thư mục storage riêng. Ghi vào README.
  - `@MockitoSpyBean(reset = MockReset.NONE)` ở 4.2. **LÝ DO**: lần gọi cần kiểm xảy ra một lần lúc context khởi động; mặc định spy bị reset sau mỗi test, nên nếu test kia chạy trước thì lần gọi bị xoá.
  - **Thêm luật ArchUnit** `scheduling_lives_in_infrastructure`: `@Scheduled` và `@EnableScheduling` chỉ nằm ở `infrastructure` (theo Global Constraints).
- [x] 4.5 Chạy lại lệnh ở 4.3. Mong đợi: PASS.
- [x] 4.6 Commit: `feat(infra): schedule session cleanup at startup and hourly`

## 5. Integration test cho dọn dẹp

**Files:**
- Test: `TEST/application/importsession/SessionCleanupIntegrationTest.java`

**Interfaces:**
- Consumes: toàn bộ app, `SessionCleanupService` (task 3), `JdbcTemplate`, ~~`ImportFlowClient` (F09)~~ `HttpTestClient`.
- Test đặt thuộc tính `importer.cleanup.enabled=false`, để scheduler không chạy song song với test. *(Đã là mặc định cho mọi test, xem 4.4.)*

- [x] 5.1 Viết `SessionCleanupIntegrationTest`:
  | Case | Mong đợi |
  |---|---|
  | upload S, PUT schema cho S; `jdbc.update("update import_session set updated_at = now() - interval '25 hours' where id = ?", S)`; upload T; gọi `cleanupExpired()` | `deletedSessions` = 1; `GET /api/import-sessions/{S}` trả 404 `SESSION_NOT_FOUND`; `storageDir/{S}` không còn; `select count(*) from import_configuration where session_id = S` = 0; `GET /api/import-sessions/{T}` trả 200 |
  | tạo `storageDir/{randomUUID}` với `setLastModifiedTime(now − 25h)`, và `storageDir/backup` với `setLastModifiedTime(now − 30 ngày)`; gọi `cleanupExpired()` | thư mục UUID bị xoá; `backup` còn; `deletedOrphans` = 1 |
- [x] 5.2 Chạy `./mvnw -q test -Dtest=SessionCleanupIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính (không nới lỏng test), rồi chạy lại.
- [x] 5.3 Commit: `test(app): session cleanup end-to-end`

## 6. Bộ test contract lỗi cho mọi endpoint

**Files:**
- Test: `TEST/api/ErrorContractIntegrationTest.java`

**Interfaces:**
- Consumes: toàn bộ app, `ImportFlowClient` (F09), fixture và config của task 7 (`RES/fixtures/e2e/customers.csv`).
- `@BeforeAll` dựng sẵn 4 session từ `customers.csv`:
  - `R`: PUT schema và mapping đầy đủ, nên ở trạng thái `READY`.
  - `U`: chỉ PUT schema, nên `name` và `email` là required mà chưa map.
  - `P`: giống `R`, rồi gọi process.
  - `F`: giống `R`, xoá `storageDir/{F}/source.bin`, rồi gọi process; khi đó process trả 500 và session sang `FAILED`.
- Bảng `ALLOWED` (endpoint → tập code) chép đúng từ spec `api-errors` của F11, cộng thêm `INTERNAL_ERROR`.

- [ ] 6.1 Viết `ErrorContractIntegrationTest` dạng `@ParameterizedTest`, mỗi dòng là một case:
  | # | Request | Status | `code` |
  |---|---|---|---|
  | 1 | `POST /api/import-sessions` không có part `file` | 400 | `REQUEST_INVALID` |
  | 2 | POST upload `data.xls` | 415 | `FILE_UNSUPPORTED` |
  | 3 | POST upload `empty.csv` (0 byte) | 422 | `FILE_EMPTY` |
  | 4 | POST upload `bad.csv`, byte `a,b\n` + `0xC3 0x28` + `\n` | 422 | `FILE_PARSE_ERROR` |
  | 5 | `GET /api/import-sessions/abc` | 400 | `REQUEST_INVALID` |
  | 6 | `GET /api/import-sessions/{randomUUID}` | 404 | `SESSION_NOT_FOUND` |
  | 7 | `GET /{R}/preview?limit=0` | 400 | `REQUEST_INVALID` |
  | 8 | `PUT /{R}/schema`, body `{"fields": [` | 400 | `REQUEST_INVALID` |
  | 9 | `PUT /{R}/schema` với 2 field `Email` và `email` | 422 | `SCHEMA_INVALID`, và `errors` không rỗng |
  | 10 | `PUT /{R}/mapping` với `targetField` = `nope` | 422 | `MAPPING_INVALID` |
  | 11 | `PUT /{R}/mapping` với `sourceColumn` = `Nope` | 422 | `SOURCE_COLUMN_NOT_FOUND` |
  | 12 | `PUT /{R}/transformations` với `type` = `reverse` | 422 | `CONFIG_INVALID` |
  | 13 | `PUT /{R}/transformations` với dateFormat `inputFormat` = `dd/MM/yyyyq` | 422 | `CONFIG_INVALID` |
  | 14 | `PUT /{R}/validations` với rule `email` trên field `score` | 422 | `CONFIG_INVALID` |
  | 15 | `POST /{U}/process` | 409 | `SESSION_NOT_READY`, và `errors` có `{field:"email", code:"TARGET_FIELD_REQUIRED"}` |
  | 16 | `PUT /{F}/schema` với schema hợp lệ | 409 | `SESSION_STATE_INVALID` |
  | 17 | `GET /{R}/result` | 409 | `RESULT_NOT_AVAILABLE` |
  | 18 | `GET /{P}/result?size=0` | 400 | `REQUEST_INVALID` |
  | 19 | `GET /{P}/export?format=xml` | 400 | `REQUEST_INVALID` |
  | 20 | `GET /{R}/export?format=json` | 409 | `RESULT_NOT_AVAILABLE` |
  | 21 | `GET /{R}/errors/export` | 409 | `RESULT_NOT_AVAILABLE` |
  | 22 | `GET /api/khong-ton-tai` | 404 | `REQUEST_INVALID` |
  | 23 | `DELETE /api/import-sessions` | 405 | `REQUEST_INVALID` |

  Với mọi case, kiểm thêm: `Content-Type` chứa `application/problem+json`, và `code` thuộc `ALLOWED` của endpoint đó.
- [ ] 6.2 Chạy `./mvnw -q test -Dtest=ErrorContractIntegrationTest`. Mong đợi: PASS. Case nào FAIL thì sửa code chính cho đúng contract (không sửa bảng cho khớp code sai), rồi chạy lại.
- [ ] 6.3 Commit: `test(api): error contract for every endpoint`

## 7. Happy path end-to-end: CSV và XLSX

**Files:**
- Create: `RES/fixtures/e2e/customers.csv`, ~~`RES/fixtures/e2e/customers.xlsx`~~ `XlsxFixtures.e2eCustomers(dir)` (sinh bằng code, xem 1.1), `TEST/support/E2eFlow.java` (config dùng chung).
- Test: `TEST/api/ImportFlowIntegrationTest.java`

**Interfaces:**
- Consumes: toàn bộ app, `ImportFlowClient` (F09), `CsvTestReader` (F10).
- Nội dung `customers.csv` (UTF-8, không BOM). Dòng 2 bắt đầu bằng 2 dấu cách:
  ```
  Name,Email,Birth Date,Active,Score
    An Nguyen ,AN@EXAMPLE.COM,25/12/1990,true,10
  Binh,binh@example.com,31/02/1991,yes,x
  Chi,an@example.com,01/01/1992,0,7.5
  ,dung@example.com,02/02/1993,1,8
  ```
- `customers.xlsx`: 1 sheet tên `Customers`, dòng 1 là 5 header dạng text như bản CSV. Có thể dùng lại fixture của F03 nếu trùng dữ liệu; nếu không thì tạo bằng LibreOffice Calc hoặc Excel. Sau khi tạo, unzip ra kiểm `xl/worksheets/sheet1.xml`: ô D2 có `t="b"`, ô C2 là số có style ngày.
  | Row | A (text) | B (text) | C | D | E |
  |---|---|---|---|---|---|
  | 2 | `  An Nguyen ` | `AN@EXAMPLE.COM` | ngày 1990-12-25 | boolean TRUE | số 10 |
  | 3 | `Binh` | `binh@example.com` | text `31/02/1991` | text `yes` | text `x` |
  | 4 | `Chi` | `an@example.com` | ngày 1992-01-01 | số 0 | số 7.5 |
  | 5 | (trống) | `dung@example.com` | ngày 1993-02-02 | số 1 | số 8 |
- Config dùng chung, viết dạng text block:
  ```json
  {"fields":[{"name":"name","type":"string","required":true,"order":0},
             {"name":"email","type":"email","required":true,"order":1},
             {"name":"dob","type":"date","required":false,"order":2},
             {"name":"active","type":"boolean","required":false,"order":3},
             {"name":"score","type":"number","required":false,"order":4},
             {"name":"country","type":"string","required":false,"order":5}]}
  ```
  ```json
  {"mappings":[{"targetField":"name","mappingType":"SOURCE_COLUMN","sourceColumn":"Name","constantValue":null},
               {"targetField":"email","mappingType":"SOURCE_COLUMN","sourceColumn":"Email","constantValue":null},
               {"targetField":"dob","mappingType":"SOURCE_COLUMN","sourceColumn":"Birth Date","constantValue":null},
               {"targetField":"active","mappingType":"SOURCE_COLUMN","sourceColumn":"Active","constantValue":null},
               {"targetField":"score","mappingType":"SOURCE_COLUMN","sourceColumn":"Score","constantValue":null},
               {"targetField":"country","mappingType":"CONSTANT","sourceColumn":null,"constantValue":"VN"}]}
  ```
  ```json
  {"validations":[{"targetField":"email","type":"unique"}]}
  ```
- Transformations của **CSV**:
  ```json
  {"transformations":[{"targetField":"name","order":0,"type":"trim"},
                      {"targetField":"email","order":0,"type":"trim"},
                      {"targetField":"email","order":1,"type":"lowercase"},
                      {"targetField":"dob","order":0,"type":"dateFormat",
                       "params":{"inputFormat":"dd/MM/yyyy","outputFormat":"yyyy-MM-dd"}}]}
  ```
  Transformations của **XLSX**: giống hệt nhưng **không có** phần `dob`, vì ô ngày trong XLSX đã được đọc ra dạng ISO (D9).

- [x] 7.1 Tạo 2 fixture theo bảng trên.
  - XLSX sinh bằng fastexcel: ô ngày là serial number với format `yyyy-mm-dd`, ô boolean là `t="b"`, ô số là số. Test xác nhận preview trả `1990-12-25`, `TRUE`, `10`, tức là parser đọc đúng kiểu ô.
  - Config nằm ở `support/E2eFlow` thay vì text block trong từng test. **LÝ DO**: ba class (task 6, 7, 8) dùng chung đúng một config; chép ra ba chỗ thì sẽ lệch nhau.
- [x] 7.2 Viết `ImportFlowIntegrationTest` với case CSV. Làm lần lượt từng bước và kiểm:
  | Bước | Mong đợi |
  |---|---|
  | upload `customers.csv` | 201; `status` = `CONFIGURING` |
  | `GET /preview` | `columns[].name` = `[Name, Email, Birth Date, Active, Score]`; `totalRows` = 4; `sheetName` = null; `rows[0].rowNumber` = 2; `rows[0].values[0]` = `"  An Nguyen "` (chưa trim) |
  | PUT schema, mapping, transformations, validations | mỗi PUT trả 200; sau PUT mapping thì `session.status` = `READY` |
  | `POST /process` | `total` 4, `valid` 1, `invalid` 3; `errorCountsByCode` = `{TRANSFORMATION_FAILED:1, VALIDATION_TYPE:2, VALIDATION_UNIQUE:1, VALIDATION_REQUIRED:1}`; `errorCountsByField` = `{dob:1, active:1, score:1, email:1, name:1}` |
  | `GET /result?view=valid` | 1 row (row 2); `values` = `{"name":"An Nguyen","email":"an@example.com","dob":"1990-12-25","active":true,"score":10,"country":"VN"}` |
  | `GET /result?view=invalid` | row 3, 4, 5. Lỗi row 3 theo thứ tự: `dob/TRANSFORMATION/dateFormat/step 0/TRANSFORMATION_FAILED/sourceValue "31/02/1991"`, `active/VALIDATION/type/VALIDATION_TYPE/"yes"`, `score/VALIDATION/type/VALIDATION_TYPE/"x"`. Row 4: `email/VALIDATION/unique/VALIDATION_UNIQUE/"an@example.com"`. Row 5: `name/VALIDATION/required/VALIDATION_REQUIRED/sourceValue null` |
  | `GET /export?format=json` | body = `[{"name":"An Nguyen","email":"an@example.com","dob":"1990-12-25","active":true,"score":10,"country":"VN"}]` |
  | `GET /export?format=csv` | BOM; `CsvTestReader` đọc ra `[[name,email,dob,active,score,country],[An Nguyen,an@example.com,1990-12-25,true,10,VN]]` |
  | `GET /errors/export` | đọc ra 5 dòng dữ liệu theo thứ tự `(3,dob)`, `(3,active)`, `(3,score)`, `(4,email)`, `(5,name)` |
- [x] 7.3 Thêm case XLSX vào cùng class, với transformations của XLSX:
  | Bước | Mong đợi |
  |---|---|
  | upload `customers.xlsx` | 201; `status` = `CONFIGURING` |
  | `GET /preview` | `sheetName` = `Customers`; `rows[0].values` = `["  An Nguyen ","AN@EXAMPLE.COM","1990-12-25","TRUE","10"]` |
  | `POST /process` | `total` 4, `valid` 1, `invalid` 3; `errorCountsByCode` = `{VALIDATION_TYPE:3, VALIDATION_UNIQUE:1, VALIDATION_REQUIRED:1}` |
  | `GET /result?view=invalid` | lỗi row 3: `dob/VALIDATION/type/VALIDATION_TYPE/"31/02/1991"`, `active/…/"yes"`, `score/…/"x"`; row 4 và row 5 giống case CSV |
  | `GET /export?format=json` | giống hệt kết quả của case CSV |
- [x] 7.4 Chạy `./mvnw -q test -Dtest=ImportFlowIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính (không sửa kết quả mong đợi cho khớp code sai), rồi chạy lại.
- [x] 7.5 Commit: `test(api): CSV and XLSX happy paths end-to-end`

## 8. Sửa config rồi process lại; vòng đời đầy đủ

**Files:**
- Test: `TEST/api/ReprocessIntegrationTest.java`, `TEST/api/SessionLifecycleIntegrationTest.java`

**Interfaces:**
- Consumes: fixture và config CSV của task 7, `ImportFlowClient` (F09).

- [ ] 8.1 Viết `ReprocessIntegrationTest`:
  | Bước | Mong đợi |
  |---|---|
  | chạy happy path CSV tới hết process | `valid` 1, `invalid` 3 |
  | PUT validations `{"validations":[]}` | 200; `session.status` = `READY`; `GET /result` trả 409 `RESULT_NOT_AVAILABLE` |
  | `POST /process` | `valid` 2, `invalid` 2 |
  | `GET /result?view=valid` | `rowNumber` = `[2, 4]`; `values.email` của row 4 = `an@example.com` |
  | PUT lại đúng `{"validations":[]}` | 200; `session.status` vẫn là `PROCESSED`; `GET /result?view=valid` trả 200 với row `[2, 4]` |
- [ ] 8.2 Viết `SessionLifecycleIntegrationTest`:
  | Bước | Mong đợi |
  |---|---|
  | upload `customers.csv` | `CONFIGURING` |
  | PUT schema | `CONFIGURING`; `readiness.ready` = false; `issues` có `TARGET_FIELD_REQUIRED` cho `name` và `email` |
  | PUT mapping | `READY`; `readiness.ready` = true |
  | PUT transformations và validations của CSV | `READY` |
  | `POST /process` | `PROCESSED` |
  | PUT transformations bỏ `lowercase` của `email` | `READY`; `GET /result` trả 409 `RESULT_NOT_AVAILABLE` |
  | `POST /process` | `PROCESSED` |
  | PUT lại đúng transformations vừa gửi | `PROCESSED`; `GET /result` trả 200 |
  | xoá `storageDir/{id}/source.bin`; PUT validations `{"validations":[]}` | `READY` |
  | `POST /process` | 500; `Content-Type` chứa `application/problem+json`; `code` = `INTERNAL_ERROR` |
  | `GET /api/import-sessions/{id}` | `status` = `FAILED` |
  | PUT schema | 409 `SESSION_STATE_INVALID`; `GET` session vẫn `FAILED` |
  | `GET /result` | 409 `RESULT_NOT_AVAILABLE` |
- [ ] 8.3 Chạy `./mvnw -q test -Dtest=ReprocessIntegrationTest,SessionLifecycleIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính, rồi chạy lại.
- [ ] 8.4 Commit: `test(api): reprocess and full session lifecycle`

## 9. Không CORS; README ở gốc repo

**Files:**
- Create: `README.md` (gốc repo)
- Test: `TEST/api/NoCorsIntegrationTest.java`

- [ ] 9.1 Viết `NoCorsIntegrationTest`:
  | Request | Mong đợi |
  |---|---|
  | `GET /api/import-sessions/{id}` kèm header `Origin: http://localhost:5173` | 200; response **không** có header `Access-Control-Allow-Origin` |
  | `OPTIONS /api/import-sessions/{id}` kèm `Origin: http://localhost:5173` và `Access-Control-Request-Method: GET` | response không có header `Access-Control-Allow-Origin` |
- [ ] 9.2 Chạy `./mvnw -q test -Dtest=NoCorsIntegrationTest`. Mong đợi: PASS, vì D3 không cấu hình CORS. Nếu có header thì tìm chỗ nào đã bật CORS và gỡ đi.
- [ ] 9.3 Viết `README.md` ở gốc repo, gồm các mục:
  1. Universal Importer là gì (2–3 câu), và cấu trúc `apps/api`, `apps/web`.
  2. Yêu cầu: Java 21, Docker, Node + pnpm.
  3. **Demo flow**:
     - `docker compose up -d`
     - `cd apps/api && ./mvnw spring-boot:run`
     - `cd apps/web && pnpm install && pnpm dev`
     - Mở `http://localhost:5173`. Vite proxy chuyển `/api` tới `API_PROXY_TARGET`, mặc định `http://localhost:8080`, nên không cần CORS.
  4. **Demo bằng curl**: đi hết upload → preview → 4 PUT (dùng đúng config của task 7) → process → result → export → errors/export, kèm lệnh cụ thể.
  5. **Bảng biến môi trường**:
     - BE: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `IMPORTER_MAX_FILE_SIZE` (20MB), `IMPORTER_MAX_REQUEST_SIZE` (21MB), `IMPORTER_STORAGE_DIR`, `IMPORTER_SESSION_TTL` (24h).
     - FE: `API_PROXY_TARGET`, `VITE_MAX_UPLOAD_MB` (20), `VITE_USE_MOCK`.
  6. **Giới hạn đã biết của V0.1**:
     - CSV chỉ nhận UTF-8 và dấu phẩy (khi lưu từ Excel, chọn "CSV UTF-8");
     - `number` không nhận dấu phân cách hàng nghìn;
     - process chạy đồng bộ;
     - `unique` giữ các giá trị đã gặp trong RAM;
     - session tự xoá sau 24h không có lệnh ghi;
     - không CORS.
  7. **Chạy test**: `cd apps/api && ./mvnw verify` (cần Docker cho Testcontainers).
- [ ] 9.4 Làm theo README từ đầu trên máy local, gồm cả phần curl. Chỗ nào không chạy được thì sửa README, rồi làm lại.
- [ ] 9.5 Commit: `docs: root README with demo flow and environment variables`

## 10. Kiểm tra toàn bộ và hoàn tất

- [ ] 10.1 Chạy `./mvnw -q verify`. Mong đợi: toàn bộ test F01–F11 xanh, gồm cả ArchitectureTest (`@EnableScheduling` và `@Scheduled` chỉ ở `infrastructure`).
- [ ] 10.2 Chạy app thật với `IMPORTER_SESSION_TTL=1m`. Tạo một session, chờ khoảng 2 phút rồi restart app. Kiểm log có dòng cleanup với `deletedSessions` ≥ 1, và `GET` session đó trả 404. Kiểm log không chứa nội dung file.
- [ ] 10.3 Tick đủ checkbox; chỗ nào làm khác kế hoạch thì gạch và ghi LÝ DO. Commit: `docs(openspec): complete be-f11 tasks`
- [ ] 10.4 Hỏi người dùng trước khi merge vào `main`. Sau khi merge: `openspec archive be-f11-integration -y`, commit phần archive. Khi đó `openspec/specs/` phản ánh đầy đủ hệ thống V0.1 đang chạy.
