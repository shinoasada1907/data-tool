# BE-F03 XLSX Parser — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** Upload XLSX đọc sheet hiển thị đầu tiên, chuyển giá trị ô đúng luật D9, và dùng chung preview với CSV.

**Architecture:** `XlsxSourceParser` cài port `SourceParser` (F02) trong `infrastructure.parser.xlsx`, cùng `ExcelDateFormats` (nhận diện format ngày) và `XlsxZipGuard` (chống zip bomb). Thư viện đọc được chốt bằng spike ở task 1. Luồng upload và preview của F02 giữ nguyên.

**Tech Stack:** Như F02. Thư viện đọc là `org.dhatim:fastexcel-reader:0.20.2` **hoặc** `org.apache.poi:poi-ooxml:5.5.1`, tuỳ kết quả spike. Test dùng thêm `org.dhatim:fastexcel:0.20.2` (writer).

**Spec:** `openspec/changes/be-f03-xlsx-parser/specs/source-parsing/spec.md`. Thiết kế: `design.md` cùng thư mục (X1–X7), `be-f02-csv-preview/design.md` (P1–P8), `be-f01-import-session/design.md` (D1–D14).

## Global Constraints

- Mọi ràng buộc chung của F01 và F02 vẫn áp dụng.
- F02 phải đã xong và nằm trong nhánh này (tạo `feature/be-f03-xlsx-parser` từ nhánh có F02).
- Không thêm Jackson 2. Kiểm bằng `./mvnw -q dependency:tree -Dincludes=com.fasterxml.jackson.core:jackson-databind`: mong đợi không có dòng nào. POI không kéo Jackson; nếu kéo thì loại bằng `<exclusions>`.
- Fixture làm tay nằm trong `apps/api/src/test/resources/fixtures/xlsx/`. Fixture sinh bằng code nằm trong `TEST/support/XlsxFixtures.java`, ghi ra `@TempDir`.
- Message lỗi không chứa giá trị ô (D13).

---

## 1. Spike: chọn thư viện đọc XLSX

**Files:**
- Create (làm tay): `apps/api/src/test/resources/fixtures/xlsx/types.xlsx`, `apps/api/src/test/resources/fixtures/xlsx/date1904.xlsx`
- Create: `TEST/support/XlsxFixtures.java`, `TEST/infrastructure/parser/xlsx/XlsxLibrarySpikeTest.java` (tạm; xoá ở 1.6)
- Modify: `apps/api/pom.xml`

**Interfaces:**
- Produces (dùng tiếp ở các task sau):
  ```java
  public final class XlsxFixtures {           // test support, dùng org.dhatim:fastexcel (writer)
      public static Path hiddenFirstSheet(Path dir);   // sheet 1 "Hidden" (HIDDEN): [x]/[1]; sheet 2 "Visible": [name]/[An]
      public static Path mergedAndGaps(Path dir);      // [a,b,c]; A2:B2 gộp = "M", C2 = "c"; dòng 3 trống; dòng 4 = 1,2,3 (số)
      public static Path duplicateHeaders(Path dir);   // dòng 1: "Email","email", C1 trống, D1 = "x"; dòng 2: 1,2,3,4
      public static Path headerOnly(Path dir);         // [name,email]
      public static Path emptyFirstSheet(Path dir);    // sheet 1 trống; sheet 2 có [a]/[1]
      public static Path blankFirstRow(Path dir);      // dòng 1 trống; dòng 2 = "a","b"
      public static Path allSheetsHidden(Path dir);    // một sheet duy nhất, HIDDEN, có [a]/[1]
      public static Path large(Path dir, int rows, int cols);   // chữ và số xen kẽ
  }
  ```

