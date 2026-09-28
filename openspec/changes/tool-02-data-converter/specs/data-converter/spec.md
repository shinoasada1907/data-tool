## ADDED Requirements

### Requirement: Đổi dataset sang định dạng khác
Hệ thống SHALL nhận `POST /api/converter/convert` với body JSON:

```
{ "source": { "datasetId", "options"?: { "sheet", "delimiter", "encoding", "hasHeader" } },
  "output": { "format", "csv"?, "json"?, "xlsx"? } }
```

Hệ thống SHALL trả `200` với file của định dạng `output.format` (`CSV`, `XLSX` hoặc `JSON`), đọc dataset bằng `source.options` giống hệt preview. `Content-Type` theo định dạng:
- `text/csv;charset=UTF-8`;
- `application/json`;
- `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

`Content-Disposition` là `attachment` với tên gốc bỏ đuôi, nối đuôi mới. Response có `Cache-Control: no-store`. Mọi cặp nguồn–đích trong {CSV, XLSX, JSON} SHALL được hỗ trợ, kể cả cùng định dạng.

#### Scenario: CSV sang JSON
- **WHEN** dataset `khách hàng.csv` có nội dung `id;ten\n1;An\n2;Bình\n`, và client gọi convert với `output.format` là `JSON`
- **THEN** response `200` có file `[{"id":"1","ten":"An"},{"id":"2","ten":"Bình"}]`, và `Content-Disposition` chứa `filename*=UTF-8''kh%C3%A1ch%20h%C3%A0ng.json`

#### Scenario: CSV sang XLSX
- **WHEN** dataset CSV có header `sku,price` và 3 row, và client gọi convert với `output.format` là `XLSX`
- **THEN** file là một workbook có một sheet `Sheet1`, dòng 1 là `sku`, `price`, và có 3 dòng dữ liệu

#### Scenario: XLSX chọn sheet sang CSV
- **WHEN** dataset là workbook có sheet `Data` và `Prices`, và client gọi convert với `source.options.sheet` là `Prices`, `output.format` là `CSV`
- **THEN** file CSV chứa header và các dòng của sheet `Prices`, bắt đầu bằng BOM UTF-8

#### Scenario: JSON sang CSV
- **WHEN** dataset JSON là `[{"a":1,"b":"x"},{"a":2}]`, và client gọi convert với `output.format` là `CSV`, `output.csv.bom` là `false`
- **THEN** file là `a,b\r\n1,x\r\n2,\r\n`

### Requirement: Giữ nguyên row và giá trị
File đích SHALL có đúng các cột của nguồn (cùng tên, cùng thứ tự), đúng số row và thứ tự row của nguồn (không tính dòng trống của CSV/XLSX), và giá trị từng ô theo luật typing:
- `PRESERVE` (mặc định): giữ kiểu gốc của ô nếu nguồn có kiểu. Số JSON giữ nguyên literal. CSV không có kiểu nên mọi ô là chữ.
- `STRING`: mọi ô là chữ.
- `INFER`: ô chưa có kiểu được ép theo kiểu suy ra của cột.

Hệ thống MUST NOT tự đổi nghĩa của giá trị ngoài các tuỳ chọn trên.

#### Scenario: JSON giữ số và mã có số 0 đầu
- **WHEN** dataset JSON là `[{"price":12.50,"code":"00123","ok":true}]`, và client gọi convert sang `JSON` với mặc định
- **THEN** file là `[{"price":12.50,"code":"00123","ok":true}]`

#### Scenario: CSV sang JSON với INFER
- **WHEN** dataset CSV là `id,code\n1,00123\n2,00456\n`, và client gọi convert sang `JSON` với `output.json.typing` là `INFER`
- **THEN** file là `[{"id":1,"code":"00123"},{"id":2,"code":"00456"}]`

#### Scenario: CSV có công thức được chặn khi ghi CSV
- **WHEN** dataset CSV có ô `note` là `=HYPERLINK("x")`, và client gọi convert sang `CSV` với mặc định
- **THEN** ô đó trong file là `'=HYPERLINK("x")`
- **AND** khi `output.csv.formulaGuard` là `false`, ô đó giữ nguyên `=HYPERLINK("x")`

### Requirement: Lỗi trước khi tải về
Mọi lỗi phát hiện được trước khi ghi byte đầu tiên SHALL trả `application/problem+json`, không kèm `Content-Disposition`:

| Trường hợp | Response |
|---|---|
| dataset không có hoặc đã hết hạn | `404 DATASET_NOT_FOUND` |
| nguồn đọc lỗi với các tuỳ chọn đó | `422` với mã tương ứng: `FILE_PARSE_ERROR`, `FILE_EMPTY`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `CONFIG_INVALID` |
| `output` sai | `422 CONFIG_INVALID`, `errors[]` nêu tuỳ chọn sai |
| đích XLSX mà dataset vượt 1 048 575 row, hoặc có ô dài hơn 32 767 ký tự | `422 LIMIT_EXCEEDED` |
| body thiếu `source` hoặc `output`, hoặc JSON hỏng | `400 REQUEST_INVALID` |

#### Scenario: Tuỳ chọn của định dạng khác
- **WHEN** client gọi convert với `output` là `{"format":"JSON","csv":{"delimiter":"SEMICOLON"}}`
- **THEN** hệ thống trả `422` với `code` là `CONFIG_INVALID`, và `errors[0].message` là `output.csv applies to CSV only.`

#### Scenario: Ô quá dài cho XLSX
- **WHEN** dataset CSV có cột `note` với một ô dài 40 000 ký tự, và client gọi convert sang `XLSX`
- **THEN** hệ thống trả `422` với `code` là `LIMIT_EXCEEDED`, và `detail` là `Column "note" has a value longer than 32767 characters, more than an XLSX cell holds.`

#### Scenario: Dataset đã xoá
- **WHEN** client xoá dataset rồi gọi convert với `datasetId` đó
- **THEN** hệ thống trả `404` với `code` là `DATASET_NOT_FOUND`

### Requirement: Mã lỗi của endpoint converter
`POST /api/converter/convert` SHALL chỉ trả các `code` sau, ngoài các ngoại lệ chung của spec `api-errors`: `REQUEST_INVALID`, `DATASET_NOT_FOUND`, `FILE_PARSE_ERROR`, `FILE_EMPTY`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `CONFIG_INVALID`, `EXPORT_FAILED`, `SERVER_BUSY`.

#### Scenario: Body không phải JSON hợp lệ
- **WHEN** client gọi convert với body `{"source":`
- **THEN** hệ thống trả `400` với `code` là `REQUEST_INVALID`
