## Context

- Thiết kế nền: `openspec/changes/core-01-toolbox-restructure/design.md`, các mục TD3 (5 kiểu tương tác), TD4 (quy ước API), TD12 (dataset), TD13 (run), TD14 (guest), TD15 (guard), TD16 (mã lỗi), TD17 (dọn dẹp), TD18 (bảo mật), TD19 (quan sát), và mục **API contract Toolbox v1**. File này ghi chi tiết cài đặt. Quyết định mang mã `PL1`…`PL13`.
- Đi sau core-02. `TableReader`/`TableWriter` và `KeyIndex` đã có. core-03 không bắt buộc phải có trước.
- Hiện trạng của Importer:
  - `LocalFileStorage` lưu `{root}/{sessionId}/source.bin` và kết quả dưới `{root}/{sessionId}/result/`.
  - `SessionCleanupService` dọn session quá TTL và thư mục mồ côi. "Mồ côi" hiện có nghĩa là không có trong bảng `import_session`.
  - `CommittedErrorPageFilter` bảo đảm lỗi sau byte đầu tiên thì huỷ kết nối.
  - `ExportFileName` làm sạch tên file tải về.

## Goals / Non-Goals

**Goals:**
- Mọi thứ tool nào cũng cần thì nằm ở `platform`, có test riêng, để các change `tool-*` chỉ việc ghép lại.
- Site public chịu được lạm dụng cơ bản mà không cần hạ tầng ngoài (Redis, API gateway).
- Importer có cùng lớp bảo vệ. Ngoài hai mã `RATE_LIMITED` và `SERVER_BUSY` mới, hành vi không đổi.

**Non-Goals:**
- Chạy nhiều instance: rate limit, gate, khoá và cache nằm trong bộ nhớ (xem Risks).
- Job bất đồng bộ (Stage 5).
- Tài khoản người dùng. Guest token là bước đệm.
- Endpoint của từng tool: nằm ở các change `tool-*`.

## Decisions

### PL1. Mã lỗi: interface, kind, enum theo phạm vi

```java
package com.universaldatatools.core.common;
public enum ErrorKind { BAD_REQUEST, UNAUTHORIZED, NOT_FOUND, CONFLICT, TOO_LARGE, UNSUPPORTED, UNPROCESSABLE,
                        RATE_LIMITED, INTERNAL, UNAVAILABLE }
public interface ErrorCode { String name(); ErrorKind kind(); }
public enum CommonError implements ErrorCode {
    REQUEST_INVALID(BAD_REQUEST), FILE_TOO_LARGE(TOO_LARGE), FILE_UNSUPPORTED(UNSUPPORTED), FILE_EMPTY(UNPROCESSABLE),
    FILE_PARSE_ERROR(UNPROCESSABLE), JSON_NOT_FLAT(UNPROCESSABLE), LIMIT_EXCEEDED(UNPROCESSABLE),
    SCHEMA_INVALID(UNPROCESSABLE), CONFIG_INVALID(UNPROCESSABLE), DATASET_NOT_FOUND(NOT_FOUND),
    RUN_NOT_FOUND(NOT_FOUND), GUEST_TOKEN_INVALID(UNAUTHORIZED), RATE_LIMITED(RATE_LIMITED),
    SERVER_BUSY(UNAVAILABLE), EXPORT_FAILED(INTERNAL), INTERNAL_ERROR(INTERNAL);
}
```

- `tools.importer.domain.ImporterError implements ErrorCode`: `SESSION_NOT_FOUND`, `SESSION_NOT_READY`, `SESSION_STATE_INVALID`, `RESULT_NOT_AVAILABLE`, `MAPPING_INVALID`, `SOURCE_COLUMN_NOT_FOUND`.
- Mỗi tool về sau khai enum riêng: `SCHEMA_INCOMPATIBLE` (validator), `DIFF_KEY_INVALID` (diff), `SCHEMA_NOT_FOUND` và `SCHEMA_VERSION_CONFLICT` (schema).
- `platform.web.ErrorHttpStatus.of(ErrorKind)`:

| ErrorKind | HTTP |
|---|---|
| `BAD_REQUEST` | 400 |
| `UNAUTHORIZED` | 401 |
| `NOT_FOUND` | 404 |
| `CONFLICT` | 409 |
| `TOO_LARGE` | 413 |
| `UNSUPPORTED` | 415 |
| `UNPROCESSABLE` | 422 |
| `RATE_LIMITED` | 429 |
| `INTERNAL` | 500 |
| `UNAVAILABLE` | 503 |

- `DomainException` giữ `ErrorCode` (interface), có thể kèm `Map<String,Object> extensions`. Extension được ghi vào ProblemDetail, ví dụ `keyProblems` của diff, và `retryAfterSeconds`, được chuyển thành header `Retry-After` rồi **không** ghi vào body.
- **`ErrorCodeRegistryTest`**:
  - quét classpath `com.universaldatatools` để tìm mọi enum cài `ErrorCode`;
  - kiểm không có tên trùng;
  - kiểm mọi tên đều xuất hiện trong bảng mã của spec `api-errors`. Test đọc `openspec/specs/api-errors/spec.md` qua đường dẫn tương đối `../../openspec/...`, và bỏ qua (assume) khi không tìm thấy file.
- Các mã tool về sau mới dùng (`SCHEMA_INCOMPATIBLE`, …) đã có mặt trong **bảng mã** ngay từ change này. Nhờ vậy các change tool chạy song song không phải cùng sửa một requirement.

### PL2. Client key và rate limit

- **`ClientKeyResolver`**:
  - Lấy `request.getRemoteAddr()`. IPv4 giữ nguyên; IPv6 lấy 64 bit đầu.
  - Chuyển thành `ClientKey` (một chuỗi opaque).
  - Chạy sau proxy thì đặt `server.forward-headers-strategy=native` (Tomcat `RemoteIpValve`), khi đó `getRemoteAddr()` là IP của khách. Mặc định không tin `X-Forwarded-For`.
- **`RateLimiter`**: token bucket theo `(bucket, clientKey)`.
  - Bucket có `capacity` và `refillPeriod`. Mặc định:
    - `upload`: 30 / 10 phút;
    - `compute`: 60 / 10 phút;
    - `read`: 600 / 10 phút.
  - Nạp lại liên tục: `capacity / refillPeriod` token mỗi giây.
  - Thiếu token thì trả `429 RATE_LIMITED`, với `Retry-After` = số giây tới khi có đủ 1 token, làm tròn lên, tối thiểu 1.
  - Map giữ `ConcurrentHashMap<Key, Bucket>`. Scheduler của platform, chạy mỗi 10 phút, xoá bucket đã đầy lại và nhàn rỗi quá 1 giờ.
- **Gắn vào endpoint** bằng annotation `@RateLimited(Bucket.UPLOAD|COMPUTE|READ)` trên method controller, kèm một `HandlerInterceptor`. Endpoint `/api/**` không có annotation mặc định dùng `READ`. Actuator và Swagger không bị giới hạn.
- **Importer**:
  - `POST /api/import-sessions` → `UPLOAD`;
  - `process`, `export`, `errors/export` → `COMPUTE`;
  - còn lại → `READ`.
- Cấu hình: `toolbox.rate-limit.{upload,compute,read}.capacity` / `.period`, và `toolbox.rate-limit.enabled` (mặc định `true`; test đặt `false`, trừ test của chính rate limit).

### PL3. `ProcessingGate`

```java
public interface ProcessingGate {
    Permit acquire(ClientKey client);          // ném DomainException(SERVER_BUSY | RATE_LIMITED) kèm retryAfterSeconds
    interface Permit extends AutoCloseable { void close(); }   // gọi nhiều lần không sao
}
```

