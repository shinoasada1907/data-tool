## Context

- Nền chung: `openspec/changes/be-f01-import-session/design.md` (D1–D14). Change này cài đặt D9 cho CSV và endpoint preview của contract V0.1.
- F01 đã có:
  - `FileTypeDetector` (đuôi file + magic bytes);
  - `FileStorage` (`save`/`open`/`delete`);
  - `ImportSession` với trạng thái `UPLOADED`;
  - `ImportSessionService.upload` (có dọn file khi lưu DB thất bại);
  - `GlobalExceptionHandler`.

## Goals / Non-Goals

**Goals:**
- Parser trung lập, dùng chung cho CSV (F02) và XLSX (F03).
- Upload đọc hết file một lần: kiểm cấu trúc, lấy header, đếm row.
- Preview đúng contract, không phải đọc hết file.

**Non-Goals:**
- XLSX (F03).
- Tự đoán delimiter, đọc encoding khác UTF-8, tìm header ở dòng khác dòng 1. Tất cả là giới hạn đã biết của V0.1.
- Lưu row vào DB.

## Decisions

### P1. Model nguồn nằm trong package `domain.source`
- `SourceColumn(int index, String name)`
- `SourceSchema(List<SourceColumn> columns, long totalRows, String sheetName)`
- `ImportRow(long rowNumber, List<String> values)` với `value(int index)` trả `null` khi vượt số ô.
- Port `SourceParser`.

Pack chỉ liệt kê `domain.importsession/schema/mapping/transformation/validation`. Thêm `domain.source` vì parser là một mối quan tâm riêng, dùng chung cho mọi loại file. Package này vẫn là Java thuần, nên D1 vẫn đúng.

### P2. `SourceParser` = `inspect` + `read`, thay cho `preview` của pack
```java
public interface SourceParser {
    boolean supports(SourceFileType type);
    SourceSchema inspect(InputStream input);    // đọc hết file; lỗi → DomainException(FILE_EMPTY | FILE_PARSE_ERROR)
    Stream<ImportRow> read(InputStream input);  // chỉ row dữ liệu không trống; caller phải close stream (đóng luôn input)
}
```
- Preview = `read(...).limit(n)`. Không cần hàm `preview(InputStream, PreviewOptions)` riêng.
- `read` tự đọc lại header để biết số cột, nên không phụ thuộc `SourceSchema` đã lưu.
- *Phương án khác*: `read` nhận `SourceSchema`. Loại, vì khi đó parser phụ thuộc trạng thái bên ngoài.

### P3. Giá trị là mảng theo index, không phải map tên→giá trị
Pack mô tả "map column/value". Nhưng hai cột trùng tên sẽ đè nhau (review B3), nên dùng `List<String>` theo `index`. Tên duy nhất do `ColumnNames` sinh; mapping (F05) tham chiếu theo tên rồi đổi sang index.

### P4. `ColumnNames.normalize(List<String> rawHeaders)` trong domain
- Trim từng tên.
- Trống → `Column <chữ cột Excel>`: index 0 → `A`, 25 → `Z`, 26 → `AA`.
- Trùng tên (không phân biệt hoa thường) với tên đã dùng → thêm ` (k)`, với k nhỏ nhất ≥ 2 mà chưa dùng. Tên tự sinh cũng được kiểm trùng.

F03 dùng lại hàm này.

### P5. Luật đọc CSV (`CsvSourceParser`)
- **Format**: `CSVFormat.RFC4180.builder().setIgnoreEmptyLines(false).get()`. Không dùng `setHeader`; header do parser tự đọc để áp P4.
- **Reader UTF-8 strict**: `StandardCharsets.UTF_8.newDecoder()`, `onMalformedInput(REPORT)`, `onUnmappableCharacter(REPORT)`.
- **BOM**: bỏ ký tự `﻿` ở đầu ô header đầu tiên.
- **Số dòng**: `rowNumber = CSVRecord.getRecordNumber()`.
  - Vì `ignoreEmptyLines=false`, dòng trống vẫn là một record (tăng số dòng).
  - Header là record 1, nên số dòng khớp với Excel.
  - Giá trị nhiều dòng trong quote vẫn là một record.
