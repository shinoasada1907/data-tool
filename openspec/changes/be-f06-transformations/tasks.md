# BE-F06 Transformation Engine — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit; tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** 5 transformation (`trim`, `uppercase`, `lowercase`, `defaultValue`, `dateFormat`) chạy theo `order`, lỗi có cấu trúc; cấu hình qua `PUT /api/import-sessions/{id}/transformations`.

**Architecture:**
- **Domain** (Java thuần, D1): contract `Transformation`, `TransformationRegistry`, `TransformationEngine`, `TransformationConfigValidator`, và phần prune của `TransformationConfig`.
- **Application**: cập nhật cấu hình thông qua service cấu hình session của F04 (khoá, lưu, readiness).
- **API**: controller cùng DTO.
- **Infrastructure**: đăng ký bean.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Jackson 3, JUnit Jupiter 6, AssertJ, Mockito, Testcontainers 2.0.5.

**Spec:** `openspec/changes/be-f06-transformations/specs/transformation/spec.md`. Thiết kế riêng nằm ở `design.md` cùng thư mục (T1–T7); quyết định nền ở `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md` (D1–D14).

## Global Constraints

- Chạy mọi lệnh Maven từ `apps/api` trong worktree `D:\Code\Product\universal-importer-be`. Viết tắt `MAIN` = `apps/api/src/main/java/com/universalimporter`, `TEST` = `apps/api/src/test/java/com/universalimporter`.
- Làm trên nhánh `feature/be-f06-transformations`, tạo từ nhánh đã có F01–F05.
- Mã lỗi, HTTP status và định dạng ProblemDetail theo D4: 422 `CONFIG_INVALID` kèm `errors[{field, code, message}]`; 400 `REQUEST_INVALID`; 404 `SESSION_NOT_FOUND`; 409 `SESSION_STATE_INVALID`.
- `domain` chỉ import `java.*` và `com.universalimporter.domain.*`; ArchitectureTest của F01 phải vẫn xanh.
- Message lỗi viết tiếng Anh và không chứa giá trị ô (D13).
- `uppercase`/`lowercase` dùng `Locale.ROOT`. `dateFormat` dùng `ResolverStyle.STRICT` và `Locale.ENGLISH`, và đổi `y` → `u` ở ngoài phần trong nháy đơn.
- Không migration mới; dùng `import_configuration.transformations_json` (V3, F04).
- Cuối commit message có dòng `Co-Authored-By`. Không push, không merge khi chưa hỏi.

---

## 1. Đối chiếu giả định với code F04/F05 đã merge

**Files:**
- Modify (nếu cần): `openspec/changes/be-f06-transformations/{design.md,tasks.md}`

- [ ] 1.1 Đọc code F04/F05 đã merge và ghi lại tên thật của các thứ sau:
  - `TargetSchema`, `TargetField`, `FieldType`;
  - aggregate config (`SessionConfiguration`);
  - service cấu hình session (`ConfigurationService`), hàm cập nhật dùng chung, cách trả `ConfigUpdateResult`;
  - khung prune (cách PUT `/schema` gọi prune từng phần và gom warning);
  - khoá session;
  - lớp serialize JSON của config.
- [ ] 1.2 Nếu tên khác giả định trong `design.md` (mục "Giả định về F02–F05"): sửa `design.md` và các task dưới đây cho khớp, gạch ngang tên cũ và ghi LÝ DO. Nếu F04 chưa có khung prune: dừng lại, hỏi người dùng. Không tự dựng một khung prune song song.
- [ ] 1.3 Commit (nếu có sửa): `docs(openspec): align be-f06 plan with merged F04/F05 names`

## 2. Mã lỗi theo row và tiện ích giá trị rỗng

**Files:**
- Create: `MAIN/domain/common/RowErrorCode.java`, `MAIN/domain/common/TextValues.java`
- Test: `TEST/domain/common/TextValuesTest.java`

