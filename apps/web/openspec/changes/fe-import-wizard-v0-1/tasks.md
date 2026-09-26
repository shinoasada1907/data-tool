> **Cách làm**
> - Task nào có logic thì viết test trước (TDD).
> - Xong mỗi nhóm, `pnpm test`, `pnpm lint`, `pnpm build` phải xanh.
> - Nhóm 5–12 có thể làm trên nhánh riêng theo gợi ý của Notion (`feature/fe-f01-upload`, …).
> - Task nào làm khác kế hoạch: gạch task cũ và ghi **LÝ DO** ngay tại chỗ.
> - Contract: bám mục "API contract V0.1" trong `design.md`. Nếu có chỗ lệch, file BE `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md` là chuẩn.
> - BE thật: feature BE nào đã merge vào `dev` thì có thể kiểm với BE thật ngay (checklist 13.4). Luồng nhánh: feature → `dev`, rồi `dev` → `main` khi xong hết (design → Migration Plan). Tới ngày 2026-09-26, `dev` có BE-F01 (upload, `GET /api/import-sessions/{id}`), BE-F02 (đọc CSV, `GET .../preview`), BE-F03 (đọc XLSX) và Swagger UI (`/swagger-ui.html`). BE-F04 trở đi chưa bắt đầu. Endpoint chưa có trả `404 REQUEST_INVALID`, và FE phải hiển thị nó như lỗi thường (4.6).
> - **Làm theo lát cắt của từng feature** (áp dụng từ FE-F01, ngày 2026-09-26). **LÝ DO:** Agent Working Rule của pack Notion quy định: nhận FE-Fxx thì chỉ làm phạm vi feature đó và dependency bắt buộc. Vì vậy các task nền của nhóm 3 và 4 được làm dần theo từng feature. Task nào mới xong một phần thì chưa tick, và ghi rõ phần đã xong ở feature nào.

## 1. Nền tảng dự án

- [x] 1.1 Dọn template: xoá `src/assets/`, `src/App.css`, nội dung demo trong `App.tsx`, `public/icons.svg` (giữ `favicon.svg`). Trong `index.html` đặt `lang="vi"` và `<title>Universal Importer</title>`. Kiểm: `pnpm build` và `pnpm lint` qua.
- [x] 1.2 Thêm `"strict": true` tường minh vào `tsconfig.app.json` và sửa lỗi type phát sinh nếu có. Kiểm: `pnpm build` qua.
  - Thêm cả vào `tsconfig.node.json` (cho `vite.config.ts`), để hai cấu hình cùng một mức kiểm tra.
- [x] 1.3 Cài devDependencies:
  - `vitest` (chọn bản có peerDependency khớp Vite 8), `jsdom`
  - `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`
  - `msw@^2`

  Thêm khối `test` vào `vite.config.ts` (`environment: 'jsdom'`, `setupFiles: ['src/test/setup.ts']`), thêm script `test` (`vitest run`) và `test:watch`. Kiểm: smoke test render `<App/>` chạy xanh.
  - Đã cài `vitest` 5.0.1, `@testing-library/dom` (peer của React Testing Library 16), `msw` 2.15.
  - **`jsdom` được ghim ở `^29.1.1`, không dùng bản 30. LÝ DO:**
    - Vitest 5.0.1 bọc `Request` để đổi `FormData`/`Blob` của jsdom sang bản của Node. Nó lấy "impl symbol" ẩn qua own symbol của `new window.Blob()`.
    - jsdom 30 không còn để lộ symbol đó, nên mọi `FormData` có file đi qua `Request` đều crash (`Cannot read properties of undefined (reading '_buffer')`).
    - Chính Vitest được phát triển cùng `jsdom ^29.1.1`.
    - Khi nâng jsdom, chạy lại test upload trước.
  - pnpm 11 đòi quyết định về build script của `msw`: đặt `allowBuilds.msw: false` trong `apps/web/pnpm-workspace.yaml`, vì `mockServiceWorker.js` sẽ được sinh bằng `msw init` (task 3.9).
- [x] 1.4 Proxy và biến môi trường (design D16):
  - `vite.config.ts` dùng `loadEnv` đọc `API_PROXY_TARGET` (mặc định `http://localhost:8080`) cho `/api`.
  - `src/config.ts` đọc `VITE_MAX_UPLOAD_MB` (mặc định **20**, khớp `IMPORTER_MAX_FILE_SIZE` của BE) và `VITE_USE_MOCK`; khai báo kiểu `ImportMetaEnv`.
  - Tạo `.env.example`.

  Test `config.ts`: dùng mặc định khi biến vắng hoặc không hợp lệ.
  - Kiểu `ImportMetaEnv` khai báo ở `src/env.d.ts`.
  - Đã chạy thử `vite` thật: trang tải được; `/api` được proxy tới 8080 (BE tắt thì nhận `502`).
- [x] 1.5 `src/index.css`: CSS variables (màu, khoảng cách, font, trạng thái lỗi/cảnh báo/thành công), reset tối thiểu, khung app `min-width: 1024px`.
- [x] 1.6 `shared/messages.ts` và `shared/format.ts`, có test cho `format.ts`:
  - `messages.ts`: chuỗi UI tiếng Việt, cùng thông điệp cho **mọi** mã trong bảng "Mã lỗi và các mã khác" của design (lỗi API, lỗi theo row, readiness issue).
  - `format.ts`: `formatBytes`, `formatNumber` theo `vi-VN`, ví dụ `1,2 MB`, `1.200`.
  - FE-F01 đã xong: `messages.ts` (đủ mọi mã), `formatBytes` có test. `formatNumber` làm ở FE-F02, vì lúc đó mới cần hiển thị tổng số dòng.
  - FE-F02 đã xong `formatNumber`, có test; số 4 chữ số cũng có dấu chấm (`1.200`), đúng ví dụ của spec source-preview.

## 2. Chốt contract với BE

> Nhóm này đã xong ngày 2026-09-25. BE chốt "API contract V0.1" và trả lời Q1–Q10. Nguồn ban đầu là `openspec/changes/be-f01-import-session/design.md`; sau khi archive, file nằm ở `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md`. Câu trả lời đã ghi vào `design.md` → Open Questions.
>
> Các task đã chỉnh theo contract: 1.4, 1.6, 3.1, 3.2, 3.5, 3.7, 3.8, 4.1, 4.4, 4.6, 5.2, 6.1, 7.1, 7.4, 9.1, 9.2, 9.4, 10.2, 11.1, 11.2, 11.6, 12.2, 13.4. Đã bỏ: 6.3 và một phần 6.2, 11.2 (có ghi lý do tại chỗ).

