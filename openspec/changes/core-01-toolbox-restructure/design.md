## Context

- **Nguồn yêu cầu**: Notion "Universal Data Tools — Data Toolbox Platform" (sửa ngày 2026-09-28), gồm trang gốc (roadmap 01–09, Shared Core, thứ tự phát triển) và 5 trang tool 02–06. Pack V0.1 của Importer nằm dưới trang 01.
- **Vai trò của file này**: là **tài liệu thiết kế nền cho BE của toolbox**, giống vai trò của design be-f01 với Importer V0.1.
  - Các change `core-02` → `core-04` và `tool-*` dẫn chiếu quyết định theo mã `TD1`…`TD20`.
  - Mục **API contract Toolbox v1** là contract chính thức giữa BE và FE. Phiên FE (`apps/web/openspec`) đã đề xuất C1–C9 ngày 2026-09-28; contract dưới đây đã hợp nhất đề xuất đó.
  - Phần riêng của change này là đổi tên và chuyển package (TD1, TD2).
- **Quyết định sản phẩm do người dùng chốt** (brainstorming 2026-09-28):
  1. Thiết kế đầy đủ Shared Core cùng 5 tool 02–06. Các tool 07–09 và Stage 2–5 của Importer chỉ phác thảo, kèm phân định phần trùng.
  2. Mỗi tool là một trang độc lập trên **website public cho khách vãng lai, không đăng nhập**. Không có chuỗi tool: output của tool này không tự thành input của tool kia.
  3. Đổi tên trong code sang `com.universaldatatools`. Giữ nguyên repo, DB `universal_importer`, docker project và API `/api/import-sessions`.
  4. Modular monolith: một app, một Maven module, ranh giới do ArchUnit canh.
  5. Người dùng **giao toàn quyền thiết kế BE**. Chỗ nào lệch Notion vẫn phải ghi ra (mục "Lệch khỏi Notion").
- **Hiện trạng `apps/api`**: Importer V0.1 đầy đủ F01–F11. Package là `com.universalimporter.{api, application, domain, infrastructure}`, có 95 file test.

## Đánh giá hiện trạng theo tiêu chí Shared Core

**Dùng lại được ngay:**
- Domain là Java thuần, có ArchUnit canh.
- Pipeline chạy stream (`Stream<ImportRow>` + `RowResultSink`), chỉ giữ counter và giá trị unique trong RAM.
- Transformation và validation đi qua registry.
- Lỗi có cấu trúc (row, field, rule, code).
- Có `FileStorage` và `ResultStore` dạng abstraction; kết quả ghi nguyên khối; cleanup có claim storage.
- Lỗi API theo ProblemDetail + `code`.
- Export CSV có chống formula injection; lỗi giữa chừng không bao giờ làm file trông như trọn vẹn.

**Dính vào Importer, cần tách:**

| Chỗ dính | Vì sao cản tool khác | Xử lý ở |
|---|---|---|
| `SourceParser` import `domain.importsession.SourceFileType` | parser phụ thuộc package session của Importer | core-01 (chuyển), core-02 (đổi contract) |
| `ImportRow(values: List<String>)` không mang kiểu ô | Converter JSON→JSON sẽ biến số thành chuỗi | core-02 (TD5) |
| CSV chỉ UTF-8 + dấu phẩy; XLSX chỉ sheet đầu; không có JSON | Notion Converter yêu cầu delimiter/encoding/header, chọn sheet, JSON | core-02 (TD6) |
| `FieldType` chỉ có 5 kiểu, không có constraints | Validator và Schema Builder cần min/max/length/pattern/format/unique | core-03 (TD9) |
| `FieldValidator` cố định thứ tự và tên 4 rule; `RowErrorCode` là enum đóng | thêm rule phải sửa 3 chỗ | core-03 (TD10) |
| Chỉ `TypeRule` biết ép kiểu, mà nó nằm trong validation | Converter và Diff cần ép kiểu mà không mang nghĩa "validate" | core-03 (TD9) |
| Exporter nhận `List<TargetField>` + `Stream<RowResult>`; không có writer XLSX (`fastexcel` writer đang để scope `test`) | Converter, Cleaner, Diff cần ghi bảng tuỳ ý ra CSV/JSON/XLSX | core-02 (TD8) |
| Upload, storage, TTL, cleanup đều xoay quanh `ImportSession` | mỗi tool sẽ phải chép lại | core-04 (TD12, TD13, TD17) |
| `ErrorCode` là enum chứa cả mã của session Importer | tool mới phải sửa enum dùng chung | core-04 (TD16) |
| Chưa có gì bảo vệ site public (rate limit, giới hạn đồng thời, dung lượng đĩa) | site public không chịu được lạm dụng | core-04 (TD15) |

## Goals / Non-Goals

**Goals:**
- Chốt nền dùng chung cho mọi tool: kiến trúc, quy ước API, mô hình dữ liệu, dataset, run, bảo vệ site public, lỗi, dọn dẹp, test. Các change tool sau chỉ việc thêm tính năng.
- core-01 chỉ đổi tên và chuyển package, **không đổi hành vi**. Toàn bộ test hiện có vẫn xanh; API Importer giữ nguyên byte-by-byte (trừ tiêu đề OpenAPI).

**Non-Goals:**
- Theo Notion, chưa làm: đăng nhập, AI, làm việc nhóm, connector ra API/DB bên ngoài, background job bất đồng bộ, JSON lồng nhau.
- Không đổi DB, docker project, tên repo hay API `/api/import-sessions`.
- Không thiết kế FE (FE có OpenSpec riêng).

## Lệch khỏi Notion

Mỗi dòng dưới đây là một chỗ thiết kế BE làm khác Notion (theo Agent Working Rule), kèm lý do.

| # | Notion | Thiết kế | Lý do |
|---|---|---|---|
| N1 | Converter: `POST /api/converter/preview` rồi `POST /api/converter/convert`, mỗi lần gửi lại file | Upload một lần qua `POST /api/datasets`, preview bằng `GET /api/datasets/{id}/preview`. Converter, Cleaner, Validator và Diff đều tham chiếu `source: {datasetId, options}` | Đổi delimiter, encoding hay sheet không phải upload lại file 20MB. Cleaner và Diff chạy sau preview cũng không phải gửi lại. Phiên FE đề xuất (C1). |
| N2 | Cleaner: `POST /api/cleaner/preview` + `POST /api/cleaner/export` | Cleaner là một **run** giống Validator và Diff: `POST /api/cleaner/runs`, xem kết quả theo trang, export từ run | Summary tính trên toàn bộ dataset, export không phải chạy lại. Ba tool dùng chung một kiểu API (C5, C9). |
| N3 | Validator: `GET /api/validator/{jobId}/errors/export`. Diff: `GET /api/diff/{id}/rows/{key}` | `/api/{tool}/runs/{id}/…`. Diff trả field-level changes ngay trong trang rows. Export là `POST …/export` với body `output` | Key ghép khó mã hoá trong URL. Export cần options (delimiter, typing…) dùng chung DTO với Converter. |
| N4 | Sơ đồ Cleaner: Row Filters → Rename → Value Transformations → Null Normalization → Duplicate Detection | Đổi giá trị (theo thứ tự step) → bỏ row rỗng → khử trùng. Rename chỉ đổi header output | Parser vốn đã bỏ dòng trống. "Bỏ row rỗng" chỉ có nghĩa với row **thành** rỗng sau khi làm sạch, nên phải đứng sau các bước đổi giá trị. |
| N5 | Schema Builder lưu vào Schema Repository; Importer/Validator/Mock đọc từ repository | Schema lưu theo **guest token** (`X-Guest-Token`), chỉ tool Schema Builder dùng kho này. Tool khác nhận schema **inline**: FE lấy schema đã lưu rồi gửi kèm request | Site không đăng nhập thì phải có chủ sở hữu, nếu không ai cũng thấy schema của nhau. Nhận inline thì tool không phụ thuộc tool khác. Guest token là bước đệm sang tài khoản. |
| N6 | "Universal Importer có thể chọn schema đã lưu" | FE lấy schema rồi gọi các `PUT` sẵn có của Importer. BE Importer không sửa | Không cần endpoint mới. Constraint mà Importer V0.1 chưa hỗ trợ thì FE cảnh báo. |
| N7 | Sơ đồ Shared Core chỉ có Parser/Schema/Transformation/Validation/Exporter | Thêm lớp `platform`: dataset, run store, guard (rate limit, giới hạn đồng thời, dung lượng đĩa), dọn dẹp, lỗi, download | Đây mới là phần mọi tool dùng lại nhiều nhất. |
| N8 | (Brainstorming) mình từng đề xuất job bất đồng bộ trả 202 | Chạy **đồng bộ**, đúng các sequence trong Notion, bọc trong `ProcessingGate` có giới hạn | File tối đa 20MB chạy trong vài giây. Bất đồng bộ để dành cho Stage 5 (file lớn). |
| N9 | Converter: "JSON V0.1: array of flat objects" | Giữ đúng như vậy. Gặp object hay mảng lồng nhau → `422 JSON_NOT_FLAT` | Trong brainstorming mình từng đề xuất dàn phẳng `a.b.c`; bỏ, theo Notion. |
| N10 | Validator: "Unique chạy trong toàn validation session" | Có hai phạm vi unique: Importer giữ `VALID_ROWS` như V0.1, Validator dùng `ALL_ROWS` (mọi lần lặp sau lần đầu đều là lỗi) | Validator là công cụ kiểm tra chất lượng: trùng là trùng, dù row đầu có lỗi khác. |
| N11 | Schema Builder: `pattern` không nói cú pháp | Cú pháp **RE2** (thư viện RE2J): không có backreference hay lookaround | Regex do khách nhập trên site public mà chạy bằng `java.util.regex` thì dính ReDoS. RE2 chạy thời gian tuyến tính. |
| N12 | Backlog "Workbench" vẫn ghi Stage 1 là Ready/Backlog; có mục "persistence ImportTemplate/ImportJob/ImportError" và "XLSX chọn sheet" | Chỉ ghi nhận, không làm theo | Backlog đã cũ. Importer V0.1 dùng `import_session` + `import_configuration` và luật sheet hiển thị đầu tiên. Chọn sheet có ở dataset của toolbox. |

