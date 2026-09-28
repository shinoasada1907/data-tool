# CORE-03 Schema Core và rule engine — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** `core.schema` (mô hình, kiểm định nghĩa có `pointer`, `TypeConverter`) và `core.validate` (plan, runner, rule constraint, `UniqueIndex`) được Importer dùng mà không đổi hành vi. Catalog transformation thêm `titleCase`, `replace`, `normalizeNull`.

**Architecture:** design.md SR1–SR10. Nền là core-01 TD9–TD11.

**Tech Stack:** thêm `com.google.re2j:re2j:1.8`.

**Spec:** `specs/schema-core/spec.md`, `specs/data-rules/spec.md`.

## Global Constraints

- core-02 đã merge vào `dev`. Nhánh `feature/core-03-schema-rules`.
- `core.schema`, `core.validate`, `core.transform` chỉ dùng JDK và RE2J.
- Test V0.1 của Importer (`FieldValidatorTest`, `TypeRuleTest`, `UniqueRuleTest`, `UniqueTrackerTest`, các test API) là **bộ canh hồi quy**. Chúng chỉ được đổi tên lớp hay import, không được đổi kỳ vọng.
- Message không chứa giá trị ô.

---

## 1. `ProblemItem.pointer`

**Files:**
- Modify: `MAIN/core/common/ProblemItem.java`, `MAIN/platform/web/GlobalExceptionHandler.java` (hoặc nơi dựng `ProblemItemDto`)
- Test: `TEST/platform/web/ProblemItemPointerTest.java`

- [ ] 1.1 Viết test:
  - `new ProblemItem("a","X","m")` có `pointer()` là `null`;
  - lỗi có `ProblemItem(null,"X","m","/fields/0/name")` → JSON `errors[0]` có `"pointer":"/fields/0/name"` và `"field":null`;
  - lỗi có `ProblemItem("email","SCHEMA_INVALID","Duplicate field name.")` → JSON `errors[0]` **không** có key `pointer`, và có `"field":"email"`.
- [ ] 1.2 Chạy. Mong đợi: FAIL.
- [ ] 1.3 Thêm field và constructor 3 tham số. DTO đánh dấu riêng `pointer` là `@JsonInclude(NON_NULL)`.
- [ ] 1.4 Chạy lại, cùng `ApiContractSnapshotTest` và `ErrorContractIntegrationTest`. Mong đợi: PASS.
- [ ] 1.5 Commit: `feat(errors): optional JSON pointer on problem items`

## 2. Mô hình schema và `SchemaDefinition.check`

**Files:**
- Create: `MAIN/core/schema/{FieldConstraints, SchemaField, DataSchema, FieldConstraintsSpec, SchemaFieldSpec, SchemaSpec, SchemaDefinition, Checked}.java`
- Test: `TEST/core/schema/SchemaDefinitionTest.java`

**Interfaces:** như design SR1–SR2. `Checked<T>` là sealed: `Ok(T value)` | `Invalid(List<ProblemItem> problems)`.

