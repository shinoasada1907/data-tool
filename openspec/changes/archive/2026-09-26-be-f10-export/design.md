## Context

- Quyết định nền nằm trong `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md`. Các mục liên quan trực tiếp:
  - D3: `message` tiếng Anh, không CORS.
  - D4: `RESULT_NOT_AVAILABLE` 409, `EXPORT_FAILED` 500.
  - D7: result store trên đĩa.
  - D10: kiểu dữ liệu sau khi ép kiểu.
  - D11: lệnh đọc không lấy khoá.
  - D13: phạm vi chống formula injection; `Content-Disposition` theo RFC 5987.
  - Mục "Export (F10)" của API contract V0.1.
- **FE** (`fe-import-wizard-v0-1`):
  - D10: tải file bằng `fetch` + blob, kiểm `response.ok`, lấy tên file từ `filename*` trước rồi mới tới `filename`.
  - Q6: đã được trả lời bằng danh sách cột của báo cáo lỗi.
  - Phiên FE đã chốt thêm (qua parent, ngày 2026-09-25):
    - FE coi `409 RESULT_NOT_AVAILABLE` là tín hiệu "kết quả đã cũ", áp chung cho result và export. Vì vậy export dùng **đúng** điều kiện của `GET /result` (F09-D4).
    - FE nhận biết session mất theo `code` `SESSION_NOT_FOUND` (404), không theo status.

### Giả định về các change khác (đối chiếu ở task 1 trước khi code)

> **Đã đối chiếu (task 1.1)**: tên thật khác giả định dưới đây.
> - Export dùng `ResultQueryService.openCurrent(id, view)`. Hàm này kiểm (F09-D4) và mở stream row tách rời trong một bước dưới khoá, trả `CurrentResult(summary, configuration, rows)`.
> - Schema là `configuration.schema().fields()`.
> - `ImportError.stage`/`code` là enum.
>
> Khối code dưới đây giữ nguyên làm lịch sử.

```java
// be-f08 (giống giả định của be-f09)
public interface ResultStore { Stream<RowResult> readRows(UUID sessionId, ResultView view); /* … */ }
public record RowResult(int rowNumber, boolean valid, Map<String, Object> values, List<ImportError> errors) {}
public record ImportError(int rowNumber, String fieldName, String stage, String rule, Integer step,
                          String code, String message, String sourceValue) {}
// be-f09
public ResultSummary ResultQueryService.requireCurrentSummary(UUID sessionId);   // 404 / 409 theo F09-D4
// be-f04: schema hiện tại của session, theo thứ tự `order`
public record TargetField(String name, FieldType type, boolean required, int order) {}
public enum FieldType { STRING, NUMBER, BOOLEAN, DATE, EMAIL }
List<TargetField> currentFields(UUID sessionId);   // trong tasks này gọi là TargetSchemaProvider#currentFields
// be-f02: org.apache.commons:commons-csv đã có trong pom.xml
```

## Goals / Non-Goals

**Goals:**
- File export đúng: đúng tên field, đúng thứ tự, đúng kiểu dữ liệu. File hợp lệ **không bao giờ** chứa row lỗi.
- Stream thẳng từ file ndjson ra response, không gom vào RAM.
- Mọi lỗi có thể biết trước đều được trả dạng ProblemDetail trước khi gửi byte đầu tiên.

**Non-Goals:**
- Export XLSX.
- Chọn cột khi export; export theo bộ lọc.
- Nén file.
- Link tải có thời hạn.
- Báo cáo lỗi dạng JSON.

## Decisions

### F10-D1. Kiểm tra trước, stream sau
- Controller gọi `ExportService.prepare…()`. Hàm này chạy đồng bộ, **trước khi** trả response:
  1. `requireCurrentSummary` (404 hoặc 409);
  2. đọc schema hiện tại;
  3. **mở** stream từ `ResultStore`. Mở thất bại (`UncheckedIOException` hoặc `IOException`) thì ném `DomainException(EXPORT_FAILED)`, và client nhận ProblemDetail 500.
