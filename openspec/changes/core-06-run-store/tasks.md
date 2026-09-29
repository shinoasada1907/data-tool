# CORE-06 Run store — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans. Mỗi task là một chu kỳ TDD. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** `platform.run` đủ cho Validator: ghi nguyên khối, xem, đọc section, xoá, TTL trượt, dọn dẹp.

**Architecture:** design core-04 PL6 cùng các chỗ khác ghi ở `design.md` (RS1–RS4).

**Spec:** `specs/tool-runs`.

## Global Constraints

- Nhánh `feature/core-06-run-store`, tạo từ `dev` sau khi core-03 đã merge.
- Test dùng DB và storage riêng (Testcontainers, thư mục tạm), như mọi test tích hợp khác.

---

## 1. Bảng và store

**Files:**
- Create: `src/main/resources/db/migration/V13__create_tool_run.sql`
- Create: `MAIN/platform/run/{RunStore, RunWriter, SectionWriter, RunRecord, RunSource, ToolRunEntity, ToolRunJpaRepository, RunProperties}.java`
- Modify: `MAIN/core/common/ErrorCode.java` (`RUN_NOT_FOUND`), `MAIN/platform/web/ErrorHttpStatus.java` (404)
- Test: `TEST/platform/run/RunStoreIntegrationTest.java`

- [x] 1.1 Viết `RunStoreIntegrationTest`:
  - `begin("sample")` → có thư mục trong `.staging`; ghi section `rows` 3 dòng; `commit(...)` → thư mục `{id}` có `rows.ndjson`, có row DB, `.staging` rỗng;
  - `close()` khi chưa commit → thư mục tạm bị xoá, không có row DB;
  - `read(id, "rows", Map.class, 1)` → 2 record cuối;
  - tên section `Rows!` → `IllegalArgumentException`;
  - `find("other", id)` → rỗng; `find("sample", id)` sau 25 giờ (`Clock` giả) → rỗng;
  - `delete("sample", id)` → không còn row và thư mục; gọi lại → `RUN_NOT_FOUND`;
  - `owns(id)` đúng khi còn row.
- [x] 1.2 Chạy. Mong đợi: FAIL.
- [x] 1.3 Cài đặt theo PL6 và RS1–RS3.
- [x] 1.4 Chạy lại. Mong đợi: PASS.
- [x] 1.5 Commit: `feat(run): atomic run store with NDJSON sections and sliding retention (V13)`

## 2. Dọn dẹp

**Files:**
- Create: `MAIN/platform/run/{RunCleanup, RunCleanupScheduler}.java`
- Test: `TEST/platform/run/RunCleanupIntegrationTest.java`

- [x] 2.1 Viết test:
  - run không dùng 25 giờ → `cleanupExpired` xoá row và thư mục;
  - run dùng cách đây 1 giờ → còn;
  - thư mục trong `.staging` sửa cách đây 2 giờ → bị xoá; sửa cách đây 10 phút → còn;
  - lượt dọn mồ côi của Importer không xoá thư mục của run còn hạn.
- [x] 2.2 FAIL → cài đặt theo RS4 → PASS.
  - ~~Test riêng `RunCleanupIntegrationTest`~~ gộp vào `RunStoreIntegrationTest` (`cleanup_takes_expired_runs_and_abandoned_staging_only`). **LÝ DO:** cùng một bộ dựng dữ liệu; tách file chỉ lặp lại phần dựng.
  - ~~Case "lượt dọn mồ côi của Importer không xoá thư mục run"~~ không viết test riêng. **LÝ DO:** `SessionCleanupService` nhận mọi bean `StorageOwner` qua `List<StorageOwner>`, và `RunStore` là một bean như vậy. `owns(id)` đã được test, và đường gọi đó đã có test cho dataset ở core-04.
  - Nhóm 1 và 2 chung một commit. **LÝ DO:** `RunCleanup` dùng `RunStore.stagingRoot()` và `deleteFiles()`, viết cùng lúc.
- [x] 2.3 Commit: `feat(run): expired runs and abandoned staging directories are cleaned up`

## 3. Hoàn tất

- [ ] 3.1 `./mvnw -q verify`. Mong đợi: PASS.
- [ ] 3.2 `openspec validate core-06-run-store --strict`, rồi `openspec archive core-06-run-store -y`.
- [ ] 3.3 Merge `--no-ff` vào `dev`, xoá nhánh.
