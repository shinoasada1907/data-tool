# CORE-04 Platform dùng chung: dataset, run, guard, lỗi, dọn dẹp — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** Có đủ `platform.{dataset, run, output, guard, identity, cleanup}`, có Dataset API theo contract Toolbox v1, có bảng mã lỗi mở rộng được, và Importer nằm dưới cùng lớp bảo vệ mà không đổi hành vi nào khác.

**Architecture:** design.md PL1–PL13. Nền là core-01 TD3–TD4, TD12–TD19, và mục API contract Toolbox v1.

**Tech Stack:** như hiện tại (Micrometer đã đi kèm Actuator). Không thêm dependency.

**Spec:** `specs/dataset-api`, `specs/tool-runs`, `specs/toolbox-platform` (ADDED), `specs/api-errors` (MODIFIED), `specs/import-session` (MODIFIED).

## Global Constraints

- core-02 đã merge. Nhánh `feature/core-04-platform-services`.
- Test dùng DB (Testcontainers) và storage riêng. `src/test/resources/config/application.yaml` thêm:
  - `toolbox.rate-limit.enabled: false`;
  - `toolbox.storage.min-free-space: 0`;
  - `toolbox.processing.acquire-timeout: 1s`.

  Test nào cần các cơ chế này thì tự bật lại bằng `@TestPropertySource`.
- **Không chạy app nhánh này trên DB dùng chung khi TTL ngắn** (luật BE-F11).
- `platform` không import `tools`. `ArchitectureTest` phải xanh.
- Test của run store dùng một "tool mẫu" chỉ có trong test: `TEST/platform/run/sample/SampleRunController` (path `/api/sample/runs`). Nhờ vậy test được mà không cần tool thật.

---

## 1. `ErrorCode` thành interface

**Files:**
- Modify: `MAIN/core/common/ErrorCode.java` (thành interface), `MAIN/core/common/DomainException.java`
- Create: `MAIN/core/common/{ErrorKind, CommonError}.java`, `MAIN/tools/importer/domain/ImporterError.java`
- Modify: `MAIN/platform/web/ErrorHttpStatus.java` (theo `ErrorKind`), cùng mọi chỗ dùng `ErrorCode.X` (compiler chỉ ra)
- Test: `TEST/platform/web/ErrorCodeRegistryTest.java`, sửa `ErrorHttpStatusTest`

- [ ] 1.1 Viết `ErrorCodeRegistryTest`:
  - quét package `com.universaldatatools` (dùng ArchUnit `ClassFileImporter`) để tìm mọi enum cài `ErrorCode`;
  - không có tên trùng;
  - mọi tên có trong `openspec/specs/api-errors/spec.md`. Đọc file bằng đường dẫn `Path.of("../../openspec/specs/api-errors/spec.md")` tính từ `apps/api`. Không thấy file thì `Assumptions.abort`.
  - Sửa `ErrorHttpStatusTest`: mỗi `ErrorKind` → đúng status theo PL1.
- [ ] 1.2 Chạy. Mong đợi: FAIL (compile).
- [ ] 1.3 Cài đặt theo PL1:
  - `CommonError` chứa các mã chung. Mã của Importer sang `ImporterError`.
  - `SCHEMA_INCOMPATIBLE`, `DIFF_KEY_INVALID`, `SCHEMA_NOT_FOUND`, `SCHEMA_VERSION_CONFLICT` **chưa** có enum ở change này; tool sẽ khai sau.
  - Bảng mã trong spec (sau archive) đã có sẵn các mã đó. Test chỉ kiểm chiều "code → có trong bảng".
- [ ] 1.4 Chạy `./mvnw -q test`. Mong đợi: PASS toàn bộ.
  - Luồng `ErrorCodeRegistryTest` đọc spec **đã archive**, nên trước khi archive change này nó còn đọc bảng cũ. Tạm cho test đọc thêm `openspec/changes/core-04-platform-services/specs/api-errors/spec.md` nếu file đó tồn tại. Sau archive thì bỏ nhánh này (task 18).
- [ ] 1.5 Commit: `refactor(errors): ErrorCode as interface with ErrorKind; importer codes in ImporterError`