- [x] 2.1 Gửi cho người phụ trách BE mục "API contract đề xuất" và danh sách Q1–Q10 trong `design.md`. BE đã nhận và trả lời đủ.
- [x] 2.2 Q1 (payload validations): trùng với đề xuất, không phải sửa `mappers.ts` hay spec `rule-config`.
- [x] 2.3 Q2, Q3 (lỗi của PUT; phân trang, lọc và `errorCounts`): trùng với đề xuất, giữ task 11.4. Điểm mới: result chưa process hoặc đã cũ trả `409 RESULT_NOT_AVAILABLE` → thêm design D18 và task 11.6.
- [x] 2.4 Q4, Q5, Q6, Q9 (`rowNumber`, `dateFormat`, định dạng error report, header trùng hoặc rỗng): đã ghi vào design. Field kiểu `date` khoá `outputFormat` (D19, spec `rule-config`). Bỏ task 6.3.
- [x] 2.5 Q7, Q8, Q10 (upload 20 MB, `413`; `rule`, `stage`, `step` và mã lỗi mới; cùng origin): `.env.example` và `messages.ts` chưa tồn tại, nên phần cập nhật được dồn vào task 1.4 và 1.6.

## 3. API client và contract

- [x] 3.1 `api/dto.ts`: khai báo đủ các kiểu trong mục "API contract V0.1" của `design.md`, gồm cả `ImportSessionDto.config` và `readiness` (V0.1 chưa dùng).
- [x] 3.2 TDD `api/client.ts` (`request()` và `ApiError` có `detail`). Ca test:
  - 2xx có JSON;
  - problem+json có `code`, `detail` và `errors[]`;
  - `404 SESSION_NOT_FOUND` và `404 REQUEST_INVALID` giữ đúng `code` để phân biệt;
  - `409 RESULT_NOT_AVAILABLE`;
  - body HTML khi `502`: `code=null`, message theo status, không lộ HTML;
  - `fetch` reject → `kind=network`; huỷ → `kind=aborted`.
  - FE-F01 đã xong:
    - `ApiError` và `errorFromHttpResponse` có test: ProblemDetail đủ trường, `errors[]` sai dạng bị bỏ qua, body HTML, JSON hỏng, `413` không có code.
    - Hai thứ này nằm ở **`api/apiError.ts`** chứ không ở `client.ts`. **LÝ DO:** upload (XHR) và `request()` (fetch) dùng chung phần parse lỗi.
  - Còn lại `request()` bằng fetch: làm ở FE-F02, cùng GET preview.
  - FE-F02 đã xong `request()`, test với MSW đủ các ca trên, thêm: body 2xx sai dạng hoặc không phải JSON → `INVALID_RESPONSE`; signal đã huỷ từ trước thì không gửi request. Đã làm mutation check (bỏ Accept, bỏ signal, bỏ validate, coi huỷ là lỗi mạng): đều có test fail.
  - **`request()` nhận thêm `validate` (bắt buộc).** **LÝ DO:** giống upload (D6), body lệch contract phải báo lỗi rõ ràng thay vì để bảng vỡ lúc render.
  - ~~Hiện `request()` mới hỗ trợ GET.~~ FE-F04 đã thêm `method` và `body` (gửi JSON kèm `Content-Type: application/json`), có test.
  - FE-F04 thêm timeout 30 giây, có test: hết giờ thì `kind=timeout`; user huỷ trước khi hết giờ thì vẫn là `aborted` (design D6).
