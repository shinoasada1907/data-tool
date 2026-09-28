# CORE-02 Đọc/ghi dataset CSV · XLSX · JSON — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** `core.table` có mô hình bảng trung lập, `TableReader` cho CSV, XLSX và JSON có tuỳ chọn, profile và giới hạn, `TableWriter` cho CSV, JSON và XLSX có typing, cùng `KeyIndex`. Importer chạy trên lớp mới mà output giống hệt từng byte.

**Architecture:** design.md IO1–IO12. Nền là core-01 TD5–TD8, TD11.

**Tech Stack:** Như core-01. `org.dhatim:fastexcel` 0.20.2 chuyển sang scope `compile`.

**Spec:** `specs/dataset-io/spec.md`.

## Global Constraints

- core-01 đã merge vào `dev`. Nhánh `feature/core-02-dataset-io` tạo từ `dev`, làm trong worktree `D:/Code/Product/universal-importer-be`.
- `MAIN` = `apps/api/src/main/java/com/universaldatatools`, `TEST` = `apps/api/src/test/java/com/universaldatatools`.
- `core.table` chỉ dùng JDK. `core.format` được dùng thêm commons-csv, fastexcel, Jackson 3. `ArchitectureTest` phải xanh sau mỗi task.
- Message lỗi không chứa giá trị ô (D13), trừ tên cột và tên sheet do người dùng đặt.
- Fixture nhị phân (XLSX) sinh bằng `XlsxFixtures` (fastexcel writer) ngay trong test, không commit file nhị phân mới. Trừ file golden ở task 2.

---

## 1. Đổi tên model và thêm kiểu gốc của ô

**Files:**
- Modify (đổi tên bằng IntelliJ *Rename*, hoặc OpenRewrite `ChangeType` như core-01):
  - `core.table.ImportRow` → `core.table.Row`
  - `core.table.SourceColumn` → `core.table.Column`
  - `core.table.SourceFileType` → `core.table.DataFormat` (thêm `JSON`)
  - `core.table.SourceSchema` → `tools.importer.domain.importsession.SourceSchema`
- Create: `MAIN/core/table/CellKind.java`, `CellKinds.java`
- Test: `TEST/core/table/CellKindsTest.java`, `TEST/core/table/RowTest.java` (đổi từ `ImportRowTest`)

**Interfaces:**
- Produces: `CellKind`, `CellKinds`; `Row(long number, List<String> cells, CellKinds kinds)` với `cell(i)`, `kind(i)`. Constructor phụ `Row(long, List<String>)` đặt `kinds = null`, để code Importer không phải đổi.

- [x] 1.1 Viết `CellKindsTest`:
  - `CellKinds.of(TEXT, null, NUMBER)`: `get(0)` → `TEXT`, `get(1)` → `null`, `get(2)` → `NUMBER`, `get(5)` → `null`.
  - Hai `CellKinds` cùng nội dung thì `equals` và `hashCode` bằng nhau.
- [x] 1.2 Viết `RowTest`, giữ các case của `ImportRowTest`, thêm:
  - `new Row(2, List.of("a"), CellKinds.of(TEXT)).kind(0)` → `TEXT`;
  - `new Row(2, List.of("a")).kind(0)` → `null`.
- [x] 1.3 Chạy `./mvnw -q test -Dtest=CellKindsTest,RowTest`. Mong đợi: FAIL (compile).
- [x] 1.4 Đổi tên 4 lớp như phần Files. Tạo `CellKind` và `CellKinds` (mảng byte, `-1` là `null`). `DataFormat` có `CSV`, `XLSX`, `JSON`. Cột `file_type` của Importer vẫn chỉ ghi `CSV`/`XLSX`.
- [x] 1.5 Chạy `./mvnw -q test`. Mong đợi: PASS toàn bộ.
- [x] 1.6 Commit: `refactor(core): neutral table model Row/Column/DataFormat with cell kinds`
  - ~~Đổi tên `SourceSchema` → `tools.importer.domain.importsession.SourceSchema` ngay ở task này.~~ **LÝ DO:** `SourceParser` (core) còn trả `SourceSchema`, nên chuyển sớm làm core phụ thuộc tool (ArchitectureTest đỏ). `SourceSchema` ở lại `core.table` tới task 13, lúc `SourceParser` bị xoá.
  - Làm khác: `Row` giữ tên accessor cũ `rowNumber()`, `values()`, `value(i)` (thay cho `number`/`cells`/`cell` trong design IO1), thêm `kinds()`/`kind(i)`. Tránh sửa hàng trăm chỗ gọi mà nghĩa không đổi.
  - Làm thêm: thêm `JSON` vào `DataFormat` làm enum `fileType` trong OpenAPI của Importer có thêm `JSON`, khiến snapshot hợp đồng đỏ. Đã ghim `@Schema(allowableValues = {"CSV", "XLSX"})` trên `ImportSessionDto.fileType` và `SourcePreviewDto.fileType`; contract Importer giữ nguyên.
  - Commit `5cfab6f` lỡ chứa trạng thái test đỏ (do lọc output bằng grep mà không kiểm exit code); đã sửa ở commit kế tiếp. Từ đây luôn kiểm exit code của Maven trước khi commit.

