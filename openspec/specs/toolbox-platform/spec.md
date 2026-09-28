# toolbox-platform Specification

## Purpose
TBD - created by archiving change core-01-toolbox-restructure. Update Purpose after archive.
## Requirements
### Requirement: Tên cấu hình của toolbox và biến môi trường cũ
Hệ thống SHALL đọc cấu hình dưới prefix `toolbox.*` như bảng dưới. Mỗi giá trị lấy từ biến môi trường mới (`TOOLBOX_*`) nếu có. Nếu không có, hệ thống SHALL lấy từ biến môi trường cũ (`IMPORTER_*`). Nếu cả hai đều không có, dùng giá trị mặc định.

| Cấu hình | Biến mới | Biến cũ | Mặc định |
|---|---|---|---|
| `toolbox.storage.dir` | `TOOLBOX_STORAGE_DIR` | `IMPORTER_STORAGE_DIR` | `${java.io.tmpdir}/universal-importer` |
| `toolbox.importer.cleanup.session-ttl` | `TOOLBOX_IMPORTER_SESSION_TTL` | `IMPORTER_SESSION_TTL` | `24h` |
| `toolbox.format.xlsx.max-uncompressed-size` | `TOOLBOX_XLSX_MAX_UNCOMPRESSED_SIZE` | `IMPORTER_XLSX_MAX_UNCOMPRESSED_SIZE` | `200MB` |
| `toolbox.format.xlsx.max-inflate-ratio` | `TOOLBOX_XLSX_MAX_INFLATE_RATIO` | `IMPORTER_XLSX_MAX_INFLATE_RATIO` | `100` |
| `toolbox.format.xlsx.max-entries` | `TOOLBOX_XLSX_MAX_ENTRIES` | `IMPORTER_XLSX_MAX_ENTRIES` | `10000` |
| `spring.servlet.multipart.max-file-size` | `TOOLBOX_MAX_FILE_SIZE` | `IMPORTER_MAX_FILE_SIZE` | `20MB` |
| `spring.servlet.multipart.max-request-size` | `TOOLBOX_MAX_REQUEST_SIZE` | `IMPORTER_MAX_REQUEST_SIZE` | `21MB` |

Các key `importer.*` cũ MUST NOT còn được đọc. Chỉ biến môi trường cũ được giữ.

#### Scenario: Biến môi trường cũ vẫn có hiệu lực
- **WHEN** ứng dụng khởi động với `IMPORTER_STORAGE_DIR=/data/udt` và không có `TOOLBOX_STORAGE_DIR`
- **THEN** file upload được lưu dưới `/data/udt`

#### Scenario: Biến mới được ưu tiên
- **WHEN** ứng dụng khởi động với `TOOLBOX_STORAGE_DIR=/data/new` và `IMPORTER_STORAGE_DIR=/data/old`
- **THEN** file upload được lưu dưới `/data/new`

#### Scenario: TTL cũ không đơn vị vẫn là giờ
- **WHEN** ứng dụng khởi động với `IMPORTER_SESSION_TTL=24`
- **THEN** TTL của session Importer là 24 giờ

#### Scenario: Giới hạn zip của XLSX đặt qua tên mới
- **WHEN** ứng dụng khởi động với `TOOLBOX_XLSX_MAX_ENTRIES=5`, và client upload một workbook có 12 entry
- **THEN** hệ thống trả `422` với `code` là `FILE_PARSE_ERROR`

### Requirement: Ranh giới module được kiểm tự động
Bộ test của BE SHALL có một test kiến trúc. Test này MUST thất bại khi code vi phạm bất kỳ luật nào sau:
1. Lớp trong `com.universaldatatools.core..`, trừ `core.format..`, phụ thuộc thứ gì ngoài `java..` và `com.universaldatatools.core..`. Từ khi có RE2J thì được phụ thuộc thêm `com.google.re2j..`.
2. Lớp trong `core.format..` phụ thuộc thứ gì ngoài `java..`, `core..`, và các thư viện định dạng `org.apache.commons.csv..`, `org.apache.commons.compress..`, `org.dhatim.fastexcel..`, `tools.jackson..`.
3. Lớp trong `core..` phụ thuộc `org.springframework..`, `jakarta..`, `platform..` hoặc `tools..`.
4. Lớp trong `platform..` phụ thuộc `tools..`.
5. Lớp trong `tools.X..` phụ thuộc `tools.Y..` với X khác Y.
6. Bên trong một tool, dependency đi ngược chiều `api → application → domain ← infrastructure`, hoặc có lớp import `infrastructure`, hoặc `tools.*.domain..` phụ thuộc thứ gì ngoài `java..`, `core..` và chính tool đó.
7. `@Scheduled`, `@EnableScheduling` hoặc `SchedulingConfigurer` nằm ngoài `platform..` và `tools.*.infrastructure..`.

#### Scenario: Tool import tool khác bị chặn
- **WHEN** một lớp trong `com.universaldatatools.tools.converter` import một lớp của `com.universaldatatools.tools.importer`
- **THEN** test kiến trúc thất bại, và thông báo nêu tên hai lớp đó

#### Scenario: Core dùng Spring bị chặn
- **WHEN** một lớp trong `com.universaldatatools.core.table` có annotation `@Component`
- **THEN** test kiến trúc thất bại

### Requirement: Đổi tên không đổi hành vi API của Importer
Sau khi đổi package, mọi endpoint `/api/import-sessions/**` SHALL giữ nguyên:
- path và method;
- JSON request/response;
- mã lỗi và HTTP status;
- tên file export.

Tài liệu OpenAPI của các endpoint này SHALL giống hệt bản trước khi đổi tên. Được khác đúng hai chỗ: `info.title`, và phần chia nhóm.

#### Scenario: Hợp đồng OpenAPI của Importer không đổi
- **WHEN** so `/v3/api-docs/importer` sau khi đổi tên với snapshot chụp trước khi đổi tên, bỏ qua `info`
- **THEN** hai tài liệu giống nhau

#### Scenario: Luồng import đầy đủ vẫn chạy
- **WHEN** client upload `customers.csv` (fixture e2e), đặt schema, mapping, transformation, validation, process rồi tải JSON
- **THEN** mọi bước trả đúng status và body như trước khi đổi tên, và file JSON tải về giống hệt từng byte