- [ ] 3.3 TDD `api/contentDisposition.ts`: `filename*=UTF-8''…` (ưu tiên), `filename="…"`, `filename=` không có ngoặc kép, không có header → `null`; loại `/` và `\` khỏi tên. → Làm ở FE-F10.
- [ ] 3.4 TDD `api/download.ts`: 2xx → `{blob, filename}`; lỗi → ném `ApiError`, không trả blob; `saveBlob()` tạo object URL, click `<a download>` rồi revoke URL. → Làm ở FE-F10.
- [x] 3.5 TDD `api/upload.ts` (XHR):
  - multipart có part `file`;
  - `onProgress` nhận phần trăm, test bằng XHR giả được inject vào;
  - huỷ qua `AbortSignal` → `aborted`;
  - `201` → `ImportSessionDto`;
  - `413 FILE_TOO_LARGE`, `415 FILE_UNSUPPORTED`, `422 FILE_EMPTY`, `422 FILE_PARSE_ERROR` → `ApiError` đúng `code` và `detail`;
  - `onerror` → `network`.
  - XHR giả nằm ở `src/test/fakeXhr.ts`, gắn vào bằng `vi.stubGlobal`, nên production code không cần tham số chỉ để test.
  - Cách đổi từng mã lỗi sang `ApiError` đã có test ở 3.2 (`apiError.ts`), nên test của upload chỉ giữ một ca lỗi đại diện (`415`). Bốn mã còn lại được kiểm ở test component 5.2.
- [ ] 3.6 `api/endpoints.ts`: 10 hàm cho các endpoint FE dùng (không gồm `GET /api/import-sessions/{id}`). Test với MSW: method, path, query và body của từng hàm; PUT trả `200 {session, warnings}` thì hàm vẫn resolve và bỏ qua body.
  - FE-F01 chỉ cần upload (3.5). Mỗi hàm còn lại làm cùng feature dùng nó.
  - FE-F02 đã xong `getPreview` (`limit=50`), có test với MSW: path, query, và body 200 sai dạng → `INVALID_RESPONSE` (kể cả ô không phải chuỗi hay `null`, vì React không vẽ boolean và vỡ trang với object).
  - FE-F04 đã xong `putSchema`, có test với MSW: method, path, body; `200 {session, warnings}` thì resolve và bỏ qua body; `422` giữ `errors[]`. Body 200 chỉ được kiểm là có `session` (để body HTML từ proxy vẫn báo `INVALID_RESPONSE`).
- [ ] 3.7 `domain/types.ts` và TDD `api/mappers.ts`:
  - đổi key ↔ tên field; `order` theo vị trí;
  - mapping chỉ gồm field đã map;
  - `order` của transformation tính riêng theo field; `trim`, `uppercase`, `lowercase` không có `params`;
  - `dateFormat` trên field kiểu `date` luôn có `outputFormat: "yyyy-MM-dd"`;
  - validations chỉ có `email`/`unique`, không có `params`;
  - preview giữ đúng thứ tự `columns[]`.
  - FE-F01 đã xong: `SessionInfo` và `toSessionInfo`. Hàm này chỉ chép field nên không có unit test riêng; test component 5.3 kiểm nó (tên file của session hiện đúng khi quay lại bước Upload). Phần còn lại làm theo feature.
  - FE-F02 đã xong `SourcePreview` và `toSourcePreview`, có test: thứ tự `columns[]` giữ nguyên kể cả cột tên dạng số, `values` theo vị trí.
  - FE-F04 đã xong `TargetField`, `FieldType`, `FIELD_TYPES` và `toTargetSchemaDto`, có test: tên đã trim, `order` theo vị trí hiển thị từ 0, không gửi key nội bộ.
- [ ] 3.8 Mock cho test:
  - `mocks/fixtures.ts`, đúng shape của contract V0.1:
    - preview CSV và XLSX, trong đó có cột tên dạng số, `totalRows` luôn có;
    - result có dòng lỗi và nhiều trang; `ImportErrorDto` có `stage`, `rule`, `step`, `fieldName`;
    - PUT trả `{session, warnings}`.
  - `mocks/handlers.ts`: session store trong bộ nhớ, chỉ trả fixture, không mô phỏng pipeline.
  - `mocks/node.ts` và `src/test/setup.ts` (jest-dom, vòng đời MSW server).
  - FE-F01 đã xong:
    - `importSessionFixture` và `problemFixture`;
    - `mocks/node.ts`; `src/test/setup.ts` (có `onUnhandledRequest: 'error'`);
    - helper `src/test/http.ts` (`mockUpload` có đếm số request, `gate` để giữ request đang chạy) và `src/test/files.ts`.
  - Fixture preview/result và session store làm theo feature.
  - FE-F02 đã xong:
    - `csvPreviewFixture` (cột tên dạng số `2024` và `1`, ô `null`, ô chỉ có khoảng trắng, số dòng nhảy cóc) và `xlsxPreviewFixture` (có `sheetName`). Shape đã đối chiếu với response của BE thật.
    - Helper `mockPreview` (dùng chung logic với `mockUpload`).
  - FE-F04 đã xong `configUpdateFixture` (body 200 của PUT cấu hình) và helper `mockSaveSchema` (ghi lại body JSON của từng lần PUT).
    - `mocks/handlers.ts` có handler mặc định cho GET preview. **LÝ DO:** upload xong là bước Xem trước gọi preview ngay; không có handler thì mọi test upload cũ phụ thuộc may rủi thời gian (request bị huỷ lúc unmount trước khi MSW kịp báo lỗi).
- [ ] 3.9 Chế độ `dev:mock`:
  - Tạo `mocks/browser.ts`; chạy `pnpm dlx msw init public/ --save` để sinh `public/mockServiceWorker.js`.
  - `main.tsx` chỉ import động và khởi động worker khi `VITE_USE_MOCK=true`.
  - Tạo `.env.mock`; script `dev:mock` = `vite --mode mock`.

  Kiểm: `pnpm dev:mock` mở được app không cần BE; bản build thường không tải chunk mock.
  - Chưa làm ở FE-F01. **LÝ DO:** lúc này chỉ bước Upload chạy được, và BE thật đã có upload, nên chế độ mock chưa đem lại gì. Sẽ làm khi có từ hai bước chạy được trở lên.

## 4. Khung wizard (phần khung của FE-F11, làm trước để các bước cắm vào)

- [ ] 4.1 TDD `wizard/state.ts` và `wizard/reducer.ts`.
  - Actions: `sessionCreated`, `previewLoaded`, `schemaEdited`, `mappingEdited`, `transformationsEdited`, `validationsEdited`, `sectionSaved`, `processCompleted`, `resultUnavailable` (nhận `409 RESULT_NOT_AVAILABLE`), `busyChanged`, `reset`.
  - Test đủ bảng "chuyển về chưa lưu / đánh dấu cũ" của spec `import-wizard`.
  - Test cascade (spec `target-schema`): đổi tên; xoá field; đổi kiểu khỏi `string` thì xoá rule `email`; đổi kiểu sang `date` thì đặt `outputFormat` về `yyyy-MM-dd`.
  - FE-F01 đã xong, có test: `sessionCreated` (thay toàn bộ state cũ, sang bước Xem trước), `reset`, `requestStarted`, `requestSettled`.
  - **Thêm action `navigate`**, vốn không có trong danh sách trên. **LÝ DO:** mọi lần đổi bước đều đi qua reducer, để guard `canEnter` và trạng thái bận được áp ở đúng một chỗ, stepper không tự quyết.
  - ~~`busyChanged`~~ được thay bằng `requestStarted`/`requestSettled`: `pendingRequests` là bộ đếm, `isBusy()` suy ra từ nó, và có hook `useBusyRequest()` bọc request. **LÝ DO (review FE-F01):** với cờ boolean, mỗi component phải tự ghép cặp bật/tắt. Quên `finally` là điều hướng bị khoá vĩnh viễn, còn hai request chồng nhau thì request xong trước nhả khoá sớm (design D2).
  - Các action còn lại làm theo feature.
  - FE-F02 đã xong `previewLoaded`, có test. Action mang id mà FE đã dùng để gửi request; khác session hiện tại thì reducer bỏ qua, để response về muộn của session cũ không ghi đè session mới. `sessionCreated` và `reset` xoá preview.
    - ~~So với `sessionId` trong response của BE~~ **LÝ DO đổi (review FE-F02):** nếu id BE gửi lại lệch dạng, reducer lặng lẽ bỏ response và spinner quay mãi mà không báo lỗi. Vì vậy model `SourcePreview` cũng bỏ field `sessionId`.
  - FE-F04 đã xong `schemaEdited` và `sectionSaved` cho schema, có test:
    - `schemaEdited` mang một `edit`: `add`, `update`, `remove`, `move`. Mọi thay đổi đưa schema về chưa lưu. Key sinh từ `nextFieldSeq` (`f1`, `f2`, …), không dùng lại sau khi xoá, và đếm lại từ `f1` khi có session mới.
    - `sectionSaved` mang đúng bản draft đã gửi; reducer chỉ đánh dấu đã lưu khi draft hiện tại vẫn là bản đó (so tham chiếu). **LÝ DO:** nếu user sửa trong lúc PUT đang chạy, bản đang hiển thị chưa được lưu. Từ review FE-F04, phần sửa bị khoá trong lúc lưu, nên phép so này là lớp phòng thủ thứ hai.
    - Reducer đọc `section` bằng `switch`: thêm section mới vào union mà quên nhánh thì compiler báo lỗi.
    - Cascade sang mapping và rules làm ở F05–F07, khi có state tương ứng.
- [ ] 4.2 TDD `wizard/guards.ts`: `canEnter(step, state)` trả `{allowed, reason}`; test đủ bảng điều kiện của spec `import-wizard`.
  - FE-F01 đã xong: Upload và Xem trước theo điều kiện thật. Các bước sau khoá kèm lý do, cho tới khi feature tương ứng đưa state của nó vào.
  - FE-F02 đã xong: bước Schema mở khi preview đã tải, kể cả file không có dòng dữ liệu; bước Xem trước được tính là "đã xong" khi có preview.
  - FE-F04 đã xong: bước Mapping mở khi schema đã lưu và có ít nhất một field; bước Schema "đã xong" theo cùng điều kiện.
- [x] 4.3 `WizardContext.tsx`, `WizardShell.tsx` và `shared/ui/Stepper`: 6 bước với trạng thái xong/đang ở/khoá; bấm bước bị khoá thì hiện lý do; khoá điều hướng khi `busy`; mỗi bước tạm là placeholder. Có component test.
  - **`WizardContext.tsx` được tách làm hai**: `wizard/context.ts` (context và hook `useWizard`) và `wizard/WizardProvider.tsx` (component). **LÝ DO:** luật `react/only-export-components` của oxlint (phục vụ Fast Refresh) không cho một file vừa export component vừa export hook.
  - `Stepper` là component generic, không biết gì về wizard; `WizardShell` tính trạng thái từng bước bằng `canEnter` và `isStepDone`.
  - Trạng thái "đã xong" có chữ "(đã xong)" cho screen reader. Bản đầu thiếu trạng thái này dù task đã tick; review FE-F01 phát hiện và đã bổ sung, có test.
  - Lý do khoá bị xoá khi đổi bước, nên không hiện lại khi quay về bước cũ. Bản đầu chỉ ẩn đi và có lỗi này; có test.
  - Thả file ra ngoài vùng upload được chặn ở `window` (`usePreventFileDrop`), để trình duyệt không mở file và rời khỏi app; có test.
- [ ] 4.4 Thành phần UI dùng chung trong `shared/ui`:
  - `ErrorBanner`: nhận `ApiError`; dòng chính theo thứ tự ưu tiên; dòng phụ là `detail` khi dòng chính lấy từ bảng của FE; hiện mã lỗi; có nút "Thử lại" tuỳ chọn; có chế độ hiện danh sách `fieldErrors` (dùng cho readiness issue).
  - `EmptyState`, `Spinner`, `Pagination`, `ConfirmPanel`.
  - `DataTable`: cột khai báo tường minh, cuộn ngang trong khung bảng.

  Test `ErrorBanner` theo các scenario "Hiển thị lỗi API nhất quán".
  - FE-F01 đã xong:
    - `ErrorBanner` (có nút hành động tuỳ chọn) và `ConfirmPanel` (focus vào nút xác nhận khi hiện ra).
    - Phần chọn nội dung được tách thành hàm thuần `describeApiError` ở `shared/describeError.ts`, có test đủ thứ tự ưu tiên, lỗi mạng, huỷ. Vì vậy `ErrorBanner` nhận `ErrorText` thay vì `ApiError`, và dùng được cho cả lỗi kiểm tra phía client.
  - Còn lại:
    - ~~`EmptyState`, `Spinner`, `DataTable` → FE-F02~~ Đã xong ở FE-F02, test qua component test của bước Xem trước. `DataTable` có thêm `EmptyCell` cho ô `null`; cách chia độ rộng cột ghi ở design D14.
    - ~~chế độ danh sách `fieldErrors` → FE-F04 / FE-F08~~ FE-F04 đã thêm `items` cho `ErrorBanner` (lỗi của BE không gắn được vào field nào). Danh sách readiness issue của FE-F08 dùng lại prop này.
    - `Pagination` → FE-F09.
  - **Thêm `wizard/StepActions.tsx`** (nút "Quay lại"/"Tiếp" ở chân bước), vốn không có trong danh sách trên. **LÝ DO:** mọi bước từ Preview tới Rules đều cần cặp nút này, cùng luật khoá khi bận và luôn hiện lý do khoá cạnh nút "Tiếp" (spec import-wizard, target-schema).
- [x] 4.5 Đăng ký `beforeunload` khi có session và gỡ khi reset; test cả hai chiều.
  - Hook `wizard/useBeforeUnload.ts`, test trong `WizardShell.test.tsx`: chưa có session; có session; đang upload thay thế thì vẫn cảnh báo, vì session cũ còn cho tới khi có session mới.
  - `returnValue = true` chứ không phải `''`: chuỗi rỗng bị trình duyệt cũ coi là "không hỏi".
- [x] 4.6 Session không dùng được nữa:
  - `code` là `SESSION_NOT_FOUND` hoặc `SESSION_STATE_INVALID` → `ErrorBanner` có nút "Upload lại", bấm thì chạy action `reset`;
  - `404` mang mã khác (`REQUEST_INVALID`) → lỗi thường.

  Test rằng không tự reset, và cả ba trường hợp trên.
  - Làm ở FE-F02, vì GET preview là lệnh đầu tiên gọi endpoint của session sau khi upload.
  - Đã xong: `isSessionUnusable` (theo `code`) và `isRetryable` (lỗi mạng, 5xx) nằm ở `api/apiError.ts`, có unit test; `isRetryable` chuyển từ `UploadStep` sang đây để các bước dùng chung. Component test ở bước Xem trước phủ cả ba trường hợp: `SESSION_NOT_FOUND`, `SESSION_STATE_INVALID` (có "Upload lại", không tự reset, bấm thì về bước Upload trống) và `404 REQUEST_INVALID` (lỗi thường). Các bước sau dùng lại cùng luật; task 13.2 rà lại toàn bộ.

## 5. FE-F01 Upload (spec import-upload)

- [x] 5.1 TDD ~~`features/upload/validateFile.ts`~~ `features/upload/checkFiles.ts`: đuôi `.csv`/`.xlsx` không phân biệt hoa thường; `.csv` mang MIME `application/vnd.ms-excel` vẫn được nhận; file 0 byte; nhiều file; vượt `VITE_MAX_UPLOAD_MB` (20 MB).
  - **Đổi tên file. LÝ DO:** hàm kiểm cả danh sách file (nhiều file, không có file nào) và trả kết quả kiểm chứ không ném lỗi, nên `checkSelectedFiles` đúng nghĩa hơn `validateFile`.
  - Có thêm ca biên: file đúng bằng 20 MB thì nhận, hơn 1 byte thì từ chối (cơ số 1024, cùng cách BE tính).
- [x] 5.2 `UploadStep`:
  - vùng kéo-thả và input `accept=".csv,.xlsx"`;
  - gợi ý định dạng (CSV UTF-8 phân cách dấu phẩy; XLSX đọc sheet hiển thị đầu tiên; tối đa 20 MB);
  - thông tin file (tên, dung lượng, loại), tiến độ %, nút "Huỷ";
  - hiển thị lỗi từ BE; khi lỗi mạng có nút "Upload lại" gửi lại đúng file đó;
  - thành công thì dispatch `sessionCreated` và sang Preview.

  Component test với MSW: thành công, `415 FILE_UNSUPPORTED`, `413 FILE_TOO_LARGE`, `422 FILE_EMPTY`, `422 FILE_PARSE_ERROR` (dòng phụ hiện `detail`), lỗi mạng, huỷ.
  - **Các test "đang upload" (tiến độ %, Huỷ, thả file chồng) dùng XHR giả thay vì MSW. LÝ DO:**
    - Interceptor XHR của MSW bỏ qua `abort()` khi handler còn treo: không phát sự kiện `abort`, và request vẫn hoàn tất với 201. Trình duyệt thật không như vậy.
    - Đã xác nhận bằng một test chẩn đoán (đã xoá sau khi xong).
  - Bổ sung sau review FE-F01, mỗi mục có test:
    - "Upload lại" hiện khi lỗi mạng **và lỗi 5xx** (proxy `502`, BE `500`); lỗi 4xx thì không có nút này. Bỏ dòng gợi ý mạng, vì dòng phụ chỉ dành cho `detail` của BE.
    - Focus: bắt đầu upload thì focus nút Huỷ; huỷ hoặc lỗi thì focus về ô chọn file.
    - Vùng live chỉ báo lúc bắt đầu và lúc gửi xong, không đọc lại theo từng %. Gửi xong 100% thì hiện "Đang đọc file trên máy chủ…".
    - Mỗi lúc chỉ một upload: thả thêm file khi đang upload thì bỏ qua, kể cả file sai đuôi.
    - Response 2xx sai dạng (không phải JSON, thiếu `id`) báo `INVALID_RESPONSE`.
  - Hai test focus (hộp xác nhận, nút Huỷ) chọn file bằng kéo-thả chứ không dùng `user.upload`. **LÝ DO:** user-event giả lập việc trả focus về input sau `change`, nên nó blur luôn nút vừa nhận focus (design D15).
  - Đã làm mutation check:
    - làm hỏng nút Huỷ, phần cập nhật %, hàng rào chống upload chồng, việc nhả cờ bận, hoặc việc gỡ listener abort thì test fail;
    - `reset` ngay khi xác nhận thì 2 test fail.
- [x] 5.3 Upload file mới khi đã có session: ~~`ConfirmPanel` → `reset` → upload~~ `ConfirmPanel` → upload; state cũ chỉ bị thay khi có session mới. Test cả nhánh xác nhận và nhánh huỷ.
  - **LÝ DO bỏ `reset` (review FE-F01, lỗi nghiêm trọng):** `reset` ngay khi xác nhận làm mất sạch session và cấu hình nếu upload thay thế lỗi hoặc bị huỷ. `sessionCreated` đã tự thay state nguyên khối khi có session mới (design D13).
  - Có test: upload thay thế bị `415`, hoặc bị huỷ, thì session cũ vẫn còn và bước Xem trước vẫn vào được. Câu hỏi xác nhận nêu tên file.

> **Review FE-F01** (senior-reviewer, 2026-09-26). Các góp ý không làm, và lý do:
> - Không gom `UPLOAD_URL` của test vào code production: test phải tự viết đường dẫn của contract, để bắt được việc hằng số production bị đổi nhầm.
> - Không bọc `xhr.open`/`xhr.send` trong `try`: với URL và method cố định, hai hàm này không ném lỗi. Chỉ sửa docstring cho đúng.
> - `formatBytes` không xử lý `NaN` hay số âm: `File.size` không bao giờ như vậy. Lỗi làm tròn (`1.024 KB`) thì đã sửa.
> - Hàm `isRetryable` chưa có unit test riêng: hành vi (có hoặc không có nút "Upload lại") đã được test component phủ cho 4xx, 5xx, 502 và lỗi mạng.

## 6. FE-F02/F03 Source Preview (spec source-preview)

- [x] 6.1 `PreviewStep`:
  - gọi GET preview một lần cho mỗi session và lưu vào state;
  - `DataTable` theo `columns[]`, cột "Dòng", placeholder cho ô `null`, giá trị hiển thị nguyên như BE trả;
  - dòng tổng quan: "Xem trước x / y dòng" (`totalRows` luôn có) và `sheetName`.

  Test: cột tên dạng số giữ đúng thứ tự; XLSX hiện tên sheet, CSV thì không; quay lại bước không gọi lại API.
  - Có thêm test: ô `null` hiện placeholder (không hiện chữ `null`), ô chỉ có khoảng trắng giữ nguyên; "Tiếp" sang Schema và bước Xem trước được đánh dấu đã xong; chạy trong `StrictMode` thì request bị huỷ ở lần chạy effect đầu không hiện thành lỗi.
  - **GET preview không khoá stepper** (không đi qua `useBusyRequest`). **LÝ DO:** ghi ở design D2.
  - Đã làm mutation check (luôn gọi lại preview, in thẳng `null`, nhận biết session hỏng theo status, "Thử lại" không xoá lỗi, bỏ khoá "Tiếp", báo cả lỗi huỷ): đều có test fail. Ca "báo cả lỗi huỷ" ban đầu lọt; bổ sung test StrictMode mới bắt được.
  - Bổ sung sau review FE-F02, mỗi mục có test và đã làm mutation check:
    - rời bước khi đang tải thì request bị huỷ (test đọc `request.signal` ở handler MSW); trong lúc tải, stepper và "Quay lại" vẫn bấm được;
    - vùng live luôn nằm trong DOM, báo "Đang tải…" rồi "Xem trước x / y dòng";
    - focus: "Tiếp", "Quay lại", "Upload lại" và upload xong đều đưa focus tới tiêu đề bước mới; "Thử lại" đưa focus về tiêu đề bước; đổi bước bằng stepper thì focus ở lại nút stepper;
    - ô số dòng là row header, vùng cuộn lấy tên từ caption.
- [x] 6.2 Các trạng thái, mỗi trạng thái có test:
  - đang tải;
  - chỉ có header, không có dòng dữ liệu;
  - ~~`FILE_PARSE_ERROR` → banner, nút "Upload file khác", khoá "Tiếp"~~ **Bỏ — LÝ DO:** BE đọc và kiểm toàn bộ file ngay lúc upload, nên `FILE_EMPTY`/`FILE_PARSE_ERROR` trả về ở bước Upload (task 5.2), không bao giờ xảy ra ở preview;
  - 5xx hoặc lỗi mạng → "Thử lại".
  - Đã test: đang tải (chỉ báo và nút "Tiếp" khoá kèm lý do); chỉ có header ("File không có dòng dữ liệu", vẫn sang được Schema); lỗi mạng, `500`, `502` HTML → "Thử lại" gọi lại và hiện bảng.
  - Lỗi preview là state cục bộ của bước: rời bước rồi quay lại thì bước tự gọi lại, không cần bấm "Thử lại". **LÝ DO:** preview chưa tải được thì chưa có gì để giữ; vào lại bước coi như một lần thử mới. Với lỗi 4xx, lần gọi lại cũng chỉ hiện lại đúng lỗi đó, không hại gì. Giữ lỗi trong wizard state chỉ thêm action mà không đem lại gì cho user. Spec "không gọi lại API" áp cho preview **đã tải xong** (có test).

> **Review FE-F02** (senior-reviewer, 2026-09-26). Đã sửa: huỷ request và quyết định không khoá stepper chưa có test; guard response cũ so với id do BE gửi lại; mất focus khi nút biến mất; vùng live bị gắn/gỡ khỏi DOM; validator chưa kiểm kiểu ô; lỗi lạ không được log; ô số dòng chưa là row header và tên vùng cuộn bị đọc hai lần. Các góp ý không làm, và lý do:
> - ~~Timeout 30 giây cho `request()`: ghi ở design D6. Timeout chung sẽ áp cả lên các lệnh PUT sau này, và PUT hết giờ trong khi BE đã ghi thì FE báo lỗi sai.~~ Đã làm ở FE-F04; lý do đổi quyết định ghi ở design D6.
> - Giữ lỗi preview trong wizard state: xem ghi chú ngay trên.
  - ~~Lưu ý khi làm: cho tới khi có BE-F03, preview của session XLSX trả `409 SESSION_STATE_INVALID`…~~ **Không còn cần. LÝ DO:** BE-F03 đã merge vào `main` (`e7f6f21`); upload XLSX giờ trả `201` ở `CONFIGURING`, và preview dùng chung shape với CSV.
- [x] ~~6.3 Cảnh báo khi `columns[].name` trùng nhau hoặc rỗng (giả định Q9); có test.~~ **Bỏ — LÝ DO:** BE tự đặt lại tên header trùng hoặc rỗng (`Email (2)`, `Column C`), nên `columns[].name` luôn duy nhất và không rỗng (Q9).

## 7. FE-F04 Target Schema (spec target-schema)

- [x] 7.1 TDD `domain/schemaRules.ts`: tên rỗng sau khi trim; tên dài quá 100 ký tự sau khi trim; trùng tên sau khi trim, không phân biệt hoa thường (đánh dấu cả hai field); schema rỗng.
  - Hàm `checkSchema` trả lỗi theo key và lý do khoá "Tiếp": "Cần ít nhất một field", "Còn field chưa đặt tên" (ưu tiên), "Còn lỗi ở tên field". Có thêm ca biên: đúng 100 ký tự thì hợp lệ; nhiều field cùng rỗng không bị coi là trùng.
  - Thêm `matchServerErrors`, có test: chia `errors[]` của `422` theo field (so tên đã trim); lỗi không có `field`, hoặc tên không khớp field nào, đưa lên đầu form.
  - Tên field được chuẩn hoá NFC rồi mới trim, cả khi kiểm, khi gửi lên BE lẫn khi ghép lỗi của BE (`normalizeFieldName`). Có test. **LÝ DO (review FE-F04):** chữ Việt gõ dạng tổ hợp (NFD) và dựng sẵn (NFC) nhìn giống hệt nhau nhưng khác chuỗi; không chuẩn hoá thì file xuất ra có hai cột trùng tiêu đề, và độ dài bị đếm dư.
  - `toLowerCase` của JS lệch `equalsIgnoreCase` của Java ở vài chữ hiếm (ς/σ, İ/i). Những ca này BE vẫn chặn bằng 422, và lỗi hiện tại field.
- [x] 7.2 `SchemaStep`:
  - thêm field (focus ô tên), xoá, Lên/Xuống (khoá ở biên);
  - ô tên, chọn một trong 5 kiểu, checkbox required;
  - lỗi inline chỉ hiện sau khi ô tên mất focus lần đầu; nút "Tiếp" khoá, lý do luôn hiện cạnh nút.

  Có component test.
  - Đã xong, có component test theo từng scenario của spec. Mỗi field là một `<fieldset>` tên "Field n"; ô tên có `aria-invalid` và `aria-describedby` trỏ tới lỗi.
  - Focus (design D14), có test: "Thêm field" → ô tên của field mới; "Lên"/"Xuống" tới biên thì nút vừa bấm bị khoá → nút chiều ngược lại của cùng field; "Xoá" → ô tên của field kề bên, hết field thì nút "Thêm field".
- [x] 7.3 Lưu khi bấm "Tiếp": PUT schema nếu chưa lưu (tên đã trim, `order` từ 0); bỏ qua nếu đã lưu; `422 SCHEMA_INVALID` → lỗi tại field có tên khớp, phần còn lại ở đầu form. Test với MSW.
  - Có thêm test: đang lưu thì khoá stepper và nút điều hướng; bấm đúp chỉ gửi một PUT; sửa field đang có lỗi từ BE thì lỗi đó biến mất; lỗi mạng không có "Thử lại" (D6), bấm "Tiếp" lần nữa là gửi lại; `SESSION_NOT_FOUND`/`SESSION_STATE_INVALID` có "Upload lại"; `404 REQUEST_INVALID` là lỗi thường và không mất dữ liệu đang nhập.
  - Lưu lỗi thì focus tới ô tên của field lỗi đầu tiên, hoặc tiêu đề bước nếu lỗi không gắn được vào field nào (nút "Tiếp" bị khoá trong lúc lưu nên đã mất focus).
  - Không có khoá riêng chống bấm đúp. **LÝ DO:** `requestStarted` khoá nút "Tiếp" ngay trong sự kiện click đầu tiên (React xử lý click đồng bộ). Mutation check cho thấy một khoá riêng không làm test nào đổi kết quả, tức là code thừa.
  - Đã làm mutation check (luôn PUT dù đã lưu, không xoá lỗi BE khi sửa, không chuyển focus sau Lên/Xuống hoặc Xoá, báo lỗi ngay khi chưa rời ô, không focus field lỗi từ BE): đều có test fail.
  - Bổ sung sau review FE-F04, mỗi mục có test và đã làm mutation check:
    - trong lúc lưu, khoá cả phần sửa (ô tên, kiểu, bắt buộc, Lên/Xuống/Xoá, "Thêm field");
    - lúc ô tên nhận focus sau khi lưu lỗi, mô tả lỗi đã gắn sẵn (`flushSync`);
    - sửa bất kỳ field nào cũng xoá lỗi BE và khối lỗi, vì chúng nói về bản đã gửi; riêng lỗi session hỏng thì giữ nút "Upload lại";
    - rời bước rồi quay lại, field đã báo lỗi vẫn báo lỗi;
    - "Xuống" ở giữa danh sách: nút của chính field vừa di chuyển giữ focus;
    - đổi thứ tự sau khi đã lưu cũng đưa schema về chưa lưu (test reducer).
  - **Chưa kiểm với BE thật:** BE-F04 chưa có. Hiện BE trả `404` với `code: REQUEST_INVALID` và không có `detail`, nên màn Schema báo "Yêu cầu không hợp lệ" và giữ nguyên dữ liệu đang nhập. Khi BE-F04 vào `dev` thì kiểm lại (13.4).
- [ ] 7.4 Test tích hợp UI cho cascade:
  - Chưa làm ở FE-F04. **LÝ DO:** state của mapping, transformations và validations chưa tồn tại; mỗi ca cascade làm cùng feature đưa state đó vào (F05, F06, F07). Key cố định của field (D3) đã có sẵn cho việc này.
  - đổi tên vẫn giữ mapping/rules, và các lần PUT mapping/transformations/validations sau đó gửi tên mới (BE đã xoá cấu hình của tên cũ khi PUT schema);
  - xoá field thì xoá cấu hình của field đó;
  - đổi kiểu khỏi `string` thì xoá rule `email`;
  - đổi kiểu sang `date` thì `outputFormat` về `yyyy-MM-dd`.

- [x] 7.5 Sinh schema từ cột nguồn (spec target-schema, requirement "Sinh schema từ cột nguồn"; người dùng yêu cầu ngày 2026-09-26, sau khi FE-F04 đã vào `dev`):
  - TDD `domain/inferSchema.ts`: `inferFieldType(values)` theo đúng luật kiểu của BE-F07, và `fieldsFromPreview(preview)`.
  - Reducer: `previewLoaded` sinh schema khi schema đang trống; thêm kiểu sửa `regenerate` vào `schemaEdited`, key tiếp tục từ `nextFieldSeq`.
  - `SchemaStep`: nút "Tạo lại từ file", hỏi xác nhận bằng `ConfirmPanel` khi đang có field.
  - Cập nhật các test đang giả định bước Schema mở ra trống.
  - Ghi chú cho FE-F05: Mapping mặc định map field sang cột nguồn cùng tên.
  - Đã xong, có test:
    - `inferFieldType`: từng kiểu và các ca biên (`1`/`0` là `number`; `" 42"`, `1,234`, `2024-02-30`, ngày có giờ, `yes`/`no` là `string`; ô `null`, rỗng, chỉ khoảng trắng hoặc NBSP bị bỏ qua).
    - Reducer: `previewLoaded` chỉ sinh khi schema đang trống; `regenerate` dùng key mới, tiếp theo `nextFieldSeq`.
    - Màn Schema: vào bước thấy field sinh sẵn; xoá rồi quay lại thì không sinh lại; "Tạo lại từ file" hỏi xác nhận khi đang có field ("Huỷ" giữ nguyên, trả focus về nút; đồng ý thì thay và focus field đầu); chưa có field thì sinh ngay; lưu schema sinh sẵn gửi đúng tên, kiểu, thứ tự.
  - Các test thao tác của FE-F04 (thêm, sửa, xoá, lưu) vẫn bắt đầu từ danh sách trống, bằng cách xoá hết field được sinh qua giao diện (helper `openSchemaStep`).
  - Đã làm mutation check (sinh đè lên field đã có, dùng lại key cũ, cột `1`/`0` thành `boolean`, không bỏ ô chỉ có khoảng trắng, luôn hỏi xác nhận, "Huỷ" không trả focus, không focus field đầu sau khi tạo lại): đều có test fail.
  - Đã chạy thật với BE ở 8080 (Chrome headless): CSV 6 cột ra đúng `string`, `string`, `date`, `number`, `boolean`, `email`; hộp xác nhận hiện đúng số field; console sạch.

## 8. FE-F05 Mapping (spec field-mapping)

- [ ] 8.1 TDD `domain/configRules.ts` phần mapping: field required chưa map → lỗi; field optional chưa map → cảnh báo; hằng rỗng → lỗi.
- [ ] 8.2 `MappingStep`:
  - mỗi field chọn Chưa map / Cột nguồn / Giá trị cố định;
  - danh sách cột theo thứ tự preview; tối đa 3 giá trị mẫu không rỗng;
  - cảnh báo và lỗi; nút "Tiếp" khoá kèm danh sách field lỗi.

  Có component test.
- [ ] 8.3 Lưu khi bấm "Tiếp": PUT mapping chỉ gồm field đã map; bỏ qua nếu đã lưu; `422` (`SOURCE_COLUMN_NOT_FOUND` / `MAPPING_INVALID`) → lỗi tại dòng của field. Test với MSW, kiểm payload đúng scenario của spec.

## 9. FE-F06/F07 Transform & Validate (spec rule-config)

- [ ] 9.1 TDD `configRules.ts` phần rules:
  - `defaultValue` rỗng và `dateFormat` thiếu định dạng → lỗi;
  - rule suy ra (`required`, `type:<kiểu>`);
  - `email` chỉ có ở field `string`;
  - `dateFormat` trên field `date` thì `outputFormat` cố định `yyyy-MM-dd`.
- [ ] 9.2 `TransformationEditor`:
  - thêm, xoá, Lên/Xuống; ô tham số;
  - gợi ý mẫu `dateFormat` (datalist); `outputFormat` điền sẵn `yyyy-MM-dd`, và **chỉ đọc** ở field kiểu `date`;
  - dòng tóm tắt "trim → uppercase" hoặc "Không biến đổi".

  Có component test.
- [ ] 9.3 `ValidationEditor`: chip rule suy ra (chỉ đọc); bật/tắt `email` (chỉ field `string`) và `unique`. Component test theo các scenario của spec.
- [ ] 9.4 `RulesStep`: ghép hai editor theo từng field, theo thứ tự schema. Test:
  - payload transformations đúng thứ tự, không có `params` cho `trim`/`uppercase`/`lowercase`;
  - payload validations chỉ có rule do user bật, không có `params`.

## 10. FE-F08 Chạy pipeline (spec pipeline-run)

- [ ] 10.1 Nút "Chạy xử lý" khoá kèm lý do khi còn lỗi cấu hình, còn phần chưa lưu, hoặc đang chạy. Có test.
- [ ] 10.2 Trình tự, dừng ở bước lỗi đầu tiên:
  1. PUT transformations (nếu chưa lưu)
  2. PUT validations (nếu chưa lưu)
  3. POST process
  4. GET result (`view` chọn theo `invalid`)
  5. sang bước Result

  Test với MSW:
  - thành công;
  - bỏ qua phần đã lưu;
  - validations trả `422 CONFIG_INVALID`;
  - process trả `409 SESSION_NOT_READY`, hiện danh sách readiness issue từ `errors[]`;
  - process trả `409 SESSION_STATE_INVALID` (đi theo 4.6);
  - process trả `422 FILE_PARSE_ERROR` → hiện nút "Upload lại" ngay (session đã `FAILED`, design D12);
  - process trả `500 INTERNAL_ERROR` → lỗi chung, "Chạy xử lý" bấm lại được.
- [ ] 10.3 Trạng thái "Đang xử lý…" và khoá điều hướng; test bấm đúp chỉ gửi một lượt request.
- [ ] 10.4 Chạy lại sau khi sửa cấu hình: kết quả mới thay kết quả cũ và bỏ đánh dấu cũ. Có test.

## 11. FE-F09 Kết quả (spec result-review)

- [ ] 11.1 `ResultStep`: 3 thẻ tóm tắt. Khi kết quả đã cũ: cảnh báo kèm nút "Chạy lại" (dùng lại trình tự của 10.2), và khoá đổi trang, đổi tab, bộ lọc.
- [ ] 11.2 Tab Hợp lệ/Lỗi, tab mặc định chọn theo `invalid`.
  - Bảng: cột "Dòng", rồi các field theo thứ tự schema.
  - Dòng lỗi: làm nổi ô lỗi và liệt kê từng lỗi gồm field, nhãn mã lỗi, rule, bước (`step + 1`, khi `stage=TRANSFORMATION`), message và giá trị nguồn.

  Test:
  - thứ tự cột theo schema, kể cả field tên dạng số;
  - lỗi validation;
  - lỗi transformation, trong đó ô có giá trị `null` hiện placeholder;
  - ~~lỗi không gắn với field~~ **Bỏ — LÝ DO:** trong contract V0.1, `ImportErrorDto.fieldName` luôn có giá trị.
- [ ] 11.3 Phân trang 50 dòng/trang; đổi trang hoặc đổi tab thì gọi lại GET result. Có test.
- [ ] 11.4 Lọc tab Lỗi theo field và mã lỗi qua query (BE đã xác nhận ở Q3); lựa chọn kèm số lỗi từ `errorCountsByField` / `errorCountsByCode`; nút "Xoá lọc"; đổi bộ lọc thì về trang 0. Có test.
- [ ] 11.5 Trạng thái rỗng "Không có dòng lỗi" / "Không có dòng hợp lệ". Có test.
- [ ] 11.6 `GET result` trả `409 RESULT_NOT_AVAILABLE` → dispatch `resultUnavailable`: đánh dấu kết quả là cũ, giữ trang đang xem, hiện cảnh báo và nút "Chạy lại" (design D18). Có test.

## 12. FE-F10 Export (spec result-export)

- [ ] 12.1 `ExportActions`: 3 nút; khoá kèm lý do theo các quy tắc: kết quả cũ, `valid=0`, `invalid=0`, đang tải. Có test.
- [ ] 12.2 Tải file qua `download.ts` và `saveBlob`:
  - tên dự phòng `<tên-gốc>-valid.<ext>` và `<tên-gốc>-errors.csv`;
  - lỗi thì hiện `ErrorBanner` và không lưu file;
  - `409 RESULT_NOT_AVAILABLE` → `resultUnavailable`.

  Test với MSW: có `filename*`; không có header; `500 EXPORT_FAILED`; `409 RESULT_NOT_AVAILABLE`; lỗi mạng.

## 13. FE-F11 Tích hợp và hoàn thiện (spec import-wizard)

- [ ] 13.1 Test tích hợp trên `<App/>` với MSW:
  - CSV đi hết 6 bước tới lúc tải JSON;
  - XLSX tương tự, có tên sheet ở bước Preview;
  - sửa cấu hình rồi chạy lại.
- [ ] 13.2 Rà lỗi nhất quán: mọi chỗ gọi API đều hiển thị qua `ErrorBanner`; request GET có nút "Thử lại"; `SESSION_NOT_FOUND` / `SESSION_STATE_INVALID` có nút "Upload lại". Bổ sung test cho chỗ còn thiếu.
- [ ] 13.3 Viết lại `apps/web/README.md`: yêu cầu cài đặt, biến môi trường, `pnpm dev` (chạy với BE thật; Postgres khởi động bằng `docker compose up -d` với `docker-compose.yml` ở gốc repo), `pnpm dev:mock`, `pnpm test`, demo flow từng bước.
- [ ] 13.4 Kiểm tay với BE thật, khi các feature BE tương ứng đã có. Ghi kết quả từng mục ngay dưới task này:
  - CSV happy path;
  - XLSX happy path;
  - field required để trống và giá trị sai kiểu ra lỗi đúng (Q1);
  - đổi tên field rồi chạy lại: mapping và rule vẫn còn;
  - sửa cấu hình rồi chạy lại;
  - lỗi hiển thị nhất quán, gồm upload file hỏng (`FILE_PARSE_ERROR`) và file vượt 20 MB (`413`);
  - file tải về đúng tên và đúng nội dung.

  Kết quả từng phần:
  - **FE-F02 (2026-09-26)**, BE bản `main` `4b75cf6` chạy ở 8080, FE `pnpm dev` qua proxy, Chrome headless điều khiển bằng DevTools Protocol:
    - CSV có header dạng số và trùng tên, dòng trống, ô rỗng, ô chỉ có khoảng trắng: bảng đúng thứ tự (`2024 | Họ tên | 1 | Email | email (2)`), số dòng 2, 3, 5, ô rỗng hiện gạch ngang, "Xem trước 3 / 3 dòng".
    - XLSX `types.xlsx` (fixture của BE): "Sheet: Data", 15 cột, giá trị đúng như BE đổi sang chuỗi (`84901234567`, `2024-12-25T13:45:30`, `#N/A`, `13:30:00`). Thay file XLSX khi đang có session CSV đi qua hộp xác nhận đúng.
    - "Tiếp" sang bước Schema. Console không có lỗi hay cảnh báo.
    - Ảnh chụp lộ lỗi bảng bị bóp cột; đã sửa (design D14).
    - Sau các sửa đổi của review, chạy lại cùng kịch bản với BE `main` ở cổng 8081 (Vite 5174, `API_PROXY_TARGET=http://localhost:8081`): kết quả như trên, console sạch. Lý do đổi cổng: lúc đó 8080 là bản BE cũ do IntelliJ chạy từ thư mục chính (nhánh FE, chỉ có code BE-F01), không có endpoint preview.
- [ ] 13.5 `pnpm test`, `pnpm lint`, `pnpm build` đều xanh; đối chiếu từng mục "Done when" phía FE của F01–F11 trong Notion.