**Interfaces:**
- Produces:
  ```java
  public enum RowErrorCode { TRANSFORMATION_FAILED, VALIDATION_REQUIRED, VALIDATION_TYPE, VALIDATION_EMAIL, VALIDATION_UNIQUE }
  public final class TextValues {
      public static boolean isEmpty(String value);   // null, hoặc mọi ký tự thoả Character.isWhitespace || Character.isSpaceChar
      public static String strip(String value);      // bỏ các ký tự trên ở hai đầu; null → null
  }
  ```

- [ ] 2.1 Viết `TextValuesTest` (parameterized):
  | Input | `isEmpty` | `strip` |
  |---|---|---|
  | `null` | true | `null` |
  | `""` | true | `""` |
  | `"   "` | true | `""` |
  | `"\u00A0\u2007\u202F"` | true | `""` |
  | `" a b "` | false | `"a b"` |
  | `"\u00A0An\u00A0"` | false | `"An"` |
  | `"\tx\n"` | false | `"x"` |
- [ ] 2.2 Chạy `./mvnw -q test -Dtest=TextValuesTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 2.3 Tạo `RowErrorCode` và `TextValues`.
- [ ] 2.4 Chạy lại lệnh ở 2.2. Mong đợi: PASS (7 case).
- [ ] 2.5 Commit: `feat(domain): row error codes and blank-aware text helpers`

## 3. Contract Transformation và 4 transformation văn bản

**Files:**
- Create: `MAIN/domain/transformation/Transformation.java`, `TransformationContext.java`, `TransformationFailure.java`, `TrimTransformation.java`, `UppercaseTransformation.java`, `LowercaseTransformation.java`, `DefaultValueTransformation.java`
- Test: `TEST/domain/transformation/TextTransformationsTest.java`, `TEST/domain/transformation/DefaultValueTransformationTest.java`

**Interfaces:**
- Consumes: `TextValues` (task 2), `FieldType` (F04).
- Produces:
  ```java
  public interface Transformation {
      String type();
      List<String> validate(TransformationContext context);   // message lỗi cấu hình; rỗng = hợp lệ
      String transform(String value, TransformationContext context) throws TransformationFailure;
  }
  public record TransformationContext(String fieldName, FieldType fieldType, Map<String, String> params) {}
  public final class TransformationFailure extends Exception { public TransformationFailure(String message); }
  // type(): "trim" | "uppercase" | "lowercase" | "defaultValue"
  ```

- [ ] 3.1 Viết `TextTransformationsTest`. Test nào đổi locale thì `@AfterEach` phải khôi phục `Locale.getDefault()`.
  | Transformation | Input | Output |
  |---|---|---|
  | trim | `"  An  "` | `"An"` |
  | trim | `"\u00A0Bình\u00A0"` | `"Bình"` |
  | trim | `"Nguyễn  Văn"` | `"Nguyễn  Văn"` |
  | trim | `null` | `null` |
  | trim | `"   "` | `"   "` |
  | uppercase | `"nguyễn văn an"` | `"NGUYỄN VĂN AN"` |
  | uppercase, với `Locale.setDefault(Locale.forLanguageTag("tr"))` | `"istanbul"` | `"ISTANBUL"` |
  | lowercase | `"ĐÀ NẴNG"` | `"đà nẵng"` |
  | lowercase, với locale `tr` | `"TITLE"` | `"title"` |
  | uppercase | `"   "` | `"   "` |

  Ngoài ra: `validate(ctx với params {})` trả `[]`; `validate(ctx với params {"foo":"1"})` trả `["Unknown parameter 'foo' for 'trim'."]`.
- [ ] 3.2 Viết `DefaultValueTransformationTest`, với `params = {"value":"VN"}`:
  | Input | Output |
  |---|---|
  | `null` | `"VN"` |
  | `""` | `"VN"` |
  | `"\u00A0 "` | `"VN"` |
  | `" JP "` | `" JP "` |

  Và `validate` với các `params` khác:
  | Params | Kết quả |
  |---|---|
  | `{}` | `["Parameter 'value' is required."]` |
  | `{"value":"  "}` | `["Parameter 'value' must not be blank."]` |
  | `{"value":"VN","x":"1"}` | `["Unknown parameter 'x' for 'defaultValue'."]` |
- [ ] 3.3 Chạy `./mvnw -q test -Dtest=TextTransformationsTest,DefaultValueTransformationTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 3.4 Tạo các class ở phần Files. Mỗi transformation kiểm `TextValues.isEmpty` trước và trả nguyên giá trị nếu rỗng; riêng `defaultValue` làm ngược lại.
- [ ] 3.5 Chạy lại lệnh ở 3.3. Mong đợi: PASS.
- [ ] 3.6 Commit: `feat(domain): trim, uppercase, lowercase and defaultValue transformations`

