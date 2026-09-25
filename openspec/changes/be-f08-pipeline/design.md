## Context

- Quyết định nền: `openspec/changes/be-f01-import-session/design.md` (D1–D14 và API contract V0.1). File này chỉ ghi những gì riêng của F08. Các điểm được dẫn chiếu:
  - D2: vòng đời session (`READY → PROCESSED`, `FAILED`, PUT đổi config thì xoá kết quả).
  - D7: result store.
  - D10: ngữ nghĩa pipeline.
  - D11: khoá.
  - B1: pipeline ghi qua sink và chỉ trả summary.
- Pack System Design §5 phác `ImportPipeline.execute(request) → PipelineResult`, với thứ tự: Parse → Map row → Transform fields → Validate fields → Build RowResult → Aggregate. F08 giữ đúng thứ tự này. Riêng bước aggregate chỉ tổng hợp summary; row được ghi dần ra sink.

### Giả định về F02–F07 (chưa merge)

Task 1 của tasks.md đối chiếu các giả định dưới đây với code đã merge.

- **F02/F03** (nguồn):
  - `SourceParser` có `boolean supports(SourceFileType)` và `Stream<ImportRow> read(InputStream)`. Stream đã bỏ dòng trống (D9).
  - `ImportRow` có `rowNumber` và giá trị theo `index` cột.
  - Lỗi cấu trúc file: `DomainException(FILE_PARSE_ERROR)`. Lỗi IO: `UncheckedIOException`.
  - Session có `SourceSchema`, gồm danh sách cột `{index, name}`.
- **F04** (config):
  - `TargetSchema`/`TargetField`/`FieldType`; aggregate config (`SessionConfiguration`).
  - Readiness (`ReadinessEvaluator` hoặc tương đương) trả danh sách `ProblemItem` với code `SCHEMA_EMPTY` hoặc `TARGET_FIELD_REQUIRED`.
  - Khoá theo session (`SessionLocks`); hàm `configHash(SessionConfiguration)` (SHA-256 của JSON chuẩn hoá).
  - Service cấu hình session với hàm cập nhật dùng chung.
- **F05**: registry `MappingStrategy` với `Object map(ImportRow row, FieldMapping mapping)`; trả `null` khi field chưa map. Aggregate `MappingConfig`.
- **F06**: `TransformationEngine.apply(...) → FieldTransformResult`; `RowErrorCode`.
- **F07**: `FieldValidator.validate(...) → FieldValidation`; `UniqueTracker` (`commitRow`/`discardRow`); `ValidationConfig`.

## Goals / Non-Goals

**Goals:**
- Pipeline deterministic, chạy dạng stream, không gom row vào RAM (chỉ có tập giá trị `unique` phình theo dữ liệu).
- Kết quả lưu an toàn: người đọc không bao giờ thấy kết quả ghi dở hoặc kết quả của config cũ.
- `/process` gọi được nhiều lần; sửa config xong chạy lại được.

**Non-Goals:**
- Xử lý nền hoặc bất đồng bộ; huỷ process đang chạy (non-goal của pack).
- Đọc kết quả qua API (F09), export (F10).
- Chia batch nhiều luồng.

## Decisions

### P1. Contract pipeline
```java
public interface ImportPipeline {
    PipelineSummary execute(Stream<ImportRow> rows, PipelineConfig config, RowResultSink sink);
}
public record PipelineConfig(SourceSchema source, TargetSchema schema, MappingConfig mapping,
                             TransformationConfig transformations, ValidationConfig validations) {}
public interface RowResultSink { void accept(RowResult row); }
public record PipelineSummary(long total, long valid, long invalid,
                              Map<String, Long> errorCountsByCode, Map<String, Long> errorCountsByField) {}
```
- `DefaultImportPipeline` là Java thuần. Constructor nhận registry `MappingStrategy`, `TransformationEngine` và `FieldValidator`.
- Mỗi lần gọi `execute` tạo một `UniqueTracker` mới.

### P2. Xử lý một row
Với mỗi `ImportRow`, đi qua từng field theo thứ tự trong schema:
1. `raw = mapping.map(row, field)` → `sourceValue`. Field chưa map thì `raw = null`. CONSTANT thì `raw = constantValue`.
2. `t = engine.apply(field.name, field.type, raw, transformations.stepsFor(field))`.
   - Nếu `t.failed()`: ghi lỗi `(stage=TRANSFORMATION, rule, step, code=TRANSFORMATION_FAILED, sourceValue=raw)`. Giá trị của field trong row lỗi là `null`. Bỏ qua validation của field này.
3. Nếu không lỗi: `v = validator.validate(field, validations.rulesFor(field), t.value, rowNumber, tracker)`.
   - Nếu `v.failed()`: ghi lỗi `(stage=VALIDATION, rule, step=null, code, message, sourceValue=raw)`.

