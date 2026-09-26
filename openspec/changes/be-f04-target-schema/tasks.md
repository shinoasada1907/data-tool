# BE-F04 Target Schema Builder — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** `PUT /schema` định nghĩa schema đích độc lập domain. Kèm theo khung cấu hình dùng chung cho F05–F08: lưu cấu hình, auto-prune, readiness, khoá theo session, chuyển trạng thái, `configHash`.

**Architecture:**
- `domain.schema`: `FieldType`, `TargetField`, `FieldSpec`, `TargetSchema.define`.
- `domain.config`: `ImportConfiguration`, `FieldScopedSection`, `ConfigPruner`, `Readiness*`, các port `ImportConfigurationRepository` và `ConfigHasher`.
- `application.configuration.ConfigurationService`: luồng cập nhật cấu hình dùng chung (design S6), khoá bằng `application.common.SessionLocks`.
- `infrastructure.persistence`: bảng `import_configuration` (V3) và `JsonConfigHasher`.
- `api.schema.SchemaController`.
- `ImportSessionDto` có thêm `config` và `readiness`.

**Tech Stack:** Như F01–F03.

**Spec:** `openspec/changes/be-f04-target-schema/specs/{target-schema,import-session}/spec.md`. Thiết kế: `design.md` cùng thư mục (S1–S9) và `be-f01-import-session/design.md` (D1–D14).

## Global Constraints

- Mọi ràng buộc chung của F01 và F02 vẫn áp dụng.
- F02 phải đã xong và nằm trong nhánh này. F03 không bắt buộc: tạo `feature/be-f04-target-schema` từ nhánh mới nhất đã có F02.
- Mọi item lỗi và warning là `ProblemItem(field, code, message)` của F01; `code` là tên enum (`SCHEMA_INVALID`, `CONFIG_PRUNED`, `SCHEMA_EMPTY`, …).
- Khoá theo session bao ngoài transaction (S5): nhả khoá sau khi commit xong.
- `now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)`.

---

## 1. Model schema đích

**Files:**
- Create: `MAIN/domain/schema/FieldType.java`, `TargetField.java`, `FieldSpec.java`, `TargetSchema.java`
- Test: `TEST/domain/schema/FieldTypeTest.java`, `TEST/domain/schema/TargetSchemaTest.java`

**Interfaces:**
- Produces: như design S1.

- [x] 1.1 Viết `FieldTypeTest`:
  - `fromCode` với `string`, `number`, `boolean`, `date`, `email` → đúng enum tương ứng.
  - `fromCode` với `String`, `text`, `""`, `null` → `Optional.empty()`.
  - `code()` trả mã chữ thường.
- [x] 1.2 Viết `TargetSchemaTest`. Đặt `f(name, type, required, order) = new FieldSpec(...)`:
  | Input `define` | Mong đợi |
  |---|---|
  | `[f(" email ","email",true,5), f("name","string",false,1)]` | fields `[("name",STRING,false,0), ("email",EMAIL,true,1)]` |
  | `[]` | `SCHEMA_INVALID`; items `[(null, "Schema must contain at least one field.")]` |
  | `[f("   ","string",false,0)]` | items `[(null, "Field name must not be blank.")]` |
  | `[f(null,"string",false,0)]` | items `[(null, "Field name must not be blank.")]` |
  | `[f("a".repeat(101),"string",false,0)]` | 1 item, message `Field name must be at most 100 characters.` |
  | `[f("a".repeat(100),"string",false,0)]` | hợp lệ |
  | `[f("Email","string",false,0), f("email","string",false,1)]` | items `[("email", "Duplicate field name.")]` |
  | `[f("note","text",false,0)]` | items `[("note", "Unknown field type.")]` |
  | `[f("a","string",false,null)]` | items `[("a", "Field order is required.")]` |
  | `[f("a","string",false,1), f("b","string",false,1)]` | items `[("b", "Duplicate field order.")]` |
  | `[f("","string",false,0), f("age","int",false,1), f("AGE","number",false,2)]` | 3 item theo đúng thứ tự: blank, unknown type, duplicate name |

  Mọi item có `code` = `SCHEMA_INVALID`. Exception có message `Target schema is invalid.`. Thêm các case: `fieldNames()` trả đúng tập tên; `field("email")` tìm thấy; `field("Email")` không thấy (so khớp chính xác); `empty().isEmpty()` là true.
