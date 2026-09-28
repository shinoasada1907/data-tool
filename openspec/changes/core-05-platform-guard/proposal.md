## Why

Change `core-04-platform-services` ban đầu gồm cả dataset, download, dọn dẹp **và** các phần bảo vệ site public, run store, guest identity, mã lỗi mở rộng. Ngày 2026-09-28 người dùng muốn có tool đầu tiên (Data Converter) sớm và tiết kiệm token. Converter chỉ cần dataset, download và dọn dẹp, nên phần còn lại được tách sang change này. Chúng vẫn bắt buộc trước khi có tool dạng run (Validator, Cleaner, Diff), trước Schema Builder (guest token), và trước khi mở site public (guard).

## What Changes

- `ErrorCode` chuyển từ enum sang interface kèm `ErrorKind`. Có các enum `CommonError`, `ImporterError`, và test canh tên mã duy nhất.
- Rate limit theo client (bucket `upload`, `compute`, `read`) → `429 RATE_LIMITED` + `Retry-After`. Áp cho cả Importer.
- `ProcessingGate` (toàn server và theo client) → `503 SERVER_BUSY` / `429`. Permit giữ tới byte cuối.
- `DiskSpaceGuard` → `503 SERVER_BUSY` khi đĩa sắp đầy. Body JSON tối đa 1 MB.
- Run store: bảng `tool_run` (Flyway `V13`), section NDJSON ghi nguyên khối, TTL trượt 24 giờ, `RUN_NOT_FOUND`, `DELETE`. Thêm `RunCatalog` vào cleanup chung.
- Guest identity: `X-Guest-Token` → `GuestKey`.
- Metrics Micrometer theo tool.

## Capabilities

### New Capabilities
- `tool-runs`: ngữ nghĩa chung của mọi run (tạo nguyên khối, xem, xoá, TTL, độc lập với dataset, phân trang).

### Modified Capabilities
- `toolbox-platform`: thêm giới hạn tần suất, giới hạn thao tác nặng, chặn khi đĩa sắp đầy, giới hạn body JSON, guest token.

## Impact

- **Code**: `platform.{guard, run, identity}`; `core.common.ErrorCode`; controller Importer có thêm annotation rate limit và dùng gate.
- **DB**: `V13__create_tool_run.sql`.
- **FE**: mọi endpoint có thể trả `429`/`503` kèm `Retry-After`.