## 2. Golden file cho export của Importer (chụp trên code hiện tại)

**Files:**
- Create: `TEST/tools/importer/api/export/ExportGoldenIntegrationTest.java`
- Create: `src/test/resources/golden/importer/{customers-valid.json, customers-valid.csv, customers-errors.csv, types-valid.json, types-valid.csv, formula-errors.csv}`

- [x] 2.1 Viết `ExportGoldenIntegrationTest`. Chạy 3 luồng qua API thật (MockMvc + Testcontainers):
  1. **customers**: `fixtures/e2e/customers.csv`, cấu hình như `ImportFlowIntegrationTest`. Tải `export?format=json`, `export?format=csv`, `errors/export`.
  2. **types**: XLSX sinh bằng `XlsxFixtures`. Cột `n` (số `12.50`), `b` (boolean), `d` (ngày), `s` (chuỗi `=cmd()`), `e` (email). Schema có đủ 5 kiểu, row nào cũng hợp lệ. Tải JSON và CSV.
  3. **formula**: CSV có một row lỗi, `sourceValue` là `+84901234567` và tên field là `=cmd()`. Tải `errors/export`.

  So byte của mỗi file với file golden. Khi có `-Dgolden.write=true` thì ghi file golden rồi fail với message `golden written`.
- [x] 2.2 Chạy `./mvnw -q test -Dtest=ExportGoldenIntegrationTest -Dgolden.write=true`. Mong đợi: FAIL `golden written`. Mở các file ra kiểm bằng mắt: có BOM ở CSV, dấu `'` trước `=cmd()`, số `12.50` giữ scale.
- [x] 2.3 Chạy lại, không có cờ. Mong đợi: PASS.
- [x] 2.4 Commit: `test(importer): golden files for exports before moving them onto TableWriter`
  - Làm khác: luồng "types" dùng XLSX e2e có sẵn (`XlsxFixtures.e2eCustomers`, đủ 5 kiểu) thay vì fixture mới; luồng "formula" có field `=cmd()`, `sourceValue` `+84901234567` và hằng `12.50`. File golden được đánh dấu `binary` trong `.gitattributes` để git không đổi xuống dòng.

## 3. Contract đọc và `CsvTableReader`: encoding

**Files:**
- Create: `MAIN/core/table/{Delimiter, TextEncoding, ReadOptions, ReadLimits, ResolvedReadOptions, SheetInfo, TableInfo, TableReader, ColumnProfile, InferredType}.java`
- Create: `MAIN/core/format/csv/CsvTableReader.java` (logic lấy từ `CsvSourceParser`; `CsvSourceParser` bị xoá ở task 13)
- Test: `TEST/core/format/csv/CsvTableReaderEncodingTest.java`

**Interfaces:** như design IO1. Ở task này `ColumnProfile` chỉ là record; việc tính profile làm ở task 7.

- [x] 3.1 Viết `CsvTableReaderEncodingTest`. `info = inspect(bytes, options)`, `rows = read(bytes, info)` gom thành list.

  | Input (byte) | `encoding` | Mong đợi |
  |---|---|---|
  | `a,b\n1,2\n` (UTF-8) | null | cột `a`,`b`; row 2 `["1","2"]`; `options.encoding=UTF_8`; `autoDetected` ⊇ `{"encoding"}` |
  | `EF BB BF` + `name\nAn\n` | null | cột `name` (không dính BOM); `autoDetected` không có `encoding` |
  | `FF FE` + UTF-16LE của `a,b\n1,2\n` | null | cột `a`,`b`; row 2 `["1","2"]`; `encoding=UTF_16` |
  | `FE FF` + UTF-16BE của `a\nx\n` | `UTF_16` | cột `a`; row 2 `["x"]` |
  | UTF-16LE **không** BOM | `UTF_16` | `DomainException(FILE_PARSE_ERROR)`, detail `UTF-16 file must start with a byte order mark.` |
  | `ten\n` + windows-1258 của `Nguyễn\n` | `WINDOWS_1258` | row 2 `["Nguyễn"]` (so sau `Normalizer.normalize(…, NFC)`) |
  | `a,b\n1,2\n` + dòng 3 có `C3 28` | null | `FILE_PARSE_ERROR`, detail `File is not valid UTF-8 (near row 3). Choose the file's encoding.` |
  | như trên | `UTF_8` | `FILE_PARSE_ERROR`, detail `File is not valid UTF-8 (near row 3).` (giữ chuỗi V0.1) |
  | `café\n` windows-1252 (`63 61 66 E9`) | `WINDOWS_1252` | cột `café` |