- [ ] 2.1 Viết `SchemaDefinitionTest`. `f(name,type)` là field không có constraint:

  | Input | Mong đợi (`code` @ `pointer`) |
  |---|---|
  | name `"KH"`, `[f("email","email")]` | Ok; field `email`, `required=false`, `unique=false` |
  | name `"  "`, `[f("a","string")]` | `SCHEMA_NAME_INVALID` @ `/name` |
  | name 201 ký tự | `SCHEMA_NAME_INVALID` @ `/name` |
  | `fields=[]` | `SCHEMA_FIELDS_INVALID` @ `/fields` |
  | 501 field | `SCHEMA_FIELDS_INVALID` @ `/fields` |
  | `[f(" ","string")]` | `FIELD_NAME_INVALID` @ `/fields/0/name`, message `Field name must not be blank.`, `field=null` |
  | tên 101 ký tự | `FIELD_NAME_INVALID` @ `/fields/0/name`, message `Field name must be at most 100 characters.` |
  | `[f("Email","email"), f("email","text")]`, name `""` | đúng 3 lỗi theo thứ tự: `SCHEMA_NAME_INVALID` @ `/name`; `FIELD_NAME_DUPLICATE` @ `/fields/1/name`; `FIELD_TYPE_INVALID` @ `/fields/1/type` |
  | `age` string, `min=18` | `CONSTRAINT_INVALID` @ `/fields/0/constraints/min`, `'min' is only allowed on number fields.` |
  | `age` number, `min=65, max=18` | `CONSTRAINT_INVALID` @ `/fields/0/constraints/max`, `'max' must not be less than 'min'.` |
  | `n` number, `maxLength=5` | `CONSTRAINT_INVALID` @ `…/maxLength`, `'maxLength' is only allowed on string and email fields.` |
  | `s` string, `maxLength=40000` | `CONSTRAINT_INVALID` @ `…/maxLength`, `'maxLength' must be between 0 and 32767.` |
  | `s` string, `minLength=5, maxLength=2` | `CONSTRAINT_INVALID` @ `…/maxLength`, `'maxLength' must not be less than 'minLength'.` |
  | `c` string, `pattern="(a)\\1"` | `CONSTRAINT_INVALID` @ `…/pattern`, `'pattern' is not a valid RE2 regular expression.` |
  | `c` string, `pattern` 501 ký tự | như dòng trên |
  | `c` number, `pattern="x"` | `'pattern' is only allowed on string and email fields.` |
  | `d` string, `format="dd/MM/yyyy"` | `'format' is only allowed on date fields.` |
  | `d` date, `format="dd/MM/yyyy HH:mm"` | `CONSTRAINT_INVALID` @ `…/format` với message của `DatePatterns` |
  | `q` number, `min=1, defaultValue="0"` | `CONSTRAINT_INVALID` @ `…/defaultValue` |
  | `q` number, `defaultValue="abc"` | `CONSTRAINT_INVALID` @ `…/defaultValue` |
  | `d` date, `format="dd/MM/yyyy"`, `defaultValue="31/01/2024"` | Ok |
  | `q` foo (type sai), `defaultValue="x"` | chỉ `FIELD_TYPE_INVALID`, không có lỗi `defaultValue` |
  | tên ` Email ` | Ok, field tên `Email` (đã trim) |
- [ ] 2.2 Chạy. Mong đợi: FAIL.
- [ ] 2.3 Thêm dependency RE2J vào `pom.xml`. Cập nhật luật 1 của `ArchitectureTest` (allowlist `com.google.re2j..`). Cài đặt theo SR2. Kiểm `defaultValue` dùng `TypeConverter` (task 3); tạm thời có thể viết task 3 trước task 2.3 nếu cần.
- [ ] 2.4 Chạy lại, cùng `ArchitectureTest`. Mong đợi: PASS.
- [ ] 2.5 Commit: `feat(core): data schema model and definition checks with JSON pointers`

## 3. `TypeConverter`

**Files:**
- Create: `MAIN/core/schema/{TypeConverter, Converted}.java`
- Modify: `MAIN/core/validate/TypeRule.java` (gọi `TypeConverter`)
- Modify: `MAIN/core/common/RowErrorCode.java` (thêm 6 mã)
- Test: `TEST/core/schema/TypeConverterTest.java`