- [x] 1.3 Chạy `./mvnw -q test -Dtest=FieldTypeTest,TargetSchemaTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 1.4 Cài 4 class theo design S1.
- [x] 1.5 Chạy lại lệnh ở 1.3. Mong đợi: PASS.
- [x] 1.6 Commit: `feat(domain): target schema with field name, type and order rules`

## 2. Khung cấu hình: prune và readiness

**Files:**
- Create: `MAIN/domain/config/ImportConfiguration.java`, `ConfigChange.java`, `FieldScopedSection.java`, `ConfigPruner.java`, `WarningCode.java`, `ReadinessIssueCode.java`, `Readiness.java`, `ReadinessRule.java`, `SchemaNotEmptyRule.java`, `ReadinessEvaluator.java`, `ImportConfigurationRepository.java`, `ConfigHasher.java`
- Test: `TEST/domain/config/ConfigPrunerTest.java`, `TEST/domain/config/ReadinessEvaluatorTest.java`, `TEST/domain/config/ImportConfigurationTest.java`

**Interfaces:**
- Produces: như design S2, S3, S4, S7, cộng thêm:
  ```java
  public interface ImportConfigurationRepository {
      Optional<ImportConfiguration> findBySessionId(UUID sessionId);
      ImportConfiguration save(ImportConfiguration configuration, Instant now);   // trả bản có version mới
  }
  public interface ConfigHasher { String hash(ImportConfiguration configuration); }
  ```

- [x] 2.1 Viết `ConfigPrunerTest`. Trong test tạo một record giả `FakeSection(Set<String> fields) implements FieldScopedSection<FakeSection>` với `sectionLabel()` là `"Mapping"`:
  | Case | Mong đợi |
  |---|---|
  | `prune(FakeSection{a,b,c}, {a}, warnings)` | trả `FakeSection{a}`. `warnings` = `[("b","CONFIG_PRUNED","Mapping for this field was removed because the field no longer exists."), ("c", …)]`, xếp theo tên field |
  | `prune(FakeSection{a}, {a,b}, warnings)` | giữ nguyên; `warnings` rỗng |
  | `prune(FakeSection{Email}, {email}, warnings)` | `Email` bị bỏ, vì so khớp chính xác |
- [x] 2.2 Viết `ReadinessEvaluatorTest` với `ReadinessEvaluator.standard()`:
  | Config | Mong đợi |
  |---|---|
  | `empty(id)` | `ready` false; issues `[(null,"SCHEMA_EMPTY","Target schema has no fields.")]` |
  | Schema có 1 field optional | `ready` true; issues rỗng |
- [x] 2.3 Viết `ImportConfigurationTest`: `empty(id).withSchema(schema)` → `configuration.schema()` là `schema`; `warnings` rỗng; `version` giữ nguyên `null`.
- [x] 2.4 Chạy `./mvnw -q test -Dtest=ConfigPrunerTest,ReadinessEvaluatorTest,ImportConfigurationTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 2.5 Cài 12 file ở phần Files.
- [x] 2.6 Chạy lại lệnh ở 2.4. Mong đợi: PASS. Chạy thêm `./mvnw -q test -Dtest=ArchitectureTest`: PASS.
- [x] 2.7 Commit: `feat(domain): configuration aggregate with auto-prune and readiness`