- [x] 3.2 Chạy test. Mong đợi: FAIL.
- [x] 3.3 Cài đặt contract và phần encoding của `CsvTableReader`. Delimiter tạm thời cố định COMMA; `hasHeader` luôn true (task 4 làm tiếp).
- [x] 3.4 Chạy test. Mong đợi: PASS.
- [x] 3.5 Commit: `feat(core): TableReader contract; CSV encodings (auto BOM/UTF-8, UTF-16, windows-1258/1252)`
  - Làm khác: gộp task 3, 4, 7, 8 (và 15) thành một vòng cho CSV. Profile và giới hạn là phần dùng chung của cả ba reader, nên dựng một lần: `TableScan` (đếm row/row trống, giới hạn, profile) + `ProfileBuilder`, rồi các reader gọi vào. Test tương ứng nằm trong `CsvTableReaderTest`, `DelimiterDetectorTest`, `ProfileBuilderTest`.
  - Fixture windows-1258: file thật lưu `ễ` thành `ê` + dấu ngã kết hợp (U+0303), vì bảng mã này không có `ễ` dựng sẵn.

## 4. CSV: tự nhận delimiter và `hasHeader`

**Files:**
- Modify: `MAIN/core/format/csv/CsvTableReader.java`
- Create: `MAIN/core/format/csv/DelimiterDetector.java`
- Test: `TEST/core/format/csv/DelimiterDetectorTest.java`, `TEST/core/format/csv/CsvTableReaderOptionsTest.java`

- [x] 4.1 Viết `DelimiterDetectorTest`, gọi `detect(String sample)`:

  | Sample | Mong đợi | Vì sao |
  |---|---|---|
  | `ma;ten;gia\n1;Bút;5000\n2;Vở;12000\n` | `SEMICOLON` | |
  | `name,note\n"An","a; b; c"\n"Binh","x"\n` | `COMMA` | `;` nằm trong quote |
  | `a\tb\n1\t2\n` | `TAB` | |
  | `a\|b\|c\n1\|2\|3\n` | `PIPE` | |
  | `email\nan@x.com\n` | `COMMA` | không ứng viên nào > 1 cột |
  | `a,b;c\n1,2;3\n4,5;6\n` | `COMMA` | COMMA và SEMICOLON đều `mode` 2, consistency 1.0; hoà thì COMMA đứng trước |
  | `a;b\n1;2\nx,y,z,w\n` | `SEMICOLON` | COMMA có `mode` 1 nên không hợp lệ; SEMICOLON `mode` 2, consistency 2/3 |
  | `"a,b";c\n"1,2";3\n` | `SEMICOLON` | dấu phẩy nằm trong quote |
  | 60 record `x;y`, record thứ 55 là `p,q,r,s` | `SEMICOLON` | chỉ xét 50 record đầu |
- [x] 4.2 Viết `CsvTableReaderOptionsTest`:
  - delimiter bỏ trống với `ma;ten\n1;A\n` → `options.delimiter=SEMICOLON`, `autoDetected` ⊇ `{"delimiter"}`;
  - delimiter `SEMICOLON` chỉ định với `a,b\n1,2\n` → một cột `a,b`, `autoDetected` không có `delimiter`;
  - `hasHeader=false` với `1,An\n2,Binh\n` → cột `Column A`, `Column B`; row đầu có số dòng `1`, giá trị `["1","An"]`;
  - `hasHeader=false`, dòng 1 trống, dòng 2 là `x,y,z` → 3 cột; row đầu có số dòng `2`.
- [x] 4.3 Chạy 2 test. Mong đợi: FAIL.
- [x] 4.4 Cài `DelimiterDetector` theo IO3. Sample lấy tối đa 64 KB đã decode. Cài `hasHeader` theo IO4.
- [x] 4.5 Chạy lại. Mong đợi: PASS. Chạy thêm `CsvSourceParserTest` (vẫn còn): PASS.
- [x] 4.6 Commit: `feat(core): CSV delimiter detection and header-less files`