- **Toàn server**: `Semaphore(max-concurrent)`, mặc định `max(2, availableProcessors)`, chờ tối đa `acquire-timeout` (mặc định `10s`). Hết thời gian chờ thì trả `SERVER_BUSY` với `retryAfter=5`.
- **Mỗi client**: đếm bằng `ConcurrentHashMap<ClientKey, AtomicInteger>`. Vượt `per-client` (mặc định 2) thì trả `RATE_LIMITED` với `retryAfter=5`. Luật này được kiểm **trước** khi chờ semaphore, nên client đã chạy đủ 2 thao tác không giữ chỗ trong hàng chờ.
- **Thao tác nặng**:
  - upload (vì có đọc cấu trúc), inspect/preview dataset, convert, `POST …/runs`, `POST …/export`, `POST /api/diff/columns`;
  - của Importer: upload, `process`, `export`, `errors/export`.
- **Thao tác stream** (download): permit được giữ trong `StreamingResponseBody` và chỉ `close()` ở `finally` sau khi ghi byte cuối, hoặc khi bị huỷ.
- **Thứ tự kiểm**: rate limit ở interceptor → DiskSpaceGuard (upload, run) → gate. Nhờ vậy 429/503 luôn xảy ra **trước** mọi tác dụng phụ.

### PL4. `DiskSpaceGuard`

- Trước upload và trước khi bắt đầu ghi run, đọc `Files.getFileStore(storageRoot).getUsableSpace()`. Nhỏ hơn `toolbox.storage.min-free-space` (mặc định `2GB`) thì trả `SERVER_BUSY` với `retryAfter=60`, và ghi log WARN, tối đa một lần mỗi phút.

### PL5. Dataset

**Bảng** (`V12__create_dataset.sql`):

```sql
CREATE TABLE dataset (
  id uuid PRIMARY KEY, original_file_name varchar(255) NOT NULL, format varchar(8) NOT NULL,
  size_bytes bigint NOT NULL, sheets jsonb NULL, created_at timestamptz NOT NULL,
  last_used_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0);
CREATE INDEX dataset_last_used_at_idx ON dataset (last_used_at);
```

**Upload** `POST /api/datasets`, part `file`:
1. Rate limit `UPLOAD`, `DiskSpaceGuard`, gate.
2. `FileTypeDetector.detect(name, head8k, allowed={CSV,XLSX,JSON})` theo TD6. Upload file 0 byte trả `FILE_EMPTY`.
3. `id = UUID.randomUUID()`, lưu vào `{root}/{id}/source.bin` bằng `FileStorage.save`.
4. XLSX: zip guard, rồi `sheets()`. Lỗi thì xoá file, trả `FILE_PARSE_ERROR`.
5. JSON: kiểm ký tự khác khoảng trắng đầu tiên là `[`. Không phải thì xoá file, trả `FILE_PARSE_ERROR`.
6. Insert row. Trả `201` + `Location: /api/datasets/{id}` + `DatasetDto`.

Bước nào lỗi sau khi đã lưu file thì xoá file, không để lại gì (như D2 của Importer).

**Preview** `GET /api/datasets/{id}/preview`:
1. Tìm dataset còn hạn, rồi touch.
2. Lấy **read lock**.
3. Lấy `TableInfo` từ cache (khoá `(id, ReadOptions đã chuẩn hoá)`), không có thì `inspect`. `ReadLimits` lấy từ `toolbox.limits.*`.
4. `read(...).limit(limit)`.
5. Trả `DatasetPreviewDto`.

`limit` ngoài khoảng 1–200 thì trả `400 REQUEST_INVALID`. Query `delimiter`/`encoding` sai giá trị thì trả `400 REQUEST_INVALID`.

**`DatasetSources`** là API cho tool:

```java
public interface DatasetSources {
    OpenedSource open(SourceRef ref);             // ref = (datasetId, ReadOptions); ném DATASET_NOT_FOUND / lỗi parse
}
public interface OpenedSource extends AutoCloseable {
    DatasetMeta dataset();                        // id, originalFileName, format, sizeBytes
    TableInfo info();                             // đã inspect (hoặc cache)
    Stream<Row> rows();                           // mỗi lần gọi mở file mới; người gọi đóng stream
    @Override void close();                       // nhả read lock
}
```