## 2. Extension của ProblemDetail và `Retry-After`

**Files:**
- Modify: `MAIN/core/common/DomainException.java` (`Map<String,Object> extensions`, hằng `RETRY_AFTER`), `MAIN/platform/web/GlobalExceptionHandler.java`
- Test: `TEST/platform/web/ProblemExtensionsTest.java`

- [ ] 2.1 Viết test, dùng controller giả trong test:
  - ném `DomainException(SERVER_BUSY, "Busy", extensions {retryAfterSeconds: 5})` → status `503`, header `Retry-After: 5`, body không có `retryAfterSeconds`;
  - ném với extension `{keyProblems: [...]}` → body có `keyProblems` ở top-level;
  - `RATE_LIMITED` không có `retryAfterSeconds` → vẫn có `Retry-After: 1` (mặc định tối thiểu).
- [ ] 2.2 Chạy. Mong đợi: FAIL.
- [ ] 2.3 Cài đặt.
- [ ] 2.4 Chạy lại. Mong đợi: PASS.
- [ ] 2.5 Commit: `feat(errors): problem extensions and Retry-After`

## 3. `ClientKeyResolver`

**Files:**
- Create: `MAIN/platform/guard/{ClientKey, ClientKeyResolver, ClientKeyArgumentResolver}.java`
- Test: `TEST/platform/guard/ClientKeyResolverTest.java`

- [ ] 3.1 Viết test với `MockHttpServletRequest.setRemoteAddr`:
  - `203.0.113.7` → key `203.0.113.7`;
  - `2001:db8:1:2:3:4:5:6` và `2001:db8:1:2:ffff::1` → **cùng** key;
  - `2001:db8:1:3::1` → key khác;
  - header `X-Forwarded-For: 1.2.3.4` với remoteAddr `10.0.0.1` → key `10.0.0.1` (không tin header khi `forward-headers-strategy` tắt).
- [ ] 3.2 Chạy. FAIL. Cài đặt. Chạy lại. PASS.
- [ ] 3.3 Commit: `feat(guard): client key from remote address, IPv6 by /64`

## 4. Rate limit

**Files:**
- Create: `MAIN/platform/guard/{RateLimited, Bucket, RateLimiter, TokenBucket, RateLimitInterceptor, RateLimitProperties}.java`, `MAIN/platform/config/WebGuardConfig.java`
- Modify: controller Importer (thêm `@RateLimited` theo PL2)
- Test: `TEST/platform/guard/TokenBucketTest.java`, `TEST/platform/guard/RateLimitIntegrationTest.java`

- [ ] 4.1 Viết `TokenBucketTest` với `Clock` giả:
  - capacity 2, period 10 phút: 2 lần `tryConsume` → true, lần 3 → false, `retryAfterSeconds` = 300;
  - sau 300 giây → true lần nữa;
  - sau 1 giờ nhàn rỗi → token đầy lại, còn đúng 2 (không vượt capacity).
- [ ] 4.2 Viết `RateLimitIntegrationTest` (`toolbox.rate-limit.enabled=true`, `upload.capacity=2`, `upload.period=10m`):
  - 3 lần `POST /api/import-sessions` từ cùng một IP → `201`, `201`, `429` với `code` `RATE_LIMITED` và `Retry-After: 300`. Lần thứ ba không tạo session: đếm row trước và sau;
  - IP khác → `201`;
  - `GET /actuator/health` 1 000 lần → luôn `200`.
- [ ] 4.3 Chạy. FAIL.
- [ ] 4.4 Cài đặt theo PL2. Dọn bucket nhàn rỗi bằng một `@Scheduled` trong `platform.config`.
- [ ] 4.5 Chạy lại, cùng toàn bộ test Importer (rate limit đang tắt trong test). PASS.
- [ ] 4.6 Commit: `feat(guard): token-bucket rate limits per client (upload/compute/read), importer included`

## 5. `ProcessingGate`