- **Dòng trống** = mọi ô đều `null` hoặc chỉ gồm khoảng trắng. Không trả về, không tính vào `totalRows`, nhưng vẫn giữ số dòng.
- **Header**: row 1 phải có ít nhất một ô không trống. Nếu không, hoặc file không có record nào: `FILE_EMPTY`, message `The first row must contain column headers.`
- **Ô và row**:
  - Ô `""` → `null`. Parser không trim.
  - Row thiếu ô thì bù `null`; row thừa ô thì bỏ phần thừa.
- **Lỗi**:
  - `UncheckedIOException` khi duyệt record → `FILE_PARSE_ERROR`.
  - Nếu nguyên nhân là `CharacterCodingException`: message `File is not valid UTF-8 (near row N).`
  - Còn lại: `CSV syntax error near row N.`
  - N = số dòng của record đọc thành công gần nhất + 1.
  - Message không chứa giá trị ô (D13).

### P6. Upload đọc file
Luồng mới của `ImportSessionService.upload`, sau khi `storage.save(id, …)`:
1. `parser = sourceParsers.find(type)`.
2. Nếu có parser: `schema = parser.inspect(storage.open(id))`. Nếu ném **bất kỳ** `RuntimeException` nào thì `storage.delete(id)` rồi ném lại. Session chưa được lưu, nên không có gì phải dọn trong DB.
3. `session = ImportSession.create(...)`; nếu có `schema` thì `session.markInspected(schema, now)` (→ `CONFIGURING`).
4. `repository.save(session)` (giữ nguyên nhánh dọn file của F01).

Không có parser cho loại file (XLSX trước F03) → session ở `UPLOADED`, như F01.

### P7. Lưu `source_schema`
- **V2**: `ALTER TABLE import_session ADD COLUMN source_schema JSONB;` (nullable; `null` khi `UPLOADED`).
- **Entity**: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_schema") String sourceSchemaJson`.
- **Serialize**: adapter dùng bean `tools.jackson.databind.json.JsonMapper` của Boot (D8), qua record `SourceSchemaDocument(List<ColumnDocument> columns, long totalRows, String sheetName)` trong `infrastructure.persistence`. Định dạng lưu trữ thuộc về infrastructure, không phải domain.

### P8. Preview
- `SourcePreviewService.preview(UUID id, int limit)` trả `SourcePreview` (application record), chưa map sang DTO.
- `limit` được kiểm ở controller bằng `@Min(1) @Max(200)` và `@Validated`. Ngoài khoảng → 400 `REQUEST_INVALID` (qua method validation của Spring).
- Session chưa có `sourceSchema` → 409 `SESSION_STATE_INVALID`, message `Source file has not been inspected.`
- Preview được phép ở mọi trạng thái đã đọc file, kể cả `FAILED`, vì đây là thao tác đọc.
- `columns` và `totalRows` lấy từ `sourceSchema` đã lưu; `rows` đọc lại từ file (`read().limit(limit)`), trong `try-with-resources`.

## Risks / Trade-offs

- [Upload chậm hơn vì phải đọc hết file] → Bị chặn bởi giới hạn 20MB. Đổi lại lỗi hiện ngay ở bước upload.
- [`@JdbcTypeCode(SqlTypes.JSON)` cần FormatMapper JSON của Hibernate; chưa chắc Hibernate 7 tự nhận Jackson 3 (review A5)] → Test persistence ở task 4 sẽ lộ ra ngay. Nếu Hibernate báo thiếu FormatMapper khi khởi động, đổi sang `@ColumnTransformer(write = "?::jsonb") @Column(columnDefinition = "jsonb") String`, và ghi LÝ DO trong tasks.md.
- [Thông điệp lỗi CSV chỉ nêu "near row N", có thể lệch một dòng khi lỗi quote kéo dài nhiều dòng] → Chấp nhận. FE chỉ hiển thị.

## Migration Plan

- Flyway V2 chỉ thêm một cột nullable, không phải backfill.
- Session tạo trước F02 ở trạng thái `UPLOADED` và không có `source_schema`, nên preview trả 409. Chỉ xảy ra với dữ liệu dev, chấp nhận.

## Open Questions

(không có)
