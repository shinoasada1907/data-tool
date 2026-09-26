# BE-F08 Pipeline Orchestration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit; tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** Nối mapping → transformation → validation thành một pipeline deterministic chạy dạng stream. Kết quả lưu vào result store (D7). Thêm `POST /api/import-sessions/{id}/process`, cho phép process lại, và xoá kết quả khi config đổi.

**Architecture:**
- **Domain** (Java thuần): `ImportPipeline`/`DefaultImportPipeline`; các kiểu `RowResult`, `ImportError`, `PipelineSummary`; hai port `ResultStore`/`ResultWriter`.
- **Infrastructure**: `FileResultStore` (ndjson, dùng Jackson 3).
- **Application**: `ProcessService` (khoá, readiness, xử lý `FAILED`).
- **API**: `ProcessController`.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Jackson 3, JUnit Jupiter 6, AssertJ, Mockito, Testcontainers 2.0.5.

**Spec:** `openspec/changes/be-f08-pipeline/specs/import-pipeline/spec.md`. Thiết kế riêng: `design.md` cùng thư mục (P1–P8). Quyết định nền: `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md` (D1–D14).

## Global Constraints

- Chạy mọi lệnh Maven từ `apps/api` trong worktree `D:\Code\Product\universal-importer-be`. Viết tắt đường dẫn: `MAIN` = `apps/api/src/main/java/com/universalimporter`, `TEST` = `apps/api/src/test/java/com/universalimporter`.
- Nhánh `feature/be-f08-pipeline`, tạo từ nhánh đã có F01–F07.
- Mã lỗi theo D4:
  - `/process` trả: 404 `SESSION_NOT_FOUND`, 409 `SESSION_NOT_READY` (kèm `errors[]` là readiness issue), 409 `SESSION_STATE_INVALID`, 422 `FILE_PARSE_ERROR`, 500 `INTERNAL_ERROR`.
  - Lỗi theo row dùng `RowErrorCode` (F06).
- FE nhận biết session mất theo `code` (`SESSION_NOT_FOUND`), không theo status. Test phải kiểm `code`.
- File kết quả: UTF-8 không BOM, xuống dòng `\n`, `BigDecimal` ghi dạng plain, `LocalDate` ghi dạng `yyyy-MM-dd`, key theo thứ tự schema.
- Không gom row vào RAM. Pipeline chỉ giữ biến đếm và `UniqueTracker`.
- `domain` chỉ import `java.*` và `com.universalimporter.domain.*`. ArchitectureTest phải xanh.
- Log không chứa giá trị ô (D13).
- Không có migration mới.
- Commit message kết thúc bằng dòng `Co-Authored-By`. Không push, không merge khi chưa hỏi.

---

## 1. Đối chiếu giả định với code F02–F07 đã merge

- [x] 1.1 Ghi lại tên thật của:
  - `SourceParser`, `ImportRow`, cách chọn parser theo `SourceFileType`, `SourceSchema`;
  - aggregate config, bộ tính readiness, khoá session, hàm `configHash`, hàm cập nhật dùng chung của F04;
  - registry `MappingStrategy`, `MappingConfig` (F05);
  - `TransformationEngine`, `TransformationConfig`, `RowErrorCode` (F06);
  - `FieldValidator`, `UniqueTracker`, `ValidationConfig` (F07).
- [x] 1.2 Tên nào khác `design.md` ("Giả định về F02–F07") thì sửa `design.md` và các task dưới: gạch tên cũ, ghi LÝ DO.
- [x] 1.3 Commit (nếu có sửa): `docs(openspec): align be-f08 plan with merged F02-F07 names`

**Kết quả đối chiếu (2026-09-26, code `dev` @ 003928d):**