## Decisions

### TD1. Modular monolith: một app, một Maven module, ranh giới do ArchUnit canh

```
com.universaldatatools
├── ToolboxApplication
├── core                   Java thuần — chỉ JDK (cộng allowlist ở dưới), không Spring
│   ├── common             DomainException, ErrorCode/ErrorKind, ProblemItem, TextValues, ThrottledWarnings
│   ├── table              Column, Row, CellKind, TableInfo, ColumnProfile, TableReader/TableWriter (SPI), ReadOptions, KeyIndex
│   ├── schema             DataSchema, SchemaField, FieldType, FieldConstraints, SchemaDefinition, TypeConverter
│   ├── transform          Transformation (SPI), registry, engine, các transformation dựng sẵn
│   ├── validate           ValidationRule (SPI), FieldRulePlan, RowValidator, UniqueScope, các rule dựng sẵn
│   └── format             đọc/ghi thật: csv, xlsx, json (commons-csv, fastexcel, jackson) — vẫn không Spring
├── platform               Spring: phần dùng chung của mọi tool
│   ├── web                xử lý lỗi, ProblemDetail, client key, header bảo mật, OpenAPI theo nhóm, download
│   ├── guard              ProcessingGate, RateLimiter, DiskSpaceGuard
│   ├── storage            FileStorage (theo UUID), LocalFileStorage, claim storage
│   ├── dataset            upload, preview, cache inspect, TTL — /api/datasets
│   ├── run                tool_run + section NDJSON, TTL, DELETE
│   ├── identity           GuestToken → GuestKey
│   ├── output             OutputSpec → TableWriter, tên file, header download
│   ├── cleanup            dọn hết hạn và mồ côi qua mọi catalog
│   └── config             Clock, scheduling, bean cho reader/writer và giới hạn
└── tools
    ├── importer           api / application / domain / infrastructure (Importer V0.1, chuyển nguyên)
    ├── converter          api / application
    ├── validator          api / application / domain
    ├── cleaner            api / application / domain
    ├── schema             api / application / domain / infrastructure (Schema Builder)
    └── diff               api / application / domain
```

**Luật ArchUnit** (`ArchitectureTest`):
1. `core..` trừ `core.format..`: chỉ phụ thuộc `java..`, `core..`, và từ core-03 thêm `com.google.re2j..`.
2. `core.format..`: chỉ phụ thuộc `java..`, `core..`, `org.apache.commons.csv..`, `org.apache.commons.compress..`, `org.dhatim.fastexcel..`, `tools.jackson..`.
3. Không lớp nào trong `core..` phụ thuộc `org.springframework..`, `jakarta..`, `platform..` hay `tools..`.
4. `platform..` không phụ thuộc `tools..`.
5. Các `tools.(*)..` không phụ thuộc lẫn nhau: `slices().matching("..tools.(*)..").should().notDependOnEachOther()`.
6. Bên trong mỗi tool là layered: `api → application → domain ← infrastructure`. `infrastructure` không bị ai import. `tools.*.domain..` chỉ phụ thuộc `java..`, `core..` và chính tool đó.
7. `@Scheduled`, `SchedulingConfigurer`, `@EnableScheduling` chỉ nằm trong `platform..` hoặc `tools.*.infrastructure..`.

**Phương án khác:**
- Maven multi-module: loại, vì build phức tạp hơn, IntelliJ phải chạy từ module `app`, worktree nặng hơn. Với một deployable và một người làm, cái giá này chưa đáng. Ranh giới đã được ArchUnit canh, nên tách module sau này chỉ là việc cơ học.
- Mỗi tool một service: loại, vì tốn vận hành mà site giai đoạn đầu không cần.

### TD2. Đổi tên (phần riêng của core-01)

**Package.** Chỉ chuyển, không đổi tên class. Class được đổi tên ở change nào thì nằm ở change đó (ví dụ `ImportRow` → `Row` ở core-02).

| Hiện tại (`com.universalimporter.`) | Đích (`com.universaldatatools.`) |
|---|---|
| `ApiApplication` | `ToolboxApplication` |
| `domain.common.*` | `core.common.*` |
| `domain.source.*` | `core.table.*` |
| `domain.importsession.{SourceFileType, FileTypeDetector}` | `core.table.*` |
| `domain.importsession.OriginalFileName` | `core.common.OriginalFileName` |
| `domain.schema.FieldType` | `core.schema.FieldType` |
| `domain.schema.{TargetField, TargetSchema, FieldSpec}` | `tools.importer.domain.schema.*` |
| `domain.transformation.*` trừ `TransformationConfig`, `TransformationConfigValidator` | `core.transform.*` |
| `domain.transformation.{TransformationConfig, TransformationConfigValidator}` | `tools.importer.domain.transformation.*` |
| `domain.validation.*` trừ 5 lớp của dòng dưới | `core.validate.*` |
| `domain.validation.{FieldValidator, ValidationConfig, ValidationConfigCheck, ValidationConfigValidator, ValidationRuleConfig}` | `tools.importer.domain.validation.*` |
| `domain.importsession.{FileStorage, StoredEntry, InstallationRepository}` | `platform.storage.*` |
| `domain.importsession.{ImportSession, ImportSessionRepository, SessionStatus, SourceFile}` | `tools.importer.domain.importsession.*` |
| `domain.{config, mapping, pipeline, export}.*` | `tools.importer.domain.{config, mapping, pipeline, export}.*` |
| `api.common.*` | `platform.web.*` |
| `api.{importsession, mapping, process, result, schema, transformation, validation, export}.*` | `tools.importer.api.*` (giữ tên sub-package) |
| `application.**` (gồm `common.SessionLocks`) | `tools.importer.application.**` |
| `infrastructure.config.ClockConfig`, `infrastructure.scheduling.SchedulingConfig` | `platform.config.*` |
| `infrastructure.config.EngineConfig` | `tools.importer.infrastructure.config.EngineConfig` |
| `infrastructure.scheduling.SessionCleanupScheduler` | `tools.importer.infrastructure.scheduling.*` |
| `infrastructure.storage.*`, `infrastructure.persistence.JpaInstallationRepository` | `platform.storage.*` |
| `infrastructure.persistence.*` còn lại, `infrastructure.export.*`, `infrastructure.result.*` | `tools.importer.infrastructure.{persistence, export, result}.*` |
| `infrastructure.parser.CsvSourceParser` | `core.format.csv.CsvSourceParser` |
| `infrastructure.parser.xlsx.*` | `core.format.xlsx.*` |

- Các lớp chuyển vào `core.format` **bỏ** `@Component` và `@ConfigurationProperties`. Chúng được khai báo bean trong `platform.config.FormatConfig`.
  - `XlsxLimits` chỉ còn là record thường.
  - Record `platform.config.XlsxLimitsProperties` (`@ConfigurationProperties("toolbox.format.xlsx")`) chuyển thành `XlsxLimits` khi tạo bean.
- `SourceParsers` (lớp gom parser) nằm ở `tools.importer.application.importsession`. Nó nhận danh sách `SourceParser` từ bean.
- Test chuyển theo đúng bảng trên. `support/*` → `com.universaldatatools.support`.

**Cấu hình.** Tên mới, biến môi trường cũ vẫn dùng được.

| Key cũ | Key mới | Biến môi trường (mới → cũ, cái nào có trước thì dùng) |
|---|---|---|
| `importer.storage.dir` | `toolbox.storage.dir` | `TOOLBOX_STORAGE_DIR` → `IMPORTER_STORAGE_DIR` → `${java.io.tmpdir}/universal-importer` |
| `importer.cleanup.enabled` / `interval` | `toolbox.importer.cleanup.enabled` / `interval` | — |
| `importer.cleanup.session-ttl` | `toolbox.importer.cleanup.session-ttl` | `TOOLBOX_IMPORTER_SESSION_TTL` → `IMPORTER_SESSION_TTL` → `24h` |
| `importer.xlsx.*` | `toolbox.format.xlsx.*` | `TOOLBOX_XLSX_MAX_UNCOMPRESSED_SIZE` → `IMPORTER_XLSX_MAX_UNCOMPRESSED_SIZE`… (cả 3 key) |
| `spring.servlet.multipart.max-file-size` | giữ nguyên | `TOOLBOX_MAX_FILE_SIZE` → `IMPORTER_MAX_FILE_SIZE` → `20MB` |
| `spring.servlet.multipart.max-request-size` | giữ nguyên | `TOOLBOX_MAX_REQUEST_SIZE` → `IMPORTER_MAX_REQUEST_SIZE` → `21MB` |

