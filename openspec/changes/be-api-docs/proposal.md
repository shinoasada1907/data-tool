## Why

Người dùng muốn **nhìn thấy trực tiếp** toàn bộ API đang chạy (có bao nhiêu endpoint, nhận gì, trả gì) và thử gọi ngay trên trình duyệt. Hiện chỉ có Postman collection và contract viết tay trong design của be-f01. Khi thêm các tính năng F04–F11, danh sách endpoint sẽ tăng dần, nên cần một trang tài liệu tự cập nhật theo code.

## What Changes

- Thêm Swagger UI qua thư viện `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1` (bản này build trên Spring Boot 4.1.0).
  - Giao diện: `/swagger-ui.html`.
  - Tài liệu OpenAPI dạng JSON: `/v3/api-docs`.
- Tài liệu có tên, phiên bản và mô tả API. Mô tả nêu định dạng lỗi chung (ProblemDetail + `code`).
- Mỗi endpoint có tóm tắt, và được gom vào nhóm "Import sessions".
- Endpoint upload hiện nút chọn file, nên thử được ngay trên trình duyệt.
- Có test tự động kiểm tra tài liệu luôn liệt kê đủ các endpoint.

## Capabilities

### New Capabilities
- `api-docs`: tài liệu API trực tuyến (Swagger UI và OpenAPI JSON), tự sinh từ code.

### Modified Capabilities
(không có)

## Impact

- **Code**:
  - `apps/api/pom.xml` (thêm springdoc);
  - `MAIN/api/common/OpenApiConfig.java` (mới);
  - thêm annotation mô tả vào `ImportSessionController` và `SourcePreviewController`.
- **Dependencies**: springdoc kéo theo swagger-core. Nếu swagger-core kéo Jackson 2 (`com.fasterxml.jackson.core:jackson-databind`) thì điều đó **lệch** Global Constraint "không thêm Jackson 2" của F01. Lý do có constraint đó là rủi ro Hibernate chọn FormatMapper Jackson 2 cho cột jsonb. Test `source_schema_is_stored_as_a_json_object_and_read_back` (kiểm `jsonb_typeof = 'object'`) canh đúng rủi ro này. Kết quả kiểm ghi ở tasks.md.
- **Bảo mật**: V0.1 chỉ chạy local, nên bật tài liệu ở mọi môi trường. Khi deploy thật cần cân nhắc tắt (`springdoc.api-docs.enabled=false`).
- **Nhánh**: làm chung nhánh `chore/be-api-tooling` với Postman collection (công cụ dev), để chỉ cần merge một lần.
