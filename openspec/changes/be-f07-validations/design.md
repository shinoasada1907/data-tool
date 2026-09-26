## Context

- Quyết định nền nằm ở `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md` (D1–D14, API contract V0.1). File này chỉ ghi phần riêng của F07. Các điểm dẫn chiếu:
  - D10: thứ tự rule, luật type, regex email, `unique` hai pha, rule suy ra từ schema, prune.
  - D4: `CONFIG_INVALID` và warning code.
  - D11: khoá.
- Pack System Design §9 phác contract `ValidationRule { String type(); ValidationResult validate(Object value, ValidationContext context); }`, kèm hai ý: registry gồm `required`, `type`, `email`, `unique`; và "`unique` có session-level context để nhớ value đã gặp".

### Giả định về F02–F06 (chưa merge)

Task 1 trong tasks.md đối chiếu các giả định này với code đã merge rồi sửa lại cho khớp.

> **Đã đối chiếu (2026-09-26):** bảng kết quả ở task 1 của `tasks.md`.
> - ~~`SessionConfiguration`~~ → `ImportConfiguration`.
> - Khung prune là `FieldScopedSection` / `ConfigPruner` / `Pruned`.
> - Thêm phần lưu và hash `validations_json`.
> - Controller đặt ở `api.validation`.

- **F04**:
  - `TargetSchema`, `TargetField(name, type, required, order)`, `FieldType { STRING, NUMBER, BOOLEAN, DATE, EMAIL }`.
  - Aggregate config (`SessionConfiguration`) lưu trong `import_configuration` (V3).
  - Service cấu hình session (`ConfigurationService`) với hàm cập nhật dùng chung: khoá, kiểm `FAILED`, lưu, tính readiness/status, trả `ConfigUpdateResult` (F04).
  - Khung prune.
- **F06**: `RowErrorCode`, `TextValues.isEmpty`, `EngineConfig`.
- **F08**: nhận các thứ F07 cung cấp và điều phối chúng:
  - Gọi `FieldValidator` cho từng field **sau** transformation (field có transformation lỗi thì bỏ qua).
  - Cuối mỗi row, gọi `UniqueTracker.commitRow(rowNumber)` nếu row không có lỗi nào, ngược lại gọi `discardRow()`.
  - Mỗi lần chạy pipeline tạo một `UniqueTracker` mới.

## Goals / Non-Goals

**Goals:**
- 4 rule đúng D10, deterministic, mỗi rule có unit test.
- Ép kiểu chính xác. Output hợp lệ không bao giờ có giá trị trùng ở field có `unique`.
- `PUT /validations` khớp FE: payload chỉ gồm `email`/`unique`; `required`/`type` hiển thị ở UI dạng chip chỉ đọc.

**Non-Goals:**
- Rule khác (`regex`, `min`/`max`, `length`, …).
- Parse số theo locale (`1.234,5`), số có dấu phân cách hàng nghìn, số dạng `1e3`: đây là giới hạn đã biết của V0.1.
- `unique` giữ tập giá trị trên đĩa: V0.1 giữ trong RAM, được chặn trên bởi giới hạn upload 20MB.

## Decisions

### V1. Contract
```java
public interface ValidationRule {
    String type();                                                   // "required" | "type" | "email" | "unique"
    ValidationResult validate(Object value, ValidationContext context);
}
public sealed interface ValidationResult {
    record Valid(Object value) implements ValidationResult {}        // value có thể đã được ép kiểu (rule type)
    record Invalid(RowErrorCode code, String message) implements ValidationResult {}
}
public record ValidationContext(String fieldName, FieldType fieldType, int rowNumber, UniqueTracker uniqueTracker) {}
```
- Giữ tên như pack. `Object value` hợp lý ở đây vì giá trị đổi kiểu dọc theo chuỗi rule: trước rule `type` là `String`, sau đó là giá trị đã ép kiểu (`BigDecimal`, `Boolean`, `LocalDate`, `String`).
- Rule `type` là nơi duy nhất ép kiểu. `Valid(value)` của nó được chuyển tiếp cho `email` và `unique`.

