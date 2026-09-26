# Universal Importer — Web (FE)

Giao diện của Universal Importer: nhập file CSV hoặc XLSX, map sang schema đích, cấu hình biến đổi và kiểm tra, chạy
xử lý trên BE, xem kết quả và tải dữ liệu hợp lệ. FE không tự xử lý dữ liệu; mọi logic nằm ở BE (`apps/api`).

React 19 + TypeScript + Vite. Test bằng Vitest, Testing Library và MSW.

## Yêu cầu

- Node.js 22.13+ hoặc 24+ (Vitest 5 và jsdom 29 không chạy trên 22.0–22.12, 23, 25), pnpm 11.
- Chạy với BE thật: Java 21 và Docker (cho Postgres).

## Cài đặt

```bash
cd apps/web
pnpm install
```

## Biến môi trường

Giá trị mặc định nằm trong `.env.example`. Muốn đổi thì đặt trong `.env.local` (đã được gitignore, dùng được trên mọi shell kể cả PowerShell), ví dụ `API_PROXY_TARGET=http://localhost:8081`.

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `API_PROXY_TARGET` | `http://localhost:8080` | Đích của proxy `/api` khi chạy `pnpm dev`. Chỉ `vite.config.ts` đọc biến này, trình duyệt không thấy. |
| `VITE_MAX_UPLOAD_MB` | `20` | Giới hạn upload (MB) kiểm trước khi gửi. Phải khớp `IMPORTER_MAX_FILE_SIZE` của BE. |

## Chạy với BE thật

Ba terminal, mỗi lệnh chạy từ gốc repo (BE và FE chạy mãi cho tới khi dừng):

```bash
docker compose up -d                  # terminal 1: Postgres ở cổng 5432 (chạy nền, trả lại terminal)
cd apps/api && ./mvnw spring-boot:run # terminal 2: BE ở cổng 8080
cd apps/web && pnpm dev               # terminal 3: FE ở http://localhost:5173
```

- FE gọi BE qua proxy `/api` của Vite, cùng origin nên không cần CORS.
- BE chạy ở cổng khác thì đặt `API_PROXY_TARGET` trong `apps/web/.env.local`.
- Swagger UI của BE: http://localhost:8080/swagger-ui.html.

## Chạy không cần BE

```bash
pnpm dev:mock
```

- Service worker của MSW (`public/mockServiceWorker.js`) chạy một BE giả ngay trong trình duyệt (`src/mocks/devHandlers.ts`). Có thể đi hết luồng, kể cả tải file.
- Dữ liệu nguồn là bảng mẫu cố định theo loại file (một bảng cho CSV, một cho XLSX), không đọc nội dung file upload.
- Kết quả dựng từ bảng mẫu theo schema và mapping bạn khai; dòng mẫu thứ hai luôn lỗi ở field đầu tiên. BE giả không chạy biến đổi hay kiểm tra, nên kiểm hành vi thật thì chạy với BE thật.
- Như BE thật: sửa cấu hình sau khi chạy thì kết quả cũ không còn (phải chạy lại); lưu lại đúng cấu hình cũ thì kết quả giữ nguyên.
- Chỉ có ở dev server. Mọi bản build (`pnpm build`) đều không có code của chế độ này và không có `mockServiceWorker.js`.
- Nâng phiên bản `msw` thì chạy `pnpm dlx msw init public` để sinh lại worker (postinstall của msw bị tắt trong `pnpm-workspace.yaml`).

## Lệnh

| Lệnh | Việc |
|---|---|
| `pnpm dev` | Dev server, proxy `/api` tới BE thật |
| `pnpm dev:mock` | Dev server với BE giả |
| `pnpm test` | Chạy toàn bộ test một lần |
| `pnpm test:watch` | Chạy test ở chế độ theo dõi |
| `pnpm lint` | oxlint |
| `pnpm build` | Kiểm kiểu (`tsc -b`) rồi build vào `dist/` |
| `pnpm preview` | Xem bản build |

Test gọi API qua MSW, không cần BE. Request nào không có handler thì test fail, để không có lệnh gọi API lọt ra ngoài.

## Demo từng bước

File mẫu để thử: `apps/api/src/test/resources/fixtures/pipeline/customers-sample.csv` (6 dòng; có dòng thiếu tên, email sai, tuổi không phải số, ngày không có thật).