| Giả định | Tên thật / cách làm | Ảnh hưởng tới F08 |
|---|---|---|
| `SourceParser`, chọn parser theo type | `domain.source.SourceParser` (`supports`, `inspect`, `read`); chọn bằng `application.importsession.SourceParsers.find(type)` | `ProcessService` dùng `SourceParsers` |
| `ImportRow` | `ImportRow(long rowNumber, List<String> values)`, ô trống là `null` | `rowNumber` ép sang `int` bằng `Math.toIntExact` (file ≤ 20MB) |
| Session file | `LocalFileStorage`: `{root}/{id}/source.bin`; root là `StorageProperties.dir` | `FileResultStore` ghi vào `{root}/{id}/result/` |
| ~~`SessionConfiguration`~~ | `ImportConfiguration(sessionId, schema, mapping, transformations, validations, version)` | `PipelineConfig` lấy từ đây |
| Readiness | `ReadinessEvaluator.standard().evaluate(config)` → `Readiness(ready, issues)` | Không |
| `configHash` | Port `domain.config.ConfigHasher` (`JsonConfigHasher`) | Không |
| Hàm cập nhật chung (P7) | `ConfigurationService.update(...)` đã chừa chỗ "F08: … result/ is deleted here" | Xoá kết quả **sau khi transaction commit**, vẫn trong khoá (xem task 5) |
| ~~`MappingStrategy.map(row, mapping)` gọi từng field~~ | F05 có `RowMapper.of(schema, mapping, source, strategies).map(row)`: đổi tên cột → index một lần mỗi job | Thêm `RowMapper.mapField(row, fieldIndex)` để bắt lỗi từng field (P2); `MappingStrategies` có constructor public để test tiêm strategy lỗi |
| `TransformationEngine`, `stepsFor` | Đúng như giả định (F06) | Gom step theo field **một lần mỗi job** (việc F06 để lại) |
| `FieldValidator`, `UniqueTracker` | F07, sau review: `tracker.beginRow(n)` rồi `commitRow()`/`discardRow()`; `validate(field, rules, value, int row, tracker)` | Pipeline bao mỗi row bằng `beginRow` |
| ~~`MAIN/api/importsession/ProcessController`~~ | Controller mới đặt theo chức năng (`api.schema`, `api.mapping`…) | `api.process` |
| ~~`TEST/application/importsession/ConfigChangeInvalidatesResultTest`~~ | Test service cấu hình ở `application.configuration` | Đặt tại đó |
| Việc F06/F07 để lại cho F08 | `DatePatterns.formatter` compile mỗi ô; engine và validator log WARN mỗi ô khi có bug | Cache formatter có giới hạn; log bug theo kiểu giới hạn (xem task 2) |

## 2. Pipeline trong domain

**Files:**
- Create: trong `MAIN/domain/pipeline/`: `ErrorStage.java`, `ImportError.java`, `RowResult.java`, `RowResultSink.java`, `PipelineConfig.java`, `PipelineSummary.java`, `ImportPipeline.java`, `DefaultImportPipeline.java`
- Test: `TEST/domain/pipeline/DefaultImportPipelineTest.java`, `TEST/domain/pipeline/SampleDataset.java` (bộ dữ liệu mẫu dựng trong bộ nhớ, dùng chung cho các test)

**Interfaces:**
- Consumes: F05 (mapping), F06 (`TransformationEngine`), F07 (`FieldValidator`, `UniqueTracker`).
- Produces: đúng các chữ ký ở design P1 và P3.

- [ ] 2.1 Viết `SampleDataset`, dựng lại "bộ dữ liệu mẫu" trong spec:
  - các `ImportRow` từ row 2 tới row 7 (ô trống là `null`);
  - schema: `name` (string, required), `email` (email, required), `age` (number), `dob` (date);
  - mapping theo tên cột;
  - transformation: `name: trim(0)`; `email: trim(0), lowercase(1)`; `dob: dateFormat(0){inputFormat:"dd/MM/yyyy"}`;
  - validation: `email: unique`.