### V2. FieldValidator — thứ tự và lỗi đầu tiên
```java
public final class FieldValidator {
    public FieldValidator(ValidationRegistry registry);
    public FieldValidation validate(TargetField field, List<ValidationRuleConfig> userRules,
                                    String value, int rowNumber, UniqueTracker tracker);
}
public record FieldValidation(Object value, ValidationFailure failure) { boolean failed(); }
public record ValidationFailure(String rule, RowErrorCode code, String message) {}
```
1. Nếu `TextValues.isEmpty(value)`:
   - field `required` → lỗi `required` (`VALIDATION_REQUIRED`);
   - ngược lại → `Valid(null)`, bỏ qua mọi rule còn lại.
2. Rule `type` luôn chạy.
3. Rule `email` nếu có trong `userRules`.
4. Rule `unique` nếu có trong `userRules`.

Chỉ trả lỗi đầu tiên gặp phải. Thứ tự rule cố định trong code, không phụ thuộc thứ tự trong payload.

Cách đặt `rule` và `code` cho lỗi:
- Lỗi email **của field kiểu `email`** có `rule = "email"`, `code = VALIDATION_EMAIL` (D10), dù lỗi phát sinh trong rule `type`.
- Các lỗi `type` khác có `rule = "type"`, `code = VALIDATION_TYPE`.

Rule ném `RuntimeException` (bug) → lỗi với `code` là mã của rule đó, `message = "Unexpected error while applying rule '<rule>'."`, log WARN không kèm giá trị. Job tiếp tục chạy.

### V3. Luật parse và ép kiểu (cụ thể hoá D10)
| FieldType | Chấp nhận | Giá trị | Message khi sai |
|---|---|---|---|
| `string` | mọi chuỗi | `String` giữ nguyên, không trim | — |
| `number` | `^-?\d+(\.\d+)?$` (chỉ chữ số ASCII, không trim) | `new BigDecimal(s)` (`"007"` → `7`, `"-3.50"` → `-3.50`) | `Value is not a valid number.` |
| `boolean` | `true`, `false`, `1`, `0`, không phân biệt hoa thường | `Boolean` | `Value is not a valid boolean (true/false/1/0).` |
| `date` | `uuuu-MM-dd`, `ResolverStyle.STRICT` | `LocalDate` | `Value is not a valid date (yyyy-MM-dd).` |
| `email` | `^[^@\s]+@[^@\s]+\.[^@\s]+$` | `String` giữ nguyên | `Value is not a valid email address.` |

- Rule `email` do người dùng thêm (chỉ cho field `string`) dùng cùng regex (`EmailAddresses.PATTERN`) và cùng message, `code = VALIDATION_EMAIL`.
- Message không chứa giá trị ô (D13).

### V4. `unique` hai pha (`UniqueTracker`)
```java
public final class UniqueTracker {
    public Optional<Integer> firstRowOf(String field, Object canonicalValue);   // row đã ghi nhận giá trị này
    public void stage(String field, Object canonicalValue);                     // giữ tạm cho row đang xử lý
    public void commitRow(int rowNumber);                                        // ghi nhận các giá trị đã stage
    public void discardRow();                                                    // bỏ các giá trị đã stage
    public static Object canonical(Object typedValue);
}
```
- **Canonical**: `BigDecimal` → `stripTrailingZeros()` (nên `1.0`, `1.00` và `1` trùng nhau); `LocalDate`, `Boolean` giữ nguyên; `String` so chính xác, phân biệt hoa thường. Muốn không phân biệt thì thêm transformation `lowercase`.
- **Rule `unique`**: gặp giá trị đã được ghi nhận → `Invalid(VALIDATION_UNIQUE, "Duplicate value; first seen in row <n>.")`. Chưa gặp → `stage` rồi trả `Valid`.
- **Chỉ `commitRow` mới ghi nhận giá trị**. F08 gọi hàm này sau khi row không có lỗi nào; row lỗi thì gọi `discardRow`. Nhờ vậy output hợp lệ không có giá trị trùng, và không giá trị nào bị mất chỉ vì một row lỗi trước đó cũng có giá trị ấy (D10).
- Mỗi field có tập giá trị riêng.

