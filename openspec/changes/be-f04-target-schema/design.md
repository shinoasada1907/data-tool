## Context

- Nền chung: `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md`. Change này cài đặt:
  - D2 (chuyển trạng thái khi PUT);
  - D3 (PUT ghi đè toàn bộ, trả `{session, warnings}`);
  - D4 (`SCHEMA_INVALID`, `SESSION_STATE_INVALID`);
  - D7 (`configHash`);
  - D8 (bảng `import_configuration`, JSON do app tự serialize);
  - D10 (luật tên field, prune);
  - D11 (khoá theo session).
- F02 đã có `ImportSession.sourceSchema()` và cách lưu cột jsonb (P7, có phương án dự phòng nếu thiếu FormatMapper).

## Goals / Non-Goals

**Goals:**
- Schema đích độc lập domain, đúng luật D10; báo mọi lỗi trong một lần.
- Một luồng cập nhật cấu hình dùng chung cho cả 4 PUT (schema ở F04; mapping, transformations, validations ở F05–F07).
- `config` và `readiness` luôn đi kèm session trong response.

**Non-Goals:**
- Mapping, transformation, validation: F05–F07 thêm vào khung này.
- Xoá `result/` khi config đổi: F08 gắn vào đúng chỗ đã chừa trong luồng cập nhật (S6).
- Giữ cấu hình khi đổi tên field: đổi tên được xử lý như xoá rồi thêm (D10).

## Decisions

### S1. Model schema trong `domain.schema`
```java
public enum FieldType { STRING("string"), NUMBER("number"), BOOLEAN("boolean"), DATE("date"), EMAIL("email");
    public String code(); public static Optional<FieldType> fromCode(String code); }   // so khớp chính xác, chữ thường
public record TargetField(String name, FieldType type, boolean required, int order) {}
public record FieldSpec(String name, String type, boolean required, Integer order) {}      // input thô, chưa kiểm
public record TargetSchema(List<TargetField> fields) {
    public static TargetSchema empty();
    public static TargetSchema define(List<FieldSpec> specs);   // lỗi → DomainException(SCHEMA_INVALID, "Target schema is invalid.", items)
    public Optional<TargetField> field(String name);            // so khớp chính xác
    public Set<String> fieldNames();
    public boolean isEmpty();
}
```
`define` gom **mọi** lỗi, xét theo thứ tự input, rồi mới ném:

| Lỗi | `field` của item | `message` |
|---|---|---|
| Danh sách rỗng | `null` | `Schema must contain at least one field.` |
| Tên trống sau khi trim | `null` | `Field name must not be blank.` |
| Tên dài hơn 100 ký tự | tên đã trim | `Field name must be at most 100 characters.` |
| Trùng tên (không phân biệt hoa thường) với field đứng trước | tên đã trim | `Duplicate field name.` |
| `type` không thuộc 5 mã | tên đã trim | `Unknown field type.` |
| `order` null | tên đã trim | `Field order is required.` |
| `order` trùng với field đứng trước | tên đã trim | `Duplicate field order.` |

Mọi item có `code` = `SCHEMA_INVALID`. Khi hợp lệ: tên đã trim, field sắp theo `order` tăng dần, `order` được đánh lại 0..n-1.

### S2. `ImportConfiguration` là một aggregate riêng, quan hệ 1:1 với session
```java
public record ImportConfiguration(UUID sessionId, TargetSchema schema, Long version) {   // F05–F07 thêm mapping, transformations, validations
    public static ImportConfiguration empty(UUID sessionId);
    public ConfigChange withSchema(TargetSchema schema);   // prune mọi section theo schema mới
}
public record ConfigChange(ImportConfiguration configuration, List<ProblemItem> warnings) {}
```
- Nếu chưa có row trong DB thì coi như `empty(sessionId)`. Row được tạo ở lần PUT đầu tiên, nên upload không phải sửa gì.
- *Phương án khác*: gộp cấu hình vào `ImportSession`. Loại, vì cấu hình lớn dần qua mỗi feature, và việc đọc session (preview, list) không cần tới nó.