## 4. dateFormat và kiểm pattern ngày

**Files:**
- Create: `MAIN/domain/transformation/DatePatterns.java`, `MAIN/domain/transformation/DateFormatTransformation.java`
- Test: `TEST/domain/transformation/DatePatternsTest.java`, `TEST/domain/transformation/DateFormatTransformationTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class DatePatterns {
      public static final String ISO = "yyyy-MM-dd";
      public static String toStrict(String pattern);                  // y → u ở ngoài phần trong '…'
      public static DateTimeFormatter formatter(String pattern);      // ofPattern(toStrict(p), Locale.ENGLISH).withResolverStyle(STRICT); sai cú pháp → IllegalArgumentException
      public static Optional<String> checkInput(String pattern);      // message lỗi hoặc empty
      public static Optional<String> checkOutput(String pattern);
      public static boolean isIso(String pattern);                    // toStrict(pattern).equals("uuuu-MM-dd")
  }
  // DateFormatTransformation.type() = "dateFormat"; params: inputFormat (bắt buộc), outputFormat (không bắt buộc, mặc định ISO)
  ```

- [ ] 4.1 Viết `DatePatternsTest`:
  | Hàm | Input | Mong đợi |
  |---|---|---|
  | `toStrict` | `dd/MM/yyyy` | `dd/MM/uuuu` |
  | `toStrict` | `'year' yyyy` | `'year' uuuu` |
  | `toStrict` | `yy` | `uu` |
  | `toStrict` | `uuuu-MM-dd` | `uuuu-MM-dd` |
  | `checkInput` | `dd/MM/yyyy` | empty |
  | `checkInput` | `yyyy-MM-dd'T'HH:mm:ss` | empty |
  | `checkInput` | `MM/yyyy` | `Date pattern 'MM/yyyy' must contain year, month and day.` |
  | `checkInput` | `HH:mm` | `Date pattern 'HH:mm' must contain year, month and day.` |
  | `checkInput` | `dd/MM/yyyyb` | `Invalid date pattern 'dd/MM/yyyyb'.` |
  | `checkOutput` | `yyyy-MM-dd` | empty |
  | `checkOutput` | `dd/MM/yyyy HH:mm` | `Date pattern 'dd/MM/yyyy HH:mm' must not contain time fields.` |
  | `isIso` | `yyyy-MM-dd` / `uuuu-MM-dd` / `dd/MM/yyyy` | true / true / false |

  Cách kiểm:
  - `checkInput` là round-trip: `LocalDate.parse(f.format(LocalDateTime.of(2001,2,3,4,5,6)), f)` phải bằng `2001-02-03`.
  - `checkOutput` gọi `f.format(LocalDate.of(2001,2,3))`, không được ném exception.
