## Context

Thiết kế gốc nằm ở design core-04, mục PL6 (`openspec/changes/archive/*-core-04-platform-services/design.md`), và core-01 TD13. File này chỉ ghi những chỗ cài khác PL6. Quyết định mang mã `RS1`…`RS4`.

## Goals / Non-Goals

**Goals:** run store đủ cho Validator, Cleaner và Diff.

**Non-Goals:**
- Rate limit, gate, guest token, metrics: vẫn thuộc core-05.
- Endpoint mẫu cho run: mỗi tool tự có controller riêng.

## Decisions

### RS1. Thư mục tạm nằm trong `{root}/.staging/`

- PL6 đặt thư mục tạm là `{root}/{id}.staging-{nonce}`. Lượt dọn mồ côi (spec `import-session`) không đụng tên không phải UUID, nên muốn dọn loại thư mục này phải sửa requirement đó.
- Cách làm ở đây: ghi vào `{root}/.staging/{id}-{nonce}/`, rồi `ATOMIC_MOVE` sang `{root}/{id}/`. Hai thư mục cùng một ổ đĩa, nên rename vẫn nguyên khối.
- `.staging` không phải UUID, nên lượt dọn mồ côi bỏ qua nó. `RunCleanup` tự xoá các thư mục trong `.staging` cũ hơn 1 giờ.
- Kết quả: spec `import-session` không phải sửa.

### RS2. API

```java
@Component public class RunStore implements StorageOwner {
    RunWriter begin(String tool);
    Optional<RunRecord> find(String tool, UUID id);      // khác tool hoặc hết hạn → empty; có touch
    RunRecord get(String tool, UUID id);                 // như find, không có thì RUN_NOT_FOUND
    <T> Stream<T> read(UUID id, String section, Class<T> type, long skip);
    void delete(String tool, UUID id);                   // RUN_NOT_FOUND nếu không có
}
public final class RunWriter implements AutoCloseable {
    SectionWriter section(String name);                  // [a-z][a-z0-9-]{0,31}
    RunRecord commit(List<RunSource> sources, Object config, Object summary);
    void close();                                        // chưa commit → xoá thư mục tạm
}
public record RunRecord(UUID id, String tool, Instant createdAt, Instant expiresAt,
                        List<RunSource> sources, JsonNode config, JsonNode summary) {}
```

`RunStore` là class Spring cụ thể, không phải interface: chỉ có một cách lưu.

### RS3. `config` và `summary` là JSON do tool tự quy định

- Store chỉ ghi và đọc lại `JsonNode`. Tool đọc bằng `JsonMapper.treeToValue` sang record của nó.
- `config` là phần tool cần để xem và export mà không cần dataset (ví dụ schema của Validator).

### RS4. Dọn dẹp

- `RunCleanup.cleanupExpired()` xoá row hết hạn trước (có điều kiện, như dataset), rồi mới xoá thư mục. Xoá thư mục lỗi thì để lượt dọn mồ côi lo.
- Cùng lượt đó xoá thư mục trong `.staging` cũ hơn 1 giờ.
- Lịch chạy: `toolbox.run.cleanup.enabled` (mặc định `true`), cùng khoảng như dọn dataset.

## Risks / Trade-offs

- Run đang được đọc (xem trang, export) mà bị xoá thì stream đang đọc có thể lỗi giữa chừng. Download sẽ bị huỷ kết nối, như mọi lỗi sau byte đầu. Chấp nhận được: xoá là hành động chủ ý của client, hết hạn thì run đã không được dùng 24 giờ.

## Migration Plan

`V13` chỉ thêm bảng. Rollback: revert nhánh.

## Open Questions

(không có)