### V5. Kiểm và chuẩn hoá cấu hình (`ValidationConfigValidator`)
```java
public ValidationConfigCheck check(ValidationConfig config, TargetSchema schema);
public record ValidationConfigCheck(ValidationConfig effective, List<ProblemItem> warnings, List<ProblemItem> errors) {}
```
- **Lỗi**, mỗi lỗi có `code = CONFIG_INVALID`, trả **tất cả** cùng lúc:
  - targetField không tồn tại;
  - type lạ (không thuộc `required`, `type`, `email`, `unique`);
  - `email` trên field `number`/`boolean`/`date`;
  - rule trùng (cùng field, cùng type);
  - params có key lạ.
- **Warning `RULE_IMPLIED_BY_SCHEMA`**, rule bị loại khỏi `effective`:
  - `required` hoặc `type` → `Rule '<type>' is derived from the schema and was ignored.`
  - `email` trên field kiểu `email` → `Rule 'email' is implied by field type email and was ignored.`
- **`effective`** chỉ gồm `email` và `unique`, sắp theo vị trí field trong schema rồi theo thứ tự `email` → `unique`, để có `configHash` ổn định (D7).
- **400 hay 422**: thiếu mảng `validations` hoặc JSON sai kiểu → 400 `REQUEST_INVALID`. Lỗi về ý nghĩa → 422 `CONFIG_INVALID`.
- **`params` vắng mặt**: thiếu `params` hoặc `params: null` được coi như `{}`, vì FE không gửi `params` cho `email`/`unique`. Việc chuẩn hoá làm trong `ValidationRuleConfig` (`params == null` → `Map.of()`), trước mọi bước kiểm tra.

### V6. Prune khi schema đổi
`ValidationConfig.prunedFor(TargetSchema newSchema)` bỏ:
- rule của field đã bị xoá: `Validation rules removed because field '<f>' no longer exists.`
- rule `email` khi field không còn kiểu `string` (kể cả khi đổi sang `email`): `Rule 'email' removed because field '<f>' is no longer of type string.`

Mỗi lần bỏ kèm warning `CONFIG_PRUNED`. Rule `unique` được giữ khi field đổi kiểu.

### V7. Đăng ký bean
`EngineConfig` (tạo từ F06) thêm `ValidationRegistry(List.of(new RequiredRule(), new TypeRule(), new EmailRule(), new UniqueRule()))`, `FieldValidator` và `ValidationConfigValidator`.

## Risks / Trade-offs

- [Chỉ báo lỗi đầu tiên mỗi field: người dùng sửa xong email mới thấy lỗi unique] → Đổi lại, bảng lỗi gọn hơn và không có lỗi "ăn theo" (ô rỗng không kéo thêm lỗi type). Nếu review đổi quyết định này thì chỉ sửa `FieldValidator`.
- [`number` không trim và không nhận `1,234`] → Người dùng thêm `trim`; dấu phân cách hàng nghìn là giới hạn đã biết (Non-Goals).
- [Tập giá trị `unique` trong RAM] → Được chặn trên bởi giới hạn upload 20MB (D5). Về sau có thể chuyển sang lưu trên đĩa.
- [Tên class của F04/F06 khác giả định] → Task 1 đối chiếu trước khi code.

## Open Questions

- ~~**OQ1** (kế thừa Open Question của be-f01): quy tắc "mỗi field chỉ báo lỗi đầu tiên" cần được xác nhận khi review.~~ **Đã chốt 2026-09-25**: người dùng đồng ý giữ quy tắc này.
- **OQ2**: rule trùng trong payload (ví dụ hai `unique` cho cùng một field) bị từ chối với 422. Có thể dùng cách khác là lặng lẽ gộp; đề xuất từ chối, vì FE vốn đã chặn chuyện này ở UI.
- **OQ3**: `email` gửi cho field kiểu `email` được xử lý như `required`/`type`: bỏ qua và trả warning `RULE_IMPLIED_BY_SCHEMA`, đúng như D10. Ghi lại ở đây để FE hiển thị chip "suy ra từ kiểu".
