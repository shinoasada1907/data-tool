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

~~1. `ErrorCode` thành interface~~

**LÝ DO:** tách sang change `core-05-platform-guard` (2026-09-28). Người dùng muốn có tool đầu tiên (Converter) sớm và tiết kiệm token; Converter chỉ cần dataset, download và dọn dẹp. Nhóm task này được chép nguyên sang `core-05-platform-guard/tasks.md`.

## 2. Extension của ProblemDetail và `Retry-After`

**Files:**
- Modify: `MAIN/core/common/DomainException.java` (`Map<String,Object> extensions`, hằng `RETRY_AFTER`), `MAIN/platform/web/GlobalExceptionHandler.java`
- Test: `TEST/platform/web/ProblemExtensionsTest.java`

- [x] 2.1 Viết test, dùng controller giả trong test:
  - ném `DomainException(SERVER_BUSY, "Busy", extensions {retryAfterSeconds: 5})` → status `503`, header `Retry-After: 5`, body không có `retryAfterSeconds`;
  - ném với extension `{keyProblems: [...]}` → body có `keyProblems` ở top-level;
  - `RATE_LIMITED` không có `retryAfterSeconds` → vẫn có `Retry-After: 1` (mặc định tối thiểu).
- [x] 2.2 Chạy. Mong đợi: FAIL.
- [x] 2.3 Cài đặt.
- [x] 2.4 Chạy lại. Mong đợi: PASS.
- [x] 2.5 Commit: `feat(errors): problem extensions and Retry-After`
  - Làm khác: test gọi thẳng `GlobalExceptionHandler.handleDomain` (unit test `ProblemExtensionsTest`), không dựng controller giả. `ErrorCode` vẫn là enum (interface chuyển sang core-05); thêm luôn `DATASET_NOT_FOUND` (404) và `SERVER_BUSY` (503), vì dataset cần chúng.

~~3. `ClientKeyResolver`~~

**LÝ DO:** tách sang change `core-05-platform-guard` (2026-09-28). Người dùng muốn có tool đầu tiên (Converter) sớm và tiết kiệm token; Converter chỉ cần dataset, download và dọn dẹp. Nhóm task này được chép nguyên sang `core-05-platform-guard/tasks.md`.

~~4. Rate limit~~

**LÝ DO:** tách sang change `core-05-platform-guard` (2026-09-28). Người dùng muốn có tool đầu tiên (Converter) sớm và tiết kiệm token; Converter chỉ cần dataset, download và dọn dẹp. Nhóm task này được chép nguyên sang `core-05-platform-guard/tasks.md`.

~~5. `ProcessingGate`~~

**LÝ DO:** tách sang change `core-05-platform-guard` (2026-09-28). Người dùng muốn có tool đầu tiên (Converter) sớm và tiết kiệm token; Converter chỉ cần dataset, download và dọn dẹp. Nhóm task này được chép nguyên sang `core-05-platform-guard/tasks.md`.

~~6. `DiskSpaceGuard`~~

**LÝ DO:** tách sang change `core-05-platform-guard` (2026-09-28). Người dùng muốn có tool đầu tiên (Converter) sớm và tiết kiệm token; Converter chỉ cần dataset, download và dọn dẹp. Nhóm task này được chép nguyên sang `core-05-platform-guard/tasks.md`.

## 7. Giới hạn body JSON và header chung

**Files:**
- Create: `MAIN/platform/web/{JsonBodyLimitFilter, SecurityHeadersFilter}.java`
- Test: `TEST/platform/web/WebFiltersIntegrationTest.java`

- [ ] 7.1 Viết test (~~giới hạn body JSON 1 MB~~ **LÝ DO:** chuyển sang core-05 cùng các guard khác):
  - mọi response có `X-Content-Type-Options: nosniff`;
  - `GET /api/datasets/{id}` (task 11) có `Cache-Control: no-store`. Case này để lại, thêm ở task 11.
- [ ] 7.2 FAIL → cài đặt → PASS.
- [ ] 7.3 Commit: `feat(web): 1 MB JSON body limit and nosniff header`

## 8. Dataset: bảng, repository, TTL