- [x] 1.1 Tạo 2 fixture làm tay bằng LibreOffice Calc (hoặc Excel) rồi lưu dạng `.xlsx`:
  - Làm khác: hai fixture được tạo bằng **Excel thật điều khiển qua COM** (PowerShell), không làm tay. Thêm cột thứ 14 `time_only` = 13:30 với format `h:mm` (Excel lưu id 20). **LÝ DO**: máy không có LibreOffice nhưng có Excel; tự động hoá thì tái tạo được. Cột `time_only` để kiểm quyết định `HH:mm:ss` (2026-09-25) với file thật. Excel lưu số 0.001 dưới dạng `1E-3`, nên C2 có giá trị thật.
  - **`types.xlsx`**: một sheet tên `Data`.
    - Dòng 1: `text | int | decimal | small | big | bool | date_builtin | date_custom | datetime | formula_num | formula_text | formula_bool | na`
    - Dòng 2:
      - `An`
      - `123`
      - `-5.25`
      - `0.001`
      - `84901234567`
      - `TRUE` (kiểu boolean)
      - ngày `2024-02-29` với format ngày ngắn mặc định (Excel: *Short Date*)
      - ngày `2024-12-25` với format `dd/mm/yyyy`
      - `2024-12-25 13:45:30` với format `yyyy-mm-dd hh:mm:ss`
      - `=B2*2`
      - `=A2&"!"`
      - `=B2>100`
      - `=NA()`

    Mở lại file để chắc chắn công thức đã có kết quả.
  - **`date1904.xlsx`**: bật hệ ngày 1904.
    - LibreOffice: *Tools › Options › LibreOffice Calc › Calculate › Date: 01/01/1904*.
    - Excel: *Options › Advanced › Use 1904 date system*.

    Dòng 1: `d`. Dòng 2: ngày `2024-01-01`, format `dd/mm/yyyy`.
- [x] 1.2 Thêm dependency tạm vào `pom.xml`, **scope test** (chỉ phục vụ spike):
  - `org.dhatim:fastexcel-reader:0.20.2`
  - `org.apache.poi:poi-ooxml:5.5.1`
  - `org.dhatim:fastexcel:0.20.2` (writer, giữ lâu dài)

  Đổi cấu hình Surefire để nhận thêm tham số JVM: `<argLine>-Duser.timezone=UTC ${surefire.extraArgLine}</argLine>`, kèm property `<surefire.extraArgLine></surefire.extraArgLine>` (mặc định rỗng). Kiểm bằng `./mvnw -q test`: mong đợi mọi test cũ vẫn PASS.
- [x] 1.3 Viết `XlsxFixtures` theo phần Interfaces. Viết `XlsxLibrarySpikeTest`: với **mỗi** ứng viên, đọc các fixture và in ra giá trị thật của từng tiêu chí C1–C8 trong design X1. Riêng C7: đọc hết `large(dir, 200_000, 10)` mà không gom row vào list.
  - Làm khác: spike chỉ chạy fastexcel; mỗi tiêu chí là một test in quan sát thật và có assertion. **LÝ DO**: luật X1 là "chọn fastexcel nếu đạt mọi tiêu chí", POI chỉ là phương án dự phòng; fastexcel đạt đủ C1–C8 nên không cần spike POI.
  - fastexcel: `new ReadableWorkbook(in, new ReadingOptions(true, false))`, `getSheets().filter(s -> s.getVisibility() == VISIBLE).findFirst()`, `Cell.getRawValue()/getDataFormatId()/getDataFormatString()`, `isDate1904()`.
  - POI: `OPCPackage.open(in)`, `XSSFReader`. Visibility đọc thuộc tính `state` trong `xl/workbook.xml`. Dùng `XSSFSheetXMLHandler` với một `DataFormatter` tự viết để lấy giá trị raw, và `DateUtil.isADateFormat`.
- [x] 1.4 Chạy `./mvnw -q test -Dtest=XlsxLibrarySpikeTest -Dsurefire.extraArgLine=-Xmx256m`. Ghi kết quả từng tiêu chí C1–C8 cho cả hai ứng viên.
  - Kết quả: 7/7 PASS với `-Xmx256m`; C7: 200.000 row × 10 cột, file 10MB, 2,4 giây. Chi tiết ở mục "Kết quả spike" của design.md.
- [x] 1.5 Điền mục "Kết quả spike" trong `design.md`: bảng C1–C8 × hai ứng viên, và lựa chọn cuối cùng. Luật chọn nằm ở X1. Phương án không chọn gạch ngang kèm LÝ DO.
- [x] 1.6 Dọn spike:
  - xoá `XlsxLibrarySpikeTest`;
  - chuyển thư viện được chọn sang scope `compile`, xoá thư viện còn lại khỏi `pom.xml`;
  - giữ `fastexcel` writer ở scope `test`;
  - chạy `./mvnw -q test` (PASS);
  - kiểm không có Jackson 2 theo Global Constraints.
- [x] 1.7 Commit: `chore(api): pick XLSX reader via spike, add XLSX fixtures`

## 2. Nhận diện format ngày

