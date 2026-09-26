## Context

- Quyết định nền nằm trong `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md` (D1–D14, mục "API contract V0.1"). Change này không chép lại các quyết định đó, chỉ dẫn chiếu.
- Các mục liên quan trực tiếp:
  - D3: phân trang, `size` mặc định 50 và tối đa 200, ngoài khoảng thì 400.
  - D4: `RESULT_NOT_AVAILABLE` là 409.
  - D7: result store trên đĩa, `configHash`.
  - D10: cách hiển thị `values` của row hợp lệ và row lỗi.
  - D11: lệnh đọc không lấy khoá.
- **FE** (`fe-import-wizard-v0-1`, D9 và Q3) gọi `GET .../result?view=valid|invalid&page=<từ 0>&size=50[&field=&code=]`. Câu trả lời Q3 trong design của be-f01 đã hứa: có phân trang, có lọc `field`/`code`, summary có số đếm lỗi, và gọi trước khi process thì trả 409 `RESULT_NOT_AVAILABLE`.

### Giả định về các change viết song song (phải đối chiếu ở task 1 trước khi code)

> **Đã đối chiếu (task 1.1)**: tên thật của F08 khác giả định dưới đây.
> - `readSummary` → `findSummary`.
> - `readRows` do F09 thêm vào `ResultStore`, cùng enum `domain.pipeline.ResultView`.
> - `ImportError.stage` / `code` là enum `ErrorStage` / `RowErrorCode`.
> - `currentConfigHash` → `ConfigHasher.hash(ImportConfiguration)` trên config đã lưu.
>
> Khối code dưới đây giữ nguyên làm lịch sử.

be-f08 được giả định cung cấp các thành phần sau. Tên có thể khác; khi khác thì dùng tên thật của F08, sửa file này và ghi LÝ DO.

```java
// port đọc/ghi kết quả theo D7
public interface ResultStore {
    Optional<ResultSummary> readSummary(UUID sessionId);          // đọc result/summary.json
    Stream<RowResult> readRows(UUID sessionId, ResultView view);  // đọc stream valid.ndjson hoặc invalid.ndjson
                                                                  // theo rowNumber tăng dần; caller phải close
}
public enum ResultView { VALID, INVALID }
public record ResultSummary(long total, long valid, long invalid,
                            Map<String, Long> errorCountsByCode, Map<String, Long> errorCountsByField,
                            Instant processedAt, String configHash) {}
public record RowResult(int rowNumber, boolean valid,
                        Map<String, Object> values,              // LinkedHashMap, key theo thứ tự schema;
                                                                 // số là BigDecimal, không phải double
                        List<ImportError> errors) {}
public record ImportError(int rowNumber, String fieldName, String stage, String rule, Integer step,
                          String code, String message, String sourceValue) {}
// tính hash của config hiện tại, cùng cách với lúc ghi summary.json
String currentConfigHash(UUID sessionId);                        // nằm trong service config của F04–F08
// DTO dùng chung với POST /process
public record PipelineSummaryDto(UUID sessionId, SessionStatus status, long total, long valid, long invalid,
                                 Map<String, Long> errorCountsByCode, Map<String, Long> errorCountsByField,
                                 Instant processedAt) {}
```

## Goals / Non-Goals

**Goals:**
- `GET /result` đúng API contract V0.1, đủ để FE-F09 hiển thị summary, hai tab row hợp lệ/row lỗi, bảng lỗi có lọc, và vẫn dùng được khi có rất nhiều lỗi.
- Kết quả tái lập được: gọi lại cùng tham số thì ra cùng dữ liệu, cùng thứ tự.

**Non-Goals:**
- Sắp xếp theo cột khác ngoài `rowNumber`.
- Tìm kiếm full-text.
- Lọc row hợp lệ.
- Index offset cho ndjson. V0.1 quét tuyến tính; dung lượng file đã bị giới hạn ở 20MB.
- Export (thuộc be-f10).

## Decisions

### F09-D1. Tham số truy vấn và cách kiểm tra
- `view`: `valid` hoặc `invalid`, không phân biệt hoa thường. Mặc định `valid`. Giá trị khác trả 400 `REQUEST_INVALID`.
- `page`: số nguyên ≥ 0, mặc định 0. `size`: số nguyên từ 1 đến 200, mặc định 50. Ngoài khoảng, hoặc không phải số, trả 400 `REQUEST_INVALID`.
- `field` và `code`: chuỗi rỗng hoặc toàn khoảng trắng coi như không gửi. FE có thể gửi `field=&code=`.
- Việc kiểm tra nằm ở controller: nó parse rồi ném `DomainException(REQUEST_INVALID, …)`. Không dùng Bean Validation cho query param, để thông điệp và mã lỗi luôn nhất quán.
- *Phương án khác*: bắt buộc có `view`. Loại, vì FE luôn gửi `view`, nên mặc định `valid` chỉ giúp gọi tay bằng curl dễ hơn mà không có rủi ro gì.

### F09-D2. Ngữ nghĩa lọc
- Bộ lọc chỉ áp cho `view=invalid`. Với `view=valid` thì bộ lọc bị **bỏ qua**, không báo lỗi, vì FE có thể giữ trạng thái bộ lọc khi chuyển tab.
- Một row lỗi khớp khi tồn tại **một** phần tử trong `errors[]` thoả đồng thời:
  - `error.fieldName` bằng đúng `field`, nếu có gửi `field`;
  - `error.code` bằng đúng `code`, nếu có gửi `code`.

  So khớp chính xác. `code` lạ không phải lỗi, chỉ đơn giản là không có row nào khớp.