- [ ] 3.1 Viết `TypeConverterTest`:

  | Giá trị | Field | Mong đợi |
  |---|---|---|
  | `12.50` | number | `BigDecimal("12.50")` |
  | ` 12` | number | `VALIDATION_TYPE`, `Value is not a valid number.` |
  | `1e3` | number | `VALIDATION_TYPE` |
  | 1 001 chữ số | number | `VALIDATION_TYPE` |
  | `TRUE`, `0` | boolean | `true`, `false` |
  | `yes` | boolean | `VALIDATION_TYPE`, `Value is not a valid boolean (true/false/1/0).` |
  | `2024-02-29` | date | `LocalDate.of(2024,2,29)` |
  | `2023-02-29` | date | `VALIDATION_TYPE`, `Value is not a valid date (yyyy-MM-dd).` |
  | `31/01/2024` | date, `format=dd/MM/yyyy` | `LocalDate.of(2024,1,31)` |
  | `30/02/2024` | date, `format=dd/MM/yyyy` | `VALIDATION_DATE_FORMAT`, `Value is not a valid date (dd/MM/yyyy).` |
  | `2024-01-31` | date, `format=dd/MM/yyyy` | `VALIDATION_DATE_FORMAT` |
  | `a@x.com` | email | `a@x.com` |
  | `a@` | email | `VALIDATION_EMAIL`, `Value is not a valid email address.` |
  | `=cmd()` | string | `=cmd()` |
- [ ] 3.2 Chạy. Mong đợi: FAIL.
- [ ] 3.3 Chuyển logic ép kiểu từ `TypeRule` sang `TypeConverter`. `TypeRule.validate` gọi `convert(text, SchemaField(name, type, false, NONE))`. Thêm 6 mã mới vào `RowErrorCode`.
- [ ] 3.4 Chạy `TypeConverterTest`, `TypeRuleTest`, `FieldValidatorTest`. Mong đợi: PASS. Hai test V0.1 không đổi kỳ vọng.
- [ ] 3.5 Commit: `refactor(core): TypeConverter is the single place values get their type`

## 4. Rule constraint

**Files:**
- Create: `MAIN/core/validate/{MinLengthRule, MaxLengthRule, PatternRule, MinRule, MaxRule}.java`
- Test: `TEST/core/validate/ConstraintRulesTest.java`

- [ ] 4.1 Viết `ConstraintRulesTest`. Giá trị đã qua `type`, nên với `number` là `BigDecimal`:

  | Rule | Constraint | Giá trị | Mong đợi |
  |---|---|---|---|
  | maxLength | 6 | `Nguyễn` (NFC) | hợp lệ |
  | maxLength | 5 | `Nguyễn` | `VALIDATION_MAX_LENGTH`, `Value must have at most 5 characters.` |
  | minLength | 3 | `ab` | `VALIDATION_MIN_LENGTH`, `Value must have at least 3 characters.` |
  | maxLength | 2 | `😀😀` (2 code point, 4 char) | hợp lệ |
  | pattern | `[A-Z]{3}-[0-9]{3}` | `ABC-123` | hợp lệ |
  | pattern | như trên | `ABC-123x` | `VALIDATION_PATTERN`, `Value does not match the required pattern.` |
  | pattern | `(a+)+$` | 30 000 `a` + `!` | `VALIDATION_PATTERN`, và chạy xong < 1 giây (`assertTimeoutPreemptively`) |
  | min | 18 | `17.99` | `VALIDATION_MIN`, `Value must be at least 18.` |
  | min | 18 | `18` | hợp lệ |
  | max | 65 | `65.0` | hợp lệ |
  | max | 65 | `66` | `VALIDATION_MAX`, `Value must be at most 65.` |
  | min | `0.5` | `0.49` | message `Value must be at least 0.5.` |

  Kiểm thêm: không message nào chứa giá trị được kiểm.
- [ ] 4.2 Chạy. Mong đợi: FAIL.
- [ ] 4.3 Cài đặt theo SR5.
- [ ] 4.4 Chạy lại. Mong đợi: PASS.
- [ ] 4.5 Commit: `feat(core): length, RE2 pattern and numeric range rules`

## 5. `FieldRulePlan` và `FieldRuleRunner`

**Files:**
- Create: `MAIN/core/validate/{FieldRulePlan, FieldRuleRunner}.java`
- Test: `TEST/core/validate/FieldRuleRunnerTest.java`