- Hàm trả về `ExportDownload(fileName, contentType, ExportBody)`, trong đó `ExportBody` là functional interface `writeTo(OutputStream)` của tầng application. Tầng này không phụ thuộc kiểu web của Spring.
- ~~Controller bọc `ExportBody` thành `StreamingResponseBody`.~~ *(Đổi sau review)*: controller **ghi đồng bộ** thẳng vào `HttpServletResponse` (xem ghi chú bên dưới).
  - Nếu có lỗi **sau khi** đã gửi byte đầu tiên thì không thể đổi status nữa. Hệ thống ghi log `error` (có `sessionId`, không có dữ liệu), ném lỗi ra để Spring đóng kết nối, và MUST NOT chèn JSON lỗi vào giữa file.
  - FE thấy tải thất bại, vì `fetch` báo lỗi mạng.
- Stream nguồn luôn được đóng trong `finally` của `writeTo`.
- *(Bổ sung khi làm, sửa lại sau review)*:
  - **Vì sao bỏ `StreamingResponseBody`**, cả ba lý do đều đã đo được:
    1. Timeout của request async tính từ lúc `startAsync`, không được gia hạn khi đang ghi. Khi timeout, Spring bỏ qua lỗi vì response đã commit, nên Tomcat kết thúc chunk như bình thường: client nhận **200 với file bị cắt ngắn** mà không hề biết.
    2. Task có thể bị huỷ trước khi chạy (timeout lúc còn trong hàng đợi, client bỏ đi), nên `writeTo` không bao giờ chạy và stream row không được đóng.
    3. Mỗi lần tải chiếm một trong 8 thread của `applicationTaskExecutor`.
  - **Ghi đồng bộ** tránh được cả ba:
    - không có timeout async; client treo thì bị cắt theo write timeout của socket;
    - `ExportDownload` là `AutoCloseable`, controller đóng nó bằng try-with-resources trên mọi nhánh (có test cho trường hợp không bao giờ ghi);
    - dùng thread của Tomcat (200).
  - Bước 1 và 3 là **một** lệnh `ResultQueryService.openCurrent(id, view)`: kiểm và mở row cùng lúc dưới khoá session, rồi nhả khoá.
  - Stream trả về là stream tách rời (xem be-f09): ghi file không giữ khoá, và không bị ảnh hưởng hay chặn bởi lần process hoặc lần đổi config kế tiếp.
  - Lỗi khi đang ghi:
    - **Chưa có byte nào tới client** (response chưa commit, tức phần đã ghi còn trong buffer 8KB): `response.reset()`, gồm cả header `Content-Disposition`, rồi ném `DomainException(EXPORT_FAILED)`. Client nhận `500` problem+json sạch.
    - **Đã commit**: ném lỗi ra. `GlobalExceptionHandler` thấy response đã commit thì ném tiếp cho Tomcat, và Tomcat cắt kết nối (`CLOSE_NOW`). Client thấy tải thất bại (thiếu chunk kết thúc).
    - Trước khi cắt, Tomcat còn **include trang `/error`** vào response đã commit, tức chèn JSON `{"timestamp":…}` vào giữa file. Review đo được điều này; mình đã kiểm lại bằng kiểm ngược.
      - `CommittedErrorPageFilter` (lắp vào `/error`, cho INCLUDE và ERROR) bỏ qua khi response đã commit.
      - Integration test đọc raw socket để kiểm: không có chunk kết thúc, không có JSON lỗi.
    - Client rời đi (`DisconnectedClientHelper`) chỉ ghi log debug.
  - **Giới hạn khi triển khai**: sau một proxy chỉ nói HTTP/1.0 (ví dụ nginx mặc định `proxy_http_version 1.0`), kết nối bị cắt trông giống hệt kết thúc body. Cần `proxy_http_version 1.1`; ghi vào README (F11).
  - Đóng row sau khi đã ghi xong mà lỗi thì chỉ ghi log warn, không làm hỏng một file đã trọn vẹn.
  - Writer JSON chỉ đóng generator khi thành công, vì đóng thì tự thêm `]`.
