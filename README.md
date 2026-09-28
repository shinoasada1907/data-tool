# Universal Data Tools

Bộ công cụ dữ liệu trên website public, mọi tool dùng chung một core (thiết kế: `openspec/changes/core-01-toolbox-restructure/design.md`). Tool đang chạy: **Universal Importer** (Tool 01).

Universal Importer nhận một file CSV hoặc XLSX. Người dùng mô tả dữ liệu mình cần (schema), ghép cột trong file vào schema, làm sạch dữ liệu và đặt luật kiểm tra. Sau đó hệ thống xử lý toàn bộ file, cho xem row hợp lệ và row lỗi, rồi xuất dữ liệu hợp lệ ra JSON/CSV, kèm báo cáo lỗi dạng CSV.

| Thư mục | Nội dung |
|---|---|
| `apps/api` | Backend: Java 21, Spring Boot, PostgreSQL. |
| `apps/web` | Frontend: React, TypeScript, Vite. Xem thêm [`apps/web/README.md`](apps/web/README.md). |
| `openspec/specs` | Đặc tả hệ thống **đang chạy**, theo từng capability. |
| `openspec/changes` | Thay đổi đang làm; `archive/` giữ design và kế hoạch của các thay đổi đã xong. |

## Yêu cầu

- Java 21
- Docker: chạy PostgreSQL cho app, và Testcontainers cho test của backend
- Node.js và pnpm (frontend)

## Chạy demo

Mỗi lệnh chạy trong một terminal riêng, bắt đầu từ gốc repo (hai lệnh sau giữ terminal cho tới khi tắt):

```bash
docker compose up -d                                # PostgreSQL 17 ở localhost:5432

cd apps/api && ./mvnw spring-boot:run               # API ở http://localhost:8080 (Windows: mvnw.cmd spring-boot:run)

cd apps/web && pnpm install && pnpm dev             # giao diện ở http://localhost:5173
```

Mở `http://localhost:5173`. Vite proxy chuyển các request `/api` tới `API_PROXY_TARGET` (mặc định `http://localhost:8080`). Trình duyệt chỉ gọi một origin, nên backend không cần và **không bật** CORS.

Tài liệu API: Swagger UI tại `http://localhost:8080/swagger-ui.html`, OpenAPI JSON tại `/v3/api-docs`.

## Demo bằng curl

Dùng file mẫu `apps/api/src/test/resources/fixtures/e2e/customers.csv` (4 row: 1 hợp lệ, 3 lỗi). Chạy từ gốc repo, bằng bash hoặc Git Bash.

```bash
API=http://localhost:8080/api/import-sessions

# 1. Upload: trả 201, session ở trạng thái CONFIGURING
UPLOAD=$(curl -s -F file=@apps/api/src/test/resources/fixtures/e2e/customers.csv $API)
ID=$(echo "$UPLOAD" | sed -nE 's/.*"id":"([0-9a-f-]{36})".*/\1/p')
[ -n "$ID" ] && echo "$ID" || echo "Upload thất bại: $UPLOAD"

# 2. Xem trước các cột và vài row đầu
curl -s "$API/$ID/preview?limit=5"

# 3. Schema, mapping, transformation, validation (mỗi lệnh trả 200)
curl -s -X PUT $API/$ID/schema -H 'Content-Type: application/json' -d '{"fields":[
  {"name":"name","type":"string","required":true,"order":0},
  {"name":"email","type":"email","required":true,"order":1},
  {"name":"dob","type":"date","required":false,"order":2},
  {"name":"active","type":"boolean","required":false,"order":3},
  {"name":"score","type":"number","required":false,"order":4},
  {"name":"country","type":"string","required":false,"order":5}]}'

curl -s -X PUT $API/$ID/mapping -H 'Content-Type: application/json' -d '{"mappings":[
  {"targetField":"name","mappingType":"SOURCE_COLUMN","sourceColumn":"Name"},
  {"targetField":"email","mappingType":"SOURCE_COLUMN","sourceColumn":"Email"},
  {"targetField":"dob","mappingType":"SOURCE_COLUMN","sourceColumn":"Birth Date"},
  {"targetField":"active","mappingType":"SOURCE_COLUMN","sourceColumn":"Active"},
  {"targetField":"score","mappingType":"SOURCE_COLUMN","sourceColumn":"Score"},
  {"targetField":"country","mappingType":"CONSTANT","constantValue":"VN"}]}'

curl -s -X PUT $API/$ID/transformations -H 'Content-Type: application/json' -d '{"transformations":[
  {"targetField":"name","order":0,"type":"trim"},
  {"targetField":"email","order":0,"type":"trim"},
  {"targetField":"email","order":1,"type":"lowercase"},
  {"targetField":"dob","order":0,"type":"dateFormat","params":{"inputFormat":"dd/MM/yyyy","outputFormat":"yyyy-MM-dd"}}]}'

curl -s -X PUT $API/$ID/validations -H 'Content-Type: application/json' \
     -d '{"validations":[{"targetField":"email","type":"unique"}]}'

# 4. Xử lý: total 4, valid 1, invalid 3
curl -s -X POST $API/$ID/process

# 5. Xem kết quả theo trang; lọc row lỗi theo field hoặc mã lỗi
curl -s "$API/$ID/result?view=valid"
curl -s "$API/$ID/result?view=invalid&code=VALIDATION_TYPE"

# 6. Tải file
curl -s -OJ "$API/$ID/export?format=json"     # customers-valid.json
curl -s -OJ "$API/$ID/export?format=csv"      # customers-valid.csv (UTF-8 có BOM, mở được bằng Excel)
curl -s -OJ "$API/$ID/errors/export"          # customers-errors.csv
```

