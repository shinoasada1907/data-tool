# BE-F07 Validation Engine — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit; tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** 4 rule `required`, `type`, `email`, `unique`, chạy sau transformation. Rule `type` ép giá trị về kiểu thật; mỗi field chỉ báo lỗi đầu tiên; `unique` hoạt động hai pha. Cấu hình qua `PUT /api/import-sessions/{id}/validations`, trong đó `required` và `type` được suy ra từ schema.

**Architecture:**
- **Domain** (Java thuần, D1): `ValidationRule`, `ValidationRegistry`, `FieldValidator`, `UniqueTracker`, `ValidationConfigValidator`, và phần prune của `ValidationConfig`.
- **Application**: cập nhật cấu hình qua service cấu hình session của F04.
- **API**: controller và DTO.
- **Infrastructure**: đăng ký bean trong `EngineConfig`.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Jackson 3, JUnit Jupiter 6, AssertJ, Mockito, Testcontainers 2.0.5.

**Spec:** `openspec/changes/be-f07-validations/specs/validation/spec.md`. Thiết kế riêng: `design.md` cùng thư mục (V1–V7). Quyết định nền: `openspec/changes/be-f01-import-session/design.md` (D1–D14).

## Global Constraints

- Chạy mọi lệnh Maven từ `apps/api` trong worktree `D:\Code\Product\universal-importer-be`. Viết tắt đường dẫn: `MAIN` = `apps/api/src/main/java/com/universalimporter`, `TEST` = `apps/api/src/test/java/com/universalimporter`.
- Nhánh: `feature/be-f07-validations`, tạo từ nhánh đã có F01–F06.
- Mã lỗi theo D4:
  - Lỗi theo row dùng `RowErrorCode` (F06): `VALIDATION_REQUIRED`, `VALIDATION_TYPE`, `VALIDATION_EMAIL`, `VALIDATION_UNIQUE`.
  - Lỗi cấu hình: 422 `CONFIG_INVALID`, 400 `REQUEST_INVALID`.
  - Warning: `RULE_IMPLIED_BY_SCHEMA`, `CONFIG_PRUNED`.
- `domain` chỉ import `java.*` và `com.universalimporter.domain.*`. ArchitectureTest phải xanh.
- Message lỗi viết tiếng Anh và không chứa giá trị ô (D13).
- Giá trị rỗng xác định bằng `TextValues.isEmpty` (F06); không viết lại logic này.
- Không thêm migration mới; dùng cột `import_configuration.validations_json` (V3, F04).
- Cuối commit message có dòng `Co-Authored-By`. Không push, không merge khi chưa hỏi.

---

## 1. Đối chiếu giả định với code F04/F06 đã merge

- [ ] 1.1 Đọc code đã merge và ghi lại tên thật của:
  - `TargetSchema`, `TargetField`, `FieldType`;
  - aggregate config;
  - service cấu hình session và hàm cập nhật dùng chung;
  - khung prune và kiểu `Pruned`;
  - `RowErrorCode`, `TextValues`, `EngineConfig` (F06).
- [ ] 1.2 Tên nào khác với `design.md` (mục "Giả định về F02–F06") thì sửa `design.md` và các task bên dưới: gạch tên cũ, ghi LÝ DO.
- [ ] 1.3 Commit (nếu có sửa): `docs(openspec): align be-f07 plan with merged F04/F06 names`

## 2. Contract, rule required và rule type

**Files:**
- Create: `MAIN/domain/validation/ValidationRule.java`, `ValidationResult.java`, `ValidationContext.java`, `EmailAddresses.java`, `RequiredRule.java`, `TypeRule.java`
- Test: `TEST/domain/validation/RequiredRuleTest.java`, `TEST/domain/validation/TypeRuleTest.java`

**Interfaces:**
- Consumes: `RowErrorCode`, `TextValues` (F06); `FieldType` (F04).
- Produces:
  ```java
  public interface ValidationRule { String type(); ValidationResult validate(Object value, ValidationContext context); }
  public sealed interface ValidationResult {
      record Valid(Object value) implements ValidationResult {}
      record Invalid(RowErrorCode code, String message) implements ValidationResult {}
  }
  public record ValidationContext(String fieldName, FieldType fieldType, int rowNumber, UniqueTracker uniqueTracker) {}
  public final class EmailAddresses { public static final Pattern PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"); }
  // RequiredRule.type() = "required"; TypeRule.type() = "type"
  ```
  (`UniqueTracker` được tạo ở task 3. Ở task này, test truyền `null` vào context.)