## 5. `XlsxTableReader`: danh sách sheet, chọn sheet, kiểu ô

**Files:**
- Create: `MAIN/core/format/xlsx/XlsxTableReader.java` (logic lấy từ `XlsxSourceParser`)
- Modify: `MAIN/core/format/xlsx/XlsxCellValues.java` (trả thêm `CellKind`)
- Test: `TEST/core/format/xlsx/XlsxTableReaderTest.java`

- [x] 5.1 Viết `XlsxTableReaderTest`, workbook sinh bằng `XlsxFixtures`:
  - `sheets()` của workbook `[Hidden(hidden), Data, Prices]` → `[{Hidden,false},{Data,true},{Prices,true}]`;
  - không chọn sheet → đọc `Data`;
  - `sheet="Prices"` → cột của `Prices`, `sheetName="Prices"`;
  - `sheet="Hidden"` → đọc được sheet ẩn;
  - `sheet="Khong co"` → `DomainException(CONFIG_INVALID)`, detail `Sheet "Khong co" does not exist.`;
  - kiểu ô, dòng 2 là `"00123"` (chuỗi), `42`, ngày 2024-02-29 (`dd/mm/yyyy`), TRUE, `=B2*2`, 13:30 (`h:mm`) → chữ `00123`,`42`,`2024-02-29`,`TRUE`,`84`,`13:30:00`; kiểu `TEXT`,`NUMBER`,`DATE`,`BOOLEAN`,`NUMBER`,`TEXT`;
  - `hasHeader=false` → cột `Column A`…; row đầu có số dòng 1;
  - zip bomb (fixture của V0.1) → `FILE_PARSE_ERROR`, như cũ.
- [x] 5.2 Chạy. Mong đợi: FAIL.
- [x] 5.3 Cài đặt theo IO5.
- [x] 5.4 Chạy lại, cùng `XlsxSourceParserTest`. Mong đợi: PASS.
- [x] 5.5 Commit: `feat(core): XLSX sheet listing/selection and cell kinds`
  - Làm thêm: XLSX đếm cả dòng **vắng mặt** trong sheet XML (khoảng trống giữa hai số dòng) vào `blankRowsSkipped`, cho nghĩa "dòng trống" giống CSV. Kiểu ô kiểm trên fixture thật `types.xlsx`.

## 6. `JsonTableReader`

**Files:**
- Create: `MAIN/core/format/json/JsonTableReader.java`
- Test: `TEST/core/format/json/JsonTableReaderTest.java`

- [x] 6.1 Viết `JsonTableReaderTest`:

  | Input | Mong đợi |
  |---|---|
  | `[{"id":1,"name":"An"},{"id":2,"email":"b@x.com"}]` | cột `id,name,email`; row 1 `["1","An",null]` kiểu `[NUMBER,TEXT,null]`; row 2 `["2",null,"b@x.com"]` |
  | `[{"price":12.50,"big":12345678901234567890,"exp":1e3,"neg":-0}]` | chữ `12.50`,`12345678901234567890`,`1e3`,`-0`; kiểu `NUMBER` |
  | `[{"ok":true,"no":false,"n":null}]` | `["true","false",null]`, kiểu `[BOOLEAN,BOOLEAN,null]` |
  | `[{},{"a":"x"}]` | cột `a`; 2 row; row 1 `[null]`; `blankRowsSkipped=0` |
  | `EF BB BF` + `[{"a":"x"}]` | đọc được |
  | `[{"Email":"a"},{"email":"b"}]` | cột `Email`, `email (2)`; row 2 `[null,"b"]` |
  | `[{"id":1},{"id":2,"address":{"city":"HN"}}]` | `JSON_NOT_FLAT`, detail `Nested value at row 2, key "address" is not supported yet.` |
  | `[{"tags":["a"]}]` | `JSON_NOT_FLAT`, detail `Nested value at row 1, key "tags" is not supported yet.` |
  | `{"data":[]}` | `FILE_PARSE_ERROR`, detail `JSON must be an array of objects.` |
  | `[1,2]` | `FILE_PARSE_ERROR`, detail `Element 1 of the array is not an object.` |
  | `[{"a":1,"a":2}]` | `FILE_PARSE_ERROR`, detail `Duplicate key "a" at row 1.` |
  | `[{"a":1},{"a":` (cụt) | `FILE_PARSE_ERROR`, detail `Invalid JSON near row 2.` |
  | `[]` | `FILE_EMPTY`, detail `JSON array is empty.` |
  | `[{"a":"x"}] trailing` | `FILE_PARSE_ERROR`, detail `Invalid JSON near row 1.` |

  Thêm một kiểm: với input sai cú pháp chứa chuỗi `SECRET`, `detail` không chứa `SECRET`.