### S3. Khung auto-prune
```java
public interface FieldScopedSection<S extends FieldScopedSection<S>> {
    String sectionLabel();                 // "Mapping", "Transformations", "Validation rules"
    Set<String> referencedFields();
    S retainFields(Set<String> fieldNames);
}
public final class ConfigPruner {
    public static <S extends FieldScopedSection<S>> S prune(S section, Set<String> fieldNames, List<ProblemItem> warnings);
}
```
- Mỗi field bị bỏ sinh một warning `ProblemItem(field, "CONFIG_PRUNED", "<sectionLabel> for this field was removed because the field no longer exists.")`. Warning của một section xếp theo tên field.
- So khớp theo tên chính xác (phân biệt hoa thường), vì tên field là key trong JSON output.
- Ở F04 chưa có section nào, nên `withSchema` chỉ thay schema và `warnings` rỗng.
- F07 tự xử lý thêm trường hợp đổi kiểu làm rule `email` mất hiệu lực (D10).

### S4. Readiness
```java
public record Readiness(boolean ready, List<ProblemItem> issues) {}      // ready ⇔ issues rỗng
public interface ReadinessRule { List<ProblemItem> check(ImportConfiguration configuration); }
public final class ReadinessEvaluator {
    public static ReadinessEvaluator standard();   // F04: [SchemaNotEmptyRule]; F05 thêm RequiredFieldsMappedRule
    public Readiness evaluate(ImportConfiguration configuration);
}
```
- `SchemaNotEmptyRule`: schema rỗng → `ProblemItem(null, "SCHEMA_EMPTY", "Target schema has no fields.")`.
- Mã warning và mã issue là enum trong `domain.config`, dùng `.name()` khi tạo `ProblemItem`:
  - `WarningCode { CONFIG_PRUNED, TARGET_FIELD_UNMAPPED, RULE_IMPLIED_BY_SCHEMA }`
  - `ReadinessIssueCode { SCHEMA_EMPTY, TARGET_FIELD_REQUIRED }`

### S5. `SessionLocks`, trong `application.common`
- ~~Cài bằng `ConcurrentHashMap<UUID, ReentrantLock>`~~ → cài bằng **bảng khoá cố định** 1024 `ReentrantLock`, chọn khoá theo `Math.floorMod(sessionId.hashCode(), 1024)`. Hàm vẫn là `<T> T withLock(UUID sessionId, Supplier<T> action)`.
  **LÝ DO** (review 2026-09-26): map tạo entry trước khi biết session có tồn tại hay không. Client gửi UUID bừa thì map phình mãi (nguy cơ OOM). `forget` của F11 không dọn được các id đó, và còn có race: xoá khoá lúc một thread khác đang giữ nó, sẽ có hai khoá cho cùng một session. Bảng cố định giới hạn được bộ nhớ và không cần dọn. Cái giá là hai session trùng ô phải chờ nhau (xác suất 1/1024); với V0.1 như vậy là chấp nhận được.
- Khoá bao **ngoài** transaction, và chỉ nhả sau khi commit xong, để request sau luôn đọc được dữ liệu đã commit.
- ~~F11 thêm `forget(UUID)` khi dọn session.~~ Không cần nữa, vì bảng khoá không lớn lên.
- Không bao giờ giữ hai khoá session cùng lúc: thứ tự lấy khoá không cố định nên có thể deadlock.
- V0.1 chỉ chạy một instance (D11).

### S6. Luồng cập nhật cấu hình dùng chung: `ConfigurationService`
```
withLock(id) → transactionTemplate.execute:
  1. session = sessions.findById(id)                                → SESSION_NOT_FOUND (404)
  2. status ∈ {UPLOADED, FAILED}                                    → SESSION_STATE_INVALID (409)
       message: "Source file has not been inspected." | "Session has failed and cannot be changed."
  3. config = configurations.findBySessionId(id).orElse(empty(id))
  4. change = mutation.apply(config)                                → SCHEMA_INVALID... (422); lỗi thì rollback, không lưu gì
  5. changed = !hasher.hash(config).equals(hasher.hash(change.configuration()))
  6. readiness = evaluator.evaluate(change.configuration())
  7. if (session.status() == PROCESSED && !changed) → giữ nguyên trạng thái
     else session.transitionTo(readiness.ready() ? READY : CONFIGURING, now)
     // F08: khi status cũ là PROCESSED và changed → xoá result/ tại đây
  8. lưu configuration và session → trả ConfigUpdateResult(session, configuration, readiness, warnings)
```
- Mỗi PUT là một hàm public gọi luồng chung này, truyền vào hàm `mutation` riêng. F04 có `updateSchema(UUID, List<FieldSpec>)`.
- `now = Instant.now(clock).truncatedTo(MICROS)`.

