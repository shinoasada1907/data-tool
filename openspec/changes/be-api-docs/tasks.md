# BE API docs (Swagger UI) — Implementation Plan

> **For agentic workers:** làm theo TDD; tick checkbox ngay khi xong; chỗ nào làm khác kế hoạch thì gạch ngang và ghi **LÝ DO**.

**Goal:** Swagger UI tại `/swagger-ui.html` và OpenAPI JSON tại `/v3/api-docs`, sinh từ code, kèm test canh giữ.

**Spec:** `openspec/changes/be-api-docs/specs/api-docs/spec.md`. Ràng buộc chung như các change BE trước (be-f01 design D1–D14).

## 1. Tài liệu API

**Files:**
- Modify: `apps/api/pom.xml`
- Create: `MAIN/api/common/OpenApiConfig.java`
- Modify: `MAIN/api/importsession/ImportSessionController.java`, `MAIN/api/importsession/SourcePreviewController.java` (annotation mô tả)
- Test: `TEST/api/common/ApiDocsIntegrationTest.java`

- [x] 1.1 Viết `ApiDocsIntegrationTest` (`@SpringBootTest(RANDOM_PORT)`, Testcontainers, `HttpTestClient`):
  | Case | Mong đợi |
  |---|---|
  | `GET /v3/api-docs` | 200; `info.title` = `Universal Importer API`; `paths` có đủ 3 đường dẫn của spec |
  | `POST /api/import-sessions` trong tài liệu | request body `multipart/form-data`, field `file` có `format: binary` |
  | `GET /swagger-ui.html` | ~~client đi theo redirect, trang cuối trả 200~~ 302 tới `/swagger-ui/index.html`, rồi trang đó trả 200 và chứa `Swagger UI`. **LÝ DO**: HTTP client của test không tự đi theo redirect, nên kiểm từng bước cho tường minh |
- [x] 1.2 Chạy `./mvnw -q test -Dtest=ApiDocsIntegrationTest`. Mong đợi: FAIL (404, chưa có springdoc).
- [x] 1.3 Thêm `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`, `OpenApiConfig` (bean `OpenAPI` với tiêu đề, phiên bản, mô tả), và `@Tag`/`@Operation` cho các controller.
- [x] 1.4 Chạy lại lệnh ở 1.2. Mong đợi: PASS.
- [x] 1.5 Kiểm Jackson 2: `./mvnw dependency:tree -Dincludes=com.fasterxml.jackson.core:jackson-databind`. Ghi kết quả vào đây. Nếu có Jackson 2 thì chạy toàn bộ test, nhất là `JpaImportSessionRepositoryTest`, để chắc cột jsonb vẫn lưu dạng object.
  - Kết quả: **có** Jackson 2, `com.fasterxml.jackson.core:jackson-databind:2.21.5`, kéo vào qua swagger-core. Lệch Global Constraint "không thêm Jackson 2" của F01, người dùng đã được báo trước và đồng ý (2026-09-26). Toàn bộ 233 test vẫn xanh, gồm `JpaImportSessionRepositoryTest` (cột jsonb vẫn lưu dạng object) và các test JSON của API (API vẫn dùng Jackson 3).
- [x] 1.6 Chạy `./mvnw verify`. Mong đợi: mọi test xanh, gồm ArchitectureTest.
  - Kết quả: 233 test, 0 failure, BUILD SUCCESS.
- [x] 1.7 Chạy app thật, mở `/swagger-ui.html` và `/v3/api-docs` để kiểm tận mắt.
  - Kết quả (database tạm, cổng 18080): `/swagger-ui.html` trả 302 tới `/swagger-ui/index.html` (200). `/v3/api-docs` là OpenAPI 3.1.0, "Universal Importer API 0.1", có 3 endpoint kèm tóm tắt.
- [x] 1.8 Commit: `feat(api): Swagger UI and OpenAPI docs`
- [ ] 1.9 Hỏi người dùng trước khi merge nhánh `chore/be-api-tooling`. Archive change này trên nhánh **trước** khi merge.