**Files:**
- Create: `MAIN/infrastructure/parser/xlsx/ExcelDateFormats.java`
- Test: `TEST/infrastructure/parser/xlsx/ExcelDateFormatsTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class ExcelDateFormats {
      public static boolean isDateFormat(Integer formatId, String formatString);
      public static boolean isTimeOnlyFormat(Integer formatId, String formatString);   // ngày/giờ nhưng không có y/d
  }
  ```
  Nếu chọn POI, `isDateFormat` gọi `DateUtil.isADateFormat` nhưng giữ nguyên chữ ký và bộ test.

- [ ] 2.1 Viết `ExcelDateFormatsTest` (parameterized):
  | formatId | formatString | Mong đợi |
  |---|---|---|
  | 14 | `m/d/yyyy` | true |
  | 22 | `m/d/yy h:mm` | true |
  | 45 | `mm:ss` | true |
  | 0 | `General` | false |
  | 2 | `0.00` | false |
  | 49 | `@` | false |
  | 164 | `dd/mm/yyyy` | true |
  | 165 | `yyyy-mm-dd hh:mm:ss` | true |
  | 166 | `[$-409]mmmm d, yyyy` | true |
  | 167 | `#,##0 "VND"` | false |
  | 168 | `0.00 "dm"` | false |
  | 169 | `[Red]0.00` | false |
  | 170 | `[h]:mm` | true |
  | 171 | `\d0.0` | false |
  | null | null | false |

  Thêm test `isTimeOnlyFormat` (parameterized). Cột "Mong đợi" là kết quả của `isTimeOnlyFormat`:
  | formatId | formatString | Mong đợi |
  |---|---|---|
  | 20 | `h:mm` | true |
  | 21 | `h:mm:ss` | true |
  | 45 | `mm:ss` | true |
  | 170 | `[h]:mm` | true |
  | 172 | `hh:mm AM/PM` | true |
  | 14 | `m/d/yyyy` | false |
  | 165 | `yyyy-mm-dd hh:mm:ss` | false |
  | 164 | `dd/mm/yyyy` | false |
  | 0 | `General` | false |
- [ ] 2.2 Chạy `./mvnw -q test -Dtest=ExcelDateFormatsTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 2.3 Cài theo design X2. `isTimeOnlyFormat` = `isDateFormat` và, sau khi bỏ phần quote/escape/`[...]`, không còn chữ `y` hay `d`.
- [ ] 2.4 Chạy lại lệnh ở 2.2. Mong đợi: PASS (15 + 9 case).
- [ ] 2.5 Commit: `feat(infra): detect Excel date formats`

## 3. Chống zip bomb

**Files:**
- Create: `MAIN/infrastructure/parser/xlsx/XlsxLimits.java`, `MAIN/infrastructure/parser/xlsx/XlsxZipGuard.java`
- Modify: `apps/api/src/main/resources/application.yaml`
- Test: `TEST/infrastructure/parser/xlsx/XlsxZipGuardTest.java`

**Interfaces:**
- Produces:
  ```java
  @ConfigurationProperties("importer.xlsx")
  public record XlsxLimits(DataSize maxUncompressedSize, int maxInflateRatio, int maxEntries) {}
  public final class XlsxZipGuard {
      public XlsxZipGuard(XlsxLimits limits);
      public void check(InputStream input);   // vượt giới hạn → DomainException(FILE_PARSE_ERROR, "XLSX file expands beyond the allowed limits.")
                                              // không có entry nào / không phải zip → FILE_PARSE_ERROR "File is not a valid XLSX workbook."
  }
  ```
- `application.yaml`:
  ```yaml
  importer:
    xlsx:
      max-uncompressed-size: ${IMPORTER_XLSX_MAX_UNCOMPRESSED_SIZE:200MB}
      max-inflate-ratio: ${IMPORTER_XLSX_MAX_INFLATE_RATIO:100}
      max-entries: ${IMPORTER_XLSX_MAX_ENTRIES:10000}
  ```

- [ ] 3.1 Viết `XlsxZipGuardTest`, với `limits = (200MB, 100, 10000)` trừ khi có ghi khác:
  | Case | Mong đợi |
  |---|---|
  | `XlsxFixtures.headerOnly` | không ném lỗi |
  | Zip có entry `xl/worksheets/sheet1.xml` gồm 50MB byte `0` | `FILE_PARSE_ERROR`, message `XLSX file expands beyond the allowed limits.` |
  | limits `(1MB, 100, 10000)`; zip có 3 entry, mỗi entry 600KB chữ ngẫu nhiên (tỉ lệ nén thấp) | `FILE_PARSE_ERROR` (vượt tổng) |
  | limits `(200MB, 100, 100)`; zip có 101 entry nhỏ | `FILE_PARSE_ERROR` (vượt số entry) |
  | Bytes `"hello"` | `FILE_PARSE_ERROR`, message `File is not a valid XLSX workbook.` |
  | Zip có entry 2MB byte `0` với limits `(200MB, 5000, 10000)` | không ném lỗi (tỉ lệ dưới ngưỡng) |
- [ ] 3.2 Chạy `./mvnw -q test -Dtest=XlsxZipGuardTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 3.3 Cài theo design X4: đếm byte nén bằng một `FilterInputStream` đếm byte bọc bên dưới `ZipInputStream`; ngắt ngay khi vượt, không đọc tiếp.
- [ ] 3.4 Chạy lại lệnh ở 3.2. Mong đợi: PASS.
- [ ] 3.5 Commit: `feat(infra): guard XLSX uploads against zip bombs`

