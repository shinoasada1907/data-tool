## Context

- Thiết kế nền: `openspec/changes/core-01-toolbox-restructure/design.md`, các mục TD9 (Schema Core), TD10 (rule engine), TD11 (KeyIndex). File này ghi chi tiết cài đặt. Quyết định mang mã `SR1`…`SR10`.
- Đi sau core-02 (`KeyHasher`, `Hash128`).
- Hiện trạng (sau core-01):
  - `core.validate` có `ValidationRule`, `RequiredRule`, `TypeRule`, `EmailRule`, `UniqueRule`, `UniqueTracker`, `ValidationRegistry`.
  - `tools.importer.domain.validation.FieldValidator` điều phối với thứ tự và map mã viết cứng.
  - `core.transform` có 5 transformation cùng `TransformationRegistry.standard()`.
- Người dùng: Validator (tool-05) và Schema Builder (tool-04) dùng `core.schema` + `core.validate`. Cleaner (tool-03) dùng các transformation mới.

## Goals / Non-Goals

**Goals:**
- Schema có constraint, kiểm được định nghĩa, và mọi lỗi chỉ đúng chỗ bằng `pointer`.
- Rule engine mở: thêm rule mới chỉ là thêm một class vào catalog cùng một mã trong enum.
- Importer giữ hành vi V0.1 từng chữ (message, thứ tự, mã).

**Non-Goals:**
- API: Validator là tool-05, Schema Builder là tool-04.
- Xuất JSON Schema: tool-04.
- `min`/`max` cho ngày; ràng buộc liên field (ví dụ `end ≥ start`).
- Đổi API cấu hình rule của Importer. Để Importer Stage 2+.

## Decisions

### SR1. Mô hình schema

```java
package com.universaldatatools.core.schema;

public record FieldConstraints(boolean unique, BigDecimal min, BigDecimal max, Integer minLength, Integer maxLength,
                               String pattern, String format, String defaultValue) {
    public static final FieldConstraints NONE;
}
public record SchemaField(String name, FieldType type, boolean required, FieldConstraints constraints) {}
public record DataSchema(String name, List<SchemaField> fields) {
    public Optional<SchemaField> field(String name);   // khớp chính xác
}
// Dữ liệu thô từ client, chưa kiểm:
public record FieldConstraintsSpec(Boolean unique, BigDecimal min, BigDecimal max, Integer minLength, Integer maxLength,
                                   String pattern, String format, String defaultValue) {}
public record SchemaFieldSpec(String name, String type, Boolean required, FieldConstraintsSpec constraints) {}
public record SchemaSpec(String name, List<SchemaFieldSpec> fields) {}
```

- Thứ tự field chính là thứ tự trong list, không có trường `order`. Importer vẫn giữ `TargetField.order` của riêng nó.
- `FieldType` giữ 5 giá trị với code chữ thường như V0.1.

### SR2. `SchemaDefinition.check(SchemaSpec)` trả `Checked<DataSchema>`

- Trả **mọi** vi phạm theo thứ tự duyệt: tên schema trước, rồi từng field theo index, trong mỗi field theo thứ tự các dòng ở bảng dưới. Không dừng ở lỗi đầu tiên.
- Mỗi vi phạm là `ProblemItem(field, code, message, pointer)`. `field` là tên field đã trim, `null` nếu tên rỗng.