- [ ] 2.1 Viết `RequiredRuleTest`:
  | Input | Mong đợi |
  |---|---|
  | `"An"` | `Valid("An")` |
  | `null` | `Invalid(VALIDATION_REQUIRED, "Value is required.")` |
  | `""` | `Invalid(VALIDATION_REQUIRED, …)` |
  | `"   "` | `Invalid(VALIDATION_REQUIRED, …)` |
  | `"\u00A0"` | `Invalid(VALIDATION_REQUIRED, …)` |
- [ ] 2.2 Viết `TypeRuleTest` (parameterized):
  | FieldType | Input | Mong đợi |
  |---|---|---|
  | STRING | `" a "` | `Valid(" a ")` |
  | NUMBER | `"42"` | `Valid(new BigDecimal("42"))` |
  | NUMBER | `"-3.50"` | `Valid(new BigDecimal("-3.50"))` (scale 2) |
  | NUMBER | `"007"` | `Valid(new BigDecimal("007"))`, tức `compareTo(7) == 0` |
  | NUMBER | `"1,234"`, `"1e3"`, `" 42"`, `"+5"`, `".5"`, `"٤٢"` (chữ số Ả Rập) | `Invalid(VALIDATION_TYPE, "Value is not a valid number.")` |
  | BOOLEAN | `"TRUE"`, `"true"`, `"1"` | `Valid(true)` |
  | BOOLEAN | `"False"`, `"0"` | `Valid(false)` |
  | BOOLEAN | `"yes"` | `Invalid(VALIDATION_TYPE, "Value is not a valid boolean (true/false/1/0).")` |
  | DATE | `"1990-12-25"` | `Valid(LocalDate.of(1990,12,25))` |
  | DATE | `"2024-02-30"`, `"25/12/1990"`, `"1990-1-5"` | `Invalid(VALIDATION_TYPE, "Value is not a valid date (yyyy-MM-dd).")` |
  | EMAIL | `"an@example.com"` | `Valid("an@example.com")` |
  | EMAIL | `"an@example"`, `"an example@x.com"`, `"a@b@c.com"` | `Invalid(VALIDATION_EMAIL, "Value is not a valid email address.")` |
