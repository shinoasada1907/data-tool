## Context

- Thiết kế nền: `openspec/changes/core-01-toolbox-restructure/design.md`, các mục TD5 (mô hình bảng), TD6 (tuỳ chọn đọc), TD7 (typing), TD8 (writer), TD11 (KeyIndex). File này chỉ ghi chi tiết cài đặt. Quyết định mang mã `IO1`…`IO12`.
- Change này đi sau core-01: package đã là `com.universaldatatools`, và `core.format` không còn Spring.
- Hiện trạng:
  - `CsvSourceParser` (commons-csv, UTF-8 chặt, dấu phẩy) và `XlsxSourceParser` (fastexcel-reader, sheet hiển thị đầu tiên, `XlsxCellValues` chuyển ô thành chữ) đã đúng spec `source-parsing`.
  - Exporter của Importer (`CsvValidRowsExporter`, `JsonValidRowsExporter`, `CsvErrorReportExporter`) đã đúng spec `data-export`.

## Goals / Non-Goals

**Goals:**
- Dựng một lớp đọc/ghi dùng chung, đủ cho Converter, Validator, Cleaner và Diff, mà Importer không đổi hành vi.
- Đọc stream, RAM không phụ thuộc số row. Chỉ `KeyIndex` là tăng theo số row, và nó có trần.

**Non-Goals:**
- API (dataset, preview) thuộc core-04.
- JSON lồng nhau: để stage sau (N9).
- Đọc `.xls`, `.ods`, `.tsv`, `.ndjson`.
- Ghi CSV với encoding khác UTF-8. Chỉ có tuỳ chọn BOM.

## Decisions

### IO1. Contract đọc

```java
package com.universaldatatools.core.table;

public enum DataFormat { CSV, XLSX, JSON }
public enum Delimiter { COMMA(','), SEMICOLON(';'), TAB('\t'), PIPE('|'); public char symbol(); }
public enum TextEncoding { UTF_8("UTF-8"), UTF_16("UTF-16"), WINDOWS_1258("windows-1258"), WINDOWS_1252("windows-1252");
                           public String apiName();   // "UTF-8", "UTF-16", "WINDOWS-1258", "WINDOWS-1252"
                           public Charset charset(); }
public record ReadOptions(String sheet, Delimiter delimiter, TextEncoding encoding, Boolean hasHeader, ReadLimits limits) {
    public static ReadOptions defaults();                 // mọi thứ null = tự nhận, limits = NONE
}
public record ReadLimits(long maxRows, int maxColumns, int maxCellLength) {   // 0 = không giới hạn
    public static final ReadLimits NONE;
}
public record ResolvedReadOptions(String sheet, Delimiter delimiter, TextEncoding encoding, boolean hasHeader) {}
public record SheetInfo(String name, boolean visible) {}
public enum CellKind { TEXT, NUMBER, BOOLEAN, DATE }
public final class CellKinds { /* byte[] gọn; of(CellKind...), get(i) → null ngoài phạm vi */ }
public record Column(int index, String name) {}
public record Row(long number, List<String> cells, CellKinds kinds) {        // kinds null: nguồn không có kiểu
    public String cell(int index); public CellKind kind(int index);
}
public enum InferredType { STRING, NUMBER, BOOLEAN, DATE, EMAIL, EMPTY;  public String apiName(); /* chữ thường */ }
public record ColumnProfile(InferredType inferredType, long emptyCount, int maxLength) {}
public record TableInfo(DataFormat format, ResolvedReadOptions options, Set<String> autoDetected,
                        String sheetName, List<SheetInfo> sheets, List<Column> columns,
                        List<ColumnProfile> profiles, long rowCount, long blankRowsSkipped) {
    public static TableInfo forRead(DataFormat format, ResolvedReadOptions options, List<Column> columns);  // cho người gọi đã biết cột
}
public interface TableReader {
    DataFormat format();
    List<SheetInfo> sheets(InputStream in);               // CSV/JSON: List.of()
    TableInfo inspect(InputStream in, ReadOptions options);
    Stream<Row> read(InputStream in, TableInfo info);    // dùng options và cột đã resolve; đóng stream thì đóng in
}
```

- `read` nhận `TableInfo` thay cho `ReadOptions`, vì hai lẽ:
  - không phải tự nhận lại delimiter hay encoding;
  - JSON cần biết trước tập cột (hợp các key), mà tập này chỉ có sau một lượt đọc toàn file.