- [ ] 4.2 Viết `DateFormatTransformationTest`:
  | inputFormat | outputFormat | Input | Mong đợi |
  |---|---|---|---|
  | `dd/MM/yyyy` | — | `25/12/1990` | `1990-12-25` |
  | `dd/MM/yyyy` | `yyyy-MM-dd` | `25/12/1990` | `1990-12-25` |
  | `yyyy-MM-dd` | `dd/MM/yyyy` | `1990-12-25` | `25/12/1990` |
  | `dd/MM/yyyy` | — | `31/02/2024` | `TransformationFailure("Value does not match pattern dd/MM/yyyy")` |
  | `dd/MM/yyyy` | — | `29/02/2024` | `2024-02-29` |
  | `dd/MM/yyyy` | — | `29/02/2023` | `TransformationFailure` |
  | `dd/MM/yyyy` | — | `1990-12-25` | `TransformationFailure` |
  | `dd MMM yyyy` | — | `05 Jan 2024` | `2024-01-05` |
  | `yyyy-MM-dd'T'HH:mm:ss` | — | `1990-12-25T08:30:00` | `1990-12-25` |
  | `dd/MM/yyyy` | — | `" 25/12/1990"` | `TransformationFailure` |
  | `dd/MM/yyyy` | — | `null` | `null` |
  | `dd/MM/yyyy` | — | `"  "` | `"  "` |

  `validate` với các `context` sau:
  | fieldType | Params | Mong đợi |
  |---|---|---|
  | string | `{}` | `["Parameter 'inputFormat' is required."]` |
  | date | `{inputFormat: dd/MM/yyyy, outputFormat: dd/MM/yyyy}` | `["Fields of type date must output yyyy-MM-dd."]` |
  | string | cùng params như dòng trên | `[]` |
  | date | `{inputFormat: dd/MM/yyyy}` | `[]` |
  | date | `{inputFormat: dd/MM/yyyy, outputFormat: yyyy-MM-dd}` | `[]` (FE luôn gửi dạng này) |
  | date | `{inputFormat: dd/MM/yyyy, outputFormat: uuuu-MM-dd}` | `[]` |
  | string | `{inputFormat: dd/MM/yyyy, foo: 1}` | `["Unknown parameter 'foo' for 'dateFormat'."]` |