- *Phương án khác*: ghi toàn bộ ra file tạm rồi mới gửi. Loại, vì tốn đĩa gấp đôi, trong khi D7 đã bảo đảm file nguồn không bị ghi dở.

### F10-D2. Chỉ row hợp lệ vào file valid
- Chỉ đọc `ResultView.VALID`. Ngoài ra còn lọc phòng thủ `row.valid() && row.errors().isEmpty()`. Row vi phạm thì bị bỏ qua, và ghi log `warn` kèm `rowNumber`.
- *(Sửa sau review)*: bản đầu thì chốt chặn này **không bao giờ kích hoạt**, vì `FileResultStore` đặt `valid = (view == VALID)`. Giờ một dòng có `errors` luôn được đọc ra là row lỗi, dù nằm ở file nào. Chốt chặn dựa vào nội dung dòng, độc lập với việc F08 xếp dòng vào file nào (có test).
- Việc bỏ qua này không bao giờ được xảy ra nếu F08 đúng; nó chỉ là chốt chặn cuối cho yêu cầu "Không export invalid rows vào valid output".

### F10-D3. JSON
- Dùng `JsonGenerator` của `JsonMapper` của app, đã bật `WRITE_BIGDECIMAL_AS_PLAIN` (be-f09 task 1, hoặc F08).
- Mảng `[` … `]`. Mỗi row là một object; key đi theo **danh sách field của schema hiện tại**, không theo thứ tự của map, nên kết quả luôn giống nhau.
  - Key không có trong `values` thì ghi `null`.
  - `BigDecimal` thành number; `Boolean` thành `true`/`false`; `String` (gồm ngày `yyyy-MM-dd`) thành string; `null` thành `null`.
- Không có row nào thì ghi `[]`. Không chống formula injection, vì JSON không phải bảng tính.
- `Content-Type: application/json`.

### F10-D4. CSV dữ liệu hợp lệ
- Ghi 3 byte BOM `EF BB BF` trước, rồi dùng `CSVPrinter` với `CSVFormat.RFC4180`: tách dòng CRLF, chỉ quote khi cần.
- Header là tên field theo thứ tự schema. Mỗi ô header cũng đi qua `CsvFormulaGuard.escape`, vì tên field do người dùng đặt (D13 đã mở rộng).
- Chuyển giá trị sang chuỗi:
  - `BigDecimal` qua `toPlainString()`;
  - `Boolean` thành `true`/`false`;
  - chuỗi (gồm ngày) giữ nguyên;
  - `null` thành ô rỗng.
- **Chống formula injection** trên ô dữ liệu chỉ áp cho field kiểu `STRING` và `EMAIL`, qua `CsvFormulaGuard.escape`: thêm tiền tố `'` khi ký tự đầu là `=`, `+`, `-`, `@`, `\t` hoặc `\r`. Field `NUMBER`, `BOOLEAN`, `DATE` đã được validate nên giữ nguyên (ví dụ `-5` không bị đổi).
- `Content-Type: text/csv;charset=UTF-8`.

### F10-D5. Báo cáo lỗi CSV
- Có BOM giống CSV dữ liệu, để Excel hiển thị đúng tiếng Việt trong `sourceValue`. Contract V0.1 chỉ ghi BOM cho CSV dữ liệu; báo cáo lỗi áp theo cùng lý do.
- Header cố định: `rowNumber,fieldName,stage,rule,step,code,message,sourceValue`.
- Mỗi phần tử của `errors[]` là một dòng. Thứ tự: `rowNumber` tăng dần, rồi theo thứ tự lỗi trong row (thứ tự field của schema, do F08 sinh ra).
- `step` là `null` thì để ô rỗng. `sourceValue` là `null` thì để ô rỗng.
- `CsvFormulaGuard` áp cho các cột `fieldName`, `rule`, `message`, `sourceValue` (D13 đã mở rộng). Các cột `rowNumber`, `stage`, `step`, `code` do hệ thống sinh và chỉ nhận một tập giá trị cố định, nên không cần.
- Không có lỗi nào thì chỉ có BOM và header.

