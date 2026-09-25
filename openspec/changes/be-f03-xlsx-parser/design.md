## Context

- Nền chung: `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md` (D1–D14), nhất là D9 (luật đọc XLSX) và D13 (bảo mật).
- F02 đã có:
  - port `SourceParser` (`inspect` + `read`);
  - `ColumnNames` (đặt tên cột, `isBlankRow`);
  - `SourceParsers` (tìm parser theo loại file);
  - luồng upload đọc file;
  - `SourcePreviewService`.

  Change này chỉ thêm một parser; luồng upload và preview không đổi.
- API của `fastexcel-reader` 0.20.2 (đã xem bằng `javap`):
  - `ReadableWorkbook(InputStream | File, ReadingOptions(withCellFormat, cellInErrorIfParseError))`
  - `isDate1904()`, `getSheets()`
  - `Sheet.getVisibility()` trả `SheetVisibility` (`VISIBLE`/`HIDDEN`/`VERY_HIDDEN`)
  - `Sheet.openStream()`
  - `Cell.getType()` trả `NUMBER`/`STRING`/`FORMULA`/`ERROR`/`BOOLEAN`/`EMPTY`
  - `Cell.getRawValue()`, `getDataFormatId()`, `getDataFormatString()`, `asDate()`

  Thư viện **không** có hàm nhận diện định dạng ngày.

## Goals / Non-Goals

**Goals:**
- XLSX cho ra cùng model (`SourceSchema`, `ImportRow`) và cùng contract preview như CSV.
- Giá trị ô giống thứ người dùng mong đợi khi làm dữ liệu: ngày dạng ISO, số không bị ký hiệu khoa học, công thức lấy giá trị.
- Đọc theo kiểu streaming, bộ nhớ không tăng theo số row.

**Non-Goals:**
- Chọn sheet khác sheet hiển thị đầu tiên.
- Tính lại công thức: chỉ dùng giá trị đã cache.
- Đọc `.xls` (định dạng BIFF): F01 đã chặn bằng 415.
- Style, comment, ảnh.

## Decisions

### X1. Spike trước khi chọn thư viện
Ứng viên:
- (a) `org.dhatim:fastexcel-reader:0.20.2`: nhẹ, streaming, có sẵn visibility và format.
- (b) `org.apache.poi:poi-ooxml:5.5.1`: dùng `XSSFReader` + SAX handler tự viết, cùng `DateUtil`.

Chọn (a) nếu nó đạt **mọi** tiêu chí dưới đây. Nếu (a) trượt một tiêu chí mà không vá được trong vài chục dòng code thì chọn (b).

| # | Tiêu chí | Cách kiểm |
|---|---|---|
| C1 | Chọn đúng sheet hiển thị đầu tiên, bỏ qua `HIDDEN` và `VERY_HIDDEN`; lấy được tên sheet | fixture `hidden-first-sheet` |
| C2 | Số lấy từ chuỗi raw và đổi đúng: `84901234567`, `0.1`, `1E-3` → `0.001`, `123` (không phải `123.0`), `-5.25` | fixture `types.xlsx` |
| C3 | Nhận ra ô ngày theo format id có sẵn (14–22, 45–47) và theo format string tự đặt (`dd/mm/yyyy`, `yyyy-mm-dd hh:mm:ss`), đúng cả với hệ ngày 1904 | fixture `types.xlsx`, `date1904.xlsx` |
| C4 | Ô công thức cho ra giá trị đã cache, đúng kiểu (số, chuỗi, boolean) và ô lỗi (`#N/A`) | fixture `types.xlsx` |
| C5 | Có số dòng thật của row (thuộc tính `r`) để giữ khoảng trống giữa các row | fixture `merged-and-gaps` |
| C6 | Chuỗi dùng chung (shared string) và chuỗi inline đều ra text thuần | fixture `types.xlsx` và fixture sinh bằng writer |
| C7 | Đọc được file 200.000 row × 10 cột khi JVM chạy với `-Xmx256m` | file sinh bằng writer trong spike |
| C8 | Có cách chống zip bomb, bằng chính thư viện hoặc bằng bước quét trước (X4) | code spike |

Kết quả spike ghi vào mục "Kết quả spike" của file này. Phương án không chọn thì gạch ngang, kèm **LÝ DO**. Code spike là code tạm: xoá sau khi quyết.

