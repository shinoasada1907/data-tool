## Context

- Thiết kế nền: core-01 design, mục "API contract Toolbox v1 → Validator (tool-05)", TD9, TD10 (unique `ALL_ROWS`), N10.
- Dùng: `core.schema` và `core.validate` (core-03), `platform.run` (core-06), `platform.dataset`, `platform.output` (core-04).
- Quyết định mang mã `VD1`…`VD7`.

## Goals / Non-Goals

**Goals:** kiểm một dataset theo schema inline; xem kết quả theo trang có lọc; tải file hợp lệ, file lỗi, hoặc danh sách lỗi.

**Non-Goals:**
- Tự điền `defaultValue` (C7). Đó chỉ là metadata.
- Đọc schema đã lưu của Schema Builder. FE lấy schema rồi gửi inline (N5).
- Rate limit, gate: core-05.

## Decisions

### VD1. Contract

```
POST /api/validator/runs   { source: SourceDto, schema: SchemaDto }        → 201 + Location, ValidatorRunDto
GET  /api/validator/runs/{id}                                               → ValidatorRunDto
GET  /api/validator/runs/{id}/rows?view=VALID|INVALID&field=&code=&page=&size=  → ValidatorRowsDto
POST /api/validator/runs/{id}/export  { content: VALID|INVALID|ERRORS, output: OutputDto }  → 200 file
DELETE /api/validator/runs/{id}                                             → 204

SchemaDto        { name, fields: [{ name, type: string|number|boolean|date|email, required?,
                   constraints?: { unique?, min?, max?, minLength?, maxLength?, pattern?, format?, defaultValue? } }] }
ValidatorRunDto  { id, createdAt, expiresAt,
                   sources: [{ role: "source", datasetId, fileName, format, options: ResolvedOptionsDto }],
                   schemaName, fields: string[],
                   compatibility: { matched: [{ field, column }], missingOptional: string[], extraColumns: string[] },
                   summary: { totalRows, validRows, invalidRows, errorCount,
                              errorCountsByCode: { code: n }, errorCountsByField: { field: n } } }
ValidatorRowsDto { view: VALID|INVALID, page: PageDto,
                   rows: [{ rowNumber, values: (string|null)[], errors: [{ field, code, rule, message, value }] }] }
```

- `min`/`max` là số JSON. Các field khác của schema đúng như core-03 SR1.
- `values` theo thứ tự `fields`, là giá trị ô gốc (C3). Field thiếu cột thì là `null`.
- `errors[].value` là giá trị ô gốc của field đó. Row hợp lệ có `errors: []`.
- Response JSON có `Cache-Control: no-store`.

### VD2. Thứ tự kiểm khi tạo run

1. Body: thiếu `source`, `schema` hoặc `datasetId` → `400 REQUEST_INVALID`.
2. Schema: `SchemaDefinition.check(spec, "/schema")`. Có vi phạm → `422 SCHEMA_INVALID`, `errors[]` là các vi phạm (có `pointer`). Chưa mở dataset.
3. Nguồn: `DatasetSources.open` → `DATASET_NOT_FOUND`, `FILE_PARSE_ERROR`, `CONFIG_INVALID`… như Converter.
4. Khớp cột (VD3). Thiếu cột cho field required → `422 SCHEMA_INCOMPATIBLE`.
5. Chạy (VD4), rồi commit run.

### VD3. Khớp field với cột

- Mỗi field lấy cột có tên **giống hệt**. Không có thì lấy cột **đầu tiên** có tên giống khi bỏ khoảng trắng hai đầu và không phân biệt hoa thường (`TextValues.strip`, `toLowerCase(ROOT)`).
- Mỗi cột chỉ được khớp một lần. Field được xét theo thứ tự trong schema, và mọi field được khớp chính xác trước.
- Field required không có cột → gom **mọi** field như vậy vào một lỗi `SCHEMA_INCOMPATIBLE`. Mỗi `errors[]` có `field` là tên field, `code` là `FIELD_MISSING`, message `No column matches this required field.`
- Field optional không có cột → vào `missingOptional`, giá trị luôn `null`, luôn hợp lệ.
- Cột không khớp field nào → vào `extraColumns` và bị bỏ qua.