- Hai lượt đọc (inspect rồi read) là chấp nhận được: file tối đa 20MB, và bảo đảm được "kiểm xong trước byte đầu tiên" (TD8).
- `SourceParser` và `SourceSchema` cũ không còn trong core. Importer có record riêng `tools.importer.domain.importsession.SourceSchema(columns, totalRows, sheetName)`, tạo từ `TableInfo`. Định dạng jsonb lưu trong DB không đổi.

### IO2. CSV: encoding

Chạy trên toàn file ở `inspect`:

1. `encoding` được chỉ định:
   - Decode chặt (`CodingErrorAction.REPORT` qua U+FFFD như V0.1 để còn nêu số dòng).
   - Byte sai → `FILE_PARSE_ERROR`:
     - với UTF-8 chỉ định rõ, `detail` giữ đúng chuỗi V0.1 `File is not valid UTF-8 (near row N).`;
     - với encoding khác: `File is not valid <API name> (near row N).`
   - `windows-1258`/`1252` gần như không bao giờ sai byte. Đó là lý do không tự đoán chúng.
   - `UTF-16` cần có BOM. Không có BOM → `FILE_PARSE_ERROR` với `detail` `UTF-16 file must start with a byte order mark.`
2. `encoding` bỏ trống:
   - BOM `EF BB BF` → UTF-8.
   - BOM `FF FE` / `FE FF` → UTF-16.
   - Không có BOM → decode UTF-8 chặt. Byte sai → `FILE_PARSE_ERROR` với `detail` `File is not valid UTF-8 (near row N). Choose the file's encoding.`
   - Trừ trường hợp có BOM, `autoDetected` luôn chứa `"encoding"`.
- BOM luôn bị bỏ khỏi dữ liệu.

### IO3. CSV: delimiter

- Chỉ định thì dùng.
- Bỏ trống thì tự nhận trên **tối đa 50 record đầu hoặc 64 KB đầu**, cái nào tới trước. Với mỗi ứng viên theo thứ tự `COMMA`, `SEMICOLON`, `TAB`, `PIPE`:
  1. Parse mẫu bằng commons-csv (RFC 4180, quote `"`) với delimiter đó. Bỏ qua record trống.
  2. `mode` = số cột xuất hiện nhiều nhất; `consistency` = tỉ lệ record có đúng `mode` cột.
  3. Ứng viên hợp lệ khi `mode > 1`. Parse lỗi (quote hỏng) → ứng viên không hợp lệ.
- Chọn ứng viên hợp lệ có `consistency` cao nhất. Hoà thì chọn `mode` lớn hơn, rồi tới thứ tự ở trên. Không ứng viên nào hợp lệ → `COMMA` (file một cột).
- `autoDetected` chứa `"delimiter"`.
- Lý do chọn luật này: đơn giản, tất định, xử lý đúng trường hợp dấu phân cách nằm trong quote. File `;` từ Excel locale VN được nhận đúng.

### IO4. CSV và XLSX: header, row trống, đánh số

- `hasHeader` mặc định `true`, giữ luật V0.1: dòng 1 là header, tên qua `ColumnNames.normalize`, row nhận `number` là số dòng như Excel.
- `hasHeader=false`:
  - Số cột bằng số ô lớn nhất ở dòng đầu tiên khác trống. Dòng về sau dài hơn thì bị cắt phần thừa, như luật V0.1.
  - Tên cột `Column A`, `Column B`…
  - Row đầu tiên có `number` 1.
- Row trống (mọi ô `null` hoặc chỉ khoảng trắng) bị bỏ, vẫn giữ chỗ trong cách đánh số, và được đếm vào `blankRowsSkipped`.
- File không có header (khi `hasHeader=true`) hoặc không có dòng nào → `FILE_EMPTY`, như V0.1.

### IO5. XLSX: sheet và kiểu ô

- `sheets(in)` trả mọi sheet theo thứ tự trong workbook, kèm `visible`, là `false` khi sheet `hidden` hoặc `veryHidden`.
- `sheet` bỏ trống → sheet hiển thị đầu tiên, như V0.1. Chỉ định tên → khớp **chính xác** tên, được chọn cả sheet ẩn. Không có sheet đó → `CONFIG_INVALID` với `detail` `Sheet "<tên>" does not exist.`
- Chữ của ô theo luật "Chuyển giá trị ô XLSX thành chuỗi" hiện hành (`XlsxCellValues`). Kiểu gốc gán thêm:

| Ô | `CellKind` |
|---|---|
| số không có format ngày/giờ | `NUMBER` |
| số có format ngày (`yyyy-MM-dd` hoặc `…'T'HH:mm:ss`) | `DATE` |
| số có format chỉ giờ (`HH:mm:ss`) | `TEXT`. Chưa có kiểu giờ; khi ghi XLSX sẽ ra ô chữ |
| boolean | `BOOLEAN`, chữ `TRUE`/`FALSE` như V0.1 |
| chuỗi | `TEXT` |
| lỗi (`#N/A`…) | `TEXT` |
| công thức | kiểu của giá trị đã cache, theo các dòng trên |
| rỗng | `null` (ô `null`) |

- Zip guard (`XlsxZipGuard`) chạy trước mọi lần đọc, như V0.1.

### IO6. JSON: mảng object phẳng

- Dùng Jackson 3 streaming (`tools.jackson.core.JsonParser`), không dựng cây. `StreamReadConstraints`: `maxNumberLength` 1 000, `maxStringLength` 1 000 000, `maxNestingDepth` 16. Độ sâu lớn hơn 2 thì luật của ta đã báo `JSON_NOT_FLAT` từ trước.
- Chỉ nhận UTF-8, có thể có BOM. `encoding` và `delimiter` của `ReadOptions` bị bỏ qua. `hasHeader` bị bỏ qua.
- Token đầu tiên phải là `START_ARRAY`. Không phải → `FILE_PARSE_ERROR` với `detail` `JSON must be an array of objects.`
- Mỗi phần tử phải là `START_OBJECT`. Không phải → `FILE_PARSE_ERROR` với `detail` `Element N of the array is not an object.`
- Giá trị trong object:

| Token | Chữ của ô | `CellKind` |
|---|---|---|
| string | nguyên văn | `TEXT` |
| number | **text gốc của token** (`12.50`, `1e3`, `-0`) | `NUMBER` |
| `true`/`false` | `true`/`false` | `BOOLEAN` |
| `null` | `null` | `null` |
| `{` hoặc `[` | — | `JSON_NOT_FLAT`, `detail` `Nested value at row N, key "k" is not supported yet.` |

- Key trùng trong cùng một object → `FILE_PARSE_ERROR` với `detail` `Duplicate key "k" at row N.`
- Row thứ N (N từ 1) là phần tử thứ N. **Không bỏ object rỗng**: `{}` là một row có mọi ô `null`. `blankRowsSkipped` luôn là 0.
- Cột:
  - `inspect` gom key theo thứ tự gặp lần đầu, rồi tên cột qua `ColumnNames.normalize`. Ví dụ `Email` và `email` thành `Email`, `email (2)`.
  - Map từ key gốc sang index được giữ trong `TableInfo` (trường ẩn `jsonKeys`) để `read` dùng.
- Mảng rỗng `[]` → `FILE_EMPTY` với `detail` `JSON array is empty.`
- JSON sai cú pháp → `FILE_PARSE_ERROR` với `detail` `Invalid JSON near row N.` Không kèm message của Jackson, vì message đó có thể trích nội dung file.

### IO7. Profile cột

Tính ngay trong `inspect`, O(1) RAM cho mỗi cột.

- `emptyCount`: số ô `null` hoặc chỉ khoảng trắng.
- `maxLength`: số code point lớn nhất của một ô.
- `inferredType`:
  - Nguồn có kiểu (JSON, XLSX):
    - mọi ô khác rỗng có cùng `CellKind`: `NUMBER` → `number`, `BOOLEAN` → `boolean`, `DATE` → `date`;
    - mọi ô là `TEXT` → áp luật chữ bên dưới **chỉ cho** `email`/`string`;
    - trộn kiểu → `string`.
  - CSV: xét chữ, không trim. Có 4 ứng viên, loại dần:

    | Ứng viên | Luật |
    |---|---|
    | boolean | `true`/`false`, không phân biệt hoa thường |
    | number | `-?(0\|[1-9][0-9]{0,14})(\.[0-9]+)?` |
    | date | ISO `uuuu-MM-dd`, strict |
    | email | `EmailAddresses.isValid` |

    Kết quả là ứng viên đầu tiên còn lại theo thứ tự boolean, number, date, email; không còn ứng viên nào thì `string`.
  - Cột không có ô khác rỗng nào → `empty`.

### IO8. Giới hạn đọc

- Kiểm trong lúc `inspect`. Vượt thì dừng ngay với `LIMIT_EXCEEDED`, không đọc tiếp.