- [ ] 2.3 Chạy `./mvnw -q test -Dtest=RequiredRuleTest,TypeRuleTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 2.4 Tạo các class ở phần Files. `TypeRule` dùng `switch` trên `FieldType` và không có nhánh `default`. Regex số: `^-?[0-9]+(\.[0-9]+)?$`. Ngày dùng `DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)`.
- [ ] 2.5 Chạy lại lệnh ở 2.3. Mong đợi: PASS.
- [ ] 2.6 Commit: `feat(domain): required and type validation rules with type coercion`

## 3. Rule email, rule unique và UniqueTracker

**Files:**
- Create: `MAIN/domain/validation/EmailRule.java`, `UniqueRule.java`, `UniqueTracker.java`
- Test: `TEST/domain/validation/EmailRuleTest.java`, `TEST/domain/validation/UniqueTrackerTest.java`, `TEST/domain/validation/UniqueRuleTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class UniqueTracker {
      public Optional<Integer> firstRowOf(String field, Object canonicalValue);
      public void stage(String field, Object canonicalValue);
      public void commitRow(int rowNumber);
      public void discardRow();
      public static Object canonical(Object typedValue);   // BigDecimal → stripTrailingZeros(); loại khác giữ nguyên
  }
  // EmailRule.type() = "email" → Invalid(VALIDATION_EMAIL, "Value is not a valid email address.")
  // UniqueRule.type() = "unique" → Invalid(VALIDATION_UNIQUE, "Duplicate value; first seen in row <n>.")
  ```

- [ ] 3.1 Viết `EmailRuleTest`:
  | Input | Mong đợi |
  |---|---|
  | `"an@example.com"` | `Valid` |
  | `"an.example.com"` | `Invalid(VALIDATION_EMAIL, "Value is not a valid email address.")` |
- [ ] 3.2 Viết `UniqueTrackerTest`. Mỗi case là một chuỗi lệnh chạy trên một tracker mới:
  | Chuỗi lệnh | Mong đợi |
  |---|---|
  | `stage("email","a@x.com")`, `commitRow(2)`, `firstRowOf("email","a@x.com")` | `Optional.of(2)` |
  | `stage("email","a@x.com")`, `discardRow()`, `firstRowOf("email","a@x.com")` | `Optional.empty()` |
  | `stage("code",canonical(1.0))`, `commitRow(2)`, `firstRowOf("code",canonical(new BigDecimal("1.00")))` | `Optional.of(2)` |
  | `stage("email","x")`, `commitRow(2)`, `firstRowOf("code","x")` | `Optional.empty()` |
  | `stage("email","A@x.com")`, `commitRow(2)`, `firstRowOf("email","a@x.com")` | `Optional.empty()` |
  | `commitRow(2)` rồi `stage("email","b")`, `commitRow(3)`, rồi `stage("email","b")`, `commitRow(4)` | `firstRowOf("email","b")` = `Optional.of(3)` (bản đầu tiên thắng) |
- [ ] 3.3 Viết `UniqueRuleTest`, dùng `UniqueRule` với tracker thật:
  | Tình huống | Mong đợi |
  |---|---|
  | Row 2 `"a@x.com"` → `Valid`, `commitRow(2)`; row 3 `"a@x.com"` | `Invalid(VALIDATION_UNIQUE, "Duplicate value; first seen in row 2.")` |
  | Row 2 `"a@x.com"` → `Valid`, `discardRow()`; row 3 `"a@x.com"` | `Valid` |
  | Row 2 `new BigDecimal("1.0")` → `commitRow(2)`; row 3 `new BigDecimal("1.00")`; row 4 `new BigDecimal("1")` | row 3 và row 4 đều `Invalid(VALIDATION_UNIQUE, "Duplicate value; first seen in row 2.")` |
- [ ] 3.4 Chạy `./mvnw -q test -Dtest=EmailRuleTest,UniqueTrackerTest,UniqueRuleTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 3.5 Tạo các class. `UniqueTracker` dùng:
  - `Map<String, Map<Object, Integer>>` cho các giá trị đã ghi nhận;
  - `Map<String, Set<Object>>` cho các giá trị đang stage.
- [ ] 3.6 Chạy lại lệnh ở 3.4. Mong đợi: PASS.
- [ ] 3.7 Commit: `feat(domain): email rule and two-phase unique tracking`

## 4. Registry và FieldValidator

**Files:**
- Create: `MAIN/domain/validation/ValidationRuleConfig.java`, `ValidationRegistry.java`, `FieldValidator.java`, `FieldValidation.java`, `ValidationFailure.java`
- Test: `TEST/domain/validation/FieldValidatorTest.java`

**Interfaces:**
- Produces:
  ```java
  public record ValidationRuleConfig(String targetField, String type, Map<String, String> params) {}   // params null → Map.of()
  public final class ValidationRegistry {
      public ValidationRegistry(List<ValidationRule> rules);
      public Optional<ValidationRule> find(String type);
  }
  public record ValidationFailure(String rule, RowErrorCode code, String message) {}
  public record FieldValidation(Object value, ValidationFailure failure) { public boolean failed(); }
  public final class FieldValidator {
      public FieldValidator(ValidationRegistry registry);
      public FieldValidation validate(TargetField field, List<ValidationRuleConfig> userRules,
                                      String value, int rowNumber, UniqueTracker tracker);
  }
  ```

