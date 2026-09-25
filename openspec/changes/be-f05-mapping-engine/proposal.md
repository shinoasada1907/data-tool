## Why

Sau khi có schema đích (F04), người dùng cần chỉ ra mỗi target field lấy giá trị từ đâu: một cột nguồn, hoặc một giá trị hằng (FR-05). BE phải kiểm cột nguồn có thật (F05 "Validate source column existence"), cảnh báo field chưa map, và chặn process khi field required chưa được map. Engine mapping này là bước đầu của pipeline (F08).

## What Changes

- `PUT /api/import-sessions/{id}/mapping` nhận `MappingConfigDto { mappings[{targetField, mappingType, sourceColumn, constantValue}] }`, chỉ gồm các field đã map. Mỗi lần PUT ghi đè toàn bộ mapping cũ. Trả `200 { session, warnings }`.
- Lỗi 422, kèm `errors[]` theo target field:
  - `MAPPING_INVALID`: target field không tồn tại, map trùng, thiếu hoặc thừa `sourceColumn`/`constantValue`, `mappingType` lạ;
  - `SOURCE_COLUMN_NOT_FOUND`: cột nguồn không có.
- Warning `TARGET_FIELD_UNMAPPED` cho mọi field chưa map.
- Readiness issue `TARGET_FIELD_REQUIRED` cho field required chưa map. Session chỉ `READY` khi mọi field required đã map.
- Prune mapping khi schema đổi, với warning `CONFIG_PRUNED`, dùng khung của F04.
- Engine trong domain:
  - registry `MappingStrategy` gồm `SourceColumnMappingStrategy` và `ConstantMappingStrategy`;
  - `RowMapper` map một `ImportRow` thành giá trị thô theo thứ tự schema.
- `session.config` có thêm `mapping`.

## Capabilities

### New Capabilities
- `field-mapping`: cấu hình mapping, map từ cột nguồn, map hằng, luật một mapping cho mỗi field, cảnh báo field chưa map, prune mapping, engine map row.

### Modified Capabilities
(không có — readiness `TARGET_FIELD_REQUIRED` được thêm dưới dạng requirement mới (ADDED) trong `import-session`; luồng cập nhật cấu hình của F04 được dùng lại, không đổi)

## Impact

- **Code**:
  - `MAIN/domain/mapping/*` (mới)
  - `MAIN/domain/config/ImportConfiguration` (thêm `mapping`), `ReadinessEvaluator.standard()` (thêm rule)
  - `MAIN/application/configuration/ConfigurationService` (thêm `updateMapping`)
  - `MAIN/infrastructure/persistence/*` (document `mapping_json`, hasher)
  - `MAIN/api/mapping/*`, `MAIN/api/importsession/SessionConfigDto` (thêm `mapping`)
- **DB**: không có migration. Cột `mapping_json` đã được tạo ở V3 (F04).
- **API**: endpoint #5. Response session có thêm `config.mapping`.