### F10-D6. Tên file và `Content-Disposition`
- `ExportFileName.of(originalFileName, suffix)`:
  1. Bỏ **đuôi cuối cùng** (`report.final.xlsx` thành `report.final`).
  2. Thay `"`, `\`, `/` và ký tự điều khiển bằng `_`. *(Sau review)*: gồm mọi ký tự điều khiển (`\p{Cc}`, cả C1) và ký tự định dạng ẩn (`\p{Cf}`, ví dụ bidi override U+202E, có thể dùng để giả đuôi file trên thanh tải về).
     - Tên như `.hidden.csv` sẽ ra `.hidden-valid.csv`, tức một dotfile trên macOS/Linux. Chấp nhận được, vì đúng quy tắc "bỏ đuôi cuối cùng".
  3. Nếu phần tên rỗng thì dùng `export`.
  4. Nối `suffix` vào: `-valid.json`, `-valid.csv` hoặc `-errors.csv`.
- Header được tạo bằng `ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build()`, để có `filename*=UTF-8''<percent-encoded>` theo RFC 5987.

### F10-D7. Tham số `format`
- Bắt buộc có; nhận `json` hoặc `csv`, không phân biệt hoa thường. Thiếu hoặc giá trị khác thì trả 400 `REQUEST_INVALID`, và service không được gọi.

### F10-D8. Timeout của stream
- ~~Đặt `spring.mvc.async.request-timeout: 5m` trong `application.yaml`. Mặc định Tomcat cắt request async sau 30 giây; 5 phút thừa sức cho giới hạn upload 20MB.~~ **Bỏ.**
  - **LÝ DO**: không còn request async (xem F10-D1).
  - Con số 5 phút tính theo kích thước upload, không tính theo lượng byte phải gửi qua mạng: 20MB CSV có thể thành khoảng 130MB JSON, và ở 3 Mbps thì tải mất hơn 5 phút. Timeout kiểu này là cái đã làm file bị cắt ngắn một cách lặng lẽ.

## Risks / Trade-offs

- [Lỗi giữa chừng khi stream không đổi được status] → Kiểm và mở nguồn trước (F10-D1). Lỗi giữa chừng chỉ còn là lỗi IO hiếm gặp, và được ghi log.
- [Tiền tố `'` làm đổi dữ liệu với consumer không phải bảng tính, ví dụ số điện thoại `+84…` trong cột string] → Đây là đánh đổi OWASP đã chấp nhận ở D13. Bản JSON không bị đổi.
- [Tên của F08, F09, F04 khác giả định] → Task 1 đối chiếu trước khi code.
- ~~[Export đang đọc file đúng lúc PUT xoá `result/` trên Windows] → Cùng rủi ro đã ghi ở be-f09; F08 quyết định cách xoá.~~ Đã giải quyết:
  - Stream của `openCurrent` là stream tách rời (hard link được xoá ngay khi mở), nên không chặn việc đổi tên hay xoá `result/`.
  - Export tải lâu cũng không giữ khoá session.

## Migration Plan

- Không có migration DB. Làm trên nhánh `feature/be-f10-export` sau khi be-f09 đã merge.
- Rollback: revert nhánh.

## Open Questions

- ~~Tên field do người dùng đặt lọt vào header của CSV dữ liệu và cột `fieldName` của báo cáo lỗi, nên có thể thành formula khi mở bằng Excel.~~ **Đã chốt** (qua parent, ngày 2026-09-25): D13 đã mở rộng. Escape cả ô header của CSV dữ liệu, và các cột `fieldName`, `rule`, `message`, `sourceValue` của báo cáo lỗi. Đã đưa vào F10-D4, F10-D5, spec `data-export` và task 3.

Không còn câu hỏi mở.