- **Thư mục storage mặc định giữ tên `universal-importer`**, để máy đang chạy không bị mồ côi dữ liệu và `.owner` vẫn đúng.
- `spring.application.name`: `universal-data-tools`.
- OpenAPI:
  - Tiêu đề `Universal Data Tools API`.
  - Swagger UI chia nhóm: `importer` gồm `/api/import-sessions/**`, `all` gồm `/api/**`. Mỗi tool mới thêm nhóm của nó.
  - Spec `api-docs` bị MODIFIED.
- **Không đổi**: bảng DB, Flyway (migration kế tiếp bắt đầu từ `V12`), path API, JSON, mã lỗi, tên file export.

### TD3. Mô hình sản phẩm và 5 kiểu tương tác

Mọi tool đều độc lập. BE có đúng 5 kiểu tương tác, tool nào cũng thuộc một trong số đó:

| Kiểu | Ai dùng | Trạng thái lưu trên server |
|---|---|---|
| **Dataset**: file upload một lần, đọc lại theo options | mọi tool mới (Converter, Validator, Cleaner, Diff) | file + metadata, TTL trượt 24h (TD12) |
| **Stateless**: tính xong trả luôn, không lưu | Converter convert, Schema validate/xuất JSON Schema, Diff so cột | không |
| **Run**: chạy đồng bộ, lưu kết quả để xem theo trang và export | Validator, Cleaner, Diff | section NDJSON + summary, TTL trượt 24h (TD13) |
| **Tài nguyên có chủ**: CRUD theo guest token | schema đã lưu của Schema Builder | lưu lâu, xoá sau 180 ngày không dùng (TD14) |
| **Session wizard**: giữ nguyên Importer V0.1 | Importer | như hiện tại (D2, TTL 24h kể từ lần sửa cuối) |

- **Không có chuỗi tool.** Run không sinh ra dataset. Muốn đưa kết quả sang tool khác thì tải về rồi upload lại.
- id của dataset và run là UUID ngẫu nhiên (122 bit, `SecureRandom`), đồng thời là **chìa khoá**: ai có id thì đọc và xoá được. Không có endpoint liệt kê dataset hay run.
- Tài nguyên có chủ không dùng id làm chìa khoá: mọi thao tác đều phải kèm guest token của chủ (TD14).

### TD4. Quy ước API của toolbox

Kế thừa D3 và D4 của Importer, cộng thêm:

- **Path**:
  - dataset: `/api/datasets`;
  - mỗi tool: `/api/<tool>/…` với `converter`, `validator`, `cleaner`, `diff`, `schemas`;
  - run: `/api/<tool>/runs/{id}`.
- **Body**:
  - Request của tool là JSON (`application/json`). Multipart chỉ dùng để upload (`POST /api/datasets`).
  - Body JSON tối đa 1 MB, vượt thì trả `413 REQUEST_INVALID`. Riêng body chứa schema tối đa 256 KB.
- **Enum** viết HOA: `CSV|XLSX|JSON`, `COMMA`, `VALID`, `ADDED`, …
  - Ngoại lệ, giữ như Importer: field type viết thường (`string|number|boolean|date|email`), op của Cleaner theo camelCase giống transformation (`trim`, `titleCase`, …).
  - Query param nhận cả hoa lẫn thường.
- **Giá trị ô trong API luôn là `string|null`**, như trong file (C3). Response không bao giờ tự ép kiểu. Kiểu chỉ có tác dụng ở file export (TD7).
- **Phân trang**: `page` bắt đầu từ 0. `size` (và `limit` của preview) mặc định 50, tối đa 200, như D3. Response trang có `page {number, size, totalElements, totalPages}`.
- **Thời gian** dạng ISO-8601 UTC. Dataset và run luôn trả `expiresAt`.
- **Lỗi** theo D4 (ProblemDetail + `code` + `errors[]`). Mỗi phần tử `errors[]` có thêm thuộc tính tuỳ chọn `pointer` (RFC 6901) chỉ tới chỗ sai trong body.
  - `pointer` luôn tính **từ gốc body của request đó**. Khi body chính là schema (`POST /api/schemas/validate`), pointer là `/fields/2/constraints/min`. Khi schema nằm trong body (`POST /api/validator/runs`, `PUT /api/schemas/{id}`), pointer có tiền tố `/schema`. Config của Cleaner dùng tiền tố `/config`.
  - Importer không dùng `pointer`, nên response của nó không đổi.
- **429 `RATE_LIMITED` và 503 `SERVER_BUSY` luôn kèm `Retry-After`** (giây).
- **Download**:
  - Luôn là `Content-Disposition: attachment; filename*=UTF-8''…`. Tên file an toàn theo luật export của Importer.
  - Kèm `Cache-Control: no-store` và `X-Content-Type-Options: nosniff`.
  - Mọi lỗi được báo **trước byte đầu tiên**. Lỗi sau byte đầu thì huỷ kết nối, như spec `data-export`.
- **Response JSON của dataset và run** cũng kèm `Cache-Control: no-store`, vì chứa dữ liệu khách.

### TD5. Mô hình bảng dùng chung (`core.table`)

```java
public record Column(int index, String name) {}                       // đổi tên từ SourceColumn (core-02)
public enum CellKind { TEXT, NUMBER, BOOLEAN, DATE }                  // kiểu gốc của ô, khi nguồn có kiểu
public record Row(long number, List<String> cells, CellKinds kinds) { // đổi tên từ ImportRow (core-02)
    public String cell(int index);                                    // ngoài phạm vi → null
    public CellKind kind(int index);                                  // null khi nguồn không có kiểu (CSV)
}
public record TableInfo(DataFormat format, ResolvedReadOptions options, Set<String> autoDetected,
                        String sheetName, List<SheetInfo> sheets, List<Column> columns,
                        List<ColumnProfile> profiles, long rowCount, long blankRowsSkipped) {}
public interface TableReader {
    DataFormat format();
    TableInfo inspect(InputStream in, ReadOptions options);           // đọc hết một lượt: kiểm cấu trúc, đếm, profile
    Stream<Row> read(InputStream in, TableInfo info);                 // dùng options/cột đã resolve; đóng stream thì đóng luôn in
}
```

- **Ô luôn là `String` hoặc `null`** cho tới khi ép kiểu (giữ quyết định D10 của Importer).
- `CellKinds` là mảng byte gọn, `null` với CSV. Nguồn gán kiểu như sau:
  - JSON: string → `TEXT`, number → `NUMBER`, boolean → `BOOLEAN`. Chữ của ô là literal gốc (`12.50`, `1e3`, `true`).
  - XLSX: ô số → `NUMBER`; ô số có format ngày → `DATE`; boolean → `BOOLEAN`; chuỗi → `TEXT`. Công thức lấy kiểu của giá trị đã cache. Chữ của ô theo luật "Chuyển giá trị ô XLSX thành chuỗi" hiện hành.
- `number` của row luôn trỏ về đúng chỗ trong file:
  - CSV/XLSX có header: số dòng như Excel hiển thị (header là dòng 1).
  - CSV/XLSX không header: dòng 1 là dữ liệu.
  - JSON: thứ tự phần tử trong mảng, bắt đầu từ 1.
- **Dòng trống** (mọi ô `null` hoặc chỉ có khoảng trắng) của CSV/XLSX bị bỏ, nhưng vẫn giữ chỗ trong cách đánh số (luật V0.1) và được đếm vào `blankRowsSkipped`. Với JSON, object nào cũng là một row, kể cả `{}`.
- Importer tiếp tục dùng model này qua `SourceParser` cũ, bọc lại ở core-02. Hành vi không đổi.

### TD6. Tuỳ chọn đọc và nhận dạng định dạng

```
ReadOptions { sheet?, delimiter?: COMMA|SEMICOLON|TAB|PIPE, encoding?: UTF-8|UTF-16|WINDOWS-1258|WINDOWS-1252,
              hasHeader?: boolean }          // null = tự nhận / mặc định
```

- **Nhận dạng lúc upload** (theo đuôi file và magic bytes, bỏ qua MIME của client như D5):
  - `.csv`:
    - Không có byte `0x00` trong 8 KB đầu.
    - Ngoại lệ: file bắt đầu bằng BOM UTF-16 (`FF FE` hoặc `FE FF`) thì được có `0x00`.
  - `.xlsx`: bắt đầu bằng `PK\x03\x04`, qua được zip guard (`toolbox.format.xlsx.*`), và danh sách sheet đọc được.
  - `.json`: UTF-8 (có thể có BOM). Ký tự khác khoảng trắng đầu tiên phải là `[`; sai thì trả `422 FILE_PARSE_ERROR` ("JSON must be an array of objects.").
  - Đuôi khác trả `415 FILE_UNSUPPORTED`.
- **CSV**:
  - `delimiter` bỏ trống: thử `,` `;` tab `|` trên tối đa 50 record đầu, có tôn trọng quote. Chọn delimiter cho nhiều hơn 1 cột và số cột ổn định trên nhiều record nhất. Hoà thì theo thứ tự vừa kể. Không delimiter nào cho quá 1 cột thì chọn `,`.
  - `encoding` bỏ trống:
    - Có BOM UTF-8 hoặc UTF-16 thì theo BOM.
    - Không có BOM thì đọc UTF-8 chặt.
    - Không phải UTF-8 thì trả `422 FILE_PARSE_ERROR`, `detail` là `File is not valid UTF-8 (near row N). Choose the file's encoding.`
    - Không bao giờ tự đoán ra windows-125x.
  - `hasHeader` mặc định `true`. Nếu `false` thì cột tên `Column A`, `Column B`…
  - Quote theo RFC 4180, như V0.1.
