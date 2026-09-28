# CORE-05 Platform guard: rate limit, gate, run store, guest, mã lỗi mở rộng — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** phần còn lại của platform, tách từ core-04 ngày 2026-09-28:
- `ErrorCode` thành interface;
- rate limit, `ProcessingGate`, `DiskSpaceGuard`, giới hạn body JSON;
- run store (`tool_run`, Flyway `V13`) cho Validator, Cleaner, Diff;
- guest identity;
- metrics.

**Architecture:** design của core-04 (`openspec/changes/archive/*-core-04-platform-services/design.md`) PL1–PL4, PL6, PL8, PL10 (giới hạn body), PL11. Nền là core-01 TD13–TD16, TD19.

**Spec:** `specs/tool-runs`, `specs/toolbox-platform` (ADDED).

## Global Constraints

- core-04 đã merge. Nhánh `feature/core-05-platform-guard`.
- Phải xong **trước** tool đầu tiên dạng run (Validator), và trước khi mở site public.
- Mọi ràng buộc chung của core-04 (DB và storage riêng cho test, không chạy trên DB dùng chung với TTL ngắn) vẫn áp dụng.
- Số thứ tự các nhóm task giữ như ở core-04, để dễ đối chiếu.

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