- Row khớp được trả **đầy đủ** `errors[]` của nó, không cắt bớt những lỗi không khớp, để FE hiện đúng mọi lỗi của row.

### F09-D3. Phân trang bằng quét stream
- Đọc `ResultStore.readRows(id, view)` theo thứ tự `rowNumber` tăng dần, lọc, bỏ qua `page*size` row đầu, rồi lấy `size` row.
- **`totalElements`**:
  - Không có bộ lọc hiệu lực: lấy từ summary (`valid` hoặc `invalid`), và dừng quét ngay khi đã đủ trang.
  - Có bộ lọc: đếm trong cùng lượt quét toàn bộ view.
- `totalPages = ceil(totalElements / size)`, bằng 0 khi `totalElements = 0`. `page` vượt trang cuối thì trả `rows: []` với status 200, không báo lỗi.
- Stream luôn được đóng bằng try-with-resources.
- *(Khác D11, xem tasks — Global Constraints)*: đọc giữ khoá session. Trên Windows không đổi tên được thư mục khi có file bên trong đang mở, nên một lần đọc chạy chen có thể làm commit của `/process` hoặc lệnh xoá khi đổi config thất bại. `requireCurrentSummary` cũng giữ khoá; khoá là reentrant nên `query` gọi nó bên trong khoá được.

### F09-D4. Điều kiện có kết quả
Kiểm theo thứ tự sau, dừng ở điều kiện đầu tiên không đạt:
1. Session không tồn tại: 404 `SESSION_NOT_FOUND`.
2. `status` khác `PROCESSED`: 409 `RESULT_NOT_AVAILABLE`. Áp cả cho `FAILED`, vì đây là lệnh đọc nên không dùng `SESSION_STATE_INVALID`.
3. Không có `summary.json`: 409 `RESULT_NOT_AVAILABLE`.
4. `summary.configHash` khác hash của config hiện tại: 409 `RESULT_NOT_AVAILABLE`.

Điều kiện 4 là lớp bảo vệ thứ hai của D7, vì bình thường PUT làm đổi config đã xoá kết quả rồi.

Phần kiểm này nằm trong một method public `ResultQueryService.requireCurrentSummary(UUID)`, để be-f10 (export) dùng lại đúng cùng điều kiện, không chép logic.

### F09-D5. Hiển thị giá trị
- `values` giữ đúng thứ tự key theo schema (dùng `LinkedHashMap` từ store).
- Row hợp lệ mang giá trị đã ép kiểu: `BigDecimal` thành JSON number, `Boolean` thành `true`/`false`, ngày là chuỗi `yyyy-MM-dd`, rỗng là `null`.
- Row lỗi mang chuỗi sau transformation, hoặc `null` (D10).
- Số luôn ghi dạng plain. Mapper JSON của app bật `StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN` qua `JsonMapperBuilderCustomizer`. Nếu không bật, `BigDecimal("0.0000001")` sẽ ra `1E-7`. Nếu F08 đã bật thì task 1 bỏ phần này.
- `summary.status` là trạng thái hiện tại của session, tức `PROCESSED`.

## Risks / Trade-offs

- [Lọc hoặc lấy trang sâu phải quét toàn bộ file ndjson] → Dung lượng bị chặn bởi giới hạn upload 20MB, nên mỗi request chỉ quét vài MB. Về sau có thể thêm file index offset nếu cần.
- [Tên thành phần của F08 khác giả định] → Task 1 đối chiếu và sửa file này trước khi code.
- [PUT xoá thư mục `result/` đúng lúc một request đang đọc file] → Trên Linux, file đã mở vẫn đọc tiếp được. Trên Windows, việc xoá có thể thất bại vì file đang mở; đây là rủi ro của cách F08 xoá kết quả, ghi ở Open Questions.
- [Mapper JSON toàn cục bật ghi số dạng plain] → Chỉ ảnh hưởng `BigDecimal`. Các endpoint khác chưa trả `BigDecimal`, nên không có tác dụng phụ.

## Migration Plan

- Không có migration DB. Làm trên nhánh `feature/be-f09-result-api` sau khi be-f08 đã merge.
- Rollback: revert nhánh.

## Open Questions

- Việc bật `WRITE_BIGDECIMAL_AS_PLAIN` cho mapper toàn cục là mối quan tâm chung của F08 (ghi ndjson), F09 (response) và F10 (export). Đề xuất F08 làm vì F08 là nơi đầu tiên ghi số. Nếu F08 chưa làm thì F09 làm ở task 1.
- ~~Trên Windows, xoá `result/` khi còn request đang đọc có thể lỗi (file đang mở). Cần F08 quyết định cách xoá, ví dụ đổi tên sang thư mục rác rồi xoá sau. F09 không đổi gì vì chuyện này.~~ Đã giải quyết:
  - F08 xoá bằng cách đổi tên sang `result.del-*`, thử lại khi bị từ chối, và làm best-effort.
  - F09 đọc trong khoá session, nên không còn đọc chen lúc đang đổi tên.