| Luật | `code` | `pointer` | `message` |
|---|---|---|---|
| `name` rỗng sau trim, hoặc dài hơn 200 | `SCHEMA_NAME_INVALID` | `/name` | `Schema name must be 1-200 characters.` |
| `fields` rỗng hoặc hơn 500 phần tử | `SCHEMA_FIELDS_INVALID` | `/fields` | `Schema must have 1-500 fields.` |
| tên field rỗng sau trim | `FIELD_NAME_INVALID` | `/fields/i/name` | `Field name must not be blank.` |
| tên field dài hơn 100 | `FIELD_NAME_INVALID` | `/fields/i/name` | `Field name must be at most 100 characters.` |
| tên trùng một field trước đó (không phân biệt hoa thường) | `FIELD_NAME_DUPLICATE` | `/fields/i/name` | `Duplicate field name.` |
| `type` không thuộc 5 kiểu | `FIELD_TYPE_INVALID` | `/fields/i/type` | `Unknown field type.` |
| `min` hoặc `max` trên kiểu khác `number` | `CONSTRAINT_INVALID` | `/fields/i/constraints/min` (hoặc `max`) | `'min' is only allowed on number fields.` |
| `min > max` | `CONSTRAINT_INVALID` | `/fields/i/constraints/max` | `'max' must not be less than 'min'.` |
| `minLength`/`maxLength` trên kiểu khác `string`/`email` | `CONSTRAINT_INVALID` | `/fields/i/constraints/minLength` (hoặc `maxLength`) | `'minLength' is only allowed on string and email fields.` |
| `minLength` hoặc `maxLength` < 0 hay > 32767 | `CONSTRAINT_INVALID` | … | `'maxLength' must be between 0 and 32767.` |
| `minLength > maxLength` | `CONSTRAINT_INVALID` | `/fields/i/constraints/maxLength` | `'maxLength' must not be less than 'minLength'.` |
| `pattern` trên kiểu khác `string`/`email` | `CONSTRAINT_INVALID` | `/fields/i/constraints/pattern` | `'pattern' is only allowed on string and email fields.` |
| `pattern` rỗng, dài hơn 500, hoặc RE2J không compile được | `CONSTRAINT_INVALID` | `/fields/i/constraints/pattern` | `'pattern' is not a valid RE2 regular expression.` |
| `format` trên kiểu khác `date` | `CONSTRAINT_INVALID` | `/fields/i/constraints/format` | `'format' is only allowed on date fields.` |
| `format` không qua luật `DatePatterns` của V0.1 | `CONSTRAINT_INVALID` | `/fields/i/constraints/format` | message của `DatePatterns` |
| `defaultValue` không ép được sang kiểu, hoặc vi phạm constraint khác (trừ `unique`) | `CONSTRAINT_INVALID` | `/fields/i/constraints/defaultValue` | `'defaultValue' does not satisfy the field's type and constraints.` |

- Khi mọi field đều hợp lệ, kết quả là `DataSchema`: tên đã trim, `required` null thành `false`, `unique` null thành `false`.
- Bỏ qua `defaultValue` khi `type` sai, vì lúc đó không biết ép sang kiểu nào.
- Lý do dùng mã item riêng (`FIELD_NAME_DUPLICATE`…) thay vì chỉ `SCHEMA_INVALID` như Importer: UI của Schema Builder cần biết đặt lỗi vào ô nào. `pointer` lo phần "ô nào", mã lo phần "sai gì".

### SR3. `ProblemItem.pointer`

- `ProblemItem(String field, String code, String message, String pointer)`, có constructor ba tham số cũ đặt `pointer = null`.
- DTO lỗi (`ProblemItemDto`) đánh dấu riêng property `pointer` là `@JsonInclude(NON_NULL)`. `field: null` vẫn được ghi như V0.1.
- Kết quả: JSON lỗi của Importer giữ nguyên từng byte. Test hợp đồng của core-01 canh việc này.

### SR4. `TypeConverter`

```java
public final class TypeConverter {
    public Converted convert(String text, SchemaField field);        // text đã khác rỗng
}
public sealed interface Converted { record Ok(Object value) implements Converted {}
                                   record Failed(RowErrorCode code, String message) implements Converted {} }
```

| Kiểu | Luật | Giá trị ra | Lỗi |
|---|---|---|---|
| `string` | mọi chuỗi | nguyên văn | — |
| `number` | luật V0.1: `^-?[0-9]+(\.[0-9]+)?$`, tối đa 1 000 ký tự | `BigDecimal` | `VALIDATION_TYPE` `Value is not a valid number.` |
| `boolean` | `true`/`false` (không phân biệt hoa thường), `1`/`0` | `Boolean` | `VALIDATION_TYPE` `Value is not a valid boolean (true/false/1/0).` |
| `date`, không có `format` | ISO `uuuu-MM-dd` strict, năm ≥ 1 | `LocalDate` | `VALIDATION_TYPE` `Value is not a valid date (yyyy-MM-dd).` |
| `date`, có `format` | `DateTimeFormatter.ofPattern(format, ROOT)` STRICT, năm ≥ 1 | `LocalDate` | `VALIDATION_DATE_FORMAT` `Value is not a valid date (<format>).` |
| `email` | `EmailAddresses.isValid` | nguyên văn | `VALIDATION_EMAIL` `Value is not a valid email address.` |

- `TypeRule` của V0.1 gọi `TypeConverter` với `FieldConstraints.NONE`. Mọi message giữ nguyên.
- Pattern ngày dùng `u` thay cho `y` như `DatePatterns` của V0.1, để `ResolverStyle.STRICT` hoạt động.