- [x] 6.2 Chạy. Mong đợi: FAIL.
- [x] 6.3 Cài đặt theo IO6. `JsonFactory` có `StreamReadConstraints` như design. `inspect` gom key; map key → index lưu trong `TableInfo` (trường package-private `jsonKeyIndex`, hoặc record phụ `JsonLayout`; ghi lựa chọn vào ghi chú).
- [x] 6.4 Chạy lại. Mong đợi: PASS.
- [x] 6.5 Commit: `feat(core): JSON reader for arrays of flat objects`
  - Làm khác: `TableInfo` có thêm `sourceKeys` (key JSON gốc theo thứ tự cột) để `read` map key sang cột. JSON biết tập cột dần dần, nên `TableScan.addColumn` / `ProfileBuilder.addColumn` cho thêm cột giữa chừng; các row trước đó tính là ô rỗng. Không phải đọc file hai lần trong `inspect`.

## 7. Profile cột

**Files:**
- Create: `MAIN/core/table/ProfileBuilder.java`
- Modify: 3 reader (gọi `ProfileBuilder` trong `inspect`)
- Test: `TEST/core/table/ProfileBuilderTest.java`, thêm case vào test của 3 reader

- [x] 7.1 Viết `ProfileBuilderTest`. Nạp từng ô bằng `accept(col, text, kind)`, rồi gọi `build()`:

  | Cột (nguồn CSV, `kind` null) | `inferredType` | `emptyCount` | `maxLength` |
  |---|---|---|---|
  | `1`, `2`, `-3.5` | `number` | 0 | 4 |
  | `00123`, `00456` | `string` | 0 | 5 |
  | `true`, `FALSE`, (rỗng) | `boolean` | 1 | 5 |
  | `2024-01-31`, `2024-02-30` | `string` (ngày không có thật) | 0 | 10 |
  | `an@x.com`, `  ` | `email` | 1 | 8 |
  | `1234567890123456` | `string` (16 chữ số) | 0 | 16 |
  | `1`, `0` | `number` (không phải boolean) | 0 | 1 |
  | (mọi ô rỗng) | `empty` | n | 0 |
  | `Nguyễn` | `string` | 0 | 6 (code point, sau NFC) |

  | Cột (nguồn có kiểu) | `inferredType` |
  |---|---|
  | `12.5`/NUMBER, `1e3`/NUMBER | `number` |
  | `1`/NUMBER, `x`/TEXT | `string` |
  | `2024-01-01`/DATE | `date` |
  | `a@x.com`/TEXT, `b@y.vn`/TEXT | `email` |
  | `123`/TEXT | `string` (chữ không bao giờ thành số) |
- [x] 7.2 Thêm case vào test reader: CSV `id,code,active,joined,mail,note` (xem scenario spec) → đúng 6 `inferredType`.
- [x] 7.3 Chạy. Mong đợi: FAIL.
- [x] 7.4 Cài `ProfileBuilder` theo IO7 và gọi nó từ 3 reader.
- [x] 7.5 Chạy lại. Mong đợi: PASS.
- [x] 7.6 Commit: `feat(core): column profiles (inferred type, empty count, max length)`

## 8. Giới hạn đọc và `blankRowsSkipped`

**Files:**
- Modify: 3 reader
- Test: `TEST/core/format/ReadLimitsTest.java` (chạy tham số hoá trên cả 3 reader)

- [x] 8.1 Viết `ReadLimitsTest` với `ReadLimits(3, 2, 5)`:
  - 4 row dữ liệu → `LIMIT_EXCEEDED`, detail `File has more than 3 rows.`;
  - 3 cột → `LIMIT_EXCEEDED`, detail `File has more than 2 columns.`;
  - ô ở row 2, cột `b` dài 6 → `LIMIT_EXCEEDED`, detail `Value at row 2, column "b" is longer than 5 characters.`;
  - đúng bằng giới hạn (3 row, 2 cột, ô dài 5) → không lỗi;
  - `ReadLimits.NONE` với 10 000 row → không lỗi.

  Thêm `blankRowsSkipped`:
  - CSV `a,b\n1,2\n\n , \n3,4\n` → 2;
  - XLSX có dòng 3 trống → 1;
  - JSON `[{},{}]` → 0.
