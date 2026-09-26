# BE-F05 Basic Mapping Engine — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** `PUT /mapping` map mỗi target field với một cột nguồn hoặc một hằng; cảnh báo field chưa map; chặn `READY` khi field required chưa map. Engine map row trong domain để F08 dùng.

**Architecture:**
- `domain.mapping`: `MappingConfig.define`, các `MappingStrategy`, `RowMapper`.
- Gắn vào khung cấu hình của F04: `ImportConfiguration.mapping`, prune, `RequiredFieldsMappedRule`.
- `ConfigurationService.updateMapping` dùng lại luồng cập nhật chung (S6), với `mutation` đổi thành `BiFunction` (design M5).
- `api.mapping.MappingController`.

**Tech Stack:** Như F01–F04.

**Spec:** `openspec/changes/be-f05-mapping-engine/specs/{field-mapping,import-session}/spec.md`. Thiết kế: `design.md` cùng thư mục (M1–M7), `be-f04-target-schema/design.md` (S1–S9), `be-f02-csv-preview/design.md` (P1–P8), `be-f01-import-session/design.md` (D1–D14).

## Global Constraints

- Mọi ràng buộc chung của F01, F02 và F04 vẫn áp dụng.
- F04 phải đã xong và nằm trong nhánh này (tạo `feature/be-f05-mapping-engine` từ nhánh có F04).
- Tên cột nguồn và tên target field so khớp **chính xác** (phân biệt hoa thường).
- Message lỗi không chứa tên cột nguồn hay giá trị ô (D13).

---

## 1. Model mapping và luật kiểm

**Files:**
- Create: `MAIN/domain/mapping/MappingType.java`, `FieldMapping.java`, `MappingSpec.java`, `MappingConfig.java`
- Test: `TEST/domain/mapping/MappingConfigTest.java`

**Interfaces:**
- Consumes: `TargetSchema` (F04), `SourceSchema` (F02), `FieldScopedSection` (F04), `DomainException`, `ProblemItem` (F01).
- Produces: như design M1.

- [x] 1.1 Viết `MappingConfigTest`. Dùng schema `[name(order 0, required), country(order 1), note(order 2)]` và source columns `["Họ tên","email"]`. `sc(t, c)` là một mapping `SOURCE_COLUMN`; `k(t, v)` là một mapping `CONSTANT`.
  | Input `define` | Mong đợi |
  |---|---|
  | `[k("country","VN"), sc("name","Họ tên")]` | hợp lệ; `mappings` theo thứ tự schema: `name`, `country` |
  | `[]` | hợp lệ; `mappings` rỗng |
  | `[sc("name","Name")]` | `SOURCE_COLUMN_NOT_FOUND`; items `[("name","SOURCE_COLUMN_NOT_FOUND","Source column does not exist.")]` |
  | `[sc("name",null)]` | `MAPPING_INVALID`; message item `sourceColumn is required for SOURCE_COLUMN mappings.` |
  | `[new MappingSpec("name","SOURCE_COLUMN","Họ tên","x")]` | `MAPPING_INVALID`; `constantValue must be null for SOURCE_COLUMN mappings.` |
  | `[k("country","  ")]` | `MAPPING_INVALID`; `constantValue must not be blank for CONSTANT mappings.` |
  | `[new MappingSpec("country","CONSTANT","Họ tên","VN")]` | `MAPPING_INVALID`; `sourceColumn must be null for CONSTANT mappings.` |
  | `[sc("name","Họ tên"), sc("name","email")]` | `MAPPING_INVALID`; item `("name", …, "Target field is mapped more than once.")` |
  | `[sc("phone","email")]` | `MAPPING_INVALID`; `Target field does not exist in the schema.` |
  | `[new MappingSpec("name","source_column","Họ tên",null)]` | `MAPPING_INVALID`; `Unknown mapping type.` |
  | `[new MappingSpec(null,"CONSTANT",null,"x")]` | `MAPPING_INVALID`; item `(null, …, "Target field is required.")` |
  | `[sc("name","Name"), sc("phone","email")]` | top-level `MAPPING_INVALID`; 2 item, lần lượt có code `SOURCE_COLUMN_NOT_FOUND` và `MAPPING_INVALID` |

  Thêm test `retainFields({"name"})` chỉ giữ mapping của `name`; `referencedFields()` trả đúng các `targetField`; `sectionLabel()` là `"Mapping"`.