Nếu dùng PowerShell, gọi `curl.exe` và gửi body tiếng Việt từ file, bằng `--data-binary @body.json`. Tham số `-d` bị đổi sang code page của Windows nên hỏng UTF-8.

## Biến môi trường

**Backend** (`apps/api`):

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/universal_importer` | Kết nối PostgreSQL |
| `DB_USERNAME` / `DB_PASSWORD` | `importer` / `importer` | Tài khoản DB (khớp `docker-compose.yml`) |
| `TOOLBOX_MAX_FILE_SIZE` | `20MB` | Dung lượng file upload tối đa |
| `TOOLBOX_MAX_REQUEST_SIZE` | `21MB` | Dung lượng request multipart tối đa (chừa chỗ cho phần bao ngoài file) |
| `TOOLBOX_STORAGE_DIR` | `<thư mục tạm>/universal-importer` | Nơi lưu file gốc và kết quả, mỗi session một thư mục. Mỗi database cần một thư mục riêng (xem phần giới hạn) |
| `TOOLBOX_IMPORTER_SESSION_TTL` | `24h` | Session không có lệnh ghi quá thời gian này thì bị xoá, cả file lẫn dữ liệu. Việc dọn chạy lúc khởi động, rồi mỗi giờ một lần. Nên ghi kèm đơn vị (`24h`, `90m`, `7d`); số trần được hiểu là giờ. Dưới 1 phút thì app không khởi động |

Giới hạn file XLSX (chống zip bomb) có thêm `TOOLBOX_XLSX_MAX_UNCOMPRESSED_SIZE`, `TOOLBOX_XLSX_MAX_INFLATE_RATIO` và `TOOLBOX_XLSX_MAX_ENTRIES`; xem `apps/api/src/main/resources/application.yaml`.

Tên biến cũ `IMPORTER_*` (ví dụ `IMPORTER_STORAGE_DIR`, `IMPORTER_SESSION_TTL`) vẫn được nhận; khi đặt cả hai thì biến `TOOLBOX_*` thắng. App chạy từ lớp `com.universaldatatools.ToolboxApplication`.

**Frontend** (`apps/web/.env.example`, chép thành `.env` để đổi):

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `API_PROXY_TARGET` | `http://localhost:8080` | Đích proxy `/api` khi chạy `pnpm dev`. Chỉ dùng trong `vite.config.ts`, không bao giờ tới trình duyệt |
| `VITE_MAX_UPLOAD_MB` | `20` | Giới hạn upload kiểm ở trình duyệt; phải khớp `TOOLBOX_MAX_FILE_SIZE` |

Chạy FE không cần backend: `cd apps/web && pnpm dev:mock` (BE giả bằng MSW trong trình duyệt, chỉ có ở dev server; xem [`apps/web/README.md`](apps/web/README.md)).

## Giới hạn đã biết của V0.1

- **CSV** chỉ nhận UTF-8, phân cách bằng dấu phẩy. Khi lưu từ Excel, chọn "CSV UTF-8".
- **Số** (`number`) không nhận dấu phân cách hàng nghìn (`1,000`).
- **Xử lý chạy đồng bộ**: `POST /process` trả về khi đã xử lý xong cả file.
- Luật **`unique`** giữ các giá trị đã gặp trong RAM; giới hạn upload chặn trên lượng này.
- **Session tự xoá** sau `TOOLBOX_IMPORTER_SESSION_TTL` (24h) tính từ lần ghi cuối cùng. Chỉ xem (GET) không tính là ghi.
- **Mỗi database cần `TOOLBOX_STORAGE_DIR` riêng.** Việc dọn dẹp xoá những thư mục session mà database của chính nó không biết.
  - Lần dọn đầu tiên ghi id của database vào `{storage}/.owner`. Thư mục storage của database khác, hoặc thư mục chưa đánh dấu mà toàn chứa session lạ, sẽ không bị dọn; log báo lỗi.
  - Nếu một lượt thấy quá nhiều thư mục mồ côi cùng lúc thì dừng, không xoá gì.
  - Nếu cố tình chuyển storage sang database khác: xoá file `.owner` (database mới sẽ nhận lại storage ở lần dọn sau), hoặc dùng thư mục mới.
- **Không CORS**: frontend phải đi qua proxy, cùng origin với API.
- **Upload quá giới hạn qua Vite dev proxy** nhận `connection reset` thay vì `413 FILE_TOO_LARGE`. Gọi thẳng API vẫn nhận 413. FE chặn file quá `VITE_MAX_UPLOAD_MB` trước khi gửi, nên chỉ gặp khi giá trị đó và `TOOLBOX_MAX_FILE_SIZE` lệch nhau: giữ hai giá trị khớp nhau.
- **Số lớn**: API trả số chính xác tới từng chữ số. Client JavaScript dùng `JSON.parse` sẽ làm tròn số có hơn khoảng 15 chữ số có nghĩa. File export luôn đúng.
- **Export hỏng giữa chừng** làm kết nối bị cắt (client thấy tải thất bại), chứ không trả về một file thiếu. Nếu đặt sau reverse proxy, proxy phải nói HTTP/1.1 với backend (nginx: `proxy_http_version 1.1`). Với HTTP/1.0, một kết nối bị cắt trông giống hệt một file đã tải xong.
- Chạy **một instance**: khoá theo session nằm trong JVM.

## Chạy test

```bash
cd apps/api && ./mvnw verify      # cần Docker đang chạy (Testcontainers)
cd apps/web && pnpm test
```

Test của backend không bao giờ đụng tới thư mục storage của app đang chạy: chúng dùng thư mục tạm riêng, và việc dọn dẹp bị tắt trong test (`apps/api/src/test/resources/config/application.yaml`).
