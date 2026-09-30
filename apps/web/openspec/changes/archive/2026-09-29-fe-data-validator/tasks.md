> Viết test trước cho hành vi mới (TDD). Xong thì `pnpm test`, `pnpm lint`, `pnpm build` phải xanh.

## 1. Khung app

- [x] 1.1 AppShell giữ các công cụ đã mở (`hidden` khi không đang mở); test "quay lại Import vẫn còn file".
  - Test dùng hai công cụ giả có bộ đếm thay cho wizard Import: kiểm được cả "giữ state" lẫn "chưa mở thì chưa mount".
  - `.page[hidden] { display: none }`. **LÝ DO:** `.page` đặt `display: flex`, đè thuộc tính `hidden` của trình duyệt.
- [x] 1.2 Đổi tên app thành "Universal Data Tools" (`messages.appName`, `index.html`); sửa các test đang kỳ vọng tên cũ.

## 2. Lớp API dùng chung

- [x] 2.1 `upload.ts`: tách `uploadFile(url, file, parse)` tổng quát. Importer vẫn dùng `uploadSourceFile` như cũ.
- [x] 2.2 `download.ts`: nhận thêm `method` và `body` JSON, để tải file bằng POST.
- [x] 2.3 `apiError.ts` đọc thêm `pointer`. `messages.ts` có thêm thông điệp cho các mã mới.
  - `describeApiError` nhận thêm `overrides`. **LÝ DO:** thông điệp chung của `FILE_PARSE_ERROR`/`FILE_UNSUPPORTED` viết cho Importer ("CSV phải UTF-8, dấu phẩy", "chỉ CSV và XLSX"), sai với dataset của toolbox.
  - Thêm `deleteQuietly` trong `client.ts` để dọn dataset/run.

## 3. DatasetSource (`src/shared/dataset`)

- [x] 3.1 `datasetApi.ts`: upload, preview, delete, `sourceOf`, hàm kiểm DTO. `checkSelectedFiles` nhận thêm bảng đuôi file.
- [x] 3.2 `DatasetSource.tsx` cùng 5 test (upload + tự nhận, đổi bảng mã, sai đuôi, lỗi đọc + "Đọc lại", xoá file).
  - Test viết **sau** code, không theo TDD. Cả 5 test xanh ngay lần chạy đầu.

## 4. Validator (`src/tools/validator`)

- [x] 4.1 `state.ts`: reducer và 9 test.
  - Thêm `revision`. **LÝ DO:** user sửa schema trong lúc đang chạy thì kết quả về tới phải bị coi là cũ ngay; chỉ đặt `stale = false` khi chạy xong là sai.
- [x] 4.2 `schema.ts` cùng 16 test: kiểm FE, `toSchemaDto`, `pointer` → ô, khớp cột, đọc/ghi file `udt.schema`.
- [x] 4.3 `validatorApi.ts`: tạo run (timeout 300s như process của Importer), trang rows, export POST, xoá run.
- [x] 4.4 `SourceStep.tsx`, `SchemaStep.tsx`, `useRunValidation.ts`.
- [x] 4.5 `ResultStep.tsx` và `src/shared/output/OutputFormatPicker.tsx`.
  - Kiểu và hằng định dạng nằm riêng ở `formats.ts`. **LÝ DO:** lint `only-export-components` (Fast Refresh).
  - `ExportPanel` có `key` theo id của run, để "Đã tải …" của run cũ không còn sau khi chạy lại.
- [x] 4.6 `ValidatorTool.test.tsx`, 5 test: đi hết luồng; `SCHEMA_INVALID` theo `pointer`; field bắt buộc thiếu cột; kết quả cũ rồi chạy lại; file schema sai.
- [x] 4.7 Đăng ký công cụ trong `tools.ts`, thêm `ValidateIcon`.

## 5. Hoàn tất

- [x] 5.1 Chạy thử với BE thật, lấy từ `dev` ở commit `372496d`, cổng 8081.
  - Gọi API bằng curl: upload CSV dấu chấm phẩy, preview, run lỗi pattern RE2 (có `pointer`), run đúng, rows lọc `code`, export `ERRORS`. Shape của mọi response khớp các hàm kiểm của FE.
  - Chạy UI bằng Chrome headless (Vite 5174): upload → Schema → Kết quả, chụp ba màn. Console không có lỗi.
  - 531 test, lint và build xanh.
- [x] 5.2 Archive change, merge vào `dev`.