- [x] 8.2 Chạy. Mong đợi: FAIL.
- [x] 8.3 Cài đặt theo IO8 và IO4. Detail dùng số thật của giới hạn (500 000 trong spec là giá trị mặc định).
- [x] 8.4 Chạy lại. Mong đợi: PASS.
- [x] 8.5 Commit: `feat(core): read limits (rows, columns, cell length) and blank row count`

## 9. Contract ghi, `CellTyping` và `CsvTableWriter`

**Files:**
- Create: `MAIN/core/table/{Typing, TypedCell, OutputColumn, WriteOptions, TableWriter, RowSink, CellTyping}.java`, `MAIN/core/format/csv/CsvTableWriter.java`
- Test: `TEST/core/table/CellTypingTest.java`, `TEST/core/format/csv/CsvTableWriterTest.java`

- [ ] 9.1 Viết `CellTypingTest` với `resolve(cell, typing, profile)`:

  | Ô (text, kind) | typing | profile.inferredType | Kiểu thật |
  |---|---|---|---|
  | `12`, NUMBER | STRING | — | TEXT |
  | `12`, NUMBER | PRESERVE | — | NUMBER |
  | `12`, null | PRESERVE | number | TEXT |
  | `12`, null | INFER | number | NUMBER |
  | `x`, null | INFER | number | TEXT (không khớp luật) |
  | `true`, null | INFER | boolean | BOOLEAN |
  | `2024-01-01`, null | INFER | date | DATE |
  | `a@x.com`, null | INFER | email | TEXT |
  | null, NUMBER | PRESERVE | — | null (ô rỗng) |
- [ ] 9.2 Viết `CsvTableWriterTest` (đọc lại bằng commons-csv khi cần):
  - mặc định, cột `a,b`, row `["x","1"]` → byte `EF BB BF` + `a,b\r\nx,1\r\n`;
  - `delimiter=SEMICOLON`, `bom=false`, row `["x;y","1"]` → `a;b\r\n"x;y";1\r\n`;
  - `header=false` → không có dòng header;
  - formula guard, ô TEXT `=SUM(A1)`, `+1`, `-x`, `@a`, `\tt`, `\rr` → mỗi ô có tiền tố `'`;
  - ô NUMBER `-5` → `-5`;
  - header `=cmd()` → `'=cmd()`;
  - `formulaGuard=false` → giữ nguyên `=SUM(A1)`;
  - ô null → trường rỗng; ô chứa `"` và xuống dòng → được quote đúng RFC 4180;
  - `abort()` sau row 1 → không ném lỗi, byte đã ghi dừng ở row 1.
- [ ] 9.3 Chạy. Mong đợi: FAIL.
- [ ] 9.4 Cài đặt theo IO9. Formula guard dùng lại `CsvFormulaGuard` (chuyển từ `tools.importer.domain.export` sang `core.format.csv`, vì đây là luật chung).
- [ ] 9.5 Chạy lại. Mong đợi: PASS.
- [ ] 9.6 Commit: `feat(core): TableWriter contract, cell typing and CSV writer`

## 10. `JsonTableWriter`

**Files:**
- Create: `MAIN/core/format/json/JsonTableWriter.java`
- Test: `TEST/core/format/json/JsonTableWriterTest.java`

- [ ] 10.1 Viết test:
  - cột `p,ok,n,s`; row `[("12.50",NUMBER),("true",BOOLEAN),(null,null),("00123",TEXT)]`; PRESERVE → `[{"p":12.50,"ok":true,"n":null,"s":"00123"}]`;
  - `("TRUE",BOOLEAN)` → `true`; `("1",BOOLEAN)` → `true`; `("0",BOOLEAN)` → `false`;
  - `("12,5",NUMBER)` (không phải số JSON) → `"12,5"`;
  - `("2024-01-31",DATE)` → `"2024-01-31"`;
  - STRING → mọi ô thành string: `{"p":"12.50","ok":"true",…}`;
  - `pretty=true` → có xuống dòng và thụt lề, parse lại ra cùng nội dung;
  - 0 row → `[]`;
  - key `=cmd()` giữ nguyên; tiếng Việt ghi UTF-8 thật, không escape `\u`;
  - `abort()` sau row 1 → output không có `]` cuối.