Cuối row:
- Không có lỗi → `tracker.commitRow(rowNumber)`, `values` là giá trị đã ép kiểu.
- Có lỗi → `tracker.discardRow()`, `values` là chuỗi sau transformation (`null` ở field có transformation lỗi).
- `sink.accept(row)`, cập nhật các biến đếm.

`MappingStrategy` ném `RuntimeException` (bug) → chuyển thành lỗi `TRANSFORMATION_FAILED` với `rule="mapping"`, `step=null`, `message="Unexpected error while mapping the value."`, log WARN, và job tiếp tục. Chỗ này bổ sung cho F06/F07, vì hai change đó đã tự bắt lỗi bất ngờ của transformation và rule.

### P3. RowResult và ImportError
```java
public enum ErrorStage { TRANSFORMATION, VALIDATION }
public record ImportError(int rowNumber, String fieldName, ErrorStage stage, String rule, Integer step,
                          RowErrorCode code, String message, String sourceValue) {}
public record RowResult(int rowNumber, boolean valid, Map<String, Object> values, List<ImportError> errors) {}
```
- `values` là `LinkedHashMap`, key theo thứ tự schema, đủ mọi field. Field optional rỗng hoặc chưa map có giá trị `null`.
- `errors` theo thứ tự field trong schema, mỗi field tối đa một lỗi (D10).

### P4. Summary
- `total = valid + invalid`, bằng số row nhận được từ stream.
- `errorCountsByCode` sắp theo tên code (`TreeMap`), chỉ gồm code có ít nhất một lỗi.
- `errorCountsByField` sắp theo thứ tự field trong schema (`LinkedHashMap`), chỉ gồm field có ít nhất một lỗi.
- Đếm theo **lỗi**, không theo row: một row có 3 lỗi đóng góp 3.

### P5. Result store (cụ thể hoá D7)
```java
public interface ResultStore {
    ResultWriter begin(UUID sessionId);                    // tạo {root}/{id}/result.tmp-{uuid}/
    Optional<ResultSummary> findSummary(UUID sessionId);   // đọc {root}/{id}/result/summary.json
    void delete(UUID sessionId);                           // xoá result/; không có thì bỏ qua
}
public interface ResultWriter extends RowResultSink, AutoCloseable {
    void commit(ResultSummary summary);   // ghi summary.json, rồi thay result/ bằng thư mục tạm
    @Override void close();               // chưa commit thì xoá thư mục tạm
}
public record ResultSummary(long total, long valid, long invalid, Map<String, Long> errorCountsByCode,
                            Map<String, Long> errorCountsByField, Instant processedAt, String configHash) {}
```
- **Định dạng file**: UTF-8 không BOM. Xuống dòng bằng `\n`, vì kết quả phải deterministic trên mọi hệ điều hành. Mỗi dòng là một object JSON; ký tự tiếng Việt ghi thẳng, không escape `\u`.
  - `valid.ndjson`: `{"rowNumber":2,"values":{"name":"An","age":30,"dob":"1990-12-25"}}`
  - `invalid.ndjson`: `{"rowNumber":3,"values":{…},"errors":[{"rowNumber":3,"fieldName":"email","stage":"VALIDATION","rule":"email","step":null,"code":"VALIDATION_EMAIL","message":"…","sourceValue":"binh@x"}]}`
  - `summary.json`: một object gồm các field của `ResultSummary`. `processedAt` theo ISO-8601 UTC.
- **Kiểu giá trị**: `BigDecimal` ghi dạng số plain (không ký hiệu `E`, giữ scale: `-3.50`). `LocalDate` ghi dạng chuỗi `yyyy-MM-dd`. `Boolean` ghi `true`/`false`. `null` ghi `null`.
- **Thay kết quả cũ**: Windows không cho `ATOMIC_MOVE` một thư mục đè lên thư mục khác, nên làm theo 3 bước:
  1. Nếu có `result/` thì đổi tên thành `result.old-{uuid}/`.
  2. Đổi tên `result.tmp-{uuid}/` thành `result/`.
  3. Xoá `result.old-*`.

  Giữa bước 1 và bước 2, người đọc có thể thấy thiếu `result/` và nhận 409 `RESULT_NOT_AVAILABLE`. Chấp nhận được: các lệnh ghi đã chạy lần lượt (D11), và người đọc không bao giờ thấy kết quả ghi dở.
- `FileResultStore` dùng chung thư mục gốc `StorageProperties.dir` với `LocalFileStorage` (F01).