- [ ] 2.2 Viết `DefaultImportPipelineTest`, dùng sink gom kết quả vào list:
  | Case | Mong đợi |
  |---|---|
  | Chạy `SampleDataset` | Sink nhận 6 row theo thứ tự 2..7. Row 2, 5, 7 `valid`; row 3, 4, 6 không `valid` |
  | Row 2 | `values` = `{name:"An", email:"an@x.com", age:BigDecimal("30"), dob:LocalDate(1990,12,25)}`; key theo đúng thứ tự schema |
  | Row 3 | `values` = `{name:"Bình", email:"binh@x", age:"abc", dob:null}`. Có đúng 3 lỗi theo thứ tự `email`/`age`/`dob`:<br>• `email`: VALIDATION, `rule` `"email"`, `step` null, VALIDATION_EMAIL, `sourceValue` `"binh@x"`<br>• `age`: VALIDATION, `rule` `"type"`, VALIDATION_TYPE, `sourceValue` `"abc"`<br>• `dob`: TRANSFORMATION, `rule` `"dateFormat"`, `step` 0, TRANSFORMATION_FAILED, `sourceValue` `"31/02/1990"` |
  | Row 4 | 2 lỗi:<br>• `name`: VALIDATION_REQUIRED, `sourceValue` null<br>• `email`: VALIDATION_UNIQUE, message `"Duplicate value; first seen in row 2."`, `sourceValue` `"an@x.com"` |
  | Row 5 | `values` = `{name:"Cường", email:"cuong@x.com", age:null, dob:null}` |
  | Row 7 | hợp lệ, dù row 6 (bị lỗi) cũng có `"dung@x.com"` |
  | Summary | `total`=6, `valid`=3, `invalid`=3<br>`errorCountsByCode` = `{TRANSFORMATION_FAILED:1, VALIDATION_EMAIL:1, VALIDATION_REQUIRED:1, VALIDATION_TYPE:2, VALIDATION_UNIQUE:1}`, key theo thứ tự tên code<br>`errorCountsByField` = `{name:1, email:2, age:2, dob:1}`, key theo thứ tự schema |
  | Chạy `SampleDataset` 2 lần trên cùng một instance pipeline | Lần 2 có summary giống hệt lần 1, và row 2 vẫn hợp lệ (mỗi lần chạy dùng tracker mới) |
  | 5 row. Registry có transformation `explode` ném `IllegalStateException("boom")` khi giá trị là `"boom"`; row 3 có giá trị đó | `total`=5; row 3 có lỗi TRANSFORMATION_FAILED; 4 row còn lại xử lý bình thường; không kết quả nào chứa `"boom"` ngoài `sourceValue` |
  | `MappingStrategy` stub ném `RuntimeException` ở row 2 | Row 2 có lỗi `{code: TRANSFORMATION_FAILED, rule: "mapping", step: null, message: "Unexpected error while mapping the value."}`; row 3 vẫn được xử lý |
- [ ] 2.3 Chạy `./mvnw -q test -Dtest=DefaultImportPipelineTest`. Mong đợi: FAIL, vì lỗi compile.
- [ ] 2.4 Viết các class theo design P1–P4.
- [ ] 2.5 Chạy lại lệnh ở 2.3. Mong đợi: PASS.
- [ ] 2.6 Commit: `feat(domain): streaming import pipeline with row results and summary`

## 3. Result store trên đĩa

**Files:**
- Create: `MAIN/domain/pipeline/ResultStore.java`, `ResultWriter.java`, `ResultSummary.java`, `MAIN/infrastructure/result/FileResultStore.java`
- Test: `TEST/infrastructure/result/FileResultStoreTest.java`

**Interfaces:**
- Consumes: `StorageProperties` (F01).
- Produces: đúng các chữ ký ở design P5.

- [ ] 3.1 Viết `FileResultStoreTest`, dùng `@TempDir root` và `new FileResultStore(new StorageProperties(root), jsonMapper)`:
  | Case | Mong đợi |
  |---|---|
  | `begin(id)` → accept row 2 hợp lệ `{name:"An", age:BigDecimal("30"), dob:LocalDate(1990,12,25)}` → accept row 3 lỗi → `commit(summary)` | `result/valid.ndjson` đúng bằng `{"rowNumber":2,"values":{"name":"An","age":30,"dob":"1990-12-25"}}\n`<br>`invalid.ndjson` có 1 dòng, có `"errors":[…]` và `"step":null`<br>`summary.json` có `configHash`<br>không còn `result.tmp-*` |
  | Row có `BigDecimal("1E+3")` và `BigDecimal("-3.50")` | ghi ra `1000` và `-3.50` |
  | Row có `"Nguyễn"` | file chứa đúng các byte UTF-8 của `Nguyễn`, không có `\u` |
  | Mọi file vừa ghi | không chứa byte `\r` và không có BOM |
  | `begin` → accept → `close()` mà không `commit` | không có `result/`, không còn `result.tmp-*` |
  | Đã có `result/` từ lần trước, `begin` → `commit` lần mới | `result/` mang nội dung mới; không còn `result.old-*` |
  | `findSummary(id)` khi chưa có kết quả | `Optional.empty()` |
  | `delete(id)` | `result/` biến mất; `source.bin` còn nguyên |
