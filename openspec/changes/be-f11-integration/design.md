## Context

- Quyết định nền nằm trong `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md`. Các mục liên quan trực tiếp:
  - D2: vòng đời, gồm nhánh `FAILED` và "PUT không đổi config thì giữ `PROCESSED`".
  - D3: không CORS, FE dùng Vite proxy.
  - D4: bảng mã lỗi.
  - D6: storage layout `{root}/{sessionId}`.
  - D11: khoá theo session.
  - D12: dọn dẹp.
  - D13: không log nội dung.
  - API contract V0.1: cột "Lỗi chính" của từng endpoint.
- Pack F11 yêu cầu: chuẩn hoá error envelope (đã làm ở F01, F11 kiểm lại), kiểm vòng đời, integration test CSV/XLSX, cấu hình môi trường cho local dev, dọn file tạm, và README có demo flow.

### Giả định về các change khác (đối chiếu ở task 1 trước khi code)

- **F02/F03**: upload đọc toàn bộ file. Thành công thì session sang `CONFIGURING`. CSV không phải UTF-8 trả 422 `FILE_PARSE_ERROR` và không tạo session. XLSX trả ngày dạng ISO, boolean `TRUE`/`FALSE`, số dạng plain (D9).
- **F04**: `import_configuration.session_id` là FK tới `import_session(id)` **có `ON DELETE CASCADE`**. Nếu không có cascade, `deleteById` phải xoá config trước.
- **F05–F07**: các PUT đúng contract; readiness issue `TARGET_FIELD_REQUIRED` có `field` là tên field.
- **F08**:
  - Khoá theo session (D11) có cách **thử lấy khoá mà không chờ**. Trong tasks gọi là `SessionLocks#tryRun(UUID, Runnable) → boolean`, trả `false` khi đang bận.
  - `POST /process` khi `source.bin` không đọc được thì đưa session sang `FAILED`. Response (đã chốt với phiên FE qua parent ngày 2026-09-25):
    - file sai cấu trúc: `422 FILE_PARSE_ERROR`;
    - lỗi IO hoặc thiếu file: `500 INTERNAL_ERROR`.

    Mọi lệnh ghi sau đó trả `409 SESSION_STATE_INVALID`.
- **F09**: helper `ImportFlowClient` trong `TEST/support`. Có `ResultQueryService.requireCurrentSummary`.
- **F10**: export JSON/CSV và báo cáo lỗi đúng spec `data-export`.

## Goals / Non-Goals

**Goals:**
- Storage và DB không phình vô hạn khi chạy lâu.
- Có bằng chứng tự động (integration test) rằng: toàn bộ luồng V0.1 chạy với CSV và XLSX; mọi lỗi đúng contract; vòng đời đúng D2.
- Người mới clone repo làm theo README là chạy được demo.

**Non-Goals:**
- Endpoint xoá session thủ công.
- Chạy nhiều instance (khoá D11 nằm trong JVM).
- CI (GitHub Actions); pack để "về sau".
- E2E có trình duyệt (việc của FE).
- CORS cho môi trường khác origin.

## Decisions

### F11-D1. Dọn session hết hạn
- `SessionCleanupService.cleanupExpired(Instant now)` trả `CleanupReport(deletedSessions, deletedOrphans, skipped, failures)`.
- `cutoff = now − sessionTtl`. Lấy `findIdsUpdatedBefore(cutoff, 500)`, sắp theo `updatedAt` tăng dần; mỗi lần chạy xử lý tối đa 500 session.
- Với mỗi id, gọi `locks.tryRun(id, …)`:
  - Nếu đang bận (có PUT hoặc process đang chạy): `skipped++`, để lần sau.
  - Nếu lấy được khoá: `storage.delete(id)` **trước**, rồi `sessions.deleteById(id)`.
- Lỗi ở một session: `log.warn` kèm `sessionId`, `failures++`, rồi làm tiếp session khác.
  - Nếu xoá storage lỗi thì không xoá row DB, để lần sau thử lại.
  - Nếu xoá row DB lỗi sau khi storage đã xoá thì lần sau vẫn thấy row quá hạn và thử lại.
- `updatedAt` chỉ đổi khi có lệnh ghi (D2). Session chỉ được đọc (GET) mà quá TTL thì vẫn bị xoá; đây là hành vi mong muốn.
- *Phương án khác*: xoá theo `createdAt`. Loại, vì một session vẫn đang được cấu hình sẽ bị xoá giữa chừng.

### F11-D2. Dọn thư mục mồ côi
- `FileStorage.listEntries()` chỉ trả thư mục con có tên parse được thành UUID, kèm `lastModified` của thư mục đó. Thư mục hoặc file có tên khác bị **bỏ qua hoàn toàn**, không bao giờ bị xoá.
- Một thư mục là mồ côi khi `lastModified < cutoff` và `!sessions.existsById(id)`. Mồ côi có thể sinh ra khi app chết giữa lúc upload và lúc ghi DB, hoặc khi xoá DB thành công mà xoá storage lỗi.
- Điều kiện `lastModified` bảo vệ upload đang dở: thư mục vừa tạo thì `lastModified` là thời điểm hiện tại.