- [ ] 4.3 Chạy `./mvnw -q test -Dtest=DatePatternsTest,DateFormatTransformationTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 4.4 Tạo `DatePatterns` và `DateFormatTransformation`. Message lỗi lúc parse chỉ chứa pattern gốc người dùng nhập, không chứa giá trị ô.
- [ ] 4.5 Chạy lại lệnh ở 4.3. Mong đợi: PASS.
- [ ] 4.6 Commit: `feat(domain): strict dateFormat transformation`

## 5. Registry và engine

**Files:**
- Create: `MAIN/domain/transformation/TransformationStep.java`, `TransformationRegistry.java`, `TransformationEngine.java`, `FieldTransformResult.java`, `TransformationError.java`
- Test: `TEST/domain/transformation/TransformationRegistryTest.java`, `TEST/domain/transformation/TransformationEngineTest.java`

**Interfaces:**
- Produces:
  ```java
  public record TransformationStep(String targetField, Integer order, String type, Map<String, String> params) {}   // params null → Map.of()
  public final class TransformationRegistry {
      public TransformationRegistry(List<Transformation> transformations);   // type trùng → IllegalArgumentException
      public Optional<Transformation> find(String type);                      // phân biệt hoa thường
      public List<String> types();                                             // sắp xếp alphabet
  }
  public record TransformationError(String rule, int step, String message) {}
  public record FieldTransformResult(String value, TransformationError error) {
      public static FieldTransformResult ok(String value);
      public static FieldTransformResult failed(TransformationError error);
      public boolean failed();
  }
  public final class TransformationEngine {
      public TransformationEngine(TransformationRegistry registry);
      public FieldTransformResult apply(String fieldName, FieldType fieldType, String value, List<TransformationStep> steps);
  }
  ```

- [ ] 5.1 Viết `TransformationRegistryTest`:
  | Case | Mong đợi |
  |---|---|
  | `find("trim")` | có `TrimTransformation` |
  | `find("TRIM")` | empty |
  | `find("replace")` | empty |
  | `types()` | `[dateFormat, defaultValue, lowercase, trim, uppercase]` |
  | tạo registry với 2 transformation cùng `type` | `IllegalArgumentException` |
- [ ] 5.2 Viết `TransformationEngineTest`, dùng registry thật cùng một `ExplodingTransformation` (type `explode`, luôn ném `IllegalStateException("boom")`) chỉ có trong test:
  | Steps | Input | Mong đợi |
  |---|---|---|
  | `[trim(0), uppercase(1)]` | `"  an "` | ok `"AN"` |
  | `[uppercase(1), trim(0)]` | `"  an "` | ok `"AN"` |
  | `[defaultValue(0){value:VN}]` | `null` | ok `"VN"` |
  | `[trim(0), dateFormat(1){inputFormat:dd/MM/yyyy}]` | `" 25/12/1990 "` | ok `"1990-12-25"` |
  | `[dateFormat(0){dd/MM/yyyy}, uppercase(1)]` | `"31/02/2024"` | failed `("dateFormat", 0, "Value does not match pattern dd/MM/yyyy")` |
  | `[]` | `"x"` | ok `"x"` |
  | `[explode(0)]` | `"x"` | failed `("explode", 0, "Unexpected error while applying transformation.")`; message không chứa `boom` |
- [ ] 5.3 Chạy `./mvnw -q test -Dtest=TransformationRegistryTest,TransformationEngineTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 5.4 Tạo các class ở phần Files. Engine sắp theo `order`, bắt `TransformationFailure` và `RuntimeException` theo design T2. `RuntimeException` được log WARN kèm `fieldName` và `type`, không kèm giá trị.
- [ ] 5.5 Chạy lại lệnh ở 5.3. Mong đợi: PASS.
- [ ] 5.6 Commit: `feat(domain): transformation registry and ordered engine`

## 6. Kiểm cấu hình transformation

**Files:**
- Create: `MAIN/domain/transformation/TransformationConfig.java`, `TransformationConfigValidator.java`
- Test: `TEST/domain/transformation/TransformationConfigValidatorTest.java`

**Interfaces:**
- Consumes: `TargetSchema` và `TargetField` (F04), `ProblemItem` (F01), `TransformationRegistry` (task 5).
- Produces:
  ```java
  public record TransformationConfig(List<TransformationStep> transformations) {
      public static TransformationConfig empty();
      public List<TransformationStep> stepsFor(String fieldName);             // sắp theo order
      public TransformationConfig normalized(TargetSchema schema);            // theo vị trí field trong schema, rồi theo order
  }
  public final class TransformationConfigValidator {
      public TransformationConfigValidator(TransformationRegistry registry);
      public List<ProblemItem> validate(TransformationConfig config, TargetSchema schema);   // code = "CONFIG_INVALID"
  }
  ```