**Files:**
- Create: `MAIN/platform/guard/{ProcessingGate, SemaphoreProcessingGate, ProcessingProperties}.java`
- Modify: `ImportSessionService.upload`, `ProcessService.process`, `ExportController`/`ExportService` của Importer (permit giữ tới khi stream xong)
- Test: `TEST/platform/guard/SemaphoreProcessingGateTest.java`, `TEST/tools/importer/api/export/ExportGateIntegrationTest.java`

- [ ] 5.1 Viết `SemaphoreProcessingGateTest`:
  - `max=1, perClient=2, timeout=200ms`: A lấy permit; B chờ rồi nhận `DomainException(SERVER_BUSY)` với `retryAfterSeconds=5`, sau ≥ 200ms;
  - A đóng permit → B lấy được ngay;
  - `perClient=1`: A lấy 1 permit, A lấy tiếp → `RATE_LIMITED` ngay, **không** chờ;
  - `close()` hai lần không làm hỏng bộ đếm;
  - 50 thread lấy rồi trả permit xen kẽ → cuối cùng số permit rảnh bằng `max`.
- [ ] 5.2 Viết `ExportGateIntegrationTest` (`max-concurrent=1`):
  - bắt đầu tải export CSV của một session lớn (5 000 row), đọc response chậm;
  - trong lúc đó gọi `process` của một session khác → `503 SERVER_BUSY`;
  - sau khi tải xong → `process` trả `200`.
- [ ] 5.3 Chạy. FAIL.
- [ ] 5.4 Cài đặt theo PL3.
- [ ] 5.5 Chạy lại, cùng toàn bộ test. PASS.
- [ ] 5.6 Commit: `feat(guard): processing gate for heavy operations, held until the last byte`

## 6. `DiskSpaceGuard`

**Files:**
- Create: `MAIN/platform/guard/DiskSpaceGuard.java` (cổng `UsableSpace` để test giả lập)
- Test: `TEST/platform/guard/DiskSpaceGuardTest.java`

- [ ] 6.1 Viết test:
  - usable 1GB, ngưỡng 2GB → `check()` ném `SERVER_BUSY`, `retryAfterSeconds=60`;
  - usable 3GB → không ném;
  - ngưỡng 0 → không bao giờ ném.
- [ ] 6.2 FAIL → cài đặt, gắn vào upload Importer ngay trước khi lưu file → PASS.
- [ ] 6.3 Commit: `feat(guard): refuse uploads and runs when storage is nearly full`

## 7. Giới hạn body JSON và header chung

**Files:**
- Create: `MAIN/platform/web/{JsonBodyLimitFilter, SecurityHeadersFilter}.java`
- Test: `TEST/platform/web/WebFiltersIntegrationTest.java`

- [ ] 7.1 Viết test:
  - `PUT /api/import-sessions/{id}/schema` với body 1.5 MB → `413`, `code` `REQUEST_INVALID`;
  - body 900 KB → không bị filter chặn (trả lỗi nghiệp vụ bình thường);
  - chunked (không có `Content-Length`), 1.5 MB → `413`;
  - mọi response có `X-Content-Type-Options: nosniff`;
  - `GET /api/datasets/{id}` (task 11) có `Cache-Control: no-store`. Case này để lại, thêm ở task 11.
- [ ] 7.2 FAIL → cài đặt → PASS.
- [ ] 7.3 Commit: `feat(web): 1 MB JSON body limit and nosniff header`

## 8. Dataset: bảng, repository, TTL

**Files:**
- Create: `src/main/resources/db/migration/V12__create_dataset.sql`
- Create: `MAIN/platform/dataset/{Dataset, DatasetRepository, JpaDatasetRepository, DatasetEntity, DatasetJpaRepository, RetentionProperties}.java`
- Test: `TEST/platform/dataset/JpaDatasetRepositoryTest.java`

- [ ] 8.1 Viết test (Testcontainers):
  - `save` rồi `findLive(id, now)` → có;
  - `lastUsedAt` cách đây 25 giờ, TTL 24 giờ → `findLive` rỗng;
  - `touch(id, now)` khi `lastUsedAt` cách đây 5 phút → không đổi; cách đây 11 phút → đổi thành `now`;
  - `listExpired(now, 10)` chỉ trả dataset hết hạn;
  - `deleteIfExpired(id, cutoff)` là một lệnh có điều kiện: dataset vừa được touch thì không bị xoá.