- **XLSX**:
  - `sheet` là tên sheet, mặc định là sheet hiển thị đầu tiên. Được chọn cả sheet ẩn nếu gọi đúng tên.
  - Tên sheet không có trả `422 CONFIG_INVALID`.
  - `hasHeader` giống CSV.
- **JSON**:
  - Mảng các object phẳng. Giá trị hợp lệ: string, number, boolean, null.
  - Gặp object hay mảng lồng bên trong trả `422 JSON_NOT_FLAT`, `detail` là `Nested value at row N, key "k" is not supported yet.`
  - Tập cột là hợp các key, theo thứ tự gặp lần đầu. Tên cột qua `ColumnNames.normalize` như header.
  - Phần tử của mảng không phải object trả `422 FILE_PARSE_ERROR`.
  - `hasHeader` bị bỏ qua.
- **Giới hạn đọc** (`toolbox.limits.*`, chỉ áp cho dataset, Importer không bị áp):

| Key | Mặc định | Vượt thì |
|---|---|---|
| `max-rows` | 500 000 | `422 LIMIT_EXCEEDED` |
| `max-columns` | 1 000 | `422 LIMIT_EXCEEDED` |
| `max-cell-length` | 32 767 ký tự | `422 LIMIT_EXCEEDED`, `detail` nêu row và cột |

  Jackson dùng `StreamReadConstraints`: `maxNumberLength` 1 000, `maxStringLength` 1 000 000. Chuỗi dài hơn 32 767 do luật của ta chặn, để còn nêu được row và cột.
- **Tên delimiter trong API** là `COMMA|SEMICOLON|TAB|PIPE`, không phải ký tự, để khỏi phải mã hoá tab và `|` trong URL.

### TD7. Suy kiểu và typing khi ghi

- **`ColumnProfile { inferredType, emptyCount, maxLength }`** được tính ngay trong `inspect`, không tốn thêm lượt đọc.
  - `inferredType` thuộc `string|number|boolean|date|email|empty`.
  - Luật với CSV, xét trên mọi ô khác rỗng và không trim:
    - `boolean`: mọi ô là `true`/`false`, không phân biệt hoa thường. `1`/`0` là number.
    - `number`: khớp `-?(0|[1-9][0-9]{0,14})(\.[0-9]+)?`. Không có số 0 đứng đầu, nên `00123` là string. Phần nguyên tối đa 15 chữ số, vì mã số dài mà thành số thì JS phía client làm mất chính xác.
    - `date`: ISO `yyyy-MM-dd` và là ngày có thật. Không đoán `dd/MM`.
    - `email`: theo `EmailAddresses` của V0.1.
    - Còn lại là `string`. Mọi ô rỗng thì là `empty`.
  - Với JSON/XLSX, `inferredType` lấy từ `CellKind`. Cột trộn nhiều kiểu thì là `string`.
- **`typing` khi ghi JSON/XLSX**: `PRESERVE` (mặc định) | `STRING` | `INFER`.
  - `PRESERVE`: giữ đúng kiểu từng ô nếu nguồn có kiểu:
    - JSON number được ghi lại **đúng literal gốc**;
    - XLSX: ô số, ô ngày, ô boolean giữ kiểu;
    - CSV không có kiểu nên mọi ô là string.
    - Riêng dữ liệu đã qua Validator hợp lệ thì "kiểu đã biết" là kiểu của schema (TD9).
  - `STRING`: mọi ô là string, ô rỗng là `null`.
  - `INFER`: ép theo `inferredType` của cột, chủ yếu dùng cho CSV. Ô không khớp kiểu cột thì giữ string.
  - Lý do: "không tự thay đổi semantic value" (Notion Converter). Nếu mặc định mọi thứ là string thì JSON→JSON đổi số thành chuỗi, cũng là đổi nghĩa.
- **Ghi số sang XLSX**:
  - Chỉ ghi ô số khi giá trị biểu diễn đúng bằng double có ≤ 15 chữ số có nghĩa.
  - Không được thì ghi ô chữ, để không mất chính xác.
  - Ô ngày ghi dạng ngày, format `yyyy-mm-dd`.

### TD8. Writer CSV/JSON/XLSX (`core.table.TableWriter` + `core.format`)

```java
public interface TableWriter {
    DataFormat format(); String contentType(); String extension();
    RowSink open(OutputStream out, List<OutputColumn> columns, WriteOptions options) throws IOException;
}
public interface RowSink extends AutoCloseable {           // close() hoàn tất file; không gọi close = file dở
    void write(List<TypedCell> cells) throws IOException;  // TypedCell = (String text, CellKind kindOrNull)
}
```

- **CSV**:
  - RFC 4180, UTF-8.
  - Tuỳ chọn: `delimiter` (mặc định `COMMA`), `header` (mặc định `true`), `bom` (mặc định `true`, để Excel đọc đúng tiếng Việt), `formulaGuard` (mặc định `true`).
  - `formulaGuard` theo luật của spec `data-export`: thêm `'` trước giá trị bắt đầu bằng `=`, `+`, `-`, `@`, tab, CR. Chỉ áp cho ô có kiểu chữ và cho header.
- **JSON**:
  - Mảng object, key là tên cột, UTF-8, không BOM.
  - `pretty` mặc định `false`.
  - Number ghi đúng literal (xem TD7). Không có formula guard.
- **XLSX**:
  - Dùng `org.dhatim:fastexcel`, ghi stream nên RAM thấp. Đổi scope từ `test` sang `compile`.
  - Tuỳ chọn `sheetName`: mặc định là tên sheet nguồn hoặc `Sheet1`. Tên được làm sạch: tối đa 31 ký tự, thay `[]:*?/\` bằng `_`.
  - Ô chữ luôn được ghi là chuỗi, không bao giờ là công thức. Vì vậy không cần formula guard.
  - Giới hạn: 1 048 575 row dữ liệu, ô tối đa 32 767 ký tự.
- **Kiểm xong trước byte đầu tiên.** Mọi tool ghi file theo trình tự:
  1. Resolve dataset hoặc run.
  2. `inspect` hoặc đọc summary.
  3. Kiểm options.
  4. Kiểm giới hạn đầu ra dựa trên profile. Ví dụ XLSX có `rowCount` hoặc `maxLength` quá giới hạn → `422 LIMIT_EXCEEDED`.
  5. Xin permit của gate.
  6. Rồi mới mở stream.
  Parser đã đọc trót lọt một lượt ở `inspect`, nên lỗi còn có thể xảy ra giữa chừng chỉ là I/O. Khi đó huỷ kết nối, như export của Importer.
- Exporter của Importer được viết lại trên `TableWriter` ở core-02. File ra phải **giống hệt từng byte**, có golden test canh.

### TD9. Schema Core (`core.schema`)

```
DataSchema { name, fields: List<SchemaField> }                   // thứ tự field = thứ tự trong list
SchemaField { name, type: FieldType, required, constraints: FieldConstraints }
FieldConstraints { unique, min, max (BigDecimal), minLength, maxLength (int),
                   pattern (RE2), format (pattern ngày), defaultValue (String) }