- [ ] 6.1 Viết `TransformationConfigValidatorTest`. Schema gồm `name` (string), `dob` (date), `email` (email).
  | Config | Mong đợi `(field, message)` |
  |---|---|
  | `[{name,0,trim}]` (không có `params`, tức `params = null`) | `[]` |
  | `[{name,0,trim},{name,1,uppercase},{email,0,lowercase}]`, cả 3 bước đều `params = null` | `[]` |
  | `[{dob,0,dateFormat,{inputFormat:"dd/MM/yyyy",outputFormat:"yyyy-MM-dd"}}]` | `[]` |
  | `[{dob,0,dateFormat,{inputFormat:"dd/MM/yyyy",outputFormat:"uuuu-MM-dd"}}]` | `[]` |
  | `[{phone,0,trim}]` | `[(phone, "Target field does not exist.")]` |
  | `[{name,0,replace}]` | `[(name, "Unknown transformation type 'replace'.")]` |
  | `[{name,null,trim}]` | `[(name, "Order is required.")]` |
  | `[{name,-1,trim}]` | `[(name, "Order must be >= 0.")]` |
  | `[{name,0,trim},{name,0,uppercase}]` | `[(name, "Duplicate order 0 for field 'name'.")]` |
  | `[{name,0,trim},{email,0,trim}]` | `[]` |
  | `[{name,0,defaultValue,{}}]` | `[(name, "Parameter 'value' is required.")]` |
  | `[{dob,0,dateFormat,{inputFormat:"dd/MM/yyyyb"}}]` | `[(dob, "Invalid date pattern 'dd/MM/yyyyb'.")]` |
  | `[{dob,0,dateFormat,{inputFormat:"MM/yyyy"}}]` | `[(dob, "Date pattern 'MM/yyyy' must contain year, month and day.")]` |
  | `[{dob,0,dateFormat,{inputFormat:"dd/MM/yyyy",outputFormat:"dd/MM/yyyy"}}]` | `[(dob, "Fields of type date must output yyyy-MM-dd.")]` |
  | `[{name,0,trim,{foo:"1"}}]` | `[(name, "Unknown parameter 'foo' for 'trim'.")]` |
  | `[{name,0,replace},{phone,0,trim}]` | 2 phần tử, đúng thứ tự trong input |

  Mọi phần tử đều có `code = "CONFIG_INVALID"`.

  Test thêm cho `normalized`: `[{dob,0,dateFormat…},{name,1,uppercase},{name,0,trim}]` cho ra `[{name,0,trim},{name,1,uppercase},{dob,0,dateFormat…}]`.