- [ ] 3.2 Chạy `./mvnw -q test -Dtest=FileResultStoreTest`. Mong đợi: FAIL, vì lỗi compile.
- [ ] 3.3 Viết `FileResultStore`:
  - Dùng `JsonMapper` của Jackson 3, bật `WRITE_BIGDECIMAL_AS_PLAIN`.
  - Đổi `LocalDate` sang `toString()` trước khi ghi.
  - Ghi từng dòng, kết thúc bằng `\n`.
  - Thay kết quả cũ bằng 3 bước rename như design P5.
- [ ] 3.4 Chạy lại lệnh ở 3.2. Mong đợi: PASS.
- [ ] 3.5 Commit: `feat(infra): file-based result store with atomic replacement`

## 4. ProcessService

**Files:**
- Create: `MAIN/application/pipeline/ProcessService.java`, `MAIN/application/pipeline/PipelineSummaryView.java`
- Modify: `MAIN/infrastructure/config/EngineConfig.java` (thêm bean `ImportPipeline`)
- Test: `TEST/application/pipeline/ProcessServiceTest.java`. Dùng fake:
  - session repo (F01) và config (F04);
  - `InMemoryFileStorage`: có thể xoá file hoặc ném IO;
  - `FakeSourceParser`: trả row của `SampleDataset`, hoặc ném lỗi ở row chỉ định;
  - `FakeResultStore` (in-memory).

**Interfaces:**
- Produces: `public PipelineSummaryView process(UUID sessionId)`, với `PipelineSummaryView(UUID sessionId, SessionStatus status, ResultSummary summary)`.

- [ ] 4.1 Viết `ProcessServiceTest` (`Clock.fixed(t0)`):
  | Case | Mong đợi |
  |---|---|
  | Session READY, config của bộ mẫu | trả `status`=PROCESSED, `total`=6, `valid`=3, `invalid`=3; session đã lưu là PROCESSED; kết quả đã commit có `configHash` bằng hash của config và `processedAt`=`t0` |
  | Session CONFIGURING, readiness có `TARGET_FIELD_REQUIRED(email)` | `DomainException(SESSION_NOT_READY)`, `items` = `[{field:"email", code:"TARGET_FIELD_REQUIRED", …}]`; store không nhận `begin` nào |
  | Session FAILED | `DomainException(SESSION_STATE_INVALID)` |
  | Id không tồn tại | `DomainException(SESSION_NOT_FOUND)` |
  | Session PROCESSED đã có kết quả; parser ném `DomainException(FILE_PARSE_ERROR)` ở row 4 | ném lại `FILE_PARSE_ERROR`; session thành FAILED; store không còn kết quả cũ, không còn thư mục tạm |
  | Storage không có file của session (`open` ném `UncheckedIOException`) | `DomainException(INTERNAL_ERROR)`; session thành FAILED |
  | Parser ném `UncheckedIOException` ở row 3 | `DomainException(INTERNAL_ERROR)`; session thành FAILED; không còn thư mục tạm |
  | Ngay sau case FILE_PARSE_ERROR, gọi `process` lần nữa | `DomainException(SESSION_STATE_INVALID)` |
  | Session PROCESSED, gọi `process` lại | vẫn PROCESSED; kết quả được thay bằng lần chạy mới |