```

- **Luật hợp lệ** (`SchemaDefinition.check`). Trả về *mọi* vi phạm, mỗi vi phạm kèm `pointer`:

| Luật | Mã item |
|---|---|
| `name` của schema 1–200 ký tự sau trim | `SCHEMA_NAME_INVALID` |
| Có 1–500 field | `SCHEMA_FIELDS_INVALID` |
| Tên field sau trim dài 1–100, duy nhất không phân biệt hoa thường (như V0.1) | `FIELD_NAME_INVALID`, `FIELD_NAME_DUPLICATE` |
| `type` thuộc 5 kiểu | `FIELD_TYPE_INVALID` |
| `min`/`max` chỉ cho `number`, và `min ≤ max` | `CONSTRAINT_INVALID` |
| `minLength`/`maxLength` chỉ cho `string`/`email`, trong khoảng 0…32 767, và `minLength ≤ maxLength` | `CONSTRAINT_INVALID` |
| `pattern` chỉ cho `string`/`email`, tối đa 500 ký tự, compile được bằng RE2J | `CONSTRAINT_INVALID` |
| `format` chỉ cho `date`, qua được luật `DatePatterns` của V0.1 | `CONSTRAINT_INVALID` |
| `defaultValue` ép được sang `type` và thoả các constraint khác | `CONSTRAINT_INVALID` |

- **`TypeConverter.convert(text, field)`** là nơi duy nhất ép kiểu. `TypeRule` của Importer gọi nó; hành vi V0.1 giữ nguyên.

| Kiểu | Chấp nhận | Giá trị ra | Lỗi |
|---|---|---|---|
| `number` | theo luật V0.1 | `BigDecimal` | |
| `boolean` | `true`/`false`/`1`/`0` | `Boolean` | |
| `date` | ISO, hoặc theo `format` nếu có | `LocalDate` | có `format` thì `VALIDATION_DATE_FORMAT`, không thì `VALIDATION_TYPE` |
| `email` | theo `EmailAddresses` | chuỗi | |
| `string` | mọi chuỗi | nguyên văn | |

- **`defaultValue` chỉ là metadata**:
  - Validator không tự điền giá trị (C7).
  - Khi đưa schema vào Importer, FE chuyển nó thành transformation `defaultValue`.
- **JSON Schema** (draft 2020-12, chi tiết ở tool-04). Schema xuất ra mô tả **dữ liệu đã chuẩn hoá** (ngày ISO), không mô tả dữ liệu nguồn. `format` ngày nguồn và `unique` được ghi vào extension `x-udt-*`.

### TD10. Rule engine: transformation và validation dùng chung

- **Transformation** (`core.transform`) là catalog. Mỗi tool tự dựng registry với tập con mình hỗ trợ:
  - Importer: đúng 5 transformation của V0.1 (`trim`, `uppercase`, `lowercase`, `defaultValue`, `dateFormat`), nên API không đổi.
  - Cleaner: `trim`, `uppercase`, `lowercase`, `titleCase`, `replace`, `normalizeNull`, `dateFormat`.
  - Transformation mới `titleCase`, `replace`, `normalizeNull` sống trong core (core-03). Tham số và luật chi tiết ở core-03.
- **Validation** (`core.validate`):
  - Mỗi field có một `FieldRulePlan`, dựng một lần cho mỗi lượt chạy từ `SchemaField` cùng các rule bổ sung của tool (ví dụ rule `email` gắn trên field string của Importer).
  - Thứ tự cố định:

    ```
    required → type (ép kiểu, gồm cả format ngày) → email → minLength → maxLength → pattern → min → max → unique
    ```
  - Mỗi field chỉ báo **lỗi đầu tiên**, như V0.1. Một row có thể có nhiều lỗi, mỗi field tối đa một lỗi.
  - Giá trị rỗng: field optional bỏ qua mọi rule; field required báo `VALIDATION_REQUIRED`.
  - Độ dài tính theo code point Unicode.
- **Mã lỗi theo row** vẫn là enum `RowErrorCode` trong core, vì mọi rule đều nằm trong catalog của core, không tool nào tự thêm rule.
  - Giữ nguyên 5 mã của V0.1: `TRANSFORMATION_FAILED`, `VALIDATION_REQUIRED`, `VALIDATION_TYPE`, `VALIDATION_EMAIL`, `VALIDATION_UNIQUE`.
  - Thêm: `VALIDATION_MIN`, `VALIDATION_MAX`, `VALIDATION_MIN_LENGTH`, `VALIDATION_MAX_LENGTH`, `VALIDATION_PATTERN`, `VALIDATION_DATE_FORMAT`.
  - `FieldValidator` bỏ map `CODES` và danh sách rule viết cứng. Thay vào đó mỗi rule tự trả mã của nó.
- **Phạm vi unique**:
  - `VALID_ROWS` (Importer, như V0.1): giá trị của row lỗi không chặn row sau.
  - `ALL_ROWS` (Validator): lần xuất hiện thứ hai trở đi luôn là lỗi.
  - Cả hai giữ message của V0.1, `Duplicate value; first seen in row N.`, và giữ cách so của V0.1: số so theo giá trị (`1` = `1.0`), chuỗi phân biệt hoa thường.
  - Cả hai đều lưu **hash 128-bit** của giá trị canonical (TD11), không lưu giá trị. Nhờ vậy RAM không phụ thuộc độ dài ô.
- **`pattern` dùng RE2J** (`com.google.re2j:re2j`), khớp **toàn chuỗi** (`matches`), chạy thời gian tuyến tính. Được thêm vào allowlist của `core` (TD1).

### TD11. Trạng thái của cả tập dữ liệu: `KeyIndex`

- Unique, khử trùng (Cleaner) và khớp key (Diff) đều cần nhớ những gì đã gặp trên toàn file.
- Cách làm: `KeyIndex` lưu **SHA-256 cắt còn 128 bit** của phần mã hoá có tiền tố độ dài của các giá trị, thay vì lưu chính giá trị. Nhờ vậy RAM không phụ thuộc độ dài ô.
  - Xác suất trùng hash với 10^6 khoá vào khoảng 10^-27, coi như không có.
  - Mỗi khoá tốn khoảng 50–100 byte, gồm cả overhead của `HashMap`. 500 000 row cỡ 50 MB.
- `toolbox.limits.max-rows` (TD6) là trần cứng, nên không tool nào làm server hết RAM được.
- Diff giữ thêm offset và hash của row; chi tiết ở tool-06.

### TD12. Dataset (`platform.dataset`)

- **Upload** `POST /api/datasets`:
  - Nhận dạng và kiểm định dạng (TD6).
  - Lưu file vào `{storage}/{id}/source.bin` (cùng cách đặt tên với session Importer).
  - Ghi bảng `dataset`.
  - **Không parse theo options** ở bước này, vì kết quả parse còn tuỳ delimiter và encoding người dùng chọn. Riêng XLSX thì đọc danh sách sheet.
- **Preview** `GET /api/datasets/{id}/preview`:
  - `inspect` toàn file theo options, rồi đọc `limit` row đầu.
  - Lỗi parse được trả ở đây: `FILE_PARSE_ERROR`, `FILE_EMPTY`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`.
- **Cache inspect**:
  - `TableInfo` được giữ trong một LRU trong bộ nhớ, tối đa 256 mục, khoá là `(datasetId, options đã chuẩn hoá)`.
  - Tool chạy ngay sau preview không phải inspect lại.
  - Mất cache (khởi động lại) thì inspect lại. Kết quả vẫn đúng, chỉ chậm hơn.
- **Khoá**: mỗi dataset có một `ReentrantReadWriteLock`, giữ trong map có dọn dẹp.
  - Đọc (preview, run, convert) giữ **read lock** suốt lúc stream file.
  - `DELETE` và cleanup lấy **write lock**. Cleanup gặp dataset đang bận thì bỏ qua ở lần chạy đó.
- **TTL trượt**: `expiresAt = lastUsedAt + toolbox.retention.dataset-ttl` (mặc định `24h`).
  - `lastUsedAt` được cập nhật mỗi khi dataset được dùng, nhưng tối đa một lần mỗi 10 phút (`touch-interval`).
  - Hết hạn thì trả `404 DATASET_NOT_FOUND`, kể cả khi cleanup chưa kịp xoá.
- **Bảng `dataset`** (Flyway `V12`):

  ```
  id uuid PK, original_file_name varchar(255), format varchar(8), size_bytes bigint,
  sheets jsonb NULL, created_at, last_used_at, version bigint
  ```

  Có index `last_used_at`.

### TD13. Run (`platform.run`)

- Một run chỉ được tạo khi **chạy xong và thành công**, vì chạy đồng bộ. Lỗi thì không có run nào, không để lại file.
- **Ghi nguyên khối**:
  1. Ghi các section NDJSON vào `{storage}/{runId}.staging-{nonce}/`.
  2. Rename thành `{storage}/{runId}/`.
  3. Rồi mới insert row DB.
  Không bao giờ có row DB trỏ tới file thiếu. File mồ côi (nếu có) do cleanup dọn.
- **Section** là file NDJSON, mỗi dòng một JSON. Tên và nội dung do tool quy định, ví dụ `valid.ndjson`, `added.ndjson`. `RunStore.read(runId, section, skip)` trả stream, giống `ResultStore` của Importer.
- **Bảng `tool_run`** (Flyway `V13`):

  ```
  id uuid PK, tool varchar(32), created_at, last_used_at,
  sources jsonb (datasetId, tên file, options đã resolve), config jsonb, summary jsonb, version bigint
  ```

  Có index `last_used_at`.
- Run **không phụ thuộc dataset sau khi tạo**: xoá dataset thì run vẫn xem và export được.
- TTL trượt giống dataset (`toolbox.retention.run-ttl`, mặc định `24h`). Run không có hoặc đã hết hạn trả `404 RUN_NOT_FOUND`. `DELETE` trả 204.

### TD14. Guest identity (`platform.identity`)

- Header `X-Guest-Token`:
  - Khớp `^[A-Za-z0-9_-]{32,128}$`. Thiếu hoặc sai định dạng thì trả `401 GUEST_TOKEN_INVALID`.
  - FE tự sinh 32 byte ngẫu nhiên, mã base64url, lưu localStorage.
- BE chỉ lưu `SHA-256(token)` với tên `guest_key`. Mọi thao tác trên tài nguyên có chủ đều lọc theo `guest_key`.
- Tài nguyên không phải của mình trả **404**, không trả 403, để không lộ việc tài nguyên đó có tồn tại.
- Chỉ Schema Builder dùng trong đợt này. Khi có tài khoản: gắn các `guest_key` vào account, hành vi của API không đổi.

### TD15. Bảo vệ site public (`platform.guard`)

- **Client key**:
  - Lấy từ `request.getRemoteAddr()`. IPv6 gom theo prefix /64.
  - Chạy sau reverse proxy thì bật `server.forward-headers-strategy` (`native` hoặc `framework`). Mặc định không tin `X-Forwarded-For`.
  - Không lưu và không log IP thô.
- **RateLimiter**: token bucket theo client key, giữ trong bộ nhớ, entry nhàn rỗi quá 1 giờ thì bị dọn.

