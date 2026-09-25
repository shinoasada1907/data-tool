## Context

- Quyết định nền nằm trong `openspec/changes/be-f01-import-session/design.md`, gồm D1–D14 và API contract V0.1. File này chỉ ghi phần riêng của F06.
  - Ngữ nghĩa transformation: D10.
  - Định dạng lỗi và mã `CONFIG_INVALID`: D4.
  - Khoá theo session: D11.
  - Prune khi schema đổi: D10.
- Pack System Design §8 phác contract `Transformation { String type(); Object transform(Object value, TransformationContext context); }`, kèm hai luật: chạy tuần tự theo `order`, và lỗi không được ném ra ngoài pipeline mà phải chuyển thành lỗi có cấu trúc.

### Giả định về F02–F05 (viết song song, chưa merge)

F06 dựa vào các thứ dưới đây. Tên trong ngoặc là tên giả định. Task 1 của tasks.md đối chiếu với code F04/F05 đã merge và sửa tên trong file này nếu khác.

- **F04**: `TargetSchema` / `TargetField(name, type, required, order)` / `FieldType { STRING, NUMBER, BOOLEAN, DATE, EMAIL }`, JSON viết thường.
  - `SessionConfiguration` gom 4 phần config, lưu trong `import_configuration` (V3), mỗi phần là một cột jsonb do app tự serialize.
  - Service cấu hình session (`ConfigurationService`) lo phần chung cho mọi PUT: khoá session (D11); kiểm session không `FAILED`; lưu config; tính lại readiness và status (D2); trả `ConfigUpdateResult` (F04).
  - Khung prune: PUT `/schema` gọi hàm prune của từng phần config và gom warning `CONFIG_PRUNED`.
- **F05**: có mapping thì giá trị mới vào được transformation. F06 không gọi thẳng code của F05.
- **F08**: gọi `TransformationEngine` cho từng field của từng row, rồi đổi lỗi thành `ImportError` (`stage=TRANSFORMATION`, `code=TRANSFORMATION_FAILED`, `sourceValue` là giá trị trước transformation).

## Goals / Non-Goals

**Goals:**
- 5 transformation đúng ngữ nghĩa D10, deterministic, mỗi cái có unit test riêng.
- Engine chạy các bước theo `order` và trả lỗi có cấu trúc, không ném exception ra pipeline.
- `PUT /transformations` kiểm cấu hình kỹ trước khi lưu. Mọi lỗi cấu hình được trả cùng một lúc trong `errors[]`.

**Non-Goals:**
- Transformation ngoài 5 loại trên (`replace`, `split`, …): ngoài scope V0.1.
- Chạy thử transformation trên dữ liệu mẫu (preview config): FE chỉ hiển thị chuỗi các bước.
- Parse ngày có múi giờ (`XXX`, `VV`).

## Decisions

### T1. Contract dùng `String`, không dùng `Object`
```java
public interface Transformation {
    String type();                                                        // "trim", "dateFormat", ...
    List<String> validate(TransformationContext context);                 // lỗi cấu hình; rỗng = hợp lệ
    String transform(String value, TransformationContext context) throws TransformationFailure;
}
public record TransformationContext(String fieldName, FieldType fieldType, Map<String, String> params) {}
```
- *Vì sao*: theo D9 và D10, giá trị luôn là chuỗi (hoặc `null`) cho tới bước ép kiểu ở F07. `String` bắt lỗi kiểu ngay lúc compile. Tên `type()`/`transform` giữ như pack.
- *Phương án khác*: `Object` như pack phác. Loại, vì mọi transformation sẽ phải tự cast.
- `TransformationFailure` là checked exception, nên engine buộc phải xử lý nó. Message viết tiếng Anh và không chứa giá trị ô (D13).

