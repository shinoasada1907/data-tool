## Context

Change này là nửa sau của `core-04-platform-services`, tách ra ngày 2026-09-28 (xem proposal). Mọi quyết định đã có sẵn trong design của core-04 và **không lặp lại ở đây**:

| Phần | Mục trong design core-04 |
|---|---|
| Mã lỗi: interface, kind, enum theo phạm vi | PL1 |
| Client key và rate limit | PL2 |
| `ProcessingGate` | PL3 |
| `DiskSpaceGuard` | PL4 |
| Run store | PL6 |
| Guest identity | PL8 |
| Giới hạn body JSON | PL10 (phần body) |
| Metrics | PL11 |

Design của core-04 nằm ở `openspec/changes/archive/*-core-04-platform-services/design.md` sau khi archive.

## Goals / Non-Goals

**Goals:** như các mục trên.

**Non-Goals:** dataset, download và dọn dẹp dataset đã xong ở core-04.

## Decisions

### G1. Thứ tự làm

Làm theo thứ tự sau:
1. Mã lỗi.
2. Guard: client key, rate limit, gate, đĩa, body.
3. Run store.
4. Guest.
5. Metrics.

Guard được đặt trước run store, vì mọi endpoint run đều cần gate ngay từ đầu.

### G2. Gắn guard vào những gì đã có

Khi core-05 bắt đầu, dataset API và Converter có thể đã tồn tại. Hai thứ đó nhận rate limit và gate theo đúng bảng PL2/PL3, và test của chúng được bổ sung case `429`/`503`.

## Risks / Trade-offs

Trong lúc chưa có core-05, site **chưa có** rate limit hay giới hạn đồng thời. Vì vậy chưa được mở public trước khi core-05 xong.

## Open Questions

(không có)