| Bucket | Endpoint | Mặc định |
|---|---|---|
| `upload` | `POST /api/datasets`, `POST /api/import-sessions` | 30 / 10 phút |
| `compute` | preview dataset, convert, `POST …/runs`, `POST …/export`, `POST /api/import-sessions/{id}/process`, export của Importer, `POST /api/diff/columns`, `POST /api/schemas/json-schema` | 60 / 10 phút |
| `read` | các GET còn lại, validate schema, CRUD schema | 600 / 10 phút |

  Hết token thì trả `429 RATE_LIMITED` kèm `Retry-After`.
- **ProcessingGate**:
  - Giới hạn số thao tác nặng chạy đồng thời. Toàn server tối đa `toolbox.processing.max-concurrent`, mặc định `max(2, số CPU)`. Mỗi client key tối đa 2.
  - Chờ tối đa `acquire-timeout` (mặc định `10s`), quá thì trả `503 SERVER_BUSY` với `Retry-After: 5`.
  - Một client đã chạy đủ 2 thao tác thì trả `429 RATE_LIMITED` với `Retry-After: 5`.
  - Thao tác stream giữ permit **tới khi ghi xong byte cuối** hoặc bị huỷ.
- **DiskSpaceGuard**: dung lượng trống của storage dưới `toolbox.storage.min-free-space` (mặc định `2GB`) thì mọi upload và run trả `503 SERVER_BUSY`.
- **Giới hạn đầu vào**: TD6 cùng dung lượng file (`max-file-size`). Diff gửi hai dataset id chứ không gửi file, nên `max-request-size` không cần tăng.
- Các giới hạn này **cũng áp cho Importer**. Spec `api-errors` sẽ thêm `RATE_LIMITED` và `SERVER_BUSY` vào những endpoint liên quan (core-04).

### TD16. Mã lỗi mở rộng được

- `core.common.ErrorCode` chuyển từ enum sang **interface** `{ String name(); ErrorKind kind(); }`.
  - `ErrorKind` là enum core:

    | ErrorKind | HTTP |
    |---|---|
    | `BAD_REQUEST` | 400 |
    | `UNAUTHORIZED` | 401 |
    | `NOT_FOUND` | 404 |
    | `CONFLICT` | 409 |
    | `TOO_LARGE` | 413 |
    | `UNSUPPORTED` | 415 |
    | `UNPROCESSABLE` | 422 |
    | `RATE_LIMITED` | 429 |
    | `INTERNAL` | 500 |
    | `UNAVAILABLE` | 503 |

  - `platform.web.ErrorHttpStatus` ánh xạ từ `ErrorKind` sang HTTP status.
- Mã được khai trong các enum sau:
  - `CommonError` (core): mã chung;
  - `ImporterError`: `SESSION_*`, `RESULT_NOT_AVAILABLE`, `MAPPING_INVALID`, `SOURCE_COLUMN_NOT_FOUND`;
  - các enum riêng của từng tool.
- Một test quét mọi `ErrorCode` để chắc tên **duy nhất toàn hệ thống** và mọi mã đều có trong bảng mã của spec `api-errors`.
- `ProblemItem` thêm `pointer` (có thể `null`).

### TD17. Dọn dẹp chung (`platform.cleanup`)

- Mỗi loại tài nguyên cài SPI `ExpiringCatalog { listExpired(now, limit); tryDelete(id) }`:
  - session Importer (luật V0.1 giữ nguyên);
  - dataset;
  - run;
  - schema đã lưu (180 ngày không dùng).
- **Mồ côi**: một thư mục UUID dưới storage bị coi là mồ côi khi **không catalog nào nhận** (`StorageOwner.owns(id)`).
  - Sửa lỗi tiềm ẩn: bộ dọn mồ côi của Importer hiện chỉ hỏi bảng `import_session`, nên sẽ xoá thư mục của dataset và run. Phải gộp chung **trước khi** dataset đầu tiên được ghi ra storage (core-04).
  - Thư mục `*.staging-*` cũ hơn 1 giờ luôn bị xoá.
  - Giữ nguyên các luật an toàn của V0.1: claim `.owner`, không đi xuyên link hay junction, "hơn 10 mồ côi và quá nửa thì dừng". Chỉ khác là mẫu số là tổng số thư mục mà các catalog biết.
- Lịch chạy như V0.1: một lần lúc khởi động, sau đó mỗi giờ. Lỗi ở một mục không chặn các mục khác.

### TD18. Bảo mật và riêng tư

- Không tin tên file của client (như D5). Tên lưu trên đĩa do server sinh.
- id dataset và run lấy từ `UUID.randomUUID()` (`SecureRandom`). Không có endpoint liệt kê.
- Chặn file bom: zip guard cho XLSX; `StreamReadConstraints` cho JSON; giới hạn row, cột, độ dài ô.
- Regex dùng RE2J, nên không có ReDoS.
- CSV có formula guard. XLSX chỉ ghi ô chữ, không ghi công thức.
- Response chứa dữ liệu khách kèm `Cache-Control: no-store`. Mọi response kèm `X-Content-Type-Options: nosniff`.
- FE cần đặt `Referrer-Policy: no-referrer`, vì id dataset và run có thể nằm trong URL của FE.
- Log không chứa giá trị ô, tên cột hay nội dung schema (D13). Log không chứa IP thô.
- Actuator chỉ mở `health`.
- Người dùng xoá ngay được dataset và run bằng `DELETE`. Còn lại tự xoá sau 24 giờ không dùng.

### TD19. Quan sát

- Micrometer counter và timer theo tool: `udt.runs` (tag `tool`, `outcome`), `udt.rows.processed`, `udt.upload.bytes`, `udt.guard.rejected` (tag `reason`: `rate`, `busy`, `disk`).
- Mỗi run hoặc convert ghi một dòng log INFO: tool, id, số row, thời gian. Không ghi dữ liệu.

### TD20. Chiến lược test

- **Core**: unit test thuần JDK.
  - **Round-trip**: `read(write(x)) == x`, xét cả chữ lẫn `CellKind`, cho mọi cặp CSV/JSON/XLSX.
  - **Golden file** cho output của Importer (phải giống từng byte).
- **ArchUnit**: 7 luật của TD1.
- **API**: MockMvc + Testcontainers PostgreSQL cho mỗi endpoint.
  - Kiểm mã lỗi đã công bố, theo spec `api-errors`.
  - Test hợp đồng: `/v3/api-docs` chứa đủ các path của contract.
- **Perf smoke** (`@Tag("perf")`, không chạy mặc định; chạy bằng `./mvnw test -Dgroups=perf`):
  - CSV 200 000 row × 10 cột chuyển sang JSON dưới 15 giây, với `-Xmx512m`.
  - Diff 200 000 × 200 000 row dưới 30 giây, với `-Xmx512m`.
- Test không bao giờ dùng DB hay storage chung (luật BE-F11: `src/test/resources/config/application.yaml` tắt cleanup và dùng storage riêng).

## API contract Toolbox v1

> Contract chính thức giữa BE và FE, hợp nhất với C1–C9 của phiên FE. Đổi contract phải sửa mục này và báo phiên FE. DTO viết theo dạng `Tên { field: kiểu }`; `?` là có thể vắng hoặc `null`.

### Kiểu dùng chung

```
SourceDto        { datasetId: uuid, options?: ReadOptionsDto }
ReadOptionsDto   { sheet?: string, delimiter?: COMMA|SEMICOLON|TAB|PIPE,
                   encoding?: UTF-8|UTF-16|WINDOWS-1258|WINDOWS-1252, hasHeader?: boolean }
ResolvedOptionsDto { sheet?: string, delimiter?: …, encoding?: …, hasHeader: boolean }   // giá trị thật đã dùng
OutputDto        { format: CSV|XLSX|JSON,
                   csv?:  { delimiter?: COMMA|SEMICOLON|TAB|PIPE = COMMA, header?: boolean = true,
                            bom?: boolean = true, formulaGuard?: boolean = true },
                   json?: { pretty?: boolean = false, typing?: PRESERVE|STRING|INFER = PRESERVE },
                   xlsx?: { sheetName?: string, typing?: PRESERVE|STRING|INFER = PRESERVE } }
PageDto          { number, size, totalElements, totalPages }
ProblemItemDto   { field?, code, message, pointer? }                                   // TD4
```

### Dataset (platform)

| Method | Path | Request | Response | Mã lỗi riêng |
|---|---|---|---|---|
| POST | `/api/datasets` | multipart `file` | `201` + `Location`, `DatasetDto` | `FILE_TOO_LARGE`, `FILE_UNSUPPORTED`, `FILE_EMPTY`, `FILE_PARSE_ERROR`, `RATE_LIMITED`, `SERVER_BUSY` |
| GET | `/api/datasets/{id}` | — | `DatasetDto` | `DATASET_NOT_FOUND` |
| GET | `/api/datasets/{id}/preview` | query `limit`, `sheet`, `delimiter`, `encoding`, `hasHeader` | `DatasetPreviewDto` | `DATASET_NOT_FOUND`, `FILE_PARSE_ERROR`, `FILE_EMPTY`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `CONFIG_INVALID`, `RATE_LIMITED`, `SERVER_BUSY` |
| DELETE | `/api/datasets/{id}` | — | `204` | `DATASET_NOT_FOUND`, `SERVER_BUSY` (dataset đang được đọc) |