- [ ] 10.2 Chạy. Mong đợi: FAIL.
- [ ] 10.3 Cài đặt bằng `JsonGenerator` của Jackson 3. Số ghi bằng `writeNumber(String)` sau khi kiểm chữ khớp grammar số JSON (`-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?`).
- [ ] 10.4 Chạy lại. Mong đợi: PASS.
- [ ] 10.5 Commit: `feat(core): JSON writer that keeps number literals`

## 11. `XlsxTableWriter`

**Files:**
- Modify: `apps/api/pom.xml` (`org.dhatim:fastexcel` bỏ `<scope>test</scope>`)
- Create: `MAIN/core/format/xlsx/XlsxTableWriter.java`
- Test: `TEST/core/format/xlsx/XlsxTableWriterTest.java`

- [ ] 11.1 Viết test, đọc lại bằng fastexcel-reader:
  - header `a,b,c,d,e`; row `[("12.5",NUMBER),("TRUE",BOOLEAN),("2024-02-29",DATE),("=1+1",TEXT),(null,null)]` → ô số 12.5, ô boolean true, ô ngày 2024-02-29, ô chuỗi `=1+1` (không phải công thức), ô trống;
  - `("12345678901234567",NUMBER)` → ô **chuỗi** `12345678901234567`;
  - `("0.1",NUMBER)` → ô số 0.1;
  - `sheetName="Q1/2024: [draft]"` → sheet `Q1_2024_ _draft_`; `sheetName` 40 ký tự → cắt còn 31; `sheetName=""` → `Sheet1`;
  - typing STRING → `12.5` là ô chuỗi;
  - 2 500 row → đọc lại đủ 2 500 row (có qua `flush`);
  - `abort()` → output không mở được như workbook hợp lệ (reader ném lỗi).
- [ ] 11.2 Chạy. Mong đợi: FAIL.
- [ ] 11.3 Đổi scope trong pom. Kiểm bằng `./mvnw -q dependency:tree -Dincludes=org.dhatim:fastexcel`: mong đợi thấy `compile`. Cài đặt theo IO9.
- [ ] 11.4 Chạy lại. Mong đợi: PASS. `ArchitectureTest` PASS (fastexcel nằm trong allowlist của `core.format`).
- [ ] 11.5 Commit: `feat(core): streaming XLSX writer with typed cells`

## 12. Round-trip

**Files:**
- Test: `TEST/core/format/RoundTripTest.java`

- [ ] 12.1 Viết test tham số hoá. Mọi cặp nguồn → đích trong {CSV, JSON, XLSX} × {CSV, JSON, XLSX}, bảng mẫu:
  - 5 cột `id`(số), `name`(tiếng Việt), `note`(có `=x`, có xuống dòng, có `"`), `ok`(boolean), `joined`(ngày ISO);
  - 50 row, có ô rỗng.

  Luồng: dựng nguồn bằng writer tương ứng → đọc (A) → ghi sang đích với PRESERVE, `formulaGuard=false` → đọc lại (B).

  Kiểm:
  - (A) và (B) cùng tên cột, cùng số row, cùng chữ từng ô;
  - với cặp mà cả hai định dạng đều có kiểu (JSON, XLSX), cùng `CellKind`.
- [ ] 12.2 Chạy. Mong đợi: PASS nếu task 3–11 đúng. Nếu FAIL: tìm nguyên nhân, sửa writer hoặc reader. Không được sửa test để né.
- [ ] 12.3 Commit: `test(core): read/write round trip across CSV, JSON and XLSX`

## 13. Importer chạy trên lớp mới

**Files:**
- Create: `MAIN/tools/importer/application/importsession/TableReaders.java` (thay `SourceParsers`), `MAIN/tools/importer/domain/importsession/ImporterReadOptions.java`
- Modify: `ImportSessionService`, `SourcePreviewService`, `ProcessService` (dùng `TableReader` + `TableInfo.forRead`); `FileTypeDetector` (tham số `Set<DataFormat> allowed`)
- Modify: `CsvValidRowsExporter`, `JsonValidRowsExporter`, `CsvErrorReportExporter` (adapter sang `TableWriter`)
- Delete: `core.table.SourceParser`, `core.format.csv.CsvSourceParser`, `core.format.xlsx.XlsxSourceParser` (test của chúng chuyển sang test reader mới, hoặc xoá nếu trùng)
- Modify: `platform.config.FormatConfig` (bean `CsvTableReader`, `XlsxTableReader`, `JsonTableReader`, 3 writer)
- Test: sửa `FakeSourceParser` → `FakeTableReader` trong `support`