- [ ] 4.2 Chạy `./mvnw -q test -Dtest=ProcessServiceTest`. Mong đợi: FAIL.
- [ ] 4.3 Viết `ProcessService` theo design P6: toàn bộ chạy trong khoá session, lưu session một lần ở cuối, không bọc cả hàm trong `@Transactional`.
- [ ] 4.4 Chạy lại lệnh ở 4.2. Mong đợi: PASS.
- [ ] 4.5 Commit: `feat(app): process service with failure handling and re-processing`

## 5. Đổi config thì xoá kết quả

**Files:**
- Modify: hàm cập nhật dùng chung trong service cấu hình session của F04 (tên thật lấy từ task 1)
- Test: `TEST/application/importsession/ConfigChangeInvalidatesResultTest.java`

- [ ] 5.1 Viết test, bắt đầu với session PROCESSED, `FakeResultStore` đang có kết quả, và readiness đạt:
  | Case | Mong đợi |
  |---|---|
  | `updateTransformations` với config khác | `resultStore.delete(id)` được gọi; status thành READY |
  | `updateValidations` với đúng config đang lưu (hash không đổi) | không gọi `delete`; status vẫn PROCESSED |
  | `updateMapping` làm field required mất mapping | `delete` được gọi; status thành CONFIGURING |
  | Session FAILED gọi `updateTransformations` | `DomainException(SESSION_STATE_INVALID)`; không lưu gì |
- [ ] 5.2 Chạy `./mvnw -q test -Dtest=ConfigChangeInvalidatesResultTest`. Mong đợi: FAIL.
- [ ] 5.3 Viết: so `configHash` trước và sau khi thay đổi; nếu khác và session đang PROCESSED thì `resultStore.delete(id)` rồi chuyển status theo D2.
- [ ] 5.4 Chạy lại lệnh ở 5.2, và toàn bộ test PUT của F04–F07. Mong đợi: PASS.
- [ ] 5.5 Commit: `feat(app): invalidate stored results when configuration changes`

## 6. API: POST /process

**Files:**
- Create: `MAIN/api/importsession/ProcessController.java`, `MAIN/api/importsession/PipelineSummaryDto.java`
- Test: `TEST/api/importsession/ProcessControllerTest.java`

**Interfaces:**
- Produces: `record PipelineSummaryDto(UUID sessionId, SessionStatus status, long total, long valid, long invalid, Map<String, Long> errorCountsByCode, Map<String, Long> errorCountsByField, Instant processedAt)`.

- [ ] 6.1 Viết `ProcessControllerTest`, dùng `@WebMvcTest(ProcessController.class)` và `@MockitoBean ProcessService`:
  | Stub | Mong đợi |
  |---|---|
  | trả summary của bộ mẫu | 200; `$.sessionId`, `$.status`=`PROCESSED`, `$.total`=6, `$.valid`=3, `$.invalid`=3, `$.errorCountsByCode.VALIDATION_TYPE`=2, `$.errorCountsByField.email`=2, `$.processedAt`=`2026-09-25T10:00:00Z` |
  | ném `SESSION_NOT_READY` với 1 item `TARGET_FIELD_REQUIRED(email)` | 409; `$.code`=`SESSION_NOT_READY`; `$.errors[0].code`=`TARGET_FIELD_REQUIRED`; `$.errors[0].field`=`email` |
  | ném `SESSION_STATE_INVALID` | 409; `$.code`=`SESSION_STATE_INVALID` |
  | ném `FILE_PARSE_ERROR` | 422; `$.code`=`FILE_PARSE_ERROR` |
  | ném `INTERNAL_ERROR` | 500; `$.code`=`INTERNAL_ERROR` |
  | ném `SESSION_NOT_FOUND` | 404; `$.code`=`SESSION_NOT_FOUND` |
- [ ] 6.2 Chạy `./mvnw -q test -Dtest=ProcessControllerTest`. Mong đợi: FAIL, vì lỗi compile.
- [ ] 6.3 Viết controller và DTO.
- [ ] 6.4 Chạy lại lệnh ở 6.2. Mong đợi: PASS.
- [ ] 6.5 Commit: `feat(api): POST process endpoint`

## 7. Integration test qua HTTP thật

