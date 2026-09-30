## Context

- Nền chung: `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md`, gồm:
  - D4 (`MAPPING_INVALID`, `SOURCE_COLUMN_NOT_FOUND`);
  - D10: map `SOURCE_COLUMN` / `CONSTANT` / `null`; giá trị là chuỗi cho tới bước ép kiểu; field required chưa map thành readiness issue.
- Nền của F02: `SourceSchema`, `SourceColumn` (tên duy nhất), `ImportRow` (giá trị theo `index`).
- Nền của F04:
  - `ImportConfiguration`, `FieldScopedSection` / `ConfigPruner`, `ReadinessRule` / `ReadinessEvaluator.standard()`;
  - luồng cập nhật cấu hình (`ConfigurationService`, S6);
  - `JsonConfigHasher`; cột `mapping_json`.

## Goals / Non-Goals

**Goals:**
- Kiểm mapping ngay khi PUT, để lỗi hiện đúng ở bước Mapping của FE.
- Engine map row trong domain, có tính xác định và test được, để F08 dùng nguyên.

**Non-Goals:**
- Map một field từ nhiều cột (ghép chuỗi), map theo biểu thức, tự đoán mapping (AI): ngoài V0.1.
- Kiểm hằng số có đúng kiểu field hay không: hằng đi qua transformation và validation như mọi giá trị khác (D10).

## Decisions

### M1. Model trong `domain.mapping`
```java
public enum MappingType { SOURCE_COLUMN, CONSTANT }
public record FieldMapping(String targetField, MappingType type, String sourceColumn, String constantValue) {}
public record MappingSpec(String targetField, String mappingType, String sourceColumn, String constantValue) {}   // input thô
public record MappingConfig(List<FieldMapping> mappings) implements FieldScopedSection<MappingConfig> {
    public static MappingConfig empty();
    public static MappingConfig define(List<MappingSpec> specs, TargetSchema schema, SourceSchema source);
    public Optional<FieldMapping> forField(String targetField);
    // sectionLabel() = "Mapping"; referencedFields() = các targetField; retainFields(names) lọc theo tên
}
```
`define` gom **mọi** vi phạm, theo thứ tự input:

| Vi phạm | `code` của item | `field` | `message` |
|---|---|---|---|
| `targetField` null hoặc trống | `MAPPING_INVALID` | `null` | `Target field is required.` |
| `targetField` không có trong schema (so khớp chính xác) | `MAPPING_INVALID` | targetField | `Target field does not exist in the schema.` |
| `targetField` đã xuất hiện ở một mapping trước đó | `MAPPING_INVALID` | targetField | `Target field is mapped more than once.` |
| `mappingType` không phải `SOURCE_COLUMN`/`CONSTANT` (phân biệt hoa thường) | `MAPPING_INVALID` | targetField | `Unknown mapping type.` |
| `SOURCE_COLUMN` mà `sourceColumn` null hoặc trống | `MAPPING_INVALID` | targetField | `sourceColumn is required for SOURCE_COLUMN mappings.` |
| `SOURCE_COLUMN` mà `constantValue` khác null | `MAPPING_INVALID` | targetField | `constantValue must be null for SOURCE_COLUMN mappings.` |
| `SOURCE_COLUMN` mà `sourceColumn` không khớp chính xác tên cột nào trong `source.columns()` | `SOURCE_COLUMN_NOT_FOUND` | targetField | `Source column does not exist.` |
| `CONSTANT` mà `constantValue` null hoặc toàn khoảng trắng | `MAPPING_INVALID` | targetField | `constantValue must not be blank for CONSTANT mappings.` |
| `CONSTANT` mà `sourceColumn` khác null | `MAPPING_INVALID` | targetField | `sourceColumn must be null for CONSTANT mappings.` |

- **Mã lỗi top-level**: có ít nhất một item `MAPPING_INVALID` thì là `MAPPING_INVALID` (message `Mapping is invalid.`); ngược lại là `SOURCE_COLUMN_NOT_FOUND` (message `Source column not found.`).
- **Message không chứa tên cột nguồn**, vì tên cột lấy từ file (D13). FE biết mình đã chọn cột nào.
- **Chuẩn hoá**: ~~mapping hợp lệ được sắp theo `order` của field trong schema (trong `define`)~~ → mapping **luôn** theo thứ tự schema. Đây là bất biến của `ImportConfiguration`: constructor gọi `MappingConfig.inSchemaOrder(schema)`, nên cùng một nội dung luôn cho cùng `configHash`, dù gửi theo thứ tự nào.
  **LÝ DO** (review 2026-09-26): nếu chỉ sắp trong `define` thì khi PUT schema đổi thứ tự field, prune giữ nguyên thứ tự cũ của mapping. Hậu quả:
  - GET trả mapping lệch thứ tự schema;
  - session `PROCESSED` nhận lại đúng mapping cũ vẫn bị coi là đã đổi, nên rơi về `READY` (từ F08 còn bị xoá `result/`).
  Đặt bất biến ở constructor thì phủ được mọi đường: `define`, prune, và đọc từ DB. F06/F07 nên làm tương tự cho phần của mình.