## 3. Persistence cấu hình (Flyway V3) và `JsonConfigHasher`

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V3__create_import_configuration.sql` (nội dung ở design S8), `MAIN/infrastructure/persistence/ImportConfigurationEntity.java`, `ImportConfigurationJpaRepository.java`, `JpaImportConfigurationRepository.java`, `TargetSchemaDocument.java`, `JsonConfigHasher.java`
- Test: `TEST/infrastructure/persistence/JpaImportConfigurationRepositoryTest.java`, `TEST/infrastructure/persistence/JsonConfigHasherTest.java`

**Interfaces:**
- Consumes: `ImportConfiguration`, `ImportConfigurationRepository`, `ConfigHasher` (task 2); `TargetSchema` (task 1).
- Produces:
  ```java
  public record TargetSchemaDocument(List<FieldDocument> fields) {
      public record FieldDocument(String name, String type, boolean required, int order) {}
      public static TargetSchemaDocument from(TargetSchema schema);
      public TargetSchema toDomain();
  }
  @Repository public class JpaImportConfigurationRepository implements ImportConfigurationRepository { … }
  @Component public class JsonConfigHasher implements ConfigHasher { public JsonConfigHasher(JsonMapper jsonMapper); }
  ```

- [ ] 3.1 Viết `JpaImportConfigurationRepositoryTest` (`@DataJpaTest`, `replace = NONE`, `@Import({TestcontainersConfiguration.class, JpaImportConfigurationRepository.class, JpaImportSessionRepository.class})`). Trước mỗi case, lưu một session thật để có khoá ngoại.
  | Case | Mong đợi |
  |---|---|
  | `save(empty(id).withSchema(schema[name:string, email:email required]).configuration(), t0)` rồi `findBySessionId(id)` | schema đọc ra bằng bản đã lưu; `version` 0 |
  | `findBySessionId(randomUUID)` | `Optional.empty()` |
  | Lưu lần 2 với schema khác, dùng bản vừa đọc ra | `version` 1; schema mới |
  | `save` cho `sessionId` không có trong `import_session` | ném exception về khoá ngoại (`DataIntegrityViolationException`) |
  | Sau lần lưu đầu, đọc thẳng cột `mapping_json` bằng `JdbcTemplate` | `{"mappings": []}` (giá trị mặc định) |
- [ ] 3.2 Viết `JsonConfigHasherTest` với `JsonMapper` thật (`JsonMapper.builder().build()`):
  | Case | Mong đợi |
  |---|---|
  | Hash cùng một cấu hình hai lần | hai chuỗi giống nhau; dài 64; khớp regex `[0-9a-f]{64}` |
  | Hai cấu hình chỉ khác `required` của một field | hai hash khác nhau |
  | Hai cấu hình cùng nội dung nhưng khác `sessionId` và `version` | hai hash giống nhau |
- [ ] 3.3 Chạy `./mvnw -q test -Dtest=JpaImportConfigurationRepositoryTest,JsonConfigHasherTest`. Mong đợi: FAIL.
- [ ] 3.4 Cài migration, entity, document, adapter và hasher. Cột JSON map theo cách F02 đã chốt (P7 hoặc phương án dự phòng, xem tasks.md của F02).
- [ ] 3.5 Chạy lại lệnh ở 3.3. Mong đợi: PASS.
- [ ] 3.6 Commit: `feat(infra): persist import configuration (Flyway V3) and hash it`

## 4. Khoá theo session

**Files:**
- Create: `MAIN/application/common/SessionLocks.java`
- Test: `TEST/application/common/SessionLocksTest.java`

**Interfaces:**
- Produces: `@Component public class SessionLocks { public <T> T withLock(UUID sessionId, Supplier<T> action); }`

- [ ] 4.1 Viết `SessionLocksTest`:
  | Case | Mong đợi |
  |---|---|
  | Hai thread cùng `withLock(id, …)`, mỗi action ngủ 100ms và ghi thời điểm bắt đầu/kết thúc | hai khoảng thời gian không chồng nhau |
  | Hai thread với hai id khác nhau, dùng `CountDownLatch` để cả hai action chờ nhau | cả hai hoàn tất trong 2 giây (không bị khoá lẫn nhau) |
  | Action ném `RuntimeException` | exception được ném lại; `withLock(id, …)` lần sau vẫn chạy được (khoá đã nhả) |
  | `withLock(id, () -> withLock(id, () -> 1))` | trả `1` (khoá reentrant) |
- [ ] 4.2 Chạy `./mvnw -q test -Dtest=SessionLocksTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 4.3 Cài bằng `ConcurrentHashMap<UUID, ReentrantLock>`, `lock()` rồi `finally unlock()`.
- [ ] 4.4 Chạy lại lệnh ở 4.2. Mong đợi: PASS.
- [ ] 4.5 Commit: `feat(app): per-session write locks`

## 5. Luồng cập nhật cấu hình và `updateSchema`

**Files:**
- Create: `MAIN/application/configuration/ConfigurationService.java`, `MAIN/application/configuration/ConfigUpdateResult.java`, `MAIN/application/importsession/SessionDetails.java`
- Modify: `MAIN/application/importsession/ImportSessionService.java` (`upload` trả `SessionDetails`; thêm `details(UUID)`; bỏ `get(UUID)` nếu không còn chỗ dùng)
- Test: `TEST/application/configuration/ConfigurationServiceTest.java`, `TEST/support/InMemoryImportConfigurationRepository.java`; sửa `TEST/application/importsession/ImportSessionServiceTest.java`