- [ ] 5.1 Viết `FieldRuleRunnerTest`:
  - thứ tự plan của field `string` có đủ `required, minLength, maxLength, pattern, unique` → `[required, type, minLength, maxLength, pattern, unique]`;
  - `code` string, `minLength=5`, `pattern=[A-Z]+`, giá trị `ab` → một lỗi `VALIDATION_MIN_LENGTH`;
  - optional, có `pattern`, giá trị `""` → hợp lệ, giá trị ra `null`;
  - required, giá trị `"  "` (có NBSP) → `VALIDATION_REQUIRED`, `Value is required.`;
  - `email` kiểu email, giá trị `a@` → lỗi mang `rule="email"`, `code=VALIDATION_EMAIL` (như V0.1);
  - field string với `extraRules={"email"}`, giá trị `x` → `VALIDATION_EMAIL`;
  - number `min=1`, giá trị `abc` → `VALIDATION_TYPE` (dừng ở type, không tới min);
  - rule ném `RuntimeException("SECRET")` (rule giả chèn vào plan qua constructor cho test) → lỗi mang mã của rule đó, message `Unexpected error while applying rule '<type>'.`, không chứa `SECRET`.
- [ ] 5.2 Chạy. Mong đợi: FAIL.
- [ ] 5.3 Cài đặt theo SR6.
- [ ] 5.4 Chạy lại. Mong đợi: PASS.
- [ ] 5.5 Commit: `feat(core): per-field rule plans in a fixed order, first failure wins`

## 6. `UniqueIndex`

**Files:**
- Create: `MAIN/core/validate/{UniqueScope, UniqueIndex}.java`
- Modify: `MAIN/core/validate/UniqueRule.java` (dùng `UniqueIndex`)
- Delete: `MAIN/core/validate/UniqueTracker.java`
- Test: `TEST/core/validate/UniqueIndexTest.java` (chuyển các case của `UniqueTrackerTest` sang `VALID_ROWS`)

- [ ] 6.1 Viết `UniqueIndexTest`:
  - **VALID_ROWS** (mọi case của `UniqueTrackerTest` cũ): stage rồi commit mới được tính; `discardRow` thì bỏ; gọi `record` khi chưa `beginRow` → `IllegalStateException`;
  - **ALL_ROWS**: row 2 `a@x.com`, `discardRow()`; row 3 `a@x.com` → `firstRowOf` = 2;
  - canonical: `1.0` và `1` trùng; `Abc` và `abc` không trùng; `LocalDate` trùng theo ngày;
  - hai field khác tên cùng giá trị `x` → không trùng nhau;
  - 300 000 giá trị khác nhau → không có trùng giả.
- [ ] 6.2 Chạy. Mong đợi: FAIL.
- [ ] 6.3 Cài đặt theo SR7. Khoá là `KeyHasher.hash([field, kindTag, canonicalText])`. Message của `UniqueRule` giữ `Duplicate value; first seen in row N.`
- [ ] 6.4 Chạy lại, cùng `UniqueRuleTest`. Mong đợi: PASS.
- [ ] 6.5 Commit: `feat(core): hashed unique index with valid-rows and all-rows scopes`

## 7. Importer chạy trên engine mới

**Files:**
- Modify: `MAIN/tools/importer/domain/validation/FieldValidator.java`: dựng `FieldRulePlan` từ `TargetField` (chuyển sang `SchemaField` với `FieldConstraints.NONE`, `unique` từ `ValidationRuleConfig`) cùng `extraRules` (`email`); gọi `FieldRuleRunner`. Bỏ map `CODES` và vòng lặp viết cứng.
- Modify: `MAIN/tools/importer/domain/pipeline/DefaultImportPipeline.java`: `UniqueTracker` → `UniqueIndex(VALID_ROWS)`. Plan được dựng **một lần mỗi lượt chạy** trong `Run`.