### X2. Nhận diện định dạng ngày: `ExcelDateFormats.isDateFormat(Integer formatId, String formatString)`
- **Format id có sẵn**: 14–22 và 45–47 là ngày/giờ.
- **Format string**:
  1. Bỏ phần trong `"…"`, các ký tự thoát `\x`, và các khối `[...]`. Riêng `[h]`, `[m]`, `[s]` (thời gian lũy kế) thì giữ.
  2. Sau đó, nếu còn một trong các chữ `y`, `m`, `d`, `h`, `s` (không phân biệt hoa thường) thì đó là ngày/giờ.
  3. `General` và `@` không phải ngày.
- Nếu chọn POI thì dùng `DateUtil.isADateFormat`. Bộ test cho X2 vẫn giữ nguyên.

### X3. Chuyển giá trị ô (D9)
| Loại ô | Chuỗi trả về |
|---|---|
| Số, format không phải ngày | `new BigDecimal(raw).toPlainString()` |
| Số, format ngày | Serial → `LocalDateTime`: gốc `1899-12-30` (hệ 1900) hoặc `1904-01-01` (hệ 1904); phần lẻ của ngày làm tròn tới giây. Phần giờ là `00:00:00` thì `yyyy-MM-dd`, ngược lại `yyyy-MM-dd'T'HH:mm:ss` |
| Số, format **chỉ có giờ** (có `h`/`s` nhưng không có `y`/`d`, xem `ExcelDateFormats.isTimeOnlyFormat`) | `HH:mm:ss`, lấy từ phần lẻ của serial (làm tròn tới giây; tròn lên đúng 24:00:00 thì ra `00:00:00`). Đã chốt 2026-09-25 |
| Chuỗi (shared/inline) | Text đúng như trong ô. `""` → `null` |
| Boolean | `TRUE` / `FALSE` |
| Công thức | Theo kiểu của giá trị đã cache, áp đúng các dòng trên. Không có giá trị cache → `null` |
| Lỗi | Chuỗi lỗi, ví dụ `#N/A`, `#DIV/0!` |
| Rỗng hoặc không có ô | `null` |
| Merged cell | Chỉ ô trên cùng bên trái có giá trị; các ô còn lại `null` |

Header cũng đi qua các luật này (header dạng ngày thành chuỗi ISO), rồi mới qua `ColumnNames.normalize`.

### X4. Chống zip bomb: `XlsxZipGuard.check(InputStream)`
- **Cách quét**: một lượt quét riêng, chạy trước khi parse trong `inspect`. `ZipInputStream` bọc ngoài một stream đếm byte nén; mỗi entry được đọc hết để đếm số byte sau giải nén.
- **Giới hạn**, cấu hình qua `importer.xlsx.*`:
  - tổng dung lượng sau giải nén: `max-uncompressed-size`, mặc định `200MB`;
  - tỉ lệ giải nén của entry nào có trên 1MB sau giải nén: `max-inflate-ratio`, mặc định `100`;
  - số entry: `max-entries`, mặc định `10000`.
- **Vượt giới hạn** → `DomainException(FILE_PARSE_ERROR, "XLSX file expands beyond the allowed limits.")`.
- `read` không chạy guard, vì file đã qua `inspect` lúc upload.

### X5. Luật lỗi và file trống
- Không phải ZIP hợp lệ, hoặc thiếu `xl/workbook.xml` → `FILE_PARSE_ERROR`, message `File is not a valid XLSX workbook.`
- Không có sheet nào hiển thị → `FILE_EMPTY`, message `Workbook has no visible sheet.`
- Dòng 1 của sheet hiển thị đầu tiên không có ô nào có giá trị, kể cả khi sheet trống hoàn toàn → `FILE_EMPTY`, message `The first row must contain column headers.` (giống CSV).

### X6. Đầu vào của parser
Nếu thư viện đã chọn cần `File` để truy cập ngẫu nhiên, `XlsxSourceParser` chép `InputStream` ra file tạm (`Files.createTempFile("xlsx-", ".xlsx")`) và xoá khi xong: `finally` với `inspect`, `onClose` với `read`. Port `FileStorage` không đổi.

### X7. Fixture
- **Làm tay** bằng LibreOffice Calc hoặc Excel, commit vào `apps/api/src/test/resources/fixtures/xlsx/`. Đây là các case cần giá trị công thức đã cache, ô lỗi, format ngày có sẵn, hệ 1904: `types.xlsx`, `date1904.xlsx`.
- **Sinh bằng code** qua `TEST/support/XlsxFixtures` dùng `org.dhatim:fastexcel` (writer, scope test), để không phải commit nhiều file nhị phân: `hidden-first-sheet`, `merged-and-gaps`, `empty-sheet`, `header-only`, `blank-first-row`, `duplicate-headers`, file lớn cho C7.
- File hỏng và zip bomb sinh bằng `ZipOutputStream`.