### F11-D3. Lịch chạy và cấu hình
- `SessionCleanupScheduler.run()` được gắn `@Scheduled(initialDelayString = "PT0S", fixedDelayString = "${importer.cleanup.interval:PT1H}")`, nên chạy ngay khi khởi động rồi lặp mỗi giờ.
- `run()` bắt mọi exception và ghi `log.error`, để luồng scheduler không bao giờ chết. Log một dòng `info` gồm các số đếm trong report, không có dữ liệu người dùng.
- Class được gắn `@ConditionalOnProperty("importer.cleanup.enabled", matchIfMissing = true)`. Integration test của cleanup đặt `false` để gọi service trực tiếp, tránh chạy song song với scheduler.
- `@EnableScheduling` đặt ở `SchedulingConfig`, thuộc package infrastructure.
- `CleanupProperties` là `@ConfigurationProperties("importer.cleanup")` với các trường `enabled`, `sessionTtl`, `interval`. `session-ttl: ${IMPORTER_SESSION_TTL:24h}`.

### F11-D4. Migration V10
```sql
CREATE INDEX idx_import_session_updated_at ON import_session (updated_at);
```
Dùng số 10 để chừa V2–V9 cho F02–F08, vì các change đó đang được viết song song. Flyway chấp nhận có khoảng trống giữa các số version. Sau F11, migration mới phải dùng số lớn hơn 10.

### F11-D5. Kiểm contract lỗi bằng bảng
- `ErrorContractIntegrationTest` dựng sẵn 4 session:
  - `P`: đã process;
  - `R`: `READY`, chưa process;
  - `U`: field required chưa map;
  - `F`: `FAILED`, bằng cách xoá `source.bin` rồi process.
- Mỗi case trong bảng ghi request, status mong đợi và `code` mong đợi. Mọi response lỗi được kiểm thêm hai điều: `Content-Type` là `application/problem+json`, và `code` thuộc tập mã đã công bố cho endpoint đó (spec `api-errors`).

### F11-D6. Fixture end-to-end
- `fixtures/e2e/customers.csv` và `fixtures/e2e/customers.xlsx` có cùng dữ liệu nghiệp vụ, gồm 4 row:
  - 1 row hợp lệ;
  - 1 row có 3 lỗi (ngày sai, boolean `yes`, số `x`);
  - 1 row trùng email;
  - 1 row thiếu tên.
- Bản XLSX dùng ô kiểu thật: ngày, boolean, số. Vì vậy config của XLSX **không** có `dateFormat`, và lỗi ngày thành `VALIDATION_TYPE` thay vì `TRANSFORMATION_FAILED`. Nhờ đó test phủ được cả hai đường.
- Config gửi lên dùng text block ngay trong test, để đọc test là thấy cả input lẫn kết quả mong đợi.

### F11-D7. Không CORS
- Không cấu hình CORS (D3). Có test khẳng định request có header `Origin: http://localhost:5173` không nhận `Access-Control-Allow-Origin`.
- README hướng dẫn chạy FE bằng `pnpm dev`: Vite proxy chuyển `/api` tới `API_PROXY_TARGET`, mặc định `http://localhost:8080`.

## Risks / Trade-offs

- [Fixture XLSX là file nhị phân tạo bằng tay] → Task 7 ghi đủ bảng ô, kiểu ô và cách kiểm lại (unzip xem `t="b"` và `s=` style ngày). Dùng lại fixture của F03 nếu được.
- [Scheduler chạy ngay khi khởi động trong mọi integration test] → TTL 24h nên session của test không bao giờ quá hạn. Thư mục storage của test là thư mục tạm riêng.
- [Nhánh `FAILED` phụ thuộc F08] → Test tạo nhánh này bằng cách xoá `source.bin` (lỗi thiếu file), rồi khẳng định:
  - `POST /process` trả `500 INTERNAL_ERROR`;
  - session sang `FAILED`;
  - lệnh ghi sau đó trả `409 SESSION_STATE_INVALID`.

  Trường hợp `422 FILE_PARSE_ERROR` (file sai cấu trúc) không tạo được bằng file hợp lệ đã qua bước upload, nên chỉ được kiểm ở unit test của F08.
- [Xoá session đúng lúc có request đang đọc] → Chấp nhận ở V0.1, vì TTL 24h tính từ lần ghi cuối.
- [Chờ đủ 500 session mỗi lần chạy] → Dư sức ở quy mô local V0.1; lần chạy sau làm tiếp.

## Migration Plan

- `V10` chỉ thêm index, an toàn với dữ liệu đang có.
- Làm trên nhánh `feature/be-f11-integration` khi F01–F10 đã merge; khi đó mới chạy integration end-to-end.
- Rollback: revert nhánh. Index thừa không gây hại, nên không cần migration xoá index.

## Open Questions

- Nếu F04 không đặt `ON DELETE CASCADE` cho `import_configuration`, `deleteById` phải xoá config trước. Task 1 kiểm điều này.
- ~~Response của `POST /process` khi file nguồn không đọc được.~~ **Đã chốt** (qua parent, ngày 2026-09-25): sai cấu trúc trả `422 FILE_PARSE_ERROR`; lỗi IO hoặc thiếu file trả `500 INTERNAL_ERROR`; session sang `FAILED`. Bảng mã của `process` trong spec `api-errors` đã có `FILE_PARSE_ERROR`.