| Giới hạn | `detail` |
|---|---|
| số row | `File has more than 500000 rows.` |
| số cột | `File has more than 1000 columns.` |
| độ dài ô | `Value at row N, column "c" is longer than 32767 characters.` |

- `read` không kiểm lại, vì file không đổi giữa hai lượt đọc.
- Importer truyền `ReadLimits.NONE`, nên spec `source-parsing` không đổi.

### IO9. Contract ghi và typing

```java
public enum Typing { PRESERVE, STRING, INFER }
public record TypedCell(String text, CellKind kind) {}                 // text null = ô rỗng
public record OutputColumn(String name, ColumnProfile profile) {}     // profile null khi không có
public record WriteOptions(Delimiter delimiter, boolean header, boolean bom, boolean formulaGuard,
                           boolean pretty, Typing typing, String sheetName) {
    public static WriteOptions csvDefaults(); public static WriteOptions jsonDefaults(); public static WriteOptions xlsxDefaults();
}
public interface TableWriter {
    DataFormat format(); String contentType(); String extension();
    RowSink open(OutputStream out, List<OutputColumn> columns, WriteOptions options) throws IOException;
}
public interface RowSink extends AutoCloseable {
    void write(List<TypedCell> cells) throws IOException;   // cells.size() == columns.size()
    @Override void close() throws IOException;              // hoàn tất file (ví dụ ']' của JSON)
    void abort();                                           // bỏ dở: không ghi phần kết thúc
}
```

**Kiểu thật của ô** (`CellTyping.resolve(cell, typing, profile)`):

| Typing | Ô có `kind` | Ô không có `kind` (CSV) |
|---|---|---|
| `STRING` | `TEXT` | `TEXT` |
| `PRESERVE` | chính `kind` | `TEXT` |
| `INFER` | chính `kind` | `profile.inferredType` nếu là `number`/`boolean`/`date` **và** chữ của ô khớp luật IO7 của kiểu đó; còn lại `TEXT` |

**Ghi theo kiểu thật:**

| Kiểu thật | JSON | XLSX | CSV |
|---|---|---|---|
| `TEXT` | string | ô chữ | chữ; formula guard nếu bật |
| `NUMBER` | literal số, **đúng chữ của ô** nếu là số JSON hợp lệ, không thì string | ô số nếu chữ parse được `BigDecimal` có ≤ 15 chữ số có nghĩa; không thì ô chữ | chữ, không guard |
| `BOOLEAN` | `true`/`false` (không phân biệt hoa thường; `1`/`0` từ Validator cũng nhận) | ô boolean | chữ, không guard |
| `DATE` | string (`yyyy-MM-dd` hoặc `…THH:mm:ss`) | ô ngày, format `yyyy-mm-dd` hoặc `yyyy-mm-dd hh:mm:ss` | chữ, không guard |
| ô `null` | `null` | để trống | trường rỗng |

- **CSV**:
  - commons-csv `RFC4180`: quote tối thiểu, xuống dòng `\r\n`.
  - BOM `EF BB BF` khi `bom=true`.
  - Header là tên cột, được guard khi `formulaGuard=true`.
  - Formula guard theo đúng luật spec `data-export`: prefix `'` cho giá trị bắt đầu bằng `=`, `+`, `-`, `@`, tab, CR.
- **JSON**:
  - `[`, rồi mỗi row một object theo thứ tự cột, rồi `]`. UTF-8, không BOM.
  - `pretty=true` dùng `DefaultPrettyPrinter` của Jackson.
  - `abort()` không ghi `]`, nên file dở không phải JSON hợp lệ.