**Files:**
- Create: `apps/api/src/test/resources/fixtures/pipeline/customers-sample.csv` (UTF-8, không BOM):
  ```
  Họ tên,Email,Tuổi,Ngày sinh
  "  An ",AN@X.COM,30,25/12/1990
  Bình,binh@x,abc,31/02/1990
  ,an@x.com,,
  Cường,cuong@x.com,,
  Dũng,dung@x.com,abc,
  Dũng 2,dung@x.com,40,
  ```
- Test: `TEST/api/importsession/ProcessApiIntegrationTest.java`, dùng `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Import(TestcontainersConfiguration.class)`, `RestClient`, và `static @TempDir storageDir` đăng ký qua `@DynamicPropertySource`.
- Helper `configureSample(id)`: gọi PUT `/schema`, `/mapping`, `/transformations`, `/validations` theo bộ mẫu. Payload giống hệt FE gửi: `trim`/`lowercase` và các rule không có `params`.

- [ ] 7.1 Viết test:
  | Case | Mong đợi |
  |---|---|
  | Upload fixture → `configureSample` → `POST /process` | 200; `total`=6, `valid`=3, `invalid`=3; `storageDir/{id}/result/valid.ndjson` có 3 dòng, `invalid.ndjson` có 3 dòng; `GET /{id}` trả `status`=`PROCESSED` |
  | Process 2 lần | `valid.ndjson` và `invalid.ndjson` của 2 lần giống hệt từng byte; `summary.json` giống nhau ở mọi field trừ `processedAt` |
  | Sau khi process, PUT `/transformations` với config khác | `result/` biến mất; `GET` trả `status`=`READY` |
  | Sau khi process, PUT lại `/validations` y hệt | `result/` còn nguyên; `status`=`PROCESSED` |
  | Upload rồi chỉ PUT `/schema`; `email` required nhưng chưa map; `POST /process` | 409; `code`=`SESSION_NOT_READY`; `errors` chứa `{field:"email", code:"TARGET_FIELD_REQUIRED"}` |
  | Upload, `configureSample`, ghi đè `storageDir/{id}/source.bin` bằng `name\n"unterminated`, rồi `POST /process` | 422; `code`=`FILE_PARSE_ERROR`; `GET` trả `status`=`FAILED`; không còn `result/` hay `result.tmp-*` |
  | Tiếp case trên: PUT `/transformations` hợp lệ, rồi `POST /process` | cả hai trả 409 với `code`=`SESSION_STATE_INVALID`; `status` vẫn `FAILED` |
  | Upload, `configureSample`, xoá `storageDir/{id}/source.bin`, rồi `POST /process` | 500; `code`=`INTERNAL_ERROR`; `status`=`FAILED` |
  | `POST /api/import-sessions/{uuid-chưa-tạo}/process` | 404; `code`=`SESSION_NOT_FOUND` |
  | 2 luồng gọi `POST /process` cùng lúc trên một session đã cấu hình | cả hai trả 200; `result/` có đủ 3 file; không còn `result.tmp-*` hay `result.old-*` |
- [ ] 7.2 Chạy `./mvnw -q test -Dtest=ProcessApiIntegrationTest`. Mong đợi: PASS. Nếu FAIL thì sửa code chính, không nới lỏng test.
- [ ] 7.3 Commit: `test(api): process pipeline end-to-end`

## 8. Kiểm tra toàn bộ và hoàn tất

- [ ] 8.1 Chạy `./mvnw -q verify`. Mong đợi: mọi test xanh, kể cả ArchitectureTest.
- [ ] 8.2 Chạy app thật với fixture:
  - upload, cấu hình bằng `curl`, rồi `POST /process`;
  - mở thư mục `result/` kiểm nội dung;
  - log không chứa giá trị ô.
- [ ] 8.3 Tick đủ checkbox. Chỗ nào làm khác kế hoạch thì gạch ngang và ghi LÝ DO. Cập nhật bảng "API contract V0.1", dòng #8 (thêm 422/500 theo OQ1), khi archive. Commit: `docs(openspec): complete be-f08 tasks`
- [ ] 8.4 Hỏi người dùng trước khi merge. Sau khi merge: `openspec archive be-f08-pipeline -y`.