- [ ] 4.1 Viết `FieldValidatorTest`, dùng registry thật và một `ExplodingRule` chỉ có trong test (type `unique`, luôn ném `IllegalStateException("boom")`; được đăng ký trong một registry riêng):
  | Field | User rules | Value | Mong đợi |
  |---|---|---|---|
  | `email` (EMAIL, required) | `[unique]` | `""` | failure `("required", VALIDATION_REQUIRED)` |
  | `email` (EMAIL, required) | `[unique]` | `"bad"` | failure `("email", VALIDATION_EMAIL)` |
  | `age` (NUMBER, optional) | `[unique]` | `"   "` | `value = null`, không có failure |
  | `age` (NUMBER, optional) | `[]` | `"abc"` | failure `("type", VALIDATION_TYPE)` |
  | `contact` (STRING) | `[email]` | `"x"` | failure `("email", VALIDATION_EMAIL)` |
  | `note` (STRING) | `[unique, email]` (thứ tự đảo) | `"a@x.com"`, đã được ghi nhận ở row 2 | failure `("unique", VALIDATION_UNIQUE)` (email chạy trước và pass) |
  | `active` (BOOLEAN) | `[]` | `"1"` | `value = Boolean.TRUE` |
  | `country` (STRING, required) | `[]` | `"VN"` | `value = "VN"` |
  | `code` (STRING) | `[unique]` với ExplodingRule | `"x"` | failure `("unique", VALIDATION_UNIQUE, "Unexpected error while applying rule 'unique'.")`; message không chứa `boom` |
- [ ] 4.2 Chạy `./mvnw -q test -Dtest=FieldValidatorTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 4.3 Tạo các class theo design V2. Thứ tự rule cố định trong code. Lỗi email của field kiểu EMAIL gán `rule = "email"`.
- [ ] 4.4 Chạy lại lệnh ở 4.2. Mong đợi: PASS.
- [ ] 4.5 Commit: `feat(domain): field validator with fixed rule order and first-error policy`

## 5. Kiểm và chuẩn hoá cấu hình validation

**Files:**
- Create: `MAIN/domain/validation/ValidationConfig.java`, `ValidationConfigValidator.java`, `ValidationConfigCheck.java`
- Test: `TEST/domain/validation/ValidationConfigValidatorTest.java`

**Interfaces:**
- Produces:
  ```java
  public record ValidationConfig(List<ValidationRuleConfig> validations) {
      public static ValidationConfig empty();
      public List<ValidationRuleConfig> rulesFor(String fieldName);
  }
  public record ValidationConfigCheck(ValidationConfig effective, List<ProblemItem> warnings, List<ProblemItem> errors) {}
  public final class ValidationConfigValidator { public ValidationConfigCheck check(ValidationConfig config, TargetSchema schema); }
  ```

- [ ] 5.1 Viết `ValidationConfigValidatorTest`. Schema gồm `name` (string, required), `email` (email), `age` (number), `note` (string).
  | Input | effective | warnings `(field, code)` | errors `(field, message)` |
  |---|---|---|---|
  | `[{email,unique}]` (`params = null`) | `[{email,unique}]` | `[]` | `[]` |
  | `[{email,unique},{note,email}]`, cả hai `params = null` | `[{email,unique},{note,email}]` | `[]` | `[]` |
  | `[{name,required}]` | `[]` | `[(name, RULE_IMPLIED_BY_SCHEMA)]` | `[]` |
  | `[{age,type}]` | `[]` | `[(age, RULE_IMPLIED_BY_SCHEMA)]` | `[]` |
  | `[{email,email}]` | `[]` | `[(email, RULE_IMPLIED_BY_SCHEMA)]` | `[]` |
  | `[{age,email}]` | — | — | `[(age, "Rule 'email' only applies to fields of type string.")]` |
  | `[{phone,unique}]` | — | — | `[(phone, "Target field does not exist.")]` |
  | `[{name,regex}]` | — | — | `[(name, "Unknown validation rule 'regex'.")]` |
  | `[{note,unique},{note,unique}]` | — | — | `[(note, "Duplicate rule 'unique' for field 'note'.")]` |
  | `[{note,unique,{foo:"1"}}]` | — | — | `[(note, "Unknown parameter 'foo' for 'unique'.")]` |
  | `[{note,unique},{note,email},{name,unique}]` | `[{name,unique},{note,email},{note,unique}]` | `[]` | `[]` |
  | `[{age,email},{phone,unique}]` | — | — | 2 errors, đúng thứ tự trong input |

  Mọi phần tử của `errors` có `code = "CONFIG_INVALID"`. Khi `errors` không rỗng, `effective` là `null`.
- [ ] 5.2 Chạy `./mvnw -q test -Dtest=ValidationConfigValidatorTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 5.3 Tạo các class theo design V5.
- [ ] 5.4 Chạy lại lệnh ở 5.2. Mong đợi: PASS.
- [ ] 5.5 Commit: `feat(domain): validate and normalise validation configuration`