### T2. Engine
```java
public final class TransformationEngine {
    public TransformationEngine(TransformationRegistry registry);
    public FieldTransformResult apply(String fieldName, FieldType fieldType, String value, List<TransformationStep> steps);
}
public record FieldTransformResult(String value, TransformationError error) { boolean failed(); }
public record TransformationError(String rule, int step, String message) {}
```
- Sắp các bước theo `order` tăng dần; không tin thứ tự trong list.
- Bước ném `TransformationFailure` → dừng, trả lỗi với `rule=type()`, `step=order`, `message` lấy từ failure.
- Bước ném `RuntimeException` (bug) → cũng dừng, trả lỗi với `message="Unexpected error while applying transformation."`, và log ở mức WARN (không log giá trị ô). Như vậy một row lỗi không làm dừng cả job.

### T3. Ngữ nghĩa chi tiết (cụ thể hoá D10)
- **"Rỗng"** do `TextValues.isEmpty(v)` quyết định: `v == null`, hoặc mọi ký tự thoả `Character.isWhitespace || Character.isSpaceChar` (tính cả NBSP `U+00A0`, `U+2007`, `U+202F`). F07 dùng lại hàm này cho `required`.
- **`trim`**: `TextValues.strip(v)` bỏ ở hai đầu những ký tự thoả điều kiện trên. Giá trị rỗng giữ nguyên, nên `"   "` vẫn là `"   "`.
- **`uppercase` / `lowercase`**: `toUpperCase(Locale.ROOT)` / `toLowerCase(Locale.ROOT)`. Kết quả không phụ thuộc locale mặc định của JVM.
- **`defaultValue`**:
  - Nếu giá trị rỗng thì trả `params.value`, ngược lại giữ nguyên.
  - `params.value` bắt buộc có và không được rỗng.
- **`dateFormat`**:
  - `params.inputFormat` bắt buộc. `params.outputFormat` không bắt buộc, mặc định `yyyy-MM-dd`.
  - Pattern theo cú pháp `DateTimeFormatter`. BE đổi mọi chữ `y` nằm ngoài phần trong nháy đơn thành `u`.
  - Formatter dùng `ResolverStyle.STRICT` và `Locale.ENGLISH`, để `MMM` hiểu được `Jan`. D10 không chốt locale cho `dateFormat`; xem Open Questions.
  - Parse bằng `LocalDate.parse(value, input)`; không khớp thì ném `TransformationFailure("Value does not match pattern <inputFormat>")`.
  - Không tự trim trước khi parse. Người dùng đặt bước `trim` trước; đây là cách giữ hành vi deterministic và tường minh.
  - Giá trị rỗng giữ nguyên.
- **Kiểm pattern lúc lưu cấu hình**, bằng round-trip với giá trị mẫu:
  - `inputFormat` phải parse ngược được `input.format(LocalDateTime.of(2001, 2, 3, 4, 5, 6))` thành `2001-02-03`. Pattern thiếu năm, tháng hoặc ngày bị từ chối. Pattern có thêm giờ phút vẫn hợp lệ, để đọc được ISO datetime từ XLSX (D9).
  - `outputFormat` phải format được `LocalDate.of(2001, 2, 3)`. Pattern có giờ phút bị từ chối.
- **Field kiểu `date`**: `outputFormat` sau khi đổi `y` → `u` phải đúng là `uuuu-MM-dd` (D10). Vì so sánh sau khi đổi, cả `"yyyy-MM-dd"` (FE luôn gửi dạng này cho field `date`) lẫn `"uuuu-MM-dd"` đều hợp lệ.
- **`params` vắng mặt**: thiếu `params` hoặc `params: null` được coi như `{}`, vì FE không gửi `params` cho `trim`/`uppercase`/`lowercase`. Việc chuẩn hoá này làm trong `TransformationStep` (`params == null` → `Map.of()`), trước mọi bước kiểm tra.