**Cache inspect** (`DatasetInspections`):
- LRU `LinkedHashMap` có đồng bộ, tối đa `toolbox.dataset.inspect-cache-size` (mặc định 256).
- Lỗi parse **cũng** được cache, dưới dạng `DomainException` đã chụp lại, để preview sai options nhiều lần không đọc lại file.
- `DELETE` và cleanup xoá mọi mục của id đó.

**Khoá** (`DatasetLocks`):
- Dùng `ReentrantReadWriteLock` theo id. Lock bị bỏ khỏi map khi không còn ai giữ, nhờ đếm tham chiếu.
- `DELETE` lấy write lock với `tryLock(5s)`. Không lấy được thì trả `503 SERVER_BUSY` với `retryAfter=5`. Lý do: `DELETE` chỉ bận khi đang có thao tác đọc, và việc này qua đi sau vài giây. `409` được dành cho xung đột trạng thái, nên không dùng ở đây.
- Cleanup dùng `tryLock()` không chờ; bận thì bỏ qua lần này.

**TTL**:
- `expiresAt = lastUsedAt + dataset-ttl` (mặc định `24h`).
- Touch chạy `UPDATE dataset SET last_used_at=:now WHERE id=:id AND last_used_at < :now - interval '10 minutes'`.
- Mọi truy vấn coi dataset hết hạn là không tồn tại (`DATASET_NOT_FOUND`).

### PL6. Run store

**Bảng** (`V13__create_tool_run.sql`):

```sql
CREATE TABLE tool_run (
  id uuid PRIMARY KEY, tool varchar(32) NOT NULL, created_at timestamptz NOT NULL,
  last_used_at timestamptz NOT NULL, sources jsonb NOT NULL, config jsonb NOT NULL, summary jsonb NOT NULL,
  version bigint NOT NULL DEFAULT 0);
CREATE INDEX tool_run_last_used_at_idx ON tool_run (last_used_at);
```

**API cho tool:**

```java
public interface RunStore {
    RunWriter begin(String tool);                                  // tạo {root}/{id}.staging-{nonce}/
    Optional<RunRecord> find(String tool, UUID id);                // khác tool hoặc hết hạn → empty; có touch
    <T> Stream<T> read(UUID id, String section, Class<T> type, long skip);   // NDJSON → record; người gọi đóng stream
    void delete(String tool, UUID id);                             // RUN_NOT_FOUND nếu không có
}
public interface RunWriter extends AutoCloseable {
    SectionWriter section(String name);                            // name khớp [a-z][a-z0-9-]{0,31}
    RunRecord commit(List<RunSource> sources, Object config, Object summary);
    @Override void close();                                        // chưa commit → xoá staging
}
public record RunRecord(UUID id, String tool, Instant createdAt, Instant expiresAt,
                        List<RunSource> sources, JsonNode config, JsonNode summary) {}
public record RunSource(String role, UUID datasetId, String fileName, DataFormat format, ResolvedReadOptions options) {}
```

- **Ghi nguyên khối**:
  1. Ghi mọi section vào `{root}/{id}.staging-{nonce}/`.
  2. `fsync` và đóng file.
  3. `Files.move(staging, {root}/{id}, ATOMIC_MOVE)`.
  4. Insert row DB.
  5. Insert lỗi thì xoá thư mục vừa rename rồi ném tiếp. Nếu xoá cũng lỗi thì thư mục thành mồ côi và bị cleanup dọn.
- **Section**: file `{name}.ndjson`, UTF-8, mỗi dòng một JSON (Jackson). `read` bỏ qua `skip` dòng đầu mà không parse chúng.
- **Phân trang có lọc** là việc của tool: đếm tổng khi quét, hoặc lấy số đếm có sẵn trong summary khi không lọc. Như `ResultQueryService` của Importer.
- **Run không phụ thuộc dataset**: mọi thứ cần để xem và export đều nằm trong section và summary.
- `find` có touch như dataset. TTL `run-ttl` mặc định `24h`.