- [ ] 8.2 FAIL → migration và cài đặt → PASS.
- [ ] 8.3 Commit: `feat(dataset): dataset table (V12) with sliding 24h retention`

## 9. Upload dataset

**Files:**
- Modify: `MAIN/core/table/FileTypeDetector.java` (JSON; CSV có BOM UTF-16)
- Create: `MAIN/platform/dataset/{DatasetService, DatasetController, DatasetDto}.java`
- Test: `TEST/core/table/FileTypeDetectorTest.java` (thêm case), `TEST/platform/dataset/DatasetUploadIntegrationTest.java`

- [ ] 9.1 Thêm case cho `FileTypeDetectorTest` với `allowed = {CSV, XLSX, JSON}`:
  - `data.JSON` + `  [{"a":1}]` → JSON;
  - `data.json` + BOM + `[` → JSON;
  - `data.json` + `{"a":1}` → `FILE_PARSE_ERROR`, detail `JSON must be an array of objects.`;
  - `x.csv` bắt đầu `FF FE` và có `00` → CSV;
  - `x.csv` có `00` mà không có BOM → `FILE_UNSUPPORTED`;
  - `x.json` với `allowed={CSV,XLSX}` → `FILE_UNSUPPORTED`.
- [ ] 9.2 Viết `DatasetUploadIntegrationTest` theo spec `dataset-api` "Upload dataset":
  - CSV tên tiếng Việt → `201`, `Location`, `format=CSV`, `sheets=null`, `expiresAt - createdAt = 24h`;
  - XLSX có sheet ẩn → `sheets` đúng thứ tự và `visible`;
  - `{"a":1}` → `422 FILE_PARSE_ERROR`; không có row và không có thư mục storage mới;
  - `data.xls` → `415`;
  - file 0 byte → `422 FILE_EMPTY`;
  - XLSX bomb → `422 FILE_PARSE_ERROR`;
  - quá 20MB → `413 FILE_TOO_LARGE`.
- [ ] 9.3 FAIL → cài đặt theo PL5 (upload) → PASS.
- [ ] 9.4 Commit: `feat(dataset): POST /api/datasets for CSV, XLSX and flat JSON`

## 10. Preview, cache inspect, khoá, `DatasetSources`

**Files:**
- Create: `MAIN/platform/dataset/{DatasetInspections, DatasetLocks, DatasetSources, DefaultDatasetSources, OpenedSource, SourceRef, DatasetPreviewDto, ReadOptionsParams}.java`
- Test: `TEST/platform/dataset/DatasetPreviewIntegrationTest.java`, `TEST/platform/dataset/DatasetInspectionsTest.java`, `TEST/platform/dataset/DatasetLocksTest.java`

- [ ] 10.1 Viết `DatasetPreviewIntegrationTest`, theo mọi scenario của requirement preview trong spec:
  - `ma;ten\n1;An\n2;Bình\n` không tham số → `SEMICOLON`, `autoDetected=["delimiter","encoding"]`, `totalRows=2`, `rows[0]={rowNumber:2, values:["1","An"]}`, `columns[0].inferredType="number"`;
  - `delimiter=comma` (chữ thường) → một cột `ma;ten`;
  - `limit=500` → `400`; `delimiter=COLON` → `400`;
  - JSON lồng nhau → `422 JSON_NOT_FLAT`; XLSX `sheet=Khong co` → `422 CONFIG_INVALID`;
  - dataset hết hạn (chỉnh `last_used_at` trong DB) → `404 DATASET_NOT_FOUND`;
  - response có `Cache-Control: no-store`.
- [ ] 10.2 Viết `DatasetInspectionsTest`:
  - hai lần `get` cùng khoá → `inspect` chỉ chạy 1 lần (đếm bằng reader giả);
  - khác `delimiter` → chạy lại;
  - lỗi `FILE_PARSE_ERROR` được cache (lần 2 không đọc file); `UncheckedIOException` **không** được cache;
  - vượt 256 mục → mục cũ nhất bị bỏ;
  - `evict(id)` bỏ mọi mục của id.