### T4. Kiểm cấu hình (`TransformationConfigValidator`)
- Kiểm toàn bộ danh sách rồi trả **mọi** lỗi cùng lúc, dưới dạng `List<ProblemItem>` (`field`, `code=CONFIG_INVALID`, `message`).
- Có lỗi thì service ném `DomainException(CONFIG_INVALID, "Transformation configuration is invalid.", items)`, và không lưu gì.
- Các lỗi, theo thứ tự kiểm:
  - targetField không có trong schema;
  - `order` null hoặc âm;
  - `order` trùng trong cùng một field;
  - type không có trong registry;
  - params có key lạ;
  - thiếu params bắt buộc hoặc params rỗng;
  - pattern sai;
  - field `date` có output khác ISO.
- **400 hay 422**: JSON hỏng, sai kiểu JSON (ví dụ `"order": "abc"`) hoặc thiếu mảng `transformations` trả 400 `REQUEST_INVALID` (lỗi Jackson hoặc Bean Validation). Mọi lỗi về ý nghĩa cấu hình trả 422 `CONFIG_INVALID`.

### T5. Lưu và chuẩn hoá
- Lưu danh sách đã sắp xếp: theo vị trí field trong schema trước, rồi theo `order`. `params` lưu nguyên như client gửi; không tự điền `outputFormat` mặc định.
- *Vì sao*: config đã chuẩn hoá cho ra `configHash` ổn định (D7). Client gửi cùng nội dung theo thứ tự khác thì hash vẫn như nhau.
- Mảng rỗng là hợp lệ, nghĩa là xoá mọi transformation.

### T6. Prune khi schema đổi
- `TransformationConfig.prunedFor(TargetSchema newSchema)` trả `Pruned<TransformationConfig>(config, warnings)`.
- Bỏ mọi bước của field không còn trong schema, kèm một warning `CONFIG_PRUNED` cho mỗi field.
- Bỏ bước `dateFormat` có output khác ISO nếu field giờ là kiểu `date`, kèm một warning `CONFIG_PRUNED` cho mỗi bước bị bỏ.
- *Vì sao*: D10 chốt nguyên tắc prune config không còn hợp lệ khi schema đổi (xoá field, bỏ `email` khi đổi kiểu). Việc bỏ bước `dateFormat` là áp cùng nguyên tắc đó; xem Open Questions.

### T7. Đăng ký bean
`infrastructure/config/EngineConfig` tạo `TransformationRegistry(List.of(new TrimTransformation(), …))` và `TransformationEngine`. Domain không dùng annotation Spring (D1).

## Risks / Trade-offs

- [Người dùng quên `trim` trước `dateFormat`, và `" 25/12/1990"` bị báo lỗi] → Message lỗi nêu rõ pattern. Tài liệu API và FE gợi ý đặt `trim` trước. Chấp nhận để giữ hành vi tường minh.
- [`Locale.ENGLISH` cho tên tháng: dữ liệu tên tháng tiếng Việt ("Tháng 1") không parse được] → Hiếm gặp trong V0.1; về sau có thể thêm `params.locale`.
- [Tên class của F04 khác giả định] → Task 1 đối chiếu và sửa trước khi code.

## Open Questions

- **OQ1**: Locale cho `dateFormat` là `Locale.ENGLISH`. D10 chỉ chốt `Locale.ROOT` cho `uppercase`/`lowercase`. Đề xuất giữ `ENGLISH`, vì `Locale.ROOT` theo CLDR không đọc được `Jan`.
- **OQ2**: Khi đổi kiểu field sang `date`, bước `dateFormat` có output khác ISO bị prune kèm `CONFIG_PRUNED` (T6), thay vì để config ở trạng thái lỗi. Cần xác nhận khi review, vì D10 chỉ nêu ví dụ với `email`.
- **OQ3**: `defaultValue.value` rỗng bị từ chối (422). Pack không nói gì; đề xuất từ chối, vì một bước như vậy không có tác dụng.
- **OQ4**: params có key lạ bị từ chối (422), để bắt lỗi gõ nhầm như `outputformat`. FE hiện gửi `params: {}` cho `trim`, `uppercase`, `lowercase`, nên không bị ảnh hưởng.