## 6. Prune khi schema đổi

**Files:**
- Modify: `MAIN/domain/validation/ValidationConfig.java` (thêm `prunedFor`), và khung prune của F04 (đăng ký phần validations)
- Test: `TEST/domain/validation/ValidationConfigPruneTest.java`

- [ ] 6.1 Viết `ValidationConfigPruneTest`:
  | Config cũ | Schema mới | Config mới | Warnings `(field, message)` |
  |---|---|---|---|
  | `[{phone,unique},{note,email}]` | `note:string` | `[{note,email}]` | `[(phone, "Validation rules removed because field 'phone' no longer exists.")]` |
  | `[{note,email},{note,unique}]` | `note:number` | `[{note,unique}]` | `[(note, "Rule 'email' removed because field 'note' is no longer of type string.")]` |
  | `[{note,email}]` | `note:email` | `[]` | `[(note, "Rule 'email' removed because field 'note' is no longer of type string.")]` |
  | `[{note,unique}]` | `note:number` | giữ nguyên | `[]` |

  Mọi warning có `code = "CONFIG_PRUNED"`.
- [ ] 6.2 Chạy `./mvnw -q test -Dtest=ValidationConfigPruneTest`. Mong đợi: FAIL.
- [ ] 6.3 Viết `prunedFor`, rồi đăng ký vào khung prune của F04.
- [ ] 6.4 Chạy lại lệnh ở 6.2, và cả test PUT `/schema` của F04 và F06. Mong đợi: PASS.
- [ ] 6.5 Commit: `feat(domain): prune validation rules when the target schema changes`

## 7. Use case: cập nhật validations

**Files:**
- Modify: service cấu hình session của F04 (thêm `updateValidations`), `MAIN/infrastructure/config/EngineConfig.java` (thêm bean `ValidationRegistry`, `FieldValidator`, `ValidationConfigValidator`)
- Test: `TEST/application/importsession/UpdateValidationsTest.java`

**Interfaces:**
- Produces: `public ConfigUpdateResult updateValidations(UUID sessionId, ValidationConfig config)`. Có errors thì ném `DomainException(CONFIG_INVALID, "Validation configuration is invalid.", errors)`. Ngược lại lưu `effective` và trả warnings.

- [ ] 7.1 Viết `UpdateValidationsTest`, dùng fake của F01/F04:
  | Case | Mong đợi |
  |---|---|
  | Config `[{email,unique},{name,required}]` | response có 1 warning `RULE_IMPLIED_BY_SCHEMA` (field `name`); config lưu là `[{email,unique}]` |
  | Config `[{age,email}]` | `DomainException(CONFIG_INVALID)` có 1 item; config cũ không đổi |
  | Session `FAILED` | `DomainException(SESSION_STATE_INVALID)` |
  | Id không tồn tại | `DomainException(SESSION_NOT_FOUND)` |
- [ ] 7.2 Chạy `./mvnw -q test -Dtest=UpdateValidationsTest`. Mong đợi: FAIL.
- [ ] 7.3 Viết `updateValidations`, gọi hàm cập nhật dùng chung của F04, và bổ sung các bean.
- [ ] 7.4 Chạy lại lệnh ở 7.2. Mong đợi: PASS.
- [ ] 7.5 Commit: `feat(app): update validation configuration`

## 8. API: PUT /validations

**Files:**
- Create: `MAIN/api/importsession/ValidationConfigController.java`, `MAIN/api/importsession/ValidationConfigDto.java`
- Modify: DTO config của session (F04): thêm `validations` vào `config`
- Test: `TEST/api/importsession/ValidationConfigControllerTest.java`

**Interfaces:**
- Produces:
  ```java
  public record ValidationConfigDto(@NotNull List<@NotNull Item> validations) {
      public record Item(String targetField, String type, Map<String, String> params) {}
      public ValidationConfig toDomain();
      public static ValidationConfigDto from(ValidationConfig config);
  }
  ```

