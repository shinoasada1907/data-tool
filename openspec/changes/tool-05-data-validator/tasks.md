# TOOL-05 Data Validator — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans. Mỗi task là một chu kỳ TDD. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** `/api/validator/runs…` theo `design.md` VD1–VD7.

**Spec:** `specs/data-validator`.

## Global Constraints

- core-03 và core-06 đã merge vào `dev`. Nhánh `feature/tool-05-data-validator`.
- Message lỗi không chứa giá trị ô. Giá trị ô chỉ xuất hiện trong `values`, `errors[].value` và file export.

---

## 1. Tạo run

**Files:**
- Create: `MAIN/tools/validator/api/{ValidatorController, ValidatorDtos}.java`, `MAIN/tools/validator/application/{ValidatorService, ColumnMatcher}.java`
- Modify: `ErrorCode` (`SCHEMA_INCOMPATIBLE`), `ErrorHttpStatus`, `ArchitectureTest.TOOL_NAMES`, `OpenApiConfig` (group `validator`)
- Test: `TEST/tools/validator/ColumnMatcherTest.java`, `TEST/tools/validator/ValidatorApiIntegrationTest.java`

- [ ] 1.1 `ColumnMatcherTest`: khớp chính xác trước; ` Email ` khớp `email`; một cột không khớp hai field; thiếu required → danh sách lỗi `FIELD_MISSING`; optional thiếu → `missingOptional`; cột thừa → `extraColumns`.
- [ ] 1.2 `ValidatorApiIntegrationTest` (tạo run): scenario "File có row lỗi", "Trùng với row lỗi vẫn là trùng", "Constraint sai kiểu" (`pointer` `/schema/fields/1/constraints/min`), "Thiếu cột bắt buộc" (không có row `tool_run`), `DATASET_NOT_FOUND`, `GET` run có `Cache-Control: no-store`, `DELETE` rồi `GET` → `404 RUN_NOT_FOUND`.
- [ ] 1.3 FAIL → cài đặt → PASS.
- [ ] 1.4 Commit: `feat(validator): validate a dataset against an inline schema into a run`

## 2. Xem row

**Files:**
- Create: `MAIN/tools/validator/application/ValidatorRows.java`
- Test: thêm vào `ValidatorApiIntegrationTest`

- [ ] 2.1 Test: `view` mặc định `VALID`; `view=invalid` có `errors[].value` là giá trị gốc; scenario "Lọc theo mã"; `view=x` → `400`; `page=5&size=10` → `rows` rỗng, tổng đúng; xoá dataset nguồn rồi `rows` vẫn `200`.
- [ ] 2.2 FAIL → cài đặt → PASS.
- [ ] 2.3 Commit: `feat(validator): paged valid and invalid rows with field and code filters`

## 3. Export

**Files:**
- Create: `MAIN/tools/validator/application/ValidatorExports.java`
- Test: thêm vào `ValidatorApiIntegrationTest`

- [ ] 3.1 Test: scenario "Ngày theo format riêng thành ngày ISO", "Danh sách lỗi"; `INVALID` sang CSV có `_row` và `_errors`; tên file `-valid.xlsx`; `content` lạ → `400`; `output.format` lạ → `422 CONFIG_INVALID`.
- [ ] 3.2 FAIL → cài đặt → PASS.
- [ ] 3.3 Commit: `feat(validator): export valid rows, invalid rows or the error list`

## 4. Hoàn tất

- [ ] 4.1 `./mvnw -q verify`. Mong đợi: PASS.
- [ ] 4.2 Chạy app thật với DB tạm: upload → run → rows → export.
- [ ] 4.3 `openspec validate tool-05-data-validator --strict`, rồi `openspec archive tool-05-data-validator -y`.
- [ ] 4.4 Merge `--no-ff` vào `dev`, xoá nhánh. Gửi contract cho phiên FE.