### SR5. Rule constraint

| Rule (`type`) | Áp cho | Kiểm trên | Lỗi (`code`, `message`) |
|---|---|---|---|
| `minLength` | string, email | số code point của chuỗi | `VALIDATION_MIN_LENGTH`, `Value must have at least N characters.` |
| `maxLength` | string, email | như trên | `VALIDATION_MAX_LENGTH`, `Value must have at most N characters.` |
| `pattern` | string, email | `re2j.Pattern.matches(value)`, khớp **toàn chuỗi** | `VALIDATION_PATTERN`, `Value does not match the required pattern.` |
| `min` | number | `value.compareTo(min) < 0` | `VALIDATION_MIN`, `Value must be at least <min.toPlainString()>.` |
| `max` | number | `value.compareTo(max) > 0` | `VALIDATION_MAX`, `Value must be at most <max.toPlainString()>.` |

- Message không bao giờ chứa giá trị ô (D13). `min`/`max` là giá trị của schema, không phải dữ liệu, nên được phép nêu.
- `re2j.Pattern` được compile **một lần mỗi lượt chạy**, ngay trong `FieldRulePlan`.

### SR6. `FieldRulePlan` và `FieldRuleRunner`

```java
public record FieldRulePlan(SchemaField field, List<ValidationRule> rules) {         // rules đã theo thứ tự chuẩn
    public static FieldRulePlan of(SchemaField field, Set<String> extraRules);       // extraRules: ví dụ {"email"} của Importer
}
public final class FieldRuleRunner {
    public FieldValidation validate(FieldRulePlan plan, String value, int rowNumber, UniqueIndex unique);
}
```

- **Thứ tự chuẩn**: `required → type → email → minLength → maxLength → pattern → min → max → unique`. Plan chỉ chứa rule mà field có:
  - `required` khi `field.required`;
  - `type` luôn có;
  - `email` chỉ khi có trong `extraRules` (rule `email` của Importer gắn trên field kiểu `string`). Field kiểu `email` đã được kiểm địa chỉ ngay trong `type`, nên không cần thêm rule `email`;
  - các rule constraint khi constraint đó khác null;
  - `unique` khi `constraints.unique` hoặc có trong `extraRules`.
- **Giá trị rỗng** (`TextValues.isEmpty`): field required → `VALIDATION_REQUIRED` (`Value is required.`); field optional → hợp lệ với giá trị `null`, bỏ qua mọi rule khác. Giữ nguyên V0.1.
- Rule thứ nhất thất bại thì dừng; mỗi field tối đa một lỗi.
- Rule ném exception bất ngờ: chuyển thành lỗi mang mã của rule đó, message `Unexpected error while applying rule '<type>'.`, và log bằng `ThrottledWarnings`. Giữ nguyên V0.1.
- Kiểu email mà rule `type` báo sai vẫn trả `rule = "email"` như V0.1 (D10).

### SR7. `UniqueIndex`

```java
public enum UniqueScope { VALID_ROWS, ALL_ROWS }
public final class UniqueIndex {
    public UniqueIndex(UniqueScope scope);
    public void beginRow(int rowNumber); public void commitRow(); public void discardRow();   // VALID_ROWS; ALL_ROWS thì commit/discard không làm gì
    OptionalInt firstRowOf(String field, Object canonical);   // gọi từ UniqueRule
    void record(String field, Object canonical);              // VALID_ROWS: stage; ALL_ROWS: ghi ngay
}
```

- Canonical giữ luật V0.1 (`UniqueTracker.canonical`): `BigDecimal` bỏ số 0 đuôi và `0` là một giá trị; chuỗi phân biệt hoa thường; `LocalDate` và `Boolean` như chính nó.
- Khoá lưu là `KeyHasher.hash([fieldName, kindTag, canonicalText])`, với `kindTag` là `N`, `B`, `D` hoặc `S`, cùng số dòng đầu tiên (`int`).
  - Lưu hash thay cho giá trị, nên RAM không phụ thuộc độ dài ô.
  - `fieldName` có trong khoá, nên một map dùng cho mọi field.
- Message: `Duplicate value; first seen in row N.` (V0.1).
- `ALL_ROWS`: lần gặp đầu được ghi ngay, dù row có lỗi ở field khác. Mọi lần gặp sau đều là lỗi.
- `VALID_ROWS`: giữ đúng hai pha của V0.1 (stage, rồi commit khi cả row hợp lệ).
- `UniqueTracker` cũ bị xoá. Test của nó chuyển thành test `UniqueIndex` với `VALID_ROWS`.