- [ ] 8.1 Viết `ValidationConfigControllerTest` với `@WebMvcTest(ValidationConfigController.class)` và `@MockitoBean` cho service:
  | Body | Stub | Mong đợi |
  |---|---|---|
  | `{"validations":[{"targetField":"email","type":"unique"}]}` | trả response kèm `warnings=[]` | 200; `$.warnings.length()`=0 |
  | `{"validations":[{"targetField":"name","type":"required"}]}` | trả response kèm warning `RULE_IMPLIED_BY_SCHEMA` | 200; `$.warnings[0].code`=`RULE_IMPLIED_BY_SCHEMA`; `$.warnings[0].field`=`name` |
  | `{}` | — | 400; `$.code`=`REQUEST_INVALID` |
  | body hợp lệ | ném `DomainException(CONFIG_INVALID, …, items)` | 422; `$.errors[0].code`=`CONFIG_INVALID` |
  | body hợp lệ | ném `SESSION_NOT_FOUND` | 404 |
  | body hợp lệ | ném `SESSION_STATE_INVALID` | 409 |
- [ ] 8.2 Chạy `./mvnw -q test -Dtest=ValidationConfigControllerTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 8.3 Tạo controller và DTO; thêm `validations` vào `config` của session DTO.
- [ ] 8.4 Chạy lại lệnh ở 8.2. Mong đợi: PASS.
- [ ] 8.5 Commit: `feat(api): PUT validations endpoint`

## 9. Integration test qua HTTP thật

**Files:**
- Test: `TEST/api/importsession/ValidationConfigApiIntegrationTest.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Import(TestcontainersConfiguration.class)`, `RestClient`, storage đặt ở `@TempDir`)

- [ ] 9.1 Viết test. Mỗi case bắt đầu bằng: upload `customers.csv` (`"name,email,age,note\nAn,an@x.com,30,hi\n"`), rồi PUT `/schema` với `name` (string, required), `email` (email), `age` (number), `note` (string).
  | Case | Mong đợi |
  |---|---|
  | PUT `/validations` gửi `[{email,unique},{name,required}]` | 200; `warnings[0].code`=`RULE_IMPLIED_BY_SCHEMA`; GET `/{id}` trả `config.validations.validations` = `[{targetField:"email",type:"unique"}]` |
  | PUT gửi `[{age,email}]` | 422; `code`=`CONFIG_INVALID`; config cũ không đổi |
  | PUT gửi `[{note,email},{note,unique}]`, rồi PUT `/schema` đổi `note` sang `number` | response của `/schema` có warning `CONFIG_PRUNED` cho `note`; GET chỉ còn `note/unique` |
  | Gửi đúng payload FE: `{"validations":[{"targetField":"email","type":"unique"},{"targetField":"note","type":"email"}]}`, không có `params` | 200; `warnings = []`; lưu cả 2 rule |
  | PUT `/api/import-sessions/{uuid-chưa-tạo}/validations` | 404; `code` = `SESSION_NOT_FOUND` |
- [ ] 9.2 Chạy `./mvnw -q test -Dtest=ValidationConfigApiIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính, không nới lỏng test.
- [ ] 9.3 Commit: `test(api): validation configuration end-to-end`

## 10. Kiểm tra toàn bộ và hoàn tất

- [ ] 10.1 Chạy `./mvnw -q verify`. Mong đợi: mọi test xanh, gồm cả ArchitectureTest.
- [ ] 10.2 Chạy app thật, dùng `curl -X PUT` gọi `/validations` với 3 body: hợp lệ, có `required` (phải nhận warning), và `email` trên field số (phải nhận 422).
- [ ] 10.3 Tick đủ checkbox; chỗ nào làm khác kế hoạch thì gạch ngang và ghi LÝ DO. Nếu review chốt khác quy tắc "chỉ báo lỗi đầu tiên" (OQ1), ghi quyết định vào đây. Commit: `docs(openspec): complete be-f07 tasks`
- [ ] 10.4 Hỏi người dùng trước khi merge. Sau khi merge: `openspec archive be-f07-validations -y`.