### PL7. Output và download

```java
public record OutputSpec(DataFormat format, WriteOptions options) {
    public static OutputSpec from(OutputDto dto);      // CONFIG_INVALID nếu tổ hợp sai (ví dụ csv options với format JSON)
}
public final class Downloads {
    public ResponseEntity<StreamingResponseBody> stream(String baseName, String suffix, OutputSpec spec,
            ProcessingGate.Permit permit, BodyWriter body);    // body ghi qua TableWriter; permit đóng ở finally
}
```

- `OutputDto` theo contract Toolbox v1. Option của định dạng khác bị **từ chối** (`CONFIG_INVALID`, `pointer` là `/output/csv`) thay vì bị bỏ qua lặng lẽ, để FE sớm thấy lỗi của chính mình.
- **Tên file**: `ExportFileName` của Importer chuyển thành `platform.output.DownloadNames`, giữ luật V0.1:
  - bỏ đuôi cuối;
  - thay `"`, `\`, `/` và ký tự điều khiển bằng `_`;
  - rỗng thì dùng `export`.

  Sau đó nối `suffix` và `.{extension}`. Importer tiếp tục dùng đúng lớp này, nên tên file của nó không đổi.
- **Header**:
  - `Content-Type` theo writer;
  - `Content-Disposition: attachment; filename="<ASCII thay thế>"; filename*=UTF-8''<percent-encoded>`, như V0.1;
  - `Cache-Control: no-store`;
  - `X-Content-Type-Options: nosniff`.
- Lỗi trước byte đầu tiên đi qua ProblemDetail như thường. Lỗi sau byte đầu: `RowSink.abort()` rồi ném tiếp, và `CommittedErrorPageFilter` (đã có) huỷ kết nối.

### PL8. Guest identity

- `@GuestKey GuestKey key` là tham số controller, được resolve từ header `X-Guest-Token`:
  - thiếu, hoặc không khớp `^[A-Za-z0-9_-]{32,128}$` → `401 GUEST_TOKEN_INVALID`;
  - hợp lệ → `GuestKey(sha256(token))` (32 byte).
- Token không bao giờ bị log. Token nằm trong header, không phải cookie, nên không có rủi ro CSRF.

### PL9. Dọn dẹp chung

```java
public interface ExpiringCatalog {                 // mỗi loại tài nguyên một bean
    String name();                                 // "import-session", "dataset", "run", …
    List<UUID> listExpired(Instant now, int limit);
    DeleteOutcome tryDelete(UUID id, Instant now); // DELETED | SKIPPED_BUSY | GONE; ném khi lỗi
    boolean owns(UUID id);                         // cho việc dọn mồ côi
    long countOwned();                             // mẫu số của van an toàn
}
```

- **`platform.cleanup.CleanupService`** chạy lúc khởi động, rồi mỗi giờ:
  1. Với mỗi catalog, lấy tối đa 1 000 id hết hạn một lượt. Với mỗi id: `tryDelete` (row trước, storage sau; luật V0.1). Lỗi ở một id không chặn id khác.
  2. **Mồ côi**: thư mục UUID dưới storage mà **không catalog nào `owns`**, cũ hơn `max(1h, TTL nhỏ nhất)`, và không phải link hay junction. Giữ đủ luật V0.1: claim `.owner`; van an toàn "hơn 10 mồ côi và hơn một nửa tổng số thư mục mà các catalog biết thì dừng".
  3. **Staging**: thư mục `*.staging-*` cũ hơn 1 giờ thì xoá.
- **Importer**:
  - `SessionCleanupService` thành `ImportSessionCatalog implements ExpiringCatalog`. Luật V0.1 giữ nguyên: TTL theo `updatedAt`, xoá có điều kiện trong một lệnh DB, bỏ qua session đang giữ khoá.
  - Report cũ (`deletedSessions`, `failures`, `skipped`, `deletedOrphans`) vẫn có, dựng từ report theo catalog.
  - Scheduler của Importer bị bỏ; scheduler duy nhất nằm ở `platform.cleanup`.

### PL10. Header chung

- Filter thêm `X-Content-Type-Options: nosniff` cho mọi response.
- Response JSON của `/api/datasets/**` và `/api/*/runs/**` kèm `Cache-Control: no-store`.
- **Giới hạn body**: filter từ chối request `application/json` có `Content-Length` > 1 MB, hoặc đếm byte vượt 1 MB khi không có `Content-Length`. Kết quả là `413` với `code` `REQUEST_INVALID`, theo luật "lỗi 4xx của framework giữ status gốc".

### PL11. Metrics

- `udt.uploads` (tag `format`, `outcome`);
- `udt.runs` (tag `tool`, `outcome`), timer `udt.run.duration` (tag `tool`);
- `udt.rows.processed` (tag `tool`);
- `udt.guard.rejected` (tag `reason` = `rate` | `busy` | `disk`).

Actuator chỉ mở `health`. Muốn mở metrics thì bật riêng khi deploy.

### PL12. OpenAPI

- Thêm `GroupedOpenApi` tên `datasets` cho `/api/datasets/**`.
- Mỗi tool thêm nhóm của nó trong change của tool.
- `POST /api/datasets` mô tả multipart `file` dạng `binary`, như Importer.

### PL13. Cấu hình

```yaml
toolbox:
  limits: { max-rows: 500000, max-columns: 1000, max-cell-length: 32767 }
  retention: { dataset-ttl: 24h, run-ttl: 24h, touch-interval: 10m }
  dataset: { inspect-cache-size: 256 }
  processing: { max-concurrent: 0, per-client: 2, acquire-timeout: 10s }   # 0 = max(2, số CPU)
  rate-limit:
    enabled: true
    upload:  { capacity: 30,  period: 10m }
    compute: { capacity: 60,  period: 10m }
    read:    { capacity: 600, period: 10m }
  storage: { min-free-space: 2GB }
```

- Mỗi key có biến môi trường `TOOLBOX_…` tương ứng theo luật relaxed binding của Spring.
- `src/test/resources/config/application.yaml` đặt `rate-limit.enabled=false` và `min-free-space=0`, để test khác không bị ảnh hưởng.

## Risks / Trade-offs

- **Một instance**: rate limit, gate, khoá dataset và cache inspect nằm trong bộ nhớ. Chạy hai instance thì mỗi instance đếm riêng, và `DELETE` có thể chạy trong lúc instance kia đang đọc. Ghi rõ trong README. Khi cần scale thì chuyển sang Redis và object storage.
- **Rate limit theo IP** có thể chặn nhầm nhiều người dùng chung một NAT (văn phòng). Số mặc định khá rộng, và cấu hình được.
- **Cache lỗi parse**: nếu lỗi là I/O tạm thời thì cache sẽ giữ lỗi sai. Vì vậy chỉ cache `DomainException` của parser (`FILE_PARSE_ERROR`, `FILE_EMPTY`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `CONFIG_INVALID`), không cache lỗi I/O.
- **Đổi `ErrorCode` từ enum sang interface** đụng mọi chỗ `switch` trên enum. Compiler chỉ ra hết, và test hợp đồng canh output.
- **Van an toàn của việc dọn mồ côi** giờ đếm trên mọi catalog. Nếu một catalog bị cấu hình sai (ví dụ `owns` luôn trả `false`), van sẽ chặn lại mà không xoá hàng loạt.

## Migration Plan

- Flyway `V12` và `V13` chỉ thêm bảng. App cũ chạy trên DB đã có `V12`/`V13` vẫn được, nhờ `ignoreMigrationPatterns *:future` (xem ghi chú vận hành của BE-F11).
- Không có dữ liệu cũ nào phải chuyển.
- Rollback: revert nhánh. Hai bảng thừa không gây hại.
- **Cảnh báo cleanup** (luật BE-F11): chạy app nhánh này trên DB dùng chung với TTL ngắn sẽ xoá session thật. Test dùng DB và storage riêng.

## Open Questions

(không có)