- [ ] 6.2 Chạy `./mvnw -q test -Dtest=TransformationConfigValidatorTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 6.3 Tạo `TransformationConfig` và `TransformationConfigValidator` theo thứ tự kiểm ở design T4.
- [ ] 6.4 Chạy lại lệnh ở 6.2. Mong đợi: PASS.
- [ ] 6.5 Commit: `feat(domain): validate transformation configuration`

## 7. Prune khi schema đổi

**Files:**
- Modify: `MAIN/domain/transformation/TransformationConfig.java` (thêm `prunedFor`)
- Modify: khung prune của F04 (tên thật lấy ở task 1), để đăng ký phần transformations
- Test: `TEST/domain/transformation/TransformationConfigPruneTest.java`

**Interfaces:**
- Produces: `public Pruned<TransformationConfig> prunedFor(TargetSchema newSchema)`. `Pruned` lấy của F04 nếu F04 đã có kiểu tương đương, nếu không thì là `record Pruned<T>(T config, List<ProblemItem> warnings)`. Warning có `code = "CONFIG_PRUNED"`.

- [ ] 7.1 Viết `TransformationConfigPruneTest`:
  | Config cũ | Schema mới | Config mới | Warnings |
  |---|---|---|---|
  | `[{phone,0,trim},{name,0,trim}]` | `name:string` | `[{name,0,trim}]` | `[(phone, "Transformations removed because field 'phone' no longer exists.")]` |
  | `[{dob,0,dateFormat,{in:dd/MM/yyyy,out:dd/MM/yyyy}}]` | `dob:date` | `[]` | `[(dob, "dateFormat step 0 removed because field 'dob' is now of type date and must output yyyy-MM-dd.")]` |
  | `[{dob,0,dateFormat,{in:dd/MM/yyyy}}]` | `dob:date` | giữ nguyên | `[]` |
  | `[{dob,0,dateFormat,{in:dd/MM/yyyy,out:yyyy-MM-dd}}]` | `dob:date` | giữ nguyên | `[]` |
  | `[{name,0,trim}]` | `name:string` | giữ nguyên | `[]` |
- [ ] 7.2 Chạy `./mvnw -q test -Dtest=TransformationConfigPruneTest`. Mong đợi: FAIL.
- [ ] 7.3 Viết `prunedFor`, rồi đăng ký vào khung prune của F04, để PUT `/schema` gom warning của phần transformations.
- [ ] 7.4 Chạy lại lệnh ở 7.2, và cả test PUT `/schema` của F04. Mong đợi: PASS.
- [ ] 7.5 Commit: `feat(domain): prune transformations when the target schema changes`

## 8. Use case: cập nhật transformations

**Files:**
- Modify: service cấu hình session của F04 (giả định `MAIN/application/configuration/ConfigurationService.java`), thêm `updateTransformations`
- Create: `MAIN/infrastructure/config/EngineConfig.java` (bean `TransformationRegistry`, `TransformationEngine`, `TransformationConfigValidator`)
- Test: `TEST/application/importsession/UpdateTransformationsTest.java` (dùng fake của F01/F04)

**Interfaces:**
- Consumes: hàm cập nhật dùng chung của F04 (khoá D11, kiểm `FAILED`, lưu, readiness/status D2, trả `ConfigUpdateResult`).
- Produces: `public ConfigUpdateResult updateTransformations(UUID sessionId, TransformationConfig config)`.

- [ ] 8.1 Viết `UpdateTransformationsTest`:
  | Case | Mong đợi |
  |---|---|
  | Session `CONFIGURING` có schema `name`, config `[{name,0,trim}]` | trả response với `warnings = []`; config được lưu đã chuẩn hoá |
  | Config có 2 lỗi | `DomainException(CONFIG_INVALID)` có `items().size() == 2`; config cũ không đổi |
  | Session `FAILED` | `DomainException(SESSION_STATE_INVALID)`; không lưu gì |
  | Id không tồn tại | `DomainException(SESSION_NOT_FOUND)` |
  | Session `READY`, config hợp lệ | status sau lệnh là `READY` (transformation không ảnh hưởng readiness) |
- [ ] 8.2 Chạy `./mvnw -q test -Dtest=UpdateTransformationsTest`. Mong đợi: FAIL.
- [ ] 8.3 Viết `updateTransformations`: validate, nếu có lỗi thì ném `DomainException(CONFIG_INVALID, "Transformation configuration is invalid.", items)`, sau đó `normalized(schema)` rồi gọi hàm cập nhật dùng chung. Tạo `EngineConfig`.
- [ ] 8.4 Chạy lại lệnh ở 8.2. Mong đợi: PASS.
- [ ] 8.5 Commit: `feat(app): update transformation configuration`

## 9. API: PUT /transformations

**Files:**
- Create: `MAIN/api/importsession/TransformationConfigController.java`, `MAIN/api/importsession/TransformationConfigDto.java`
- Modify: DTO config của session (F04), thêm phần `transformations` vào `config`
- Test: `TEST/api/importsession/TransformationConfigControllerTest.java`

**Interfaces:**
- Produces:
  ```java
  public record TransformationConfigDto(@NotNull List<@NotNull Item> transformations) {
      public record Item(String targetField, Integer order, String type, Map<String, String> params) {}
      public TransformationConfig toDomain();
      public static TransformationConfigDto from(TransformationConfig config);
  }
  // PUT /api/import-sessions/{id}/transformations, @Valid @RequestBody → 200 ConfigUpdateResponseDto
  ```

- [ ] 9.1 Viết `TransformationConfigControllerTest` với `@WebMvcTest(TransformationConfigController.class)` và `@MockitoBean` cho service:
  | Body | Stub | Mong đợi |
  |---|---|---|
  | `{"transformations":[{"targetField":"name","order":0,"type":"trim"}]}` | trả response session `CONFIGURING`, `warnings=[]` | 200; `$.session.id`; `$.warnings.length()` = 0 |
  | `{"transformations":[{"targetField":"name","order":0,"type":"trim","params":{}}]}` | như trên | 200 |
  | `{"transformations":[]}` | như trên | 200 |
  | `{}` | — | 400; `$.code` = `REQUEST_INVALID` |
  | `{"transformations":[{"targetField":"name","order":"abc","type":"trim"}]}` | — | 400; `$.code` = `REQUEST_INVALID` |
  | `not json` | — | 400; `$.code` = `REQUEST_INVALID` |
  | body hợp lệ | ném `DomainException(CONFIG_INVALID, …, [ProblemItem("phone","CONFIG_INVALID","Target field does not exist.")])` | 422; `$.code` = `CONFIG_INVALID`; `$.errors[0].field` = `phone` |
  | body hợp lệ | ném `DomainException(SESSION_NOT_FOUND, …)` | 404 |
  | body hợp lệ | ném `DomainException(SESSION_STATE_INVALID, …)` | 409 |
- [ ] 9.2 Chạy `./mvnw -q test -Dtest=TransformationConfigControllerTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 9.3 Tạo controller và DTO, và thêm `transformations` vào `config` trong DTO của session.
- [ ] 9.4 Chạy lại lệnh ở 9.2. Mong đợi: PASS.
- [ ] 9.5 Commit: `feat(api): PUT transformations endpoint`