- [ ] 7.1 Chạy toàn bộ test V0.1 trước khi sửa, để ghi baseline. Mong đợi: PASS.
- [ ] 7.2 Sửa như phần Files.
- [ ] 7.3 Chạy `./mvnw -q test`. Mong đợi: PASS toàn bộ, gồm `ExportGoldenIntegrationTest` và các test e2e. Không test V0.1 nào đổi kỳ vọng.
- [ ] 7.4 Commit: `refactor(importer): validate through core rule plans and unique index`

## 8. Transformation mới và catalog

**Files:**
- Create: `MAIN/core/transform/{TitleCaseTransformation, ReplaceTransformation, NormalizeNullTransformation, TransformationCatalog, ListParams}.java`
- Modify: `MAIN/core/transform/TransformationRegistry.java` (`of(Set<String>)`)
- Test: `TEST/core/transform/{TitleCaseTransformationTest, ReplaceTransformationTest, NormalizeNullTransformationTest, TransformationCatalogTest}.java`

- [ ] 8.1 Viết test:

  | Transformation | Tham số | Input | Mong đợi |
  |---|---|---|---|
  | titleCase | — | `nguyễn  VĂN a` | `Nguyễn  Văn A` |
  | titleCase | — | `o'NEIL` | `O'neil` |
  | titleCase | — | `null` | `null` |
  | titleCase | `{x:1}` | — | lỗi cấu hình `Unknown parameter 'x' for 'titleCase'.` |
  | replace | `find=N/A` | `N/A`, `N/A2` | `""`, `N/A2` |
  | replace | `find=hn, replaceWith=Hà Nội, match=contains, caseSensitive=false` | `HN-hn` | `Hà Nội-Hà Nội` |
  | replace | `find=., replaceWith=",", match=contains` | `1.5.0` | `1,5,0` |
  | replace | `find=A` (mặc định caseSensitive) | `a` | `a` |
  | replace | không có `find` | — | `Parameter 'find' is required.` |
  | replace | `match=regex` | — | lỗi cấu hình nêu `match` |
  | normalizeNull | mặc định | `N/A`, ` - `, `NULL`, `  `, `Hà Nội ` | `null`,`null`,`null`,`null`,`Hà Nội ` |
  | normalizeNull | `tokens="không có"` | `Không Có`, `N/A` | `null`, `N/A` |
  | normalizeNull | `tokens="X", caseSensitive=true` | `x` | `x` |
  | normalizeNull | 51 token | — | lỗi cấu hình |

  `TransformationCatalogTest`:
  - `all()` có đúng 8 type;
  - `TransformationRegistry.standard().types()` = `[dateFormat, defaultValue, lowercase, trim, uppercase]`;
  - `of(Set.of("trim","nope"))` → `IllegalArgumentException`.
- [ ] 8.2 Chạy. Mong đợi: FAIL.
- [ ] 8.3 Cài đặt theo SR8–SR9. `ListParams.split(String)` và `ListParams.join(List<String>)` mã hoá danh sách bằng `\n`.
- [ ] 8.4 Chạy lại, cùng test transformation của Importer (`PUT /transformations` với `titleCase` vẫn trả `CONFIG_INVALID`). Mong đợi: PASS.
- [ ] 8.5 Commit: `feat(core): titleCase, replace and normalizeNull in the shared transformation catalog`

## 9. Hoàn tất

- [ ] 9.1 `./mvnw -q verify`. Mong đợi: PASS.
- [ ] 9.2 `openspec validate core-03-schema-rules --strict`, rồi `openspec archive core-03-schema-rules -y`. Commit: `docs(openspec): archive core-03-schema-rules`.
- [ ] 9.3 Kiểm thư mục chính rồi merge `--no-ff` vào `dev`. Báo người dùng rằng `pom.xml` thêm RE2J, nên cần reload Maven.
- [ ] 9.4 `git branch -d feature/core-03-schema-rules`.