### SR8. Transformation mới

Tham số transformation là `Map<String,String>` như V0.1. Tham số dạng danh sách được mã hoá thành chuỗi nối bằng `\n`. Tool chuyển từ mảng JSON sang dạng này, ví dụ Cleaner. Chuỗi thành phần không được chứa xuống dòng.

| Type | Tham số | Hành vi | Lỗi cấu hình |
|---|---|---|---|
| `titleCase` | không có | Tách theo khoảng trắng, giữ nguyên các khoảng trắng. Mỗi từ: code point đầu → `Character.toTitleCase`, phần còn lại → `toLowerCase(Locale.ROOT)`. `null` giữ `null`. | tham số lạ |
| `replace` | `find` (bắt buộc, 1–1 000 ký tự), `replaceWith` (mặc định `""`), `match` = `exact`\|`contains` (mặc định `exact`), `caseSensitive` = `true`\|`false` (mặc định `true`) | `exact`: cả giá trị bằng `find` thì thay bằng `replaceWith`. `contains`: thay **mọi** lần xuất hiện, so theo nghĩa đen, không phải regex. So không phân biệt hoa thường thì dùng `toLowerCase(ROOT)` trên cả hai phía. `null` giữ `null`. | thiếu `find`; `match` hay `caseSensitive` sai giá trị |
| `normalizeNull` | `tokens` (danh sách, mặc định `null`, `n/a`, `na`, `none`, `-`), `caseSensitive` (mặc định `false`) | Giá trị sau khi trim (chỉ để so) bằng `""` hoặc một token thì thành `null`. Giá trị khác giữ nguyên, **không** trim. | token chứa xuống dòng; hơn 50 token |

- Ví dụ `titleCase`: `nguyễn văn  a` → `Nguyễn Văn  A`; `o'NEIL` → `O'neil`.
- Ví dụ `replace` kiểu `contains`, không phân biệt hoa thường: `find=hn`, `replaceWith=Hà Nội`, giá trị `HN-hn` → `Hà Nội-Hà Nội`.
- Message lỗi cấu hình dùng lại mẫu V0.1 (`TransformationParams`): `Unknown parameter 'x' for 'replace'.`, `Parameter 'find' is required.`…

### SR9. Catalog và registry theo tool

- `TransformationCatalog.all()`: 8 transformation `trim`, `uppercase`, `lowercase`, `titleCase`, `defaultValue`, `dateFormat`, `replace`, `normalizeNull`.
- `TransformationRegistry.of(Set<String> types)` lấy tập con từ catalog. Type lạ thì ném `IllegalArgumentException` ngay lúc khởi động.
- `TransformationRegistry.standard()` vẫn trả đúng 5 loại V0.1. Importer dùng nó, nên `PUT /transformations` với `titleCase` vẫn là `CONFIG_INVALID` như cũ.
- `ValidationRegistry` thành catalog nội bộ của `FieldRulePlan`. Tool không tự dựng registry validation.

### SR10. RE2J và ArchUnit

- Dependency `com.google.re2j:re2j:1.8`, thuần Java, không dependency con.
- Luật 1 của `ArchitectureTest` thêm `com.google.re2j..` vào allowlist của `core`.
- Giá trị được kiểm dài tối đa bằng giới hạn ô (32 767), pattern tối đa 500 ký tự. RE2 chạy thời gian tuyến tính, nên chi phí xấu nhất có trần.

## Risks / Trade-offs

- **RE2 thiếu backreference và lookaround.** Người dùng quen regex Java có thể thấy thiếu. Message cấu hình nêu rõ "RE2".
- **Đổi `UniqueTracker` sang hash** có thể làm lệch thứ tự hay message nếu cài sai. Test V0.1 của unique và luồng e2e của Importer canh việc này.
- **`defaultValue` chỉ là metadata**, nên người dùng có thể tưởng Validator sẽ tự điền. Tool-05 ghi rõ trong spec, và FE hiển thị.

## Migration Plan

Không có migration DB. Thứ tự (tasks.md):
1. `pointer`.
2. Schema model và check.
3. `TypeConverter`.
4. Constraint rules và RE2J.
5. Plan và runner.
6. `UniqueIndex`.
7. Chuyển Importer.
8. Transformation mới.

Rollback: revert nhánh.

## Open Questions

(không có)