**Interfaces:**
- Consumes: `SessionLocks` (task 4); `ImportConfigurationRepository`, `ConfigHasher`, `ReadinessEvaluator` (task 2); `ImportSessionRepository` (F01).
- Produces:
  ```java
  public record ConfigUpdateResult(ImportSession session, ImportConfiguration configuration, Readiness readiness,
                                   List<ProblemItem> warnings) {}
  public record SessionDetails(ImportSession session, ImportConfiguration configuration, Readiness readiness) {}
  @Service public class ConfigurationService {
      public ConfigurationService(ImportSessionRepository sessions, ImportConfigurationRepository configurations,
                                  ConfigHasher hasher, SessionLocks locks, TransactionTemplate transactions, Clock clock);
      public ConfigUpdateResult updateSchema(UUID sessionId, List<FieldSpec> fields);
      // private ConfigUpdateResult update(UUID sessionId, Function<ImportConfiguration, ConfigChange> mutation);   // design S6
  }
  ```
- Test dùng `TransactionTemplate` với một `PlatformTransactionManager` giả (chỉ gọi callback) và `ConfigHasher` giả (`c -> c.schema().toString()`).

- [ ] 5.1 Viết `ConfigurationServiceTest`:
  | Case | Mong đợi |
  |---|---|
  | Session `CONFIGURING`, `updateSchema(id, [f("note","string",false,0)])` | `session.status` `READY`; `readiness.ready` true; `warnings` rỗng; repository cấu hình có schema mới |
  | Session `READY`, `updateSchema(id, [])` | ném `SCHEMA_INVALID`; session vẫn `READY`; cấu hình không đổi |
  | Session `UPLOADED` | ném `SESSION_STATE_INVALID`, message `Source file has not been inspected.` |
  | Session `FAILED` | ném `SESSION_STATE_INVALID`, message `Session has failed and cannot be changed.` |
  | Id không tồn tại | ném `SESSION_NOT_FOUND` |
  | Session `PROCESSED`, cấu hình đang có schema S, `updateSchema` với đúng S | `session.status` vẫn `PROCESSED`; `updatedAt` không đổi |
  | Session `PROCESSED`, `updateSchema` với schema khác | `session.status` `READY` |
  | Sau mỗi lần gọi | action chạy bên trong `SessionLocks.withLock(id, …)` (dùng một `SessionLocks` giả để ghi lại) |
- [ ] 5.2 Sửa `ImportSessionServiceTest`:
  - `upload` hợp lệ → `SessionDetails` có `configuration.schema().isEmpty()` true; `readiness` có issue `SCHEMA_EMPTY`.
  - `details(id)` sau `updateSchema` → trả cấu hình đã lưu.
  - `details(randomUUID)` → `SESSION_NOT_FOUND`.
- [ ] 5.3 Chạy `./mvnw -q test -Dtest=ConfigurationServiceTest,ImportSessionServiceTest`. Mong đợi: FAIL.
- [ ] 5.4 Cài theo design S6. `SessionDetails` được lắp từ `session`, `configurations.findBySessionId(...).orElse(empty)` và `ReadinessEvaluator.standard().evaluate(...)`.
- [ ] 5.5 Chạy lại lệnh ở 5.3. Mong đợi: PASS.
- [ ] 5.6 Commit: `feat(app): shared configuration update flow and PUT schema use case`

## 6. API: PUT /schema; session có thêm config và readiness

**Files:**
- Create: `MAIN/api/schema/SchemaController.java`, `MAIN/api/schema/TargetSchemaDto.java`, `MAIN/api/schema/TargetFieldDto.java`, `MAIN/api/importsession/ConfigUpdateResponseDto.java`, `MAIN/api/importsession/SessionConfigDto.java`, `MAIN/api/importsession/ReadinessDto.java`
- Modify: `MAIN/api/importsession/ImportSessionDto.java` (thêm `config` và `readiness`; `from(SessionDetails)`), `MAIN/api/importsession/ImportSessionController.java` (dùng `details`)
- Test: `TEST/api/schema/SchemaControllerTest.java`; sửa `TEST/api/importsession/ImportSessionControllerTest.java`

**Interfaces:**
- Consumes: `ConfigurationService`, `SessionDetails` (task 5).
- Produces:
  ```java
  public record TargetSchemaDto(@NotNull List<@NotNull @Valid TargetFieldDto> fields) {
      public static TargetSchemaDto from(TargetSchema schema);
      public List<FieldSpec> toSpecs();
  }
  public record TargetFieldDto(String name, String type, boolean required, Integer order) {}
  public record SessionConfigDto(TargetSchemaDto schema) {}             // F05–F07 thêm mapping, transformations, validations
  public record ReadinessDto(boolean ready, List<ProblemItem> issues) {}
  public record ConfigUpdateResponseDto(ImportSessionDto session, List<ProblemItem> warnings) {}
  // PUT /api/import-sessions/{id}/schema → 200 ConfigUpdateResponseDto
  ```