### VD4. Chạy

- Một `FieldRulePlan` cho mỗi field, dựng một lần. Một `UniqueIndex(ALL_ROWS)` cho cả lượt.
- Mỗi row: kiểm **mọi** field (mỗi field tối đa một lỗi). Row hợp lệ khi không field nào lỗi.
- Row ghi vào section `valid` hoặc `invalid`, mỗi dòng NDJSON:
  `{"rowNumber":2,"values":["…",null],"errors":[{"field":"email","code":"VALIDATION_EMAIL","rule":"email","message":"…"}]}`.
  `value` của lỗi không lưu thêm, vì đó chính là `values[index của field]`.
- Summary đếm như Importer: `errorCountsByCode` sắp theo mã; `errorCountsByField` theo thứ tự field, chỉ gồm field có lỗi.
- `config` của run lưu `schema` đã kiểm, `compatibility`, và `maxLengths` (độ dài lớn nhất theo code point của giá trị mỗi field). Nhờ đó export không cần dataset.
- Giới hạn row và ô là của dataset (`toolbox.limits`), không có giới hạn riêng.

### VD5. Xem row

- `view` mặc định `VALID`, nhận cả chữ thường. Giá trị lạ → `400 REQUEST_INVALID`.
- `field` và `code` chỉ lọc view `INVALID`: giữ row có ít nhất một lỗi khớp **cả hai** điều kiện được cho. Với `VALID` thì bị bỏ qua.
- Không lọc: tổng lấy từ summary, bỏ qua `page*size` dòng đầu mà không parse. Có lọc: quét cả section để đếm.
- Phân trang theo spec `tool-runs`.

### VD6. Export

| `content` | Cột | Row |
|---|---|---|
| `VALID` | các field theo thứ tự schema | row hợp lệ. Kiểu ô lấy từ schema (TD7): `number` → số (text gốc), `boolean` → `true`/`false`, `date` → ngày ISO, `string`/`email` → chữ. Rỗng → ô trống |
| `INVALID` | `_row`, các field, `_errors` | row lỗi, giá trị gốc dạng chữ. `_errors` là `field: message` nối bằng `; ` |
| `ERRORS` | `row`, `field`, `code`, `rule`, `message`, `value` | mỗi lỗi một dòng, theo thứ tự row rồi thứ tự field |

- Typing của `output` áp như Converter. `PRESERVE` giữ kiểu ở cột trên; `STRING` ghi mọi ô là chữ; `INFER` ở đây giống `PRESERVE`, vì kiểu đã biết từ schema.
- Tên file: `<tên gốc bỏ đuôi>-valid.<ext>`, `-invalid`, `-errors`.
- XLSX: số row cần ghi (`validRows`, `invalidRows`, hoặc `errorCount`) vượt `1 048 575`, hay `maxLengths` vượt `32 767` → `422 LIMIT_EXCEEDED` trước byte đầu.
- `content` thiếu hoặc lạ → `400 REQUEST_INVALID`. Lỗi của `output` → `422 CONFIG_INVALID` như Converter.

### VD7. Gói code

```
tools.validator.api          ValidatorController, ValidatorDtos
tools.validator.application  ValidatorService (tạo run), ColumnMatcher, ValidatorRows (xem), ValidatorExports (export)
```

Không có `domain`: logic kiểm nằm trong core.

## Risks / Trade-offs

- **Lọc có quét cả section.** Run lớn (500 000 row) lọc theo field sẽ đọc cả file mỗi trang. Chấp nhận cho bản đầu, giống Importer.
- **Khớp cột không phân biệt hoa thường** có thể khớp nhầm khi file có hai cột chỉ khác hoa thường. Cột giống hệt luôn được ưu tiên, nên chỉ lệch khi schema dùng cách viết không có trong file.

## Migration Plan

Không có migration. Rollback: revert nhánh.

## Open Questions

(không có)