### P6. ProcessService
```java
public PipelineSummaryView process(UUID sessionId);   // PipelineSummaryView = ResultSummary + sessionId + status
```
Toàn bộ chạy trong khoá session (D11):
1. Nạp session. Không có → `SESSION_NOT_FOUND`. Đang `FAILED` → `SESSION_STATE_INVALID`.
2. Nạp config và tính readiness. Chưa sẵn sàng → `DomainException(SESSION_NOT_READY, "Session is not ready to process.", issues)`.
3. `hash = configHash(config)`; chọn parser theo `fileType`.
4. `try (InputStream in = storage.open(id); Stream<ImportRow> rows = parser.read(in); ResultWriter w = resultStore.begin(id))`:
   - `summary = pipeline.execute(rows, pipelineConfig, w)`
   - `w.commit(new ResultSummary(..., now, hash))`
5. `session.transitionTo(PROCESSED, now)` rồi lưu.

Khi bước 4 gặp `DomainException(FILE_PARSE_ERROR)` hoặc `UncheckedIOException`:
- `ResultWriter.close()` xoá thư mục tạm, và `resultStore.delete(id)` xoá kết quả cũ, vì `FAILED` là trạng thái cuối.
- `session.transitionTo(FAILED, now)` rồi lưu.
- Ném lại lỗi: `FILE_PARSE_ERROR` → 422. `UncheckedIOException` được bọc thành `DomainException(INTERNAL_ERROR, "Source file could not be read.")` → 500.

`now = Instant.now(clock).truncatedTo(MICROS)`.

Thiếu file (`storage.open` ném `UncheckedIOException`) và lỗi IO giữa chừng được xử lý như nhau: 500 `INTERNAL_ERROR`, session chuyển sang `FAILED`.

Sau khi session đã `FAILED`, mọi lệnh ghi đều trả 409 `SESSION_STATE_INVALID`. Việc chặn này nằm ở bước 1 của `process` và ở hàm cập nhật dùng chung của F04 cho các PUT. F08 thêm test để chứng minh điều đó.

Id không tồn tại trả 404 `SESSION_NOT_FOUND`. FE nhận biết session đã mất theo `code`, không theo status.

Không bọc cả hàm trong `@Transactional`, vì chạy lâu. Session chỉ được lưu một lần ở cuối.

### P7. PUT config xoá kết quả
- Hàm cập nhật dùng chung của F04 so `configHash` trước và sau khi áp thay đổi.
  - Hash khác và session đang `PROCESSED` → `resultStore.delete(id)`, rồi status chuyển về `READY` hoặc `CONFIGURING` theo readiness (D2).
  - Hash như cũ → giữ nguyên kết quả và giữ `PROCESSED`.
- Cài đặt: thêm một bước trong hàm dùng chung của F04. Tên thật được xác định ở task 1.

### P8. HTTP
- `POST /api/import-sessions/{id}/process` không có body, trả `200 PipelineSummaryDto { sessionId, status, total, valid, invalid, errorCountsByCode, errorCountsByField, processedAt }`.
- Lỗi:
  | Code | HTTP |
  |---|---|
  | `SESSION_NOT_FOUND` | 404 |
  | `SESSION_NOT_READY` | 409, kèm `errors[]` là các readiness issue |
  | `SESSION_STATE_INVALID` | 409 |
  | `FILE_PARSE_ERROR` | 422 |
  | `INTERNAL_ERROR` | 500 |

## Risks / Trade-offs

- [Process đồng bộ với file 20MB có thể mất vài giây] → FE hiển thị "Đang xử lý…". Xử lý nền là non-goal.
- [Tập giá trị `unique` trong RAM] → Được chặn trên bởi giới hạn upload (D5).
- [Kết quả không có trong khoảnh khắc thay thư mục (P5)] → Người đọc nhận 409 và thử lại. Không bao giờ đọc phải dữ liệu hỏng.
- [Kết quả đã commit nhưng lưu session thất bại] → Session còn ở `READY` trong khi kết quả khớp `configHash`. F09/F10 dựa vào `configHash` nên vẫn phục vụ đúng. Lần process sau ghi đè lên kết quả này.
- [Tên class F02–F07 khác giả định] → Task 1 đối chiếu trước khi code.

## Open Questions

- **OQ1**: Bảng contract của be-f01 (dòng #8) chưa liệt kê 422 `FILE_PARSE_ERROR` và 500 `INTERNAL_ERROR` cho `/process`, dù D2 đã nói lỗi đọc file thì chuyển session sang `FAILED`. Đề xuất bổ sung hai mã này vào bảng contract khi archive, và báo phiên FE.
- **OQ2**: Test tính xác định so **từng byte** của `valid.ndjson` và `invalid.ndjson`. Còn `summary.json` được so từng field, trừ `processedAt`, vì thời điểm chạy luôn khác nhau.
- **OQ3**: Lỗi bất ngờ trong `MappingStrategy` được ghi thành `TRANSFORMATION_FAILED` với `rule="mapping"` (P2). Chưa có row error code riêng cho mapping, và bảng mã lỗi ở D4 đã chốt 5 mã. Đề xuất giữ như vậy, vì mapping lỗi ở runtime chỉ có thể do bug.