- [ ] 10.3 Viết `DatasetLocksTest`:
  - đang có read lock thì `tryWrite(5s)` chờ, rồi được ngay khi read lock nhả;
  - hai read lock song song được;
  - sau khi nhả hết, map không còn mục của id đó.
- [ ] 10.4 FAIL → cài đặt theo PL5 → PASS.
- [ ] 10.5 Commit: `feat(dataset): preview with read options, inspection cache, read/write locks and DatasetSources`

## 11. GET và DELETE dataset

**Files:**
- Modify: `DatasetController`, `DatasetService`
- Test: `TEST/platform/dataset/DatasetLifecycleIntegrationTest.java`

- [ ] 11.1 Viết test:
  - `GET` → `DatasetDto` kèm `Cache-Control: no-store`;
  - `DELETE` → `204`; sau đó `GET` và preview → `404 DATASET_NOT_FOUND`; thư mục storage không còn; cache đã bị xoá;
  - `DELETE` trong lúc đang giữ read lock (giữ bằng `DatasetSources.open` trong test) → `503 SERVER_BUSY` sau khoảng 5 giây (test đặt timeout ngắn qua property);
  - `GET /api/datasets/not-a-uuid` → `400 REQUEST_INVALID`;
  - touch: gọi preview lúc T+12h thì `expiresAt` thành T+36h (dùng `Clock` giả).
- [ ] 11.2 FAIL → cài đặt → PASS.
- [ ] 11.3 Commit: `feat(dataset): read and delete datasets`

## 12. Run store

**Files:**
- Create: `src/main/resources/db/migration/V13__create_tool_run.sql`
- Create: `MAIN/platform/run/{RunStore, FileRunStore, RunWriter, SectionWriter, RunRecord, RunSource, ToolRunEntity, ToolRunJpaRepository, RunPages}.java`
- Test: `TEST/platform/run/FileRunStoreTest.java`, `TEST/platform/run/sample/SampleRunController.java`, `TEST/platform/run/RunLifecycleIntegrationTest.java`

- [ ] 12.1 Viết `FileRunStoreTest`:
  - `begin("sample")` → có thư mục `{id}.staging-*`; ghi section `rows` 3 dòng; `commit(...)` → thư mục `{id}` có `rows.ndjson`, có row DB, không còn staging;
  - `close()` khi chưa commit → staging bị xoá, không có row DB;
  - insert DB ném lỗi (repository giả) → thư mục `{id}` bị xoá, exception được ném tiếp;
  - `read(id, "rows", Row.class, 1)` → 2 record cuối;
  - tên section `Rows!` → `IllegalArgumentException`;
  - `find("other", id)` → rỗng; `find("sample", id)` sau 25 giờ → rỗng.
- [ ] 12.2 Viết `SampleRunController` (chỉ trong test). Nó có đủ `POST /runs`, `GET /runs/{id}`, `GET /runs/{id}/rows`, `DELETE /runs/{id}`, dùng `RunStore` và `RunPages` (phân trang chung).
- [ ] 12.3 Viết `RunLifecycleIntegrationTest` theo spec `tool-runs`:
  - `POST` → `201` + `Location`; `GET` → có `Cache-Control: no-store`;
  - `GET /api/sample/runs/{id của tool khác}` → `404 RUN_NOT_FOUND`;
  - `DELETE` → `204`, rồi `GET` → `404`;
  - `rows?page=5&size=10` với 30 row → `rows` rỗng, `totalElements=30`, `totalPages=3`;
  - `size=0` → `400`;
  - xoá dataset nguồn, rồi `rows` vẫn `200`.
- [ ] 12.4 FAIL → cài đặt theo PL6 → PASS.
- [ ] 12.5 Commit: `feat(run): atomic run store with NDJSON sections and sliding retention (V13)`

## 13. Output và download