- **Hằng rỗng bị từ chối**, dù D10 cho phép field rỗng. Lý do: hằng rỗng tương đương "chưa map" nhưng lại che mất warning `TARGET_FIELD_UNMAPPED`, và FE cũng coi đây là lỗi cấu hình.

### M2. Warning và readiness
- `ImportConfiguration.withMapping(MappingConfig)` trả `ConfigChange`. Mỗi field trong schema chưa có mapping sinh một warning `(field, "TARGET_FIELD_UNMAPPED", "Field is not mapped.")`, theo thứ tự schema.
- `RequiredFieldsMappedRule implements ReadinessRule`: mỗi field `required` chưa map sinh issue `(field, "TARGET_FIELD_REQUIRED", "Required field is not mapped.")`, theo thứ tự schema.
- `ReadinessEvaluator.standard()` giờ là `[SchemaNotEmptyRule, RequiredFieldsMappedRule]`.
- Field optional chưa map chỉ sinh warning, không phải readiness issue.
- PUT `/schema` không sinh `TARGET_FIELD_UNMAPPED`. Warning này chỉ đi kèm PUT `/mapping`, còn readiness luôn phản ánh field required chưa map.

### M3. Prune
- `ImportConfiguration` có thêm `MappingConfig mapping`, mặc định `empty()`.
- `withSchema(newSchema)` gọi `ConfigPruner.prune(mapping, newSchema.fieldNames(), warnings)`. Mỗi field bị bỏ sinh warning `CONFIG_PRUNED` với nhãn `Mapping`.
- Đổi kiểu field không ảnh hưởng mapping.

### M4. Engine map row
Pack định nghĩa `Object map(ImportRow row, FieldMapping mapping)`. Ở đây trả `String`, vì mọi giá trị là chuỗi cho tới bước ép kiểu (D10). Tên cột được đổi sang index **một lần** khi dựng `RowMapper`, không đổi lại ở mỗi row.
```java
public record ResolvedMapping(String targetField, MappingType type, int columnIndex, String constantValue) {}  // columnIndex = -1 với CONSTANT
public interface MappingStrategy { MappingType type(); String map(ImportRow row, ResolvedMapping mapping); }
public final class SourceColumnMappingStrategy implements MappingStrategy { … }   // row.value(columnIndex)
public final class ConstantMappingStrategy implements MappingStrategy { … }       // mapping.constantValue()
public final class MappingStrategies {
    public static MappingStrategies standard();                  // SOURCE_COLUMN, CONSTANT
    public MappingStrategy strategyFor(MappingType type);        // thiếu → IllegalStateException (lỗi lập trình)
}
public final class RowMapper {
    public static RowMapper of(TargetSchema schema, MappingConfig mapping, SourceSchema source, MappingStrategies strategies);
    public LinkedHashMap<String, String> map(ImportRow row);    // key theo thứ tự schema; field chưa map → null
}
```
Giá trị trả về là **giá trị thô** trước transformation, tức cũng là `sourceValue` của `ImportError` (D10).

### M5. Mở rộng luồng cập nhật của F04
- Hàm `mutation` riêng của `ConfigurationService.update` đổi từ `Function<ImportConfiguration, ConfigChange>` sang `BiFunction<ImportSession, ImportConfiguration, ConfigChange>`, vì `MappingConfig.define` cần `session.sourceSchema()`.
- Session đi qua bước kiểm trạng thái (không `UPLOADED`) thì luôn đã có `sourceSchema`.
- `updateMapping(UUID, List<MappingSpec>)` = `update(id, (s, c) -> c.withMapping(MappingConfig.define(specs, c.schema(), s.sourceSchema().orElseThrow())))`.

### M6. Persistence và hash
- `MappingDocument(List<FieldMappingDocument> mappings)`, với `FieldMappingDocument(targetField, mappingType, sourceColumn, constantValue)` và `mappingType` là tên enum.
- Adapter đọc và ghi cột `mapping_json`.
- `JsonConfigHasher` hash thêm `MappingDocument`, đúng thứ tự S7 của F04.

### M7. API
- **Endpoint**: `PUT /api/import-sessions/{id}/mapping` nằm trong `api.mapping.MappingController`.
  - Body: `MappingConfigDto(@NotNull List<@NotNull @Valid FieldMappingDto> mappings)`.
  - `FieldMappingDto(String targetField, String mappingType, String sourceColumn, String constantValue)`.
  - `mappingType` nhận dạng String: giá trị lạ trả 422 `MAPPING_INVALID`, không phải 400.
- **`SessionConfigDto`** có thêm `MappingConfigDto mapping`, với mapping đã sắp theo thứ tự schema.

## Risks / Trade-offs

- [Tên cột phải khớp chính xác, kể cả hoa thường] → FE lấy `columns[].name` từ preview, nên luôn khớp. Tên cột đã được làm duy nhất ở F02.
- [Hằng không được kiểm kiểu khi PUT, nên hằng sai kiểu làm mọi row lỗi] → Lỗi hiện rõ ở result, với `VALIDATION_TYPE` trên từng row. Kiểm sớm để dành cho phiên bản sau (YAGNI).

## Migration Plan

- Không có migration. Cấu hình lưu từ trước F05 có `mapping_json` bằng giá trị mặc định `{"mappings": []}`.

## Open Questions

(không có)