### S7. `ConfigHasher`
- Port nằm ở `domain.config`: `String hash(ImportConfiguration)`.
- Cài đặt `infrastructure.persistence.JsonConfigHasher`: serialize **các document lưu trữ** (S8) theo thứ tự cố định `schema, mapping, transformations, validations` bằng một `JsonMapper` bật `ORDER_MAP_ENTRIES_BY_KEYS`, rồi lấy SHA-256 dạng hex chữ thường (64 ký tự).
- Không đưa `sessionId`, `version`, `updatedAt` vào hash: hash chỉ phản ánh nội dung cấu hình.

### S8. Persistence (V3)
```sql
CREATE TABLE import_configuration (
    session_id           UUID        PRIMARY KEY REFERENCES import_session (id) ON DELETE CASCADE,
    target_schema_json   JSONB       NOT NULL DEFAULT '{"fields": []}',
    mapping_json         JSONB       NOT NULL DEFAULT '{"mappings": []}',
    transformations_json JSONB       NOT NULL DEFAULT '{"transformations": []}',
    validations_json     JSONB       NOT NULL DEFAULT '{"validations": []}',
    version              BIGINT      NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);
```
- **Document** ở `infrastructure.persistence`: `TargetSchemaDocument(List<FieldDocument> fields)`, trong đó `FieldDocument(String name, String type, boolean required, int order)` và `type` là mã chữ thường.
- **Cột chưa dùng**: F04 chưa đọc hay ghi 3 cột JSON còn lại; khi insert, các cột này lấy giá trị mặc định. F05–F07 mỗi feature thêm document cho cột của mình.
- **Cách map cột JSON**: giống F02 P7, gồm cả phương án dự phòng.
- **Xoá theo dây chuyền**: `ON DELETE CASCADE` giúp F11 xoá session là xoá luôn cấu hình.

### S9. API
- **Endpoint**: `PUT /api/import-sessions/{id}/schema` nằm trong `api.schema.SchemaController`.
  - Body: `TargetSchemaDto(@NotNull List<@NotNull @Valid TargetFieldDto> fields)`.
  - `TargetFieldDto(String name, String type, boolean required, Integer order)`. Thiếu `required` thì mặc định `false`.
  - JSON hỏng, thiếu `fields`, hoặc sai kiểu JSON → 400 `REQUEST_INVALID`. Mọi luật nội dung → 422 `SCHEMA_INVALID`.
- **Response**: `ConfigUpdateResponseDto(ImportSessionDto session, List<ProblemItem> warnings)`.
- **`ImportSessionDto`** có thêm:
  - `SessionConfigDto config`: ở F04 là `{ schema: TargetSchemaDto }`, trong đó `fields` đã sắp và `order` đã chuẩn hoá;
  - `ReadinessDto readiness`: `{ ready, issues }`.
- **Thay đổi ở tầng application**:
  - `ImportSessionService.upload` trả `SessionDetails(session, configuration, readiness)`.
  - Thêm `details(UUID)`, thay cho `get(UUID)` ở controller.
  - Upload trả `config.schema.fields = []` và `readiness = {ready: false, issues: [SCHEMA_EMPTY]}`.

## Risks / Trade-offs

- ~~[Map khoá trong `SessionLocks` không bao giờ nhỏ lại cho tới F11] → Mỗi entry chỉ vài chục byte. F11 gọi `forget` khi dọn session.~~ Đã thay bằng bảng khoá cố định (xem S5).
- [Hai session trùng ô khoá thì chờ nhau] → Chỉ làm chậm, không sai. Xác suất 1/1024 cho mỗi cặp session ghi cùng lúc.
- [Đổi tên field làm mất cấu hình của field đó] → Đúng như D10. Warning `CONFIG_PRUNED` báo rõ. FE đã có cơ chế PUT lại theo thứ tự.
- [Khoá chỉ có tác dụng trong một JVM] → V0.1 chỉ chạy một instance. Cột `version` là lớp bảo vệ thứ hai.

## Migration Plan

- Flyway V3 tạo bảng mới. Session có sẵn không có row cấu hình, nên được coi là cấu hình rỗng (S2).

## Open Questions

(không có)