**Files:**
- Create: `src/main/resources/db/migration/V12__create_dataset.sql`
- Create: `MAIN/platform/dataset/{Dataset, DatasetRepository, JpaDatasetRepository, DatasetEntity, DatasetJpaRepository, RetentionProperties}.java`
- Test: `TEST/platform/dataset/JpaDatasetRepositoryTest.java`

- [x] 8.1 Viết test (Testcontainers):
  - `save` rồi `findLive(id, now)` → có;
  - `lastUsedAt` cách đây 25 giờ, TTL 24 giờ → `findLive` rỗng;
  - `touch(id, now)` khi `lastUsedAt` cách đây 5 phút → không đổi; cách đây 11 phút → đổi thành `now`;
  - `listExpired(now, 10)` chỉ trả dataset hết hạn;
  - `deleteIfExpired(id, cutoff)` là một lệnh có điều kiện: dataset vừa được touch thì không bị xoá.
- [x] 8.2 FAIL → migration và cài đặt → PASS.
- [x] 8.3 Commit: `feat(dataset): dataset table (V12) with sliding 24h retention`

## 9. Upload dataset

**Files:**
- Modify: `MAIN/core/table/FileTypeDetector.java` (JSON; CSV có BOM UTF-16)
- Create: `MAIN/platform/dataset/{DatasetService, DatasetController, DatasetDto}.java`
- Test: `TEST/core/table/FileTypeDetectorTest.java` (thêm case), `TEST/platform/dataset/DatasetUploadIntegrationTest.java`

- [x] 9.1 Thêm case cho `FileTypeDetectorTest` với `allowed = {CSV, XLSX, JSON}`:
  - `data.JSON` + `  [{"a":1}]` → JSON;
  - `data.json` + BOM + `[` → JSON;
  - `data.json` + `{"a":1}` → `FILE_PARSE_ERROR`, detail `JSON must be an array of objects.`;
  - `x.csv` bắt đầu `FF FE` và có `00` → CSV;
  - `x.csv` có `00` mà không có BOM → `FILE_UNSUPPORTED`;
  - `x.json` với `allowed={CSV,XLSX}` → `FILE_UNSUPPORTED`.
- [x] 9.2 Viết `DatasetUploadIntegrationTest` theo spec `dataset-api` "Upload dataset":
  - CSV tên tiếng Việt → `201`, `Location`, `format=CSV`, `sheets=null`, `expiresAt - createdAt = 24h`;
  - XLSX có sheet ẩn → `sheets` đúng thứ tự và `visible`;
  - `{"a":1}` → `422 FILE_PARSE_ERROR`; không có row và không có thư mục storage mới;
  - `data.xls` → `415`;
  - file 0 byte → `422 FILE_EMPTY`;
  - XLSX bomb → `422 FILE_PARSE_ERROR`;
  - quá 20MB → `413 FILE_TOO_LARGE`.
- [x] 9.3 FAIL → cài đặt theo PL5 (upload) → PASS.
- [x] 9.4 Commit: `feat(dataset): POST /api/datasets for CSV, XLSX and flat JSON`
  - Làm khác: `FileTypeDetector` có hàm riêng `detectDataset` cho toolbox (nhận `.json`, cho phép CSV UTF-16 có BOM); `detect` cũ giữ nguyên cho Importer, nên upload V0.1 không đổi. Upload **không** qua rate limit, `DiskSpaceGuard` hay gate: các guard đó thuộc core-05. Test dataset: `DatasetRepositoryTest`, `DatasetApiIntegrationTest`.

## 10. Preview, cache inspect, khoá, `DatasetSources`

**Files:**
- Create: `MAIN/platform/dataset/{DatasetInspections, DatasetLocks, DatasetSources, DefaultDatasetSources, OpenedSource, SourceRef, DatasetPreviewDto, ReadOptionsParams}.java`
- Test: `TEST/platform/dataset/DatasetPreviewIntegrationTest.java`, `TEST/platform/dataset/DatasetInspectionsTest.java`, `TEST/platform/dataset/DatasetLocksTest.java`