## Risks / Trade-offs

- [Nhận diện ngày theo format có thể nhầm với format số lạ có chữ `d`/`m`, ví dụ `0.00 "dm"`] → Đã bỏ phần trong quote trước khi kiểm. Các case lạ khác chấp nhận là giới hạn đã biết; người dùng có thể sửa format trong Excel.
- [Excel chỉ lưu 15 chữ số có nghĩa: số điện thoại dài hoặc có số 0 đầu đã bị mất từ trong file] → Không cứu được ở BE. README khuyên lưu các cột mã/số điện thoại ở dạng Text.
- [Quét zip bomb đọc file thêm một lượt] → Tốn thêm thời gian ngang một lượt parse; file đã bị chặn ở 20MB.
- [Fixture làm tay khó dựng lại] → tasks.md ghi rõ nội dung từng ô để dựng lại được.

## Migration Plan

- Không có migration DB.
- Session XLSX tạo trước F03 vẫn ở `UPLOADED` và không preview được. Chỉ xảy ra với dữ liệu dev, chấp nhận.

## Kết quả spike

Chạy ngày 2026-09-26 bằng `XlsxLibrarySpikeTest`, với `-Xmx256m`. Fixture `types.xlsx` và `date1904.xlsx` do Excel thật tạo ra (qua COM).

| # | fastexcel-reader 0.20.2 | Quan sát |
|---|---|---|
| C1 | Đạt | `getVisibility()` trả `HIDDEN`/`VISIBLE`; sheet hiển thị đầu tiên là `Visible`; workbook mọi sheet đều ẩn thì không có sheet `VISIBLE` nào |
| C2 | Đạt | Raw `123`, `-5.25`, `1E-3` (Excel ghi đúng như vậy), `84901234567`, qua `toPlainString()` ra `0.001` |
| C3 | Đạt | Format id 14 (fastexcel trả chuỗi `mm-dd-yy`), 164 `dd/mm/yyyy`, 165 `yyyy\-mm\-dd\ hh:mm:ss` (có ký tự escape), 20 `h:mm`; `isDate1904()` = true |
| C4 | Đạt | Ô công thức có type `FORMULA`, nhưng `getValue()` trả giá trị cache đã có kiểu: `BigDecimal 246`, `String "An!"`, `Boolean true`, `String "#N/A"`. Raw của công thức boolean là `1`, nên phải dựa vào `getValue()`, không dựa vào raw |
| C5 | Đạt | `getRowNum()` trả `1, 2, 4` (dòng 3 vắng mặt); ô phụ của vùng gộp là `null` |
| C6 | Đạt | Shared string của Excel (`An`) và string do writer ghi đều ra text; ô thiếu là `null` |
| C7 | Đạt | 200.000 row × 10 cột (file 10MB) đọc hết trong 2,4 giây, heap tối đa 256MB, không OOM |
| C8 | Đạt | Theo X4 (bước quét riêng), không phụ thuộc thư viện |

**Chọn fastexcel-reader.** ~~(b) `org.apache.poi:poi-ooxml:5.5.1`~~ **LÝ DO**: fastexcel đạt đủ C1–C8, và theo luật X1 thì POI chỉ là phương án dự phòng; không cần spike POI.

Hệ quả cho X3: ô công thức lấy kiểu từ `getValue()`. `BigDecimal` áp luật số hoặc ngày theo format của ô; `Boolean` ra `TRUE`/`FALSE`; `String` giữ nguyên (gồm cả mã lỗi như `#N/A`).

## Open Questions

- ~~**Ô chỉ có giờ** (format `hh:mm`, serial < 1): D9 chỉ nói ngày và ngày-giờ. Theo đúng luật X3, ô này sẽ ra `1899-12-30T13:30:00`, khó dùng. Đề xuất trả `HH:mm:ss` (ví dụ `13:30:00`) khi format chỉ có giờ (có `h`/`s` nhưng không có `y`/`d`). **Cần người dùng quyết**; chưa chốt thì làm theo X3 và ghi lại.~~ **Đã chốt 2026-09-25**: người dùng đồng ý trả `HH:mm:ss`; đã đưa vào X3.
  - Giới hạn đã biết: format thời gian luỹ kế (`[h]:mm`) với giá trị từ 24 giờ trở lên chỉ giữ phần giờ trong ngày, vì `HH:mm:ss` không biểu diễn được quá 24 giờ.