```
DatasetDto        { id, originalFileName, format: CSV|XLSX|JSON, sizeBytes,
                    sheets?: [{ name, visible }], createdAt, expiresAt }
DatasetPreviewDto { datasetId, format, options: ResolvedOptionsDto, autoDetected: ("delimiter"|"encoding")[],
                    sheetName?, sheets?, columns: [{ index, name, inferredType, emptyCount }],
                    totalRows, blankRowsSkipped, previewLimit, rows: [{ rowNumber, values: (string|null)[] }] }
```

### Converter (tool-02)

| Method | Path | Request | Response | Mã lỗi riêng |
|---|---|---|---|---|
| POST | `/api/converter/convert` | `{ source: SourceDto, output: OutputDto }` | `200` stream file, `Content-Disposition` `<tên gốc bỏ đuôi>.<csv\|xlsx\|json>` | `DATASET_NOT_FOUND`, `FILE_PARSE_ERROR`, `FILE_EMPTY`, `JSON_NOT_FLAT`, `LIMIT_EXCEEDED`, `CONFIG_INVALID`, `EXPORT_FAILED`, `RATE_LIMITED`, `SERVER_BUSY` |

### Run chung (Validator, Cleaner, Diff)

| Method | Path | Response |
|---|---|---|
| POST | `/api/{tool}/runs` | `201` + `Location`, `<Tool>RunDto` |
| GET | `/api/{tool}/runs/{id}` | `<Tool>RunDto` |
| GET | `/api/{tool}/runs/{id}/rows` | trang row, theo tool |
| POST | `/api/{tool}/runs/{id}/export` | `200` stream file |
| DELETE | `/api/{tool}/runs/{id}` | `204` |

Mọi `<Tool>RunDto` đều có `{ id, createdAt, expiresAt, sources: [{ role, datasetId, fileName, format, options: ResolvedOptionsDto }], summary, … }`. Id không có hoặc đã hết hạn trả `404 RUN_NOT_FOUND`.

### Validator (tool-05)

```
POST /api/validator/runs          { source: SourceDto, schema: SchemaDto }
ValidatorRunDto { …chung, schemaName, fields: string[],
                  compatibility: { matched: [{ field, column }], missingOptional: string[], extraColumns: string[] },
                  summary: { totalRows, validRows, invalidRows, errorCount,
                             errorCountsByCode: { code: n }, errorCountsByField: { field: n } } }   // tên map như importer
GET  /api/validator/runs/{id}/rows?view=VALID|INVALID&field=&code=&page=&size=
     → { view, page: PageDto, rows: [{ rowNumber, values: (string|null)[]  (theo thứ tự fields),
                                       errors: [{ field, code, rule, message, value }] }] }         // value = giá trị ô gốc
POST /api/validator/runs/{id}/export   { content: VALID|INVALID|ERRORS, output: OutputDto }
```

- Mã lỗi riêng:
  - `SCHEMA_INVALID`: `errors[]` kèm `pointer`.
  - `SCHEMA_INCOMPATIBLE`: `errors[]` có `field` là tên field required không tìm thấy cột, `code` là `FIELD_MISSING`.
  - Cùng các mã của nguồn: `DATASET_NOT_FOUND`, `FILE_PARSE_ERROR`, …

### Cleaner (tool-03)

```
POST /api/cleaner/runs  { source: SourceDto, config: {
        steps: [{ op: trim|uppercase|lowercase|titleCase|replace|normalizeNull|dateFormat,
                  columns: ["*"] | string[], params?: { … } }],
        renames?: [{ column, to }],
        removeEmptyRows?: boolean = false,
        dedupe?: { mode: NONE|FULL_ROW|KEY_COLUMNS = NONE, keyColumns?: string[] } } }
CleanerRunDto { …chung, columns: [{ index, sourceName, outputName }],
                summary: { totalRows, outputRows, removedEmpty, removedDuplicate, changedRows, changedCells,
                           byStep: [{ index, op, changedCells, failedCells }], byColumn: [{ column, changedCells }] } }
GET  /api/cleaner/runs/{id}/rows?filter=CHANGED|REMOVED|ALL&page=&size=
     → { filter, page: PageDto, rows: [{ rowNumber, status: KEPT|REMOVED_EMPTY|REMOVED_DUPLICATE,
                                         duplicateOf?: number, before: (string|null)[], after: (string|null)[],
                                         changedColumns: number[] }] }
POST /api/cleaner/runs/{id}/export   { output: OutputDto }         // chỉ row KEPT, header theo outputName
```

- Step, `dedupe` và `renames` tham chiếu cột theo **tên gốc**.
- Mã lỗi riêng: `CONFIG_INVALID` (có `errors[]` kèm `pointer`).

### Diff (tool-06)

```
POST /api/diff/columns  { old: SourceDto, new: SourceDto }
     → { common: [{ old, new }], onlyInOld: string[], onlyInNew: string[] }
POST /api/diff/runs     { old: SourceDto, new: SourceDto, keyColumns: string[],
                          options?: { ignoreWhitespace?: false, caseInsensitive?: false, ignoredColumns?: string[] } }
DiffRunDto { …chung, keyColumns: string[],
             columns: [{ name, oldName?, newName?, role: KEY|COMPARED|IGNORED|ONLY_OLD|ONLY_NEW }],
             summary: { oldRows, newRows, added, updated, deleted, unchanged } }
GET  /api/diff/runs/{id}/rows?type=ADDED|UPDATED|DELETED|UNCHANGED&page=&size=
     → { type, page: PageDto, rows: [{ type, key: (string|null)[], oldRowNumber?, newRowNumber?,
                                       oldValues?: (string|null)[], newValues?: (string|null)[],   // theo columns
                                       changedColumns: number[] }] }
POST /api/diff/runs/{id}/export  { type: ADDED|UPDATED|DELETED|UNCHANGED|SUMMARY|ALL, output: OutputDto }
```

- `keyColumns` và `ignoredColumns` là tên cột **theo file mới**. Tên được so với file cũ sau khi trim và không phân biệt hoa thường (C6).
- `422 DIFF_KEY_INVALID` kèm hai extension:
  - `keyProblems: [{ side: OLD|NEW, kind: NULL|DUPLICATE, key: (string|null)[], rowNumbers: number[] }]`, tối đa 20 phần tử, mỗi phần tử tối đa 10 `rowNumbers`;
  - `keyProblemCounts: { oldNull, oldDuplicate, newNull, newDuplicate }`.
- Cột key không có trong cả hai file trả `422 CONFIG_INVALID`.

### Schema Builder (tool-04)

```
SchemaDto { name, fields: [{ name, type: string|number|boolean|date|email, required: boolean,
            constraints?: { unique?, min?, max?, minLength?, maxLength?, pattern?, format?, defaultValue? } }] }
POST /api/schemas/validate      SchemaDto → 200 { valid: boolean, errors: ProblemItemDto[] }   // luôn 200
POST /api/schemas/json-schema   SchemaDto → 200 file application/schema+json "<name>.schema.json" | 422 SCHEMA_INVALID
— Có header X-Guest-Token (TD14):
GET    /api/schemas             → [{ id, name, fieldCount, version, updatedAt }]
POST   /api/schemas             SchemaDto → 201 SavedSchemaDto { id, version, schema, createdAt, updatedAt }
GET    /api/schemas/{id}        → SavedSchemaDto
PUT    /api/schemas/{id}        { version, schema } → 200 SavedSchemaDto | 409 SCHEMA_VERSION_CONFLICT
DELETE /api/schemas/{id}        → 204
```

- Mã lỗi riêng:
  - `GUEST_TOKEN_INVALID` (401);
  - `SCHEMA_NOT_FOUND` (404, cũng dùng khi schema không phải của mình);
  - `SCHEMA_INVALID`;
  - `LIMIT_EXCEEDED` (quá 100 schema cho mỗi token).
- File nội bộ `{ "format": "udt.schema", "version": 1, …SchemaDto }` do FE đọc/ghi; BE đặc tả định dạng ở tool-04.

### Bảng mã lỗi mới (bổ sung cho D4)

| HTTP | Code | Khi nào |
|---|---|---|
| 401 | `GUEST_TOKEN_INVALID` | Thiếu hoặc sai định dạng `X-Guest-Token` |
| 404 | `DATASET_NOT_FOUND`, `RUN_NOT_FOUND`, `SCHEMA_NOT_FOUND` | Không có, đã hết hạn, hoặc không phải của mình |
| 409 | `SCHEMA_VERSION_CONFLICT` | `PUT` với `version` cũ |
| 422 | `JSON_NOT_FLAT` | JSON có giá trị lồng |
| 422 | `LIMIT_EXCEEDED` | Vượt giới hạn row, cột, ô, XLSX output, hoặc số schema |
| 422 | `SCHEMA_INCOMPATIBLE` | Field required của schema không có cột tương ứng |
| 422 | `DIFF_KEY_INVALID` | Key rỗng hoặc trùng |
| 429 | `RATE_LIMITED` | Hết lượt, hoặc client đã chạy đủ số thao tác nặng |
| 503 | `SERVER_BUSY` | Hàng chờ đầy hoặc đĩa sắp hết |

## Tổng quan BE từng tool