1. **Upload file.** Kéo-thả hoặc chọn file CSV (UTF-8, phân cách bằng dấu phẩy) hoặc XLSX (đọc sheet hiển thị đầu tiên), tối đa 20 MB.
2. **Xem trước dữ liệu.** Hiện 50 dòng đầu, đúng thứ tự cột của file. Với XLSX có thêm tên sheet.
3. **Schema đích.** Schema được sinh sẵn từ các cột, kèm kiểu đoán từ dữ liệu; có thể sửa tên, kiểu, bắt buộc, thứ tự. Với file mẫu: đặt `Email` kiểu `email`, `Tuổi` kiểu `number`, `Ngày sinh` kiểu `date`, đánh dấu `Họ tên` bắt buộc. "Tiếp" lưu schema lên BE.
4. **Mapping.** Mỗi field được map sẵn với cột cùng tên. Có thể đổi cột nguồn, dùng giá trị cố định, hoặc bỏ map field không bắt buộc. "Tiếp" lưu mapping.
5. **Biến đổi & kiểm tra.** Mỗi field có các bước biến đổi chạy theo thứ tự (`trim`, `uppercase`, `lowercase`, `defaultValue`, `dateFormat`) và rule kiểm tra (`email`, `unique`). `required` và kiểu được suy ra từ schema. Với file mẫu: thêm `trim` cho `Họ tên`, `dateFormat` với đầu vào `dd/MM/yyyy` cho `Ngày sinh`, bật `unique` cho `Email`. Bấm "Chạy xử lý".
6. **Kết quả & export.**
   - Có thẻ Tổng / Hợp lệ / Lỗi và hai tab Hợp lệ / Lỗi.
   - Dòng lỗi được tô ô sai và liệt kê từng lỗi ngay dưới dòng.
   - Có thể lọc theo field hoặc mã lỗi, và phân trang 50 dòng.
   - Tải được dữ liệu hợp lệ (JSON hoặc CSV) và báo cáo lỗi (CSV).
   - Với file mẫu:
     - dòng lỗi là 3, 4 và 6; dòng 3 có 3 lỗi;
     - dòng hợp lệ là 2, 5 và 7.

Sửa cấu hình sau khi đã chạy thì kết quả bị đánh dấu cũ: tải file bị khoá và chỉ còn nút "Chạy lại".

## Deploy

- `pnpm build` ra các file tĩnh trong `dist/`. Phục vụ chúng cùng origin với BE: reverse proxy chuyển `/api` tới BE, trình duyệt không gọi origin khác nên BE không cần CORS.
- **Proxy phải chuyển nguyên `413` của BE.** BE đọc hết body của upload quá cỡ rồi mới trả `413 FILE_TOO_LARGE` (`server.tomcat.max-swallow-size: -1`). Proxy trả lời sớm hoặc cắt kết nối thì trình duyệt chỉ thấy lỗi mạng, FE báo "Không kết nối được máy chủ". Với nginx: đặt `client_max_body_size` ít nhất bằng `IMPORTER_MAX_REQUEST_SIZE` (mặc định 21 MB); mặc định 1 MB của nginx sẽ chặn file từ 1 MB bằng một trang 413 HTML.
- Proxy dev của Vite cũng mắc lỗi này: file quá giới hạn thành lỗi mạng thay cho 413. Trong dev không gặp, vì FE chặn file quá `VITE_MAX_UPLOAD_MB` trước khi gửi.

## Cấu trúc thư mục

```
src/
  api/        # gọi BE: client (fetch + timeout), upload (XHR), tải file, DTO và mapper
  domain/     # kiểu nội bộ và luật kiểm cấu hình phía client
  wizard/     # state (useReducer + Context), guard điều hướng, khung các bước
  features/   # từng bước: upload, preview, schema, mapping, rules, run, result, export
  shared/     # UI dùng chung, chuỗi giao diện (messages.ts), định dạng
  mocks/      # fixture, handler MSW cho test, BE giả cho dev:mock
  test/       # helper chỉ dùng trong test
```

## Tài liệu thiết kế

- Yêu cầu, thiết kế và tiến độ của FE: `openspec/changes/fe-import-wizard-v0-1/`. Sau khi archive, bản chính nằm ở `openspec/specs/`.
- Contract API với BE: `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md` (ở gốc repo), mục "API contract V0.1".