**Files:**
- Create: `MAIN/platform/output/{OutputDto, OutputSpec, Downloads, DownloadNames, BodyWriter}.java`
- Modify: Importer dùng `DownloadNames` thay cho `ExportFileName` (xoá lớp cũ)
- Test: `TEST/platform/output/OutputSpecTest.java`, `DownloadNamesTest.java` (chuyển từ `ExportFileNameTest`), `DownloadsIntegrationTest.java` (qua `SampleRunController` thêm `POST /runs/{id}/export`)

- [ ] 13.1 Viết `OutputSpecTest`:
  - `{format:CSV}` → mặc định `COMMA`, header, BOM, guard;
  - `{format:JSON, json:{pretty:true, typing:STRING}}` → đúng;
  - `{format:JSON, csv:{…}}` → `CONFIG_INVALID` với `pointer` `/output/csv`;
  - `{format:XLSX, xlsx:{sheetName:"Q1"}}` → sheet `Q1`;
  - `format` thiếu → `CONFIG_INVALID` với `pointer` `/output/format`.
- [ ] 13.2 Viết `DownloadsIntegrationTest`:
  - export CSV của run mẫu có tên nguồn `báo cáo.xlsx` → `Content-Disposition` chứa `filename*=UTF-8''b%C3%A1o%20c%C3%A1o`, có `Cache-Control: no-store` và `nosniff`;
  - writer ném lỗi trước byte đầu → `500` problem+json, không có `Content-Disposition`;
  - lỗi sau khi đã flush hơn bộ đệm → kết nối bị huỷ (dùng lại cách kiểm của test export Importer);
  - permit của gate được trả sau khi stream xong (`gate.available()` về lại `max`).
- [ ] 13.3 FAIL → cài đặt theo PL7 → PASS. Test tên file của Importer (`data-export`) vẫn PASS.
- [ ] 13.4 Commit: `feat(output): OutputDto to TableWriter; safe streamed downloads shared by all tools`

## 14. Guest identity

**Files:**
- Create: `MAIN/platform/identity/{GuestKey, GuestKeyArgumentResolver}.java`
- Test: `TEST/platform/identity/GuestKeyIntegrationTest.java` (controller giả trong test)

- [ ] 14.1 Viết test:
  - không có header → `401 GUEST_TOKEN_INVALID`;
  - token 31 ký tự → `401`; token có `.` → `401`;
  - token 43 ký tự base64url → controller nhận `GuestKey` = SHA-256 của token;
  - log của request (bắt bằng `OutputCaptureExtension`) không chứa token.
- [ ] 14.2 FAIL → cài đặt theo PL8 → PASS.
- [ ] 14.3 Commit: `feat(identity): X-Guest-Token resolved to a hashed guest key`

## 15. Dọn dẹp chung

**Files:**
- Create: `MAIN/platform/cleanup/{ExpiringCatalog, DeleteOutcome, CleanupService, CleanupReport, OrphanSweeper, CleanupScheduler}.java`
- Create: `MAIN/platform/dataset/DatasetCatalog.java`, `MAIN/platform/run/RunCatalog.java`, `MAIN/tools/importer/application/importsession/ImportSessionCatalog.java`
- Delete/Modify: `SessionCleanupService`, `SessionCleanupScheduler` của Importer (logic chuyển vào catalog và platform)
- Test: chuyển `SessionCleanupServiceTest` và `SessionCleanupScheduler*Test` sang `TEST/platform/cleanup/…`. **Giữ mọi case V0.1.** Thêm `TEST/platform/cleanup/MultiCatalogCleanupTest.java`.

- [ ] 15.1 Chạy test cleanup V0.1 hiện có, ghi baseline. PASS.
- [ ] 15.2 Viết `MultiCatalogCleanupTest`:
  - thư mục `{D}` của dataset còn hạn (tạo 30 giờ trước, dùng 1 giờ trước) → **không** bị xoá;
  - thư mục `{R}` của run còn hạn → không bị xoá;
  - thư mục UUID không ai nhận, cũ 25 giờ → bị xoá;
  - `{R}.staging-a1b2` cũ 2 giờ → bị xoá; cũ 10 phút → giữ;
  - dataset hết hạn → row và thư mục bị xoá; dataset đang bị đọc (giữ read lock) → `skipped`;
  - van an toàn: 12 mồ côi và 10 thư mục đã biết → không xoá gì, có log ERROR;
  - lỗi xoá ở catalog dataset không chặn catalog run.
