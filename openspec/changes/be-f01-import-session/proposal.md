## Why

Mọi bước của Universal Importer V0.1 (preview, cấu hình, process, export) đều gắn vào một import session được tạo từ file người dùng upload. Vì vậy phải có session, chỗ lưu file và một định dạng lỗi thống nhất trước khi làm bất kỳ feature nào khác. FE đang làm song song và chờ contract của bước upload để nối API thật.

## What Changes

- `POST /api/import-sessions` nhận multipart part `file`. BE kiểm tra đuôi file, magic bytes và dung lượng; lưu file bằng tên do server sinh; tạo session ở trạng thái `UPLOADED`.
- `GET /api/import-sessions/{id}` trả trạng thái và metadata file của session (endpoint bổ sung so với pack — xem design.md, mục "Lệch khỏi pack").
- `ImportSession` domain model với bảng chuyển trạng thái `UPLOADED → CONFIGURING ⇄ READY → PROCESSED`, và `FAILED` là trạng thái cuối.
- `FileStorage` abstraction, cùng bản cài đặt lưu trên đĩa local.
- Định dạng lỗi chung cho mọi API: ProblemDetail (RFC 9457) có thêm `code` và `errors[]`, cùng bảng mã lỗi và HTTP status. Pack để việc này ở F11; kéo lên F01 vì F01 đã phải trả lỗi.
- Nền tảng dự án:
  - Flyway V1 (bảng `import_session`).
  - Testcontainers cho test cần DB.
  - ArchUnit kiểm chiều phụ thuộc giữa các package.
  - Surefire chạy test với `-Duser.timezone=UTC`.
  - Đổi tên `compose.yaml` thành `docker-compose.yml`.
- design.md của change này là **tài liệu thiết kế nền cho cả BE V0.1**. Nó chứa API contract chính thức (đã hợp nhất với bản FE đề xuất) và câu trả lời cho Q1–Q10 của FE. Các change `be-f02` → `be-f11` tham chiếu tới nó.

## Capabilities

### New Capabilities
- `import-session`: upload file, kiểm tra loại và dung lượng file, lưu file, vòng đời trạng thái session, đọc thông tin session.
- `api-errors`: định dạng lỗi API (ProblemDetail + `code` + `errors[]`), bảng mã lỗi và HTTP status, cách xử lý lỗi không lường trước.

### Modified Capabilities
(không có — chưa có spec nào trong `openspec/specs/`)

## Impact

- **Code**: `apps/api/src/main/java/com/universalimporter/{api,application,domain,infrastructure}`; `application.yaml`; migration Flyway `V1__create_import_session.sql`.
- **Dependencies (test)**: `spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql` và `testcontainers-junit-jupiter` (do BOM của Boot quản lý, 2.0.5), `com.tngtech.archunit:archunit` 1.4.1.
- **Biến môi trường mới**:
  - `IMPORTER_MAX_FILE_SIZE` (mặc định `20MB`)
  - `IMPORTER_STORAGE_DIR` (mặc định `${java.io.tmpdir}/universal-importer`)
- **API**: 2 endpoint mới. FE (`apps/web`, change `fe-import-wizard-v0-1`) cần đặt `VITE_MAX_UPLOAD_MB=20` cho khớp giới hạn upload.
- **Hạ tầng**: `docker-compose.yml` (PostgreSQL 17) ở gốc repo.