- [x] 10.1 Viết `DatasetPreviewIntegrationTest`, theo mọi scenario của requirement preview trong spec:
  - `ma;ten\n1;An\n2;Bình\n` không tham số → `SEMICOLON`, `autoDetected=["delimiter","encoding"]`, `totalRows=2`, `rows[0]={rowNumber:2, values:["1","An"]}`, `columns[0].inferredType="number"`;
  - `delimiter=comma` (chữ thường) → một cột `ma;ten`;
  - `limit=500` → `400`; `delimiter=COLON` → `400`;
  - JSON lồng nhau → `422 JSON_NOT_FLAT`; XLSX `sheet=Khong co` → `422 CONFIG_INVALID`;
  - dataset hết hạn (chỉnh `last_used_at` trong DB) → `404 DATASET_NOT_FOUND`;
  - response có `Cache-Control: no-store`.
- [x] 10.2 Viết `DatasetInspectionsTest`:
  - hai lần `get` cùng khoá → `inspect` chỉ chạy 1 lần (đếm bằng reader giả);
  - khác `delimiter` → chạy lại;
  - lỗi `FILE_PARSE_ERROR` được cache (lần 2 không đọc file); `UncheckedIOException` **không** được cache;
  - vượt 256 mục → mục cũ nhất bị bỏ;
  - `evict(id)` bỏ mọi mục của id.
- [x] 10.3 Viết `DatasetLocksTest`:
  - đang có read lock thì `tryWrite(5s)` chờ, rồi được ngay khi read lock nhả;
  - hai read lock song song được;
  - sau khi nhả hết, map không còn mục của id đó.
- [x] 10.4 FAIL → cài đặt theo PL5 → PASS.
- [x] 10.5 Commit: `feat(dataset): preview with read options, inspection cache, read/write locks and DatasetSources`
  - Làm khác: `DatasetSources` là class (không phải interface + impl). Test cache và khoá gộp vào `DatasetInspectionsAndLocksTest`. Test tham số preview và case hết hạn nằm trong `DatasetApiIntegrationTest`.

## 11. GET và DELETE dataset

**Files:**
- Modify: `DatasetController`, `DatasetService`
- Test: `TEST/platform/dataset/DatasetLifecycleIntegrationTest.java`

- [x] 11.1 Viết test:
  - `GET` → `DatasetDto` kèm `Cache-Control: no-store`;
  - `DELETE` → `204`; sau đó `GET` và preview → `404 DATASET_NOT_FOUND`; thư mục storage không còn; cache đã bị xoá;
  - `DELETE` trong lúc đang giữ read lock (giữ bằng `DatasetSources.open` trong test) → `503 SERVER_BUSY` sau khoảng 5 giây (test đặt timeout ngắn qua property);
  - `GET /api/datasets/not-a-uuid` → `400 REQUEST_INVALID`;
  - touch: gọi preview lúc T+12h thì `expiresAt` thành T+36h (dùng `Clock` giả).
- [x] 11.2 FAIL → cài đặt → PASS.
- [x] 11.3 Commit: `feat(dataset): read and delete datasets`
  - Làm khác: case touch (xem lúc T+12h thì `expiresAt` thành T+36h) kiểm ở tầng repository (`DatasetRepositoryTest`), không qua API với `Clock` giả. Không có preview hay touch nào đi qua API.

~~12. Run store~~

**LÝ DO:** tách sang change `core-05-platform-guard` (2026-09-28). Người dùng muốn có tool đầu tiên (Converter) sớm và tiết kiệm token; Converter chỉ cần dataset, download và dọn dẹp. Nhóm task này được chép nguyên sang `core-05-platform-guard/tasks.md`.

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

~~14. Guest identity~~

**LÝ DO:** tách sang change `core-05-platform-guard` (2026-09-28). Người dùng muốn có tool đầu tiên (Converter) sớm và tiết kiệm token; Converter chỉ cần dataset, download và dọn dẹp. Nhóm task này được chép nguyên sang `core-05-platform-guard/tasks.md`.

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

~~16. Metrics~~

**LÝ DO:** tách sang change `core-05-platform-guard` (2026-09-28). Người dùng muốn có tool đầu tiên (Converter) sớm và tiết kiệm token; Converter chỉ cần dataset, download và dọn dẹp. Nhóm task này được chép nguyên sang `core-05-platform-guard/tasks.md`.

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