- [ ] 13.1 Thêm case vào `ImportSessionApiIntegrationTest`: upload `data.json` (`[{"a":1}]`) → `415 FILE_UNSUPPORTED`. Chạy. Mong đợi: PASS ngay, vì Importer hiện chưa biết JSON. Đây là test canh hồi quy.
- [ ] 13.2 Chuyển Importer sang `TableReader`:
  - `ImporterReadOptions.V0_1` = `(null, COMMA, UTF_8, true, ReadLimits.NONE)`.
  - Upload gọi `inspect` rồi dựng `SourceSchema(columns, rowCount, sheetName)`.
  - Preview và process gọi `read(in, TableInfo.forRead(format, resolved, columns))`, trong đó `resolved` dựng từ `ImporterReadOptions` + `sheetName` đã lưu.
- [ ] 13.3 Chạy `./mvnw -q test`. Mong đợi: PASS toàn bộ.
  - Chỗ hay vỡ: `detail` của lỗi UTF-8 phải giữ chuỗi V0.1 (không có câu "Choose the file's encoding."), vì encoding được chỉ định rõ.
- [ ] 13.4 Chuyển 3 exporter sang adapter trên `TableWriter` (IO10).
- [ ] 13.5 Chạy `./mvnw -q test -Dtest=ExportGoldenIntegrationTest` rồi toàn bộ test. Mong đợi: PASS. Golden giống từng byte.
- [ ] 13.6 Xoá các lớp cũ không còn ai dùng. `grep -rn "SourceParser" src` → không còn.
- [ ] 13.7 Commit: `refactor(importer): read and export through core TableReader/TableWriter`

## 14. `KeyHasher` và `KeyIndex`

**Files:**
- Create: `MAIN/core/table/{Hash128, KeyHasher, KeyIndex}.java`
- Test: `TEST/core/table/KeyHasherTest.java`, `KeyIndexTest.java`

- [ ] 14.1 Viết test:
  - `hash(["ab","c"]) != hash(["a","bc"])`;
  - `hash([null]) != hash([""])`;
  - `hash(["x"]) == hash(["x"])` giữa hai instance khác nhau;
  - `hash([])` ổn định;
  - 200 000 khoá `"k"+i` → không trùng hash nào;
  - `KeyIndex.putIfAbsent(h, 2)` lần đầu trả `null`, lần hai trả `2`, `size()` là 1.
- [ ] 14.2 Chạy. Mong đợi: FAIL.
- [ ] 14.3 Cài đặt theo IO11.
- [ ] 14.4 Chạy lại. Mong đợi: PASS.
- [ ] 14.5 Commit: `feat(core): 128-bit key hashing for dataset-wide indexes`

## 15. Mã lỗi `JSON_NOT_FLAT` và `LIMIT_EXCEEDED`

**Files:**
- Modify: `MAIN/core/common/ErrorCode.java`, `MAIN/platform/web/ErrorHttpStatus.java`
- Test: `TEST/platform/web/ErrorHttpStatusTest.java` (có sẵn thì thêm case)

- [x] 15.1 Thêm case: `JSON_NOT_FLAT` → 422; `LIMIT_EXCEEDED` → 422. Chạy. Mong đợi: FAIL.
  - Nếu hai mã đã được thêm vào enum từ task 6 và 8 (để test reader compile) thì phần còn lại là ánh xạ HTTP.
- [x] 15.2 Thêm hai mã và ánh xạ. Chạy. Mong đợi: PASS.
- [x] 15.3 Commit: `feat(errors): JSON_NOT_FLAT and LIMIT_EXCEEDED codes`
  - Làm khác: hai mã được thêm ngay ở task 3 (reader cần chúng để compile), không commit riêng.

## 16. Hoàn tất

- [ ] 16.1 `./mvnw -q verify`. Mong đợi: PASS.
- [ ] 16.2 `openspec validate core-02-dataset-io --strict`, rồi `openspec archive core-02-dataset-io -y`. Commit: `docs(openspec): archive core-02-dataset-io`.
- [ ] 16.3 Kiểm thư mục chính sạch trong `apps/api/src` (xem Global Constraints của core-01). Rồi `git -C D:/Code/Product/universal-importer merge --no-ff feature/core-02-dataset-io`.
- [ ] 16.4 Báo người dùng:
  - `pom.xml` đổi scope fastexcel, nên IntelliJ cần reload Maven;
  - API không đổi.

  Báo phiên FE: không có gì mới cho FE.
- [ ] 16.5 `git branch -d feature/core-02-dataset-io`.