## 4. XlsxSourceParser

**Files:**
- Create: `MAIN/infrastructure/parser/xlsx/XlsxSourceParser.java`, `MAIN/infrastructure/parser/xlsx/XlsxCellValues.java`
- Test: `TEST/infrastructure/parser/xlsx/XlsxSourceParserTest.java`, `TEST/infrastructure/parser/xlsx/XlsxCellValuesTest.java`

**Interfaces:**
- Consumes: `SourceParser`, `SourceSchema`, `ImportRow`, `ColumnNames` (F02); `ExcelDateFormats` (task 2); `XlsxZipGuard` (task 3).
- Produces:
  ```java
  @Component public class XlsxSourceParser implements SourceParser {   // supports(XLSX) == true, supports(CSV) == false
      public XlsxSourceParser(XlsxZipGuard guard);
  }
  final class XlsxCellValues {   // chuyển giá trị ô theo design X3
      static String number(String raw, Integer formatId, String formatString, boolean date1904);
      static String serialToIso(BigDecimal serial, boolean date1904);   // "2024-02-29" | "2024-12-25T13:45:30"
  }
  ```

- [ ] 4.1 Viết `XlsxCellValuesTest`:
  | Hàm | Input | Mong đợi |
  |---|---|---|
  | `number` | `("123", 0, "General", false)` | `123` |
  | `number` | `("8.4901234567E10", 0, "General", false)` | `84901234567` |
  | `number` | `("1E-3", 0, "General", false)` | `0.001` |
  | `number` | `("45351", 14, "m/d/yyyy", false)` | `2024-02-29` |
  | `number` | `("45651.5732638889", 165, "yyyy-mm-dd hh:mm:ss", false)` | `2024-12-25T13:45:30` (49530 giây = 13:45:30) |
  | `number` | `("45651.99999999", 14, "m/d/yyyy", false)` | `2024-12-26` (làm tròn tới giây thì sang ngày mới, phần giờ là 0) |
  | `number` | `("0.5625", 20, "h:mm", false)` | `13:30:00` (ô chỉ có giờ; không kèm `1899-12-30`) |
  | `number` | `("0.5732638889", 21, "hh:mm:ss", false)` | `13:45:30` |
  | `number` | `("45651.5625", 20, "h:mm", false)` | `13:30:00` (format chỉ có giờ thì bỏ phần ngày của serial) |
  | `number` | `("0.99999999", 21, "hh:mm:ss", false)` | `00:00:00` (làm tròn lên 24:00:00 thì ra 00:00:00) |
  | `serialToIso` | `(43830, true)` | `2024-01-01` (hệ 1904) |
  | `serialToIso` | `(45292, false)` | `2024-01-01` (hệ 1900) |