- [ ] 15.3 Chạy. FAIL.
- [ ] 15.4 Cài đặt theo PL9. Report của Importer (`deletedSessions`, `failures`, `skipped`, `deletedOrphans`) dựng từ report chung.
- [ ] 15.5 Chạy test cleanup V0.1 (đã chuyển chỗ) và `MultiCatalogCleanupTest`. PASS. Không case V0.1 nào đổi kỳ vọng.
- [ ] 15.6 Commit: `feat(cleanup): one cleanup over all catalogs; orphans only when nobody owns them`

## 16. Metrics

**Files:**
- Create: `MAIN/platform/config/ToolboxMetrics.java`
- Test: `TEST/platform/config/ToolboxMetricsTest.java`

- [ ] 16.1 Viết test với `SimpleMeterRegistry`:
  - upload CSV thành công → `udt.uploads{format=CSV,outcome=ok}` = 1;
  - bị rate limit → `udt.guard.rejected{reason=rate}` = 1;
  - server bận → `{reason=busy}`; đĩa đầy → `{reason=disk}`.
- [ ] 16.2 FAIL → cài đặt → PASS.
- [ ] 16.3 Commit: `feat(observability): toolbox metrics for uploads, runs and guard rejections`

## 17. OpenAPI và snapshot hợp đồng

**Files:**
- Modify: `MAIN/platform/web/OpenApiConfig.java` (nhóm `datasets`)
- Modify: `ApiContractSnapshotTest`: vẫn chỉ so `/api/import-sessions/**`; thêm kiểm nhóm `datasets` có đủ 4 endpoint
- Test: `TEST/platform/web/DatasetsApiDocsTest.java`

- [ ] 17.1 Viết test:
  - `/v3/api-docs/datasets` có `/api/datasets`, `/api/datasets/{id}`, `/api/datasets/{id}/preview`;
  - `POST /api/datasets` là `multipart/form-data` với `file` dạng `binary`.
- [ ] 17.2 FAIL → cài đặt → PASS. `ApiContractSnapshotTest` PASS: path của Importer không đổi. Mã lỗi mới chỉ nằm trong mô tả, không ở `paths`/`components`; nếu có đổi thì xem diff và ghi LÝ DO.
- [ ] 17.3 Commit: `feat(api-docs): datasets group`

## 18. Hoàn tất

- [ ] 18.1 `./mvnw -q verify`. PASS.
- [ ] 18.2 Chạy app thật ở cổng 8081, với DB **riêng** (container tạm) và storage riêng (luật BE-F11). Dùng `curl` và file JSON UTF-8 (`--data-binary @file`):
  1. Upload `ma;ten\n1;An\n` → `201`.
  2. Preview → `SEMICOLON`.
  3. `DELETE` → `204`.
  4. 31 lần upload → lần 31 trả `429` có `Retry-After`.

  Tắt app, rồi kiểm `netstat`.
- [ ] 18.3 `openspec validate core-04-platform-services --strict`, rồi `openspec archive core-04-platform-services -y`. Bỏ nhánh "đọc spec trong changes" của `ErrorCodeRegistryTest` (task 1.4). Commit: `docs(openspec): archive core-04-platform-services`.
- [ ] 18.4 Kiểm thư mục chính, rồi merge `--no-ff` vào `dev`. Báo người dùng:
  - có migration `V12`, `V13` (chỉ thêm bảng);
  - cấu hình mới `toolbox.*`;
  - chạy sau proxy thì bật `server.forward-headers-strategy`.
- [ ] 18.5 Báo phiên FE bằng SendMessage:
  - Dataset API đã chạy, theo đúng contract Toolbox v1 (kèm request/response mẫu);
  - mọi endpoint, kể cả Importer, có thể trả `429`/`503` kèm `Retry-After`;
  - header `Cache-Control: no-store`.
- [ ] 18.6 `git branch -d feature/core-04-platform-services`.