| Tool | Luồng BE | Core dùng lại | Mới trong core | Change |
|---|---|---|---|---|
| 02 Converter | dataset → inspect → kiểm output → stream `TableReader` → `TableWriter` | table, format | reader JSON, writer XLSX, typing | tool-02-data-converter |
| 05 Validator | dataset + schema inline → khớp field với cột → `RowValidator` (`ALL_ROWS`) → run: `valid`/`invalid` → export | table, schema, validate, format | constraint rules, RE2J | tool-05-data-validator |
| 03 Cleaner | dataset + config → step (transform catalog) → bỏ row rỗng → dedupe (`KeyIndex`) → run: `rows` → export row KEPT | table, transform, format | `titleCase`, `replace`, `normalizeNull` | tool-03-data-cleaner |
| 04 Schema Builder | validate và xuất JSON Schema (stateless) + CRUD theo guest token | schema | JSON Schema exporter | tool-04-schema-builder |
| 06 Diff | 2 dataset → khớp cột → index file cũ (hash) → quét file mới → section `added`/`updated`/`deleted`/`unchanged` → export | table, format, KeyIndex | DiffEngine (thuộc tool) | tool-06-data-diff |

## Phác thảo Future và Stage 2–5 của Importer

Chỉ phác thảo, theo quyết định sản phẩm 1. Mỗi mục có spec riêng khi tới lượt.

### 07 — API Data Mapper
- **Việc**: map payload JSON (có lồng nhau) sang một target contract, rồi xuất JSON.
- **BE**:
  - Reader JSON **lồng nhau**: dàn phẳng theo JSON Pointer và dựng lại khi ghi. Đây là phần mở rộng TD6 đã chừa sẵn (`JSON_NOT_FLAT` sẽ có đường khác).
  - Mapping nâng cao lấy từ `core.mapping` (xem Stage 3).
  - Kiểu stateless: `POST /api/mapper/transform`.
- **Rủi ro**: ngôn ngữ biểu thức phải là grammar riêng, nhỏ, không script engine, không truy cập hệ thống.

### 08 — Mock Data Generator
- **Việc**: `SchemaDto` + số row (tối đa 100 000 trên site public) + seed → file CSV/JSON/XLSX. Stateless, stream qua `TableWriter`.
- **BE**:
  - Generator theo kiểu, tôn trọng constraint.
  - `unique` dùng `KeyIndex` và sinh lại khi trùng (có trần số lần).
  - `pattern` chỉ hỗ trợ tập con RE2 sinh được (lớp ký tự, lượng từ). Ngoài tập đó trả `CONFIG_INVALID`.
  - Cùng seed thì cùng kết quả.
- **Cần**: Schema Core (core-03).

### 09 — SQL Generator
- **Việc**: dataset + tên bảng + dialect (PostgreSQL, MySQL, SQL Server) + chế độ `INSERT|UPDATE|UPSERT` + key → file `.sql` stream. **Không kết nối DB.**
- **BE**:
  - `core.sql` lo quote identifier và escape literal theo từng dialect, có test cho ký tự đặc biệt và Unicode.
  - Gom `INSERT` theo batch.
  - Kiểu cột lấy từ schema, hoặc suy từ profile.

### Importer Stage 2–5

| Stage | Nội dung | Thiết kế định hướng |
|---|---|---|
| 2 — Templates | lưu schema, mapping, rule | Template = `SchemaDto` + mapping + transformation + validation của Importer, định dạng file `udt.import-template` v1. Trước tiên chỉ xuất/nhập file; sau đó lưu theo guest token (TD14) trong `tools.importer`. |
| 3 — Advanced Mapping | concatenate, conditional, lookup, expression | Chuyển `MappingStrategy` của Importer vào `core.mapping` và thêm strategy mới. 07 dùng lại. |
| 4 — API Import | HTTP connector, batching, retry, auth | `platform.http`: client đi ra ngoài có **chặn SSRF** (chặn IP private/loopback/link-local, pin DNS, không theo redirect sang IP private, chỉ cổng 80/443). Secret chỉ sống trong một lượt chạy, không lưu. Cần job bất đồng bộ (Stage 5). |
| 5 — Database & Scale | DB connector, upsert, background job, streaming | Connector tới DB của khách **chỉ bật ở bản self-host** (feature flag), vì trên site public vừa rủi ro SSRF vừa phải giữ credential. `platform.run` thêm trạng thái `QUEUED/RUNNING/SUCCEEDED/FAILED` và executor. `FileStorage` thêm bản cài object storage. Upsert dùng `core.sql` của 09. |

### Phân định phần trùng (ai sở hữu)

| Chủ đề | Trùng giữa | Chủ sở hữu | Ghi chú |
|---|---|---|---|
| Định nghĩa schema, constraint, file `udt.schema` | Importer Stage 2, 04, 05, 08 | model ở `core.schema`; kho lưu ở `tools.schema` | Tool khác nhận schema inline (N5) |
| Template import | Importer Stage 2 và 04 | `tools.importer` | Template tham chiếu `SchemaDto`, không trùng kho schema |
| Mapping nâng cao | Importer Stage 3 và 07 | `core.mapping`, xây trong Importer Stage 3 | 07 chỉ thêm I/O JSON lồng nhau |
| JSON lồng nhau | 02 (stage sau) và 07 | `core.format.json` | Tool nào tới trước thì xây |
| Validation nhiều rule | Importer và 05 | `core.validate` (core-03) | Importer mở API rule mới ở Stage 2+ |
| Sinh SQL | 09 và Importer Stage 5 | `core.sql`, xây trong 09 | Stage 5 chỉ thêm phần thực thi |
| Job bất đồng bộ | Stage 5 và mọi tool | `platform.run` | Thêm trạng thái; API run giữ tương thích |

## Roadmap và thứ tự change

```
core-01 đổi tên ─▶ core-02 đọc/ghi ─┬─▶ core-04 platform ─┬─▶ tool-02 Converter ─▶ tool-03 Cleaner ─┐
                                    └─▶ core-03 schema ───┴─▶ tool-05 Validator ─▶ tool-04 Schema ───┴─▶ tool-06 Diff
```

- core-03 và core-04 độc lập nhau, làm song song được. Tool-02 chỉ cần core-02 và core-04.
- **Trạng thái 2026-09-28: các change `tool-*` để Future.** Người dùng tạm dừng thiết kế chi tiết 5 tool để tiết kiệm token. Contract của chúng đã chốt ở mục "API contract Toolbox v1" và "Tổng quan BE từng tool". Khi tới lượt, mỗi tool viết một change riêng (proposal, design, specs, tasks) dựa trên hai mục đó.
- Thứ tự Notion được giữ: Core → Converter, Validator → Cleaner, Schema Builder → Diff.

**Definition of Done cho "Ổn định Shared Data Core"** (xong core-01 → core-04):
- [ ] Package theo TD1. Đủ 7 luật ArchUnit. Toàn bộ test Importer xanh. API Importer giữ nguyên (chỉ đổi tiêu đề OpenAPI).
- [ ] Reader CSV (có options), XLSX (chọn sheet), JSON phẳng. Writer CSV/JSON/XLSX. Có round-trip test cho mọi cặp.
- [ ] Schema Core và constraint rules. Importer chạy trên rule engine mới mà spec V0.1 không đổi.
- [ ] Dataset API, run store, guard (429/503), cleanup chung. Có bảng mã lỗi mới.
- [ ] Perf smoke của TD20 đạt.

## Risks / Trade-offs

- **Đổi package lớn làm xung đột với code dở của người dùng trên `dev`.**
  - Làm core-01 trong một nhánh sống ngắn, chuyển hàng loạt bằng script.
  - Trước khi merge, kiểm `git status` ở thư mục chính. Nếu người dùng có file đang sửa trong `apps/api/src` thì dừng và báo.
- **Chạy đồng bộ giữ một thread Tomcat suốt lượt chạy.** File tối đa 20MB và `ProcessingGate` có giới hạn, nên chấp nhận được. Stage 5 sẽ chuyển sang bất đồng bộ.
- **Rate limit, gate, khoá và cache inspect nằm trong bộ nhớ**, nên chỉ đúng khi chạy **một instance**.
  - Chạy nhiều instance thì cần Redis (rate limit), storage dùng chung, và sticky session hoặc cache phân tán.
  - Chưa làm; ghi lại để biết.
- **Hash 128-bit trong `KeyIndex` có xác suất trùng khác 0.** Khoảng 10^-27, chấp nhận được.
- **RE2 không có backreference hay lookaround.** Người quen regex Java có thể thấy thiếu. Đổi lại site public an toàn; message lỗi nêu rõ lý do.
- **Guest token mất khi xoá localStorage** thì mất luôn quyền tới các schema đã lưu. FE nên có nút "xuất tất cả" và hiện token để sao lưu.

## Migration Plan (core-01)

1. Chạy toàn bộ test, ghi lại số test (baseline). Chụp `/v3/api-docs` làm snapshot hợp đồng.
2. Viết `ArchitectureTest` mới cho cây package đích. Nó đỏ.
3. Chuyển từng nhóm theo bảng TD2 bằng `git mv` và script sửa `package`/`import`. Sau mỗi nhóm thì compile và chạy test.
4. Đổi config key, thêm fallback biến môi trường cũ. Đổi tên app và tiêu đề OpenAPI.
5. So `/v3/api-docs` với snapshot: chỉ khác `info.title` và phần nhóm.
6. Cập nhật README và `openspec/config.yaml`.

- **Rollback**: revert nhánh. Không có migration DB.
- IntelliJ phải reload Maven và chạy `ToolboxApplication` thay cho `ApiApplication`. Nhắc người dùng lúc merge.

## Open Questions

(không còn — mọi câu hỏi sản phẩm đã chốt trong brainstorming 2026-09-28 và trao đổi contract với phiên FE)