- [x] 1.2 Chạy `./mvnw -q test -Dtest=MappingConfigTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 1.3 Cài 4 class theo design M1.
- [x] 1.4 Chạy lại lệnh ở 1.2. Mong đợi: PASS.
- [x] 1.5 Commit: `feat(domain): field mapping configuration with validation rules`

## 2. Engine map row

**Files:**
- Create: `MAIN/domain/mapping/ResolvedMapping.java`, `MappingStrategy.java`, `SourceColumnMappingStrategy.java`, `ConstantMappingStrategy.java`, `MappingStrategies.java`, `RowMapper.java`
- Test: `TEST/domain/mapping/MappingStrategiesTest.java`, `TEST/domain/mapping/RowMapperTest.java`

**Interfaces:**
- Consumes: `MappingConfig` (task 1), `ImportRow`, `SourceSchema` (F02), `TargetSchema` (F04).
- Produces: như design M4.

- [x] 2.1 Viết `MappingStrategiesTest`:
  | Case | Mong đợi |
  |---|---|
  | `SourceColumnMappingStrategy.map(row(2,["An",null]), resolved(idx 0))` | `"An"` |
  | Như trên với idx 1 | `null` |
  | Như trên với idx 5 | `null` (row thiếu ô) |
  | `ConstantMappingStrategy.map(anyRow, resolved(const "VN"))` | `"VN"` |
  | `standard().strategyFor(SOURCE_COLUMN)` | là `SourceColumnMappingStrategy` |
  | `standard().strategyFor(CONSTANT)` | là `ConstantMappingStrategy` |
- [x] 2.2 Viết `RowMapperTest`. Schema `[name, country, note]`; source columns `["x","Họ tên"]`; mapping `name ← "Họ tên"`, `country ← "VN"`, `note` chưa map.
  | Row | Mong đợi |
  |---|---|
  | `(2, ["x","An"])` | `{name:"An", country:"VN", note:null}`; thứ tự key đúng `name, country, note` |
  | `(3, ["y"])` | `{name:null, country:"VN", note:null}` |
  | Gọi `map` 2 lần với cùng row | hai kết quả `equals` nhau |
- [x] 2.3 Chạy `./mvnw -q test -Dtest=MappingStrategiesTest,RowMapperTest`. Mong đợi: FAIL vì lỗi compile.
- [x] 2.4 Cài 6 class theo design M4. `RowMapper.of` đổi tên cột sang index một lần; không tìm lại theo tên ở mỗi row.
- [x] 2.5 Chạy lại lệnh ở 2.3. Mong đợi: PASS.
- [x] 2.6 Commit: `feat(domain): mapping strategies and row mapper`

## 3. Gắn mapping vào khung cấu hình (prune, warning, readiness)

**Files:**
- Create: `MAIN/domain/mapping/RequiredFieldsMappedRule.java`
- Modify: `MAIN/domain/config/ImportConfiguration.java` (thêm `MappingConfig mapping`, `withMapping`, prune trong `withSchema`), `MAIN/domain/config/ReadinessEvaluator.java` (`standard()` thêm rule)
- Test: sửa `TEST/domain/config/ImportConfigurationTest.java`, `TEST/domain/config/ReadinessEvaluatorTest.java`

**Interfaces:**
- Produces:
  ```java
  // ImportConfiguration(UUID sessionId, TargetSchema schema, MappingConfig mapping, Long version)
  public ConfigChange withMapping(MappingConfig mapping);    // warnings TARGET_FIELD_UNMAPPED theo thứ tự schema
  public final class RequiredFieldsMappedRule implements ReadinessRule { … }
  ```

- [ ] 3.1 Thêm test vào `ImportConfigurationTest`:
  | Case | Mong đợi |
  |---|---|
  | Schema `[name(required), note]`; `withMapping(mapping chỉ có name)` | `warnings` = `[("note","TARGET_FIELD_UNMAPPED","Field is not mapped.")]` |
  | Cấu hình có mapping `phone`; `withSchema(schema không có phone)` | mapping của `phone` bị xoá; `warnings` = `[("phone","CONFIG_PRUNED","Mapping for this field was removed because the field no longer exists.")]` |
  | Cấu hình có mapping `name`; `withSchema(schema vẫn có name, đổi type)` | mapping `name` giữ nguyên; `warnings` rỗng |
- [ ] 3.2 Thêm test vào `ReadinessEvaluatorTest`:
  | Config | Mong đợi |
  |---|---|
  | Schema `[email(required), note]`, mapping chỉ `note` | `ready` false; issues `[("email","TARGET_FIELD_REQUIRED","Required field is not mapped.")]` |
  | Schema `[email(required)]`, mapping `email` | `ready` true |
  | Schema rỗng | issues chỉ có `SCHEMA_EMPTY` (rule mới không sinh thêm issue) |
- [ ] 3.3 Chạy `./mvnw -q test -Dtest=ImportConfigurationTest,ReadinessEvaluatorTest`. Mong đợi: FAIL.
- [ ] 3.4 Cài theo design M2 và M3. Cập nhật mọi chỗ tạo `ImportConfiguration` (constructor có thêm `mapping`).
- [ ] 3.5 Chạy lại lệnh ở 3.3, rồi `./mvnw -q test`. Mong đợi: PASS toàn bộ.
- [ ] 3.6 Commit: `feat(domain): prune mappings and require mapped required fields`

## 4. Persistence và hash của mapping

**Files:**
- Create: `MAIN/infrastructure/persistence/MappingDocument.java`
- Modify: `MAIN/infrastructure/persistence/ImportConfigurationEntity.java`, `JpaImportConfigurationRepository.java`, `JsonConfigHasher.java`
- Test: sửa `TEST/infrastructure/persistence/JpaImportConfigurationRepositoryTest.java`, `TEST/infrastructure/persistence/JsonConfigHasherTest.java`

**Interfaces:**
- Produces:
  ```java
  public record MappingDocument(List<FieldMappingDocument> mappings) {
      public record FieldMappingDocument(String targetField, String mappingType, String sourceColumn, String constantValue) {}
      public static MappingDocument from(MappingConfig mapping);
      public MappingConfig toDomain();
  }
  ```

- [ ] 4.1 Thêm test:
  | Test | Case | Mong đợi |
  |---|---|---|
  | Repository | Lưu cấu hình có mapping `name ← "Họ tên"`, `country ← "VN"`, rồi đọc lại | mapping đọc ra bằng bản đã lưu |
  | Repository | Đọc một row có `mapping_json` bằng giá trị mặc định | `mapping` là `MappingConfig.empty()` |
  | Hasher | Hai cấu hình cùng schema, khác mapping | hai hash khác nhau |
  | Hasher | Cùng mapping, `define` từ hai input khác thứ tự | hai hash giống nhau (nhờ đã chuẩn hoá thứ tự, M1) |
- [ ] 4.2 Chạy `./mvnw -q test -Dtest=JpaImportConfigurationRepositoryTest,JsonConfigHasherTest`. Mong đợi: FAIL.
- [ ] 4.3 Cài `MappingDocument`; đọc/ghi cột `mapping_json`; hasher thêm mapping theo thứ tự S7.
- [ ] 4.4 Chạy lại lệnh ở 4.2. Mong đợi: PASS.
- [ ] 4.5 Commit: `feat(infra): persist and hash field mappings`

## 5. Use case `updateMapping`

**Files:**
- Modify: `MAIN/application/configuration/ConfigurationService.java` (đổi `mutation` sang `BiFunction`, thêm `updateMapping`), `TEST/application/configuration/ConfigurationServiceTest.java`

**Interfaces:**
- Produces: `public ConfigUpdateResult updateMapping(UUID sessionId, List<MappingSpec> mappings);`

- [ ] 5.1 Thêm test vào `ConfigurationServiceTest`. Session `CONFIGURING` có source columns `["Họ tên","email"]` và schema `[name(required), email(required), note]`.
  | Case | Mong đợi |
  |---|---|
  | `updateMapping` map `name`, `email` | `status` `READY`; `warnings` = `[note TARGET_FIELD_UNMAPPED]` |
  | `updateMapping` chỉ map `name` | `status` `CONFIGURING`; readiness issues `[email TARGET_FIELD_REQUIRED]` |
  | `updateMapping` với cột `Name` | ném `SOURCE_COLUMN_NOT_FOUND`; cấu hình không đổi |
  | Id không tồn tại | ném `SESSION_NOT_FOUND` |
  | Session `FAILED` | ném `SESSION_STATE_INVALID` |
  | Sau khi `READY`, gọi `updateSchema` bỏ field `email` | `warnings` chứa `("email","CONFIG_PRUNED",…)`; mapping không còn `email` |
- [ ] 5.2 Chạy `./mvnw -q test -Dtest=ConfigurationServiceTest`. Mong đợi: FAIL.
- [ ] 5.3 Cài theo design M5. `updateSchema` đổi sang `BiFunction` nhưng hành vi giữ nguyên.
- [ ] 5.4 Chạy lại lệnh ở 5.2. Mong đợi: PASS, cả các case F04 cũ.
- [ ] 5.5 Commit: `feat(app): PUT mapping use case`

## 6. API: PUT /mapping

**Files:**
- Create: `MAIN/api/mapping/MappingController.java`, `MAIN/api/mapping/MappingConfigDto.java`, `MAIN/api/mapping/FieldMappingDto.java`
- Modify: `MAIN/api/importsession/SessionConfigDto.java` (thêm `MappingConfigDto mapping`), `ImportSessionDto.from`
- Test: `TEST/api/mapping/MappingControllerTest.java`; sửa `TEST/api/importsession/ImportSessionControllerTest.java`

**Interfaces:**
- Produces:
  ```java
  public record MappingConfigDto(@NotNull List<@NotNull @Valid FieldMappingDto> mappings) {
      public static MappingConfigDto from(MappingConfig mapping);
      public List<MappingSpec> toSpecs();
  }
  public record FieldMappingDto(String targetField, String mappingType, String sourceColumn, String constantValue) {}
  // PUT /api/import-sessions/{id}/mapping → 200 ConfigUpdateResponseDto
  ```

- [ ] 6.1 Viết `MappingControllerTest` (`@WebMvcTest(MappingController.class)`, `@MockitoBean ConfigurationService`):
  | Request | Stub | Mong đợi |
  |---|---|---|
  | PUT `{"mappings":[{"targetField":"name","mappingType":"SOURCE_COLUMN","sourceColumn":"Họ tên","constantValue":null}]}` | trả kết quả có warning `note` | 200; `$.session.config.mapping.mappings[0].sourceColumn` `Họ tên`; `$.warnings[0].code` `TARGET_FIELD_UNMAPPED`. Service nhận đúng `MappingSpec` |
  | PUT `{}` | — | 400; `$.code` `REQUEST_INVALID` |
  | PUT `{"mappings":[null]}` | — | 400; `$.code` `REQUEST_INVALID` |
  | PUT hợp lệ | ném `SOURCE_COLUMN_NOT_FOUND` kèm item | 422; `$.code` `SOURCE_COLUMN_NOT_FOUND`; `$.errors[0].field` `name` |
  | PUT hợp lệ | ném `SESSION_NOT_FOUND` | 404; `$.code` `SESSION_NOT_FOUND` |
  | PUT hợp lệ | ném `SESSION_STATE_INVALID` | 409 |
- [ ] 6.2 Sửa `ImportSessionControllerTest`: `GET /{id}` trả `$.config.mapping.mappings` (mảng).
- [ ] 6.3 Chạy `./mvnw -q test -Dtest=MappingControllerTest,ImportSessionControllerTest`. Mong đợi: FAIL.
- [ ] 6.4 Cài controller và DTO; sửa `SessionConfigDto` và `ImportSessionDto.from`.
- [ ] 6.5 Chạy lại lệnh ở 6.3. Mong đợi: PASS.
- [ ] 6.6 Commit: `feat(api): PUT mapping endpoint`

## 7. Integration test qua HTTP thật

**Files:**
- Test: `TEST/api/mapping/MappingIntegrationTest.java` (setup như các integration test trước)

- [ ] 7.1 Viết các case. Mọi case bắt đầu bằng: upload `customers.csv` = `Họ tên,email\nAn,an@x.com\n`, rồi PUT schema `[name(string, required, 0), email(email, required, 1), country(string, 2)]`.
  | Case | Mong đợi |
  |---|---|
  | PUT mapping `name ← "Họ tên"`, `email ← "email"`, `country ← hằng "VN"` | 200; `status` `READY`; `warnings` rỗng |
  | PUT mapping chỉ `name` | 200; `status` `CONFIGURING`; `readiness.issues[0]` = `{field "email", code "TARGET_FIELD_REQUIRED"}`; `warnings` có `email` và `country` |
  | PUT mapping `name ← "Name"` | 422 `SOURCE_COLUMN_NOT_FOUND`; GET cho thấy mapping cũ không đổi |
  | Sau khi map đủ, PUT schema đổi `country` thành `nation` | `warnings` có `{field "country", code "CONFIG_PRUNED"}`; GET không còn mapping `country` |
  | PUT mapping cho UUID chưa từng tạo | 404 `SESSION_NOT_FOUND` |
- [ ] 7.2 Chạy `./mvnw -q test -Dtest=MappingIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính, không nới lỏng test.
- [ ] 7.3 Commit: `test(api): field mapping over real HTTP`

## 8. Kiểm tra toàn bộ và hoàn tất

- [ ] 8.1 Chạy `./mvnw -q verify`. Mong đợi: mọi test xanh, gồm ArchitectureTest (`domain.mapping` chỉ dùng `java.*` và `domain.*`).
- [ ] 8.2 Chạy app thật và thử bằng `curl`: upload, PUT schema, PUT mapping (hợp lệ, sai cột, map trùng), GET session.
- [ ] 8.3 Tick checkbox, ghi LÝ DO cho mọi chỗ làm khác kế hoạch. Commit: `docs(openspec): complete be-f05 tasks`
- [ ] 8.4 Hỏi người dùng trước khi merge. Sau khi merge: `openspec archive be-f05-mapping-engine -y`.