- [ ] 6.1 Viết `SchemaControllerTest` (`@WebMvcTest(SchemaController.class)`, `@MockitoBean ConfigurationService`):
  | Request | Stub | Mong đợi |
  |---|---|---|
  | PUT `{"fields":[{"name":"email","type":"email","required":true,"order":0}]}` | trả kết quả `READY` | 200; `$.session.status` `READY`; `$.session.config.schema.fields[0].name` `email`; `$.session.readiness.ready` true; `$.warnings` `[]`. Service nhận đúng `FieldSpec("email","email",true,0)` |
  | PUT `{}` | — | 400; `$.code` `REQUEST_INVALID` |
  | PUT `{"fields":[{"name":"a","required":"yes","order":0}]}` | — | 400; `$.code` `REQUEST_INVALID` |
  | PUT body không phải JSON | — | 400; `$.code` `REQUEST_INVALID` |
  | PUT hợp lệ | ném `SCHEMA_INVALID` kèm item `("email","SCHEMA_INVALID","Duplicate field name.")` | 422; `$.errors[0].field` `email` |
  | PUT hợp lệ | ném `SESSION_NOT_FOUND` | 404; `$.code` `SESSION_NOT_FOUND` |
  | PUT hợp lệ | ném `SESSION_STATE_INVALID` | 409; `$.code` `SESSION_STATE_INVALID` |
  | PUT `{"fields":[{"name":"a","type":"string","order":0}]}` (không có `required`) | trả kết quả bất kỳ | service nhận `required` = false |
- [ ] 6.2 Sửa `ImportSessionControllerTest`:
  - upload và `GET /{id}` trả `$.config.schema.fields` (mảng), `$.readiness.ready`, `$.readiness.issues[0].code` `SCHEMA_EMPTY`;
  - `GET` với id không tồn tại vẫn trả 404 `SESSION_NOT_FOUND`.
- [ ] 6.3 Chạy `./mvnw -q test -Dtest=SchemaControllerTest,ImportSessionControllerTest`. Mong đợi: FAIL.
- [ ] 6.4 Cài các DTO và controller. Sửa `ImportSessionDto.from(SessionDetails)` và `ImportSessionController`.
- [ ] 6.5 Chạy lại lệnh ở 6.3. Mong đợi: PASS.
- [ ] 6.6 Commit: `feat(api): PUT schema endpoint; sessions expose config and readiness`

## 7. Integration test qua HTTP thật

**Files:**
- Test: `TEST/api/schema/SchemaIntegrationTest.java` (setup như các integration test trước)

- [ ] 7.1 Viết các case. Mọi case bắt đầu bằng upload `customers.csv` = `name,email\nAn,an@x.com\n`, trừ khi có ghi khác.
  | Case | Mong đợi |
  |---|---|
  | Upload rồi GET | `readiness.issues[0].code` `SCHEMA_EMPTY`; `status` `CONFIGURING` |
  | PUT schema hợp lệ gồm 5 field với 5 kiểu | 200; `status` `READY`. GET trả đúng 5 field, `order` 0..4 |
  | PUT schema có `Email` và `email` | 422; `errors[0].message` `Duplicate field name.`; GET cho thấy schema cũ không đổi |
  | PUT schema cho UUID chưa từng tạo | 404; `code` `SESSION_NOT_FOUND` |
  | Hai PUT schema đồng thời (hai thread, schema `a` và schema `b`) | cả hai 200. GET trả schema khớp response hoàn tất sau |
- [ ] 7.2 Chạy `./mvnw -q test -Dtest=SchemaIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính, không nới lỏng test.
- [ ] 7.3 Commit: `test(api): target schema over real HTTP`

## 8. Kiểm tra toàn bộ và hoàn tất

- [ ] 8.1 Chạy `./mvnw -q verify`. Mong đợi: mọi test xanh, gồm ArchitectureTest.
- [ ] 8.2 Chạy app thật và thử bằng `curl`:
  - upload một CSV;
  - PUT schema hợp lệ, rồi PUT lại với tên trùng;
  - GET session, xem `config` và `readiness`.
- [ ] 8.3 Tick checkbox, ghi LÝ DO cho mọi chỗ làm khác kế hoạch. Commit: `docs(openspec): complete be-f04 tasks`
- [ ] 8.4 Hỏi người dùng trước khi merge. Sau khi merge: `openspec archive be-f04-target-schema -y`.