## 10. Integration test qua HTTP thật

**Files:**
- Test: `TEST/api/importsession/TransformationConfigApiIntegrationTest.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Import(TestcontainersConfiguration.class)`, `RestClient`, storage dir là `@TempDir`)

- [ ] 10.1 Viết test. Mỗi case bắt đầu bằng upload `customers.csv` (`"name,dob\nAn,25/12/1990\n"`), rồi PUT `/schema` với `name` (string) và `dob` (date):
  | Case | Mong đợi |
  |---|---|
  | PUT `/transformations` gửi `[{dob,0,dateFormat,{inputFormat:"dd/MM/yyyy"}},{name,0,trim}]` | 200; GET `/{id}` trả `config.transformations.transformations` theo thứ tự `name/trim`, rồi `dob/dateFormat` |
  | PUT gửi `[{name,0,replace},{phone,0,trim}]` | 422; `code` = `CONFIG_INVALID`; `errors` có 2 phần tử; GET cho thấy config không đổi |
  | PUT hợp lệ, rồi PUT `/schema` chỉ còn `name` | response của `/schema` có warning `CONFIG_PRUNED` với `field` = `dob`; GET chỉ còn bước của `name` |
  | Gửi đúng payload FE: `[{name,0,trim},{name,1,uppercase},{dob,0,dateFormat,{inputFormat:"dd/MM/yyyy",outputFormat:"yyyy-MM-dd"}}]`, bước trim/uppercase không có `params` | 200; `warnings = []` |
  | PUT `/api/import-sessions/{uuid-chưa-tạo}/transformations` | 404; `code` = `SESSION_NOT_FOUND` |
- [ ] 10.2 Chạy `./mvnw -q test -Dtest=TransformationConfigApiIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính, không nới lỏng test.
- [ ] 10.3 Commit: `test(api): transformation configuration end-to-end`

## 11. Kiểm tra toàn bộ và hoàn tất

- [ ] 11.1 Chạy `./mvnw -q verify`. Mong đợi: mọi test xanh, kể cả ArchitectureTest.
- [ ] 11.2 Chạy app thật và gọi `curl -X PUT` tới `/transformations` với một body hợp lệ và một body lỗi. Kiểm tra 200/422 và log không chứa giá trị ô.
- [ ] 11.3 Tick đủ checkbox; chỗ nào làm khác kế hoạch thì gạch ngang và ghi LÝ DO. Commit: `docs(openspec): complete be-f06 tasks`
- [ ] 11.4 Hỏi người dùng trước khi merge. Sau khi merge: `openspec archive be-f06-transformations -y`.