- **XLSX**:
  - fastexcel `Workbook` → một `Worksheet`, gọi `flush()` sau mỗi 1 000 row để RAM thấp.
  - Header là ô chữ ở dòng 1.
  - Tên sheet: `sheetName`, hoặc `Sheet1` khi trống. Tên được làm sạch: `[]:*?/\` thay bằng `_`, cắt còn 31 ký tự, không để trống.
  - Hơn 1 048 575 row dữ liệu hoặc ô dài hơn 32 767 ký tự → `IllegalStateException`. Người gọi phải kiểm trước bằng profile (TD8), nên đây chỉ là lưới an toàn.
  - `abort()` không `finish()` workbook, nên file dở không mở được. Như vậy là đúng ý.

### IO10. Importer chạy trên lớp mới, không đổi hành vi

**Đọc:**
- `SourceParsers` → `TableReaders` (application Importer), chỉ giữ reader CSV và XLSX.
- Upload gọi `inspect` với `ImporterReadOptions.V0_1 = (sheet null, COMMA, UTF_8, hasHeader true, ReadLimits.NONE)`.
- Pipeline gọi `read` với `TableInfo.forRead(...)`, dựng từ `SourceSchema` đã lưu.
- `.json` upload vào Importer vẫn là `415 FILE_UNSUPPORTED`. `FileTypeDetector` nhận tập định dạng cho phép làm tham số.

**Ghi:**
- `CsvValidRowsExporter`, `JsonValidRowsExporter` và `CsvErrorReportExporter` thành adapter:
  1. Chuyển `RowResult` sang `TypedCell`: `BigDecimal` → `(toPlainString, NUMBER)`, `Boolean` → `BOOLEAN`, `LocalDate` → `DATE`, `String` → `TEXT`.
  2. Gọi `TableWriter` với option tương ứng:
     - CSV: COMMA, header, BOM, guard;
     - JSON: compact, `PRESERVE`.
- Formula guard V0.1 chỉ áp cho field `string`/`email` và các cột chữ của báo cáo lỗi. Điều đó trùng với luật "chỉ guard ô `TEXT`", vì mọi cột khác của báo cáo lỗi đều là số hoặc enum.

**Golden test:** chụp output hiện tại (trước khi đổi) cho 3 fixture: `customers.csv` e2e, một XLSX có đủ kiểu, và một file có row lỗi chứa `=cmd()`. Sau khi đổi, file ra phải giống **từng byte**.

### IO11. `KeyHasher` và `KeyIndex`

```java
public record Hash128(long high, long low) {}
public final class KeyHasher {                 // không thread-safe; mỗi lượt chạy một instance
    public Hash128 hash(List<String> parts);   // SHA-256 của [len(u32) + UTF-8] mỗi phần; null → len = 0xFFFFFFFF; lấy 16 byte đầu
}
public final class KeyIndex<V> {               // HashMap<Hash128, V> + đếm
    public V putIfAbsent(Hash128 key, V value); public V get(Hash128 key); public int size();
}
```

- Mã hoá có tiền tố độ dài, nên `["ab","c"]` và `["a","bc"]` không trùng hash. `null` khác chuỗi rỗng.
- `MessageDigest` được tạo một lần mỗi instance và `reset()` mỗi lần hash.
- Chuẩn hoá (NFC, trim, lowercase…) là việc của người gọi, **trước khi** hash.

### IO12. Mã lỗi mới

- `ErrorCode` (vẫn là enum tới core-04) thêm `JSON_NOT_FLAT` và `LIMIT_EXCEEDED`. `ErrorHttpStatus` ánh xạ cả hai sang 422.
- Spec `api-errors` chưa đổi, vì chưa endpoint nào trả hai mã này. core-04 thêm chúng vào bảng mã khi `/api/datasets` ra đời.

## Risks / Trade-offs

- **Hai lượt đọc** (inspect rồi read) tốn gấp đôi CPU so với một lượt. File tối đa 20MB, và tool nào cũng cần inspect trước để báo lỗi trước byte đầu tiên. Cache inspect của core-04 bỏ được lượt đầu khi người dùng vừa preview xong.
- **Tự nhận delimiter** có thể chọn sai với file lạ (ví dụ một cột chữ có nhiều dấu phẩy mà không quote). Preview trả `options` và `autoDetected`, và người dùng chọn lại được.
- **Chữ của ô số XLSX** đã làm tròn về 15 chữ số có nghĩa (luật V0.1), nên XLSX→XLSX có thể đổi phần đuôi của số có hơn 15 chữ số. Excel cũng chỉ hiển thị 15 chữ số, nên chấp nhận.
- **Golden test phụ thuộc thư viện**: nếu nâng commons-csv hay Jackson mà output đổi, golden test sẽ đỏ. Đó chính là việc nó phải làm.

## Migration Plan

Không có migration DB. Thứ tự làm (theo tasks.md):
1. Đổi tên model.
2. Golden test của Importer, chụp trên code cũ.
3. Reader CSV mới, rồi XLSX, rồi JSON.
4. Profile và giới hạn.
5. Writer.
6. Chuyển Importer sang lớp mới; golden test phải xanh.
7. KeyIndex.

Rollback: revert nhánh.

## Open Questions

(không có)
