## Why

Notion đã đổi hướng dự án (2026-09-28): Universal Importer giờ chỉ là Tool 01 của **Universal Data Tools**, một website public nhiều tool dữ liệu độc lập, dùng chung một Shared Data Core. Code BE hiện đặt mọi thứ dưới `com.universalimporter` và gắn chặt vào import session. Tool mới không có chỗ đứng, còn phần dùng chung không có ranh giới nào canh. Vì vậy phải đặt lại cây package và luật phụ thuộc trước khi viết bất kỳ tool nào. Change này cũng là nơi ghi thiết kế nền của cả toolbox.

## What Changes

- Đổi package gốc `com.universalimporter` → `com.universaldatatools`, chia thành:
  - `core.*`: Java thuần;
  - `core.format.*`: đọc/ghi file bằng thư viện, không Spring;
  - `platform.*`: Spring, phần dùng chung;
  - `tools.importer.*`: Importer V0.1, chuyển nguyên, không đổi hành vi.

  Bảng chuyển nằm ở design TD2.
- `ArchitectureTest` mới với 7 luật (TD1). Luật quan trọng nhất: `core` không dùng Spring, `platform` không biết tool nào, tool không import tool khác.
- Các parser trong `core.format` bỏ `@Component` và `@ConfigurationProperties`; bean được khai báo trong `platform.config.FormatConfig`.
- Đổi tên cấu hình:
  - `importer.*` → `toolbox.*`. Biến môi trường `TOOLBOX_*` mới được ưu tiên; `IMPORTER_*` cũ vẫn có hiệu lực.
  - `spring.application.name` → `universal-data-tools`.
- **BREAKING (chỉ tài liệu):** OpenAPI đổi tiêu đề thành `Universal Data Tools API` và chia nhóm theo tool. Path, JSON, mã lỗi, DB đều giữ nguyên.
- `design.md` là **tài liệu thiết kế nền của toolbox**, gồm:
  - quyết định TD1–TD20;
  - **API contract Toolbox v1** (đã hợp nhất với phiên FE);
  - chỗ lệch Notion;
  - phác thảo tool 07–09 và Stage 2–5 của Importer;
  - roadmap;
  - DoD của "Ổn định Shared Data Core".

## Capabilities

### New Capabilities
- `toolbox-platform`: các cam kết chung của toolbox mà người vận hành và dev cảm nhận được: tên cấu hình, biến môi trường cũ vẫn hiệu lực, ranh giới module được kiểm tự động, đổi tên không đổi hành vi API. Các change core sau thêm requirement vào capability này.

### Modified Capabilities
- `api-docs`: đổi tiêu đề tài liệu và thêm nhóm theo tool.

## Impact

- **Code**: toàn bộ `apps/api/src/main/java` và `src/test/java` đổi package. Không đổi logic.
- **Cấu hình**: `application.yaml`, `src/test/resources/config/application.yaml`, và các test đặt property `importer.*`.
- **Chạy local**: IntelliJ phải reload Maven và chạy `ToolboxApplication` thay cho `ApiApplication`. Biến môi trường `IMPORTER_*` đang dùng vẫn chạy.
- **FE**: không ảnh hưởng. Mọi path và JSON giữ nguyên.
- **DB**: không có migration.
- **Công cụ**: dùng OpenRewrite (`rewrite-maven-plugin`) một lần để chuyển package và sửa import. Không thêm dependency vào `pom.xml`.