- [ ] 4.2 Viết `XlsxSourceParserTest` (dùng fixture của task 1):
  | Fixture | Mong đợi |
  |---|---|
  | `types.xlsx` | `sheetName` `Data`; 13 cột đúng tên header; `totalRows` 1; row `(2, ["An","123","-5.25","0.001","84901234567","TRUE","2024-02-29","2024-12-25","2024-12-25T13:45:30","246","An!","TRUE","#N/A"])` |
  | `date1904.xlsx` | row `(2, ["2024-01-01"])` |
  | `hiddenFirstSheet` | `sheetName` `Visible`; columns `[name]`; row `(2, ["An"])` |
  | `mergedAndGaps` | rows `(2, ["M",null,"c"])`, `(4, ["1","2","3"])`; `totalRows` 2 |
  | `duplicateHeaders` | columns `["Email","email (2)","Column C","x"]` |
  | `headerOnly` | `totalRows` 0; `read` rỗng |
  | `emptyFirstSheet` | `inspect` ném `FILE_EMPTY` |
  | `blankFirstRow` | `inspect` ném `FILE_EMPTY`, message `The first row must contain column headers.` |
  | `allSheetsHidden` | `inspect` ném `FILE_EMPTY`, message `Workbook has no visible sheet.` |
  | Bytes `PK\x03\x04` + 100 byte rác | `inspect` ném `FILE_PARSE_ERROR` |
  | Zip hợp lệ không có `xl/workbook.xml` | `inspect` ném `FILE_PARSE_ERROR`, message `File is not a valid XLSX workbook.` |
  | `read(headerOnly)` rồi `close()` | input đã đóng; không còn file tạm `xlsx-*.xlsx` trong `java.io.tmpdir` được tạo sau thời điểm bắt đầu test (nếu áp dụng X6) |
- [ ] 4.3 Chạy `./mvnw -q test -Dtest=XlsxCellValuesTest,XlsxSourceParserTest`. Mong đợi: FAIL vì lỗi compile.
- [ ] 4.4 Cài `XlsxCellValues` và `XlsxSourceParser` theo design X3, X5, X6:
  - `inspect` gọi `guard.check` trước (qua một lượt mở stream riêng), rồi mới parse;
  - `read` bỏ qua guard;
  - `rowNumber` lấy theo số dòng của sheet;
  - dùng `ColumnNames.normalize` và `ColumnNames.isBlankRow`.
- [ ] 4.5 Chạy lại lệnh ở 4.3. Mong đợi: PASS.
- [ ] 4.6 Commit: `feat(infra): XLSX source parser (first visible sheet, ISO dates, plain numbers)`

## 5. Upload và preview XLSX qua HTTP thật

**Files:**
- Test: `TEST/api/importsession/XlsxUploadIntegrationTest.java` (setup như `SourcePreviewIntegrationTest` của F02)

- [ ] 5.1 Viết các case:
  | Case | Mong đợi |
  |---|---|
  | Upload `types.xlsx` | 201, `status` `CONFIGURING`, `fileType` `XLSX`. Preview: `sheetName` `Data`, `rows[0].values[6]` = `2024-02-29`, `rows[0].values[4]` = `84901234567` |
  | Upload `hiddenFirstSheet` | preview `sheetName` = `Visible` |
  | Upload `emptyFirstSheet` | 422 `FILE_EMPTY`; không còn thư mục storage mới |
  | Upload zip bomb (50MB byte `0`, đặt tên `bomb.xlsx`) | 422 `FILE_PARSE_ERROR` |
  | Upload `large(dir, 5000, 10)` | 201; preview `totalRows` 5000 |
- [ ] 5.2 Chạy `./mvnw -q test -Dtest=XlsxUploadIntegrationTest`. Mong đợi: PASS. Parser đã tự đăng ký vào `SourceParsers` qua `@Component`, không phải sửa luồng upload. Nếu FAIL thì sửa code chính, không nới lỏng test.
- [ ] 5.3 Commit: `test(api): XLSX upload and preview over real HTTP`

## 6. Kiểm tra toàn bộ và hoàn tất

- [ ] 6.1 Chạy `./mvnw -q verify`. Mong đợi: mọi test xanh, gồm ArchitectureTest.
- [ ] 6.2 Chạy app thật và upload một file XLSX thật do người dùng cung cấp (có tiếng Việt, ngày, số điện thoại lưu dạng số). Kiểm preview qua `curl` và ghi các bất thường vào mục Open Questions của `design.md`.
- [ ] 6.3 Tick checkbox, commit: `docs(openspec): complete be-f03 tasks`. ("Ô chỉ có giờ" đã chốt 2026-09-25 là `HH:mm:ss`, có test ở 2.1 và 4.1.)
- [ ] 6.4 Hỏi người dùng trước khi merge. Sau khi merge: `openspec archive be-f03-xlsx-parser -y`.
