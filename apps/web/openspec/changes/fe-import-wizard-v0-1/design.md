## Context

- **Nguồn yêu cầu**: Notion "Universal Importer — Agent Project Pack" (https://app.notion.com/p/Universal-Importer-Agent-Project-Pack-3e61b2cd91a38141a574e102dbf6cec6), gồm 00 Context, 01 Requirements, 02 System Design, 03 Feature Breakdown. Change này chỉ lấy phần **FE-F01 → FE-F11**.
- **Hiện trạng `apps/web`**: template Vite, gồm React 19, TypeScript 6, Vite 8, oxlint, pnpm. Chưa có test tooling. `App.tsx` là trang demo, và `tsconfig.app.json` chưa khai báo `strict`.
- **Backend**: Java 21 + Spring Boot 4.1.1, phát triển riêng; mỗi feature BE làm trong worktree và nhánh `feature/be-fxx-*` riêng.
  - Ngày 2026-09-25, BE đã chốt **API contract V0.1** và trả lời Q1–Q10 của FE. Sau khi BE-F01 được archive, file nằm ở `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md` ở gốc repo (xem mục "API contract V0.1 (chính thức)" và "Trả lời Open Questions của FE"). Spec BE đang chạy nằm ở `openspec/specs/` ở gốc repo.
  - **BE-F01, BE-F02 và BE-F03 đã có trên `main`** (commit `e7f6f21`):
    - `POST /api/import-sessions`: với CSV, BE đọc toàn bộ file ngay trong request; thành công trả `201` và session ở `CONFIGURING`.
    - `GET /api/import-sessions/{id}`.
    - `GET /api/import-sessions/{id}/preview?limit=` (1–200; ngoài khoảng trả `400 REQUEST_INVALID`).
    - XLSX (BE-F03): upload trả `201` ở `CONFIGURING`, preview cùng shape với CSV. `sheetName` là sheet hiển thị đầu tiên. Ô ngày trả `yyyy-MM-dd`, ô chỉ có giờ trả `HH:mm:ss`, số trả dạng chuỗi plain (làm tròn 15 chữ số như Excel), boolean trả `TRUE`/`FALSE`. Không có sheet hiển thị hoặc dòng 1 trống thì trả `422 FILE_EMPTY`; file hỏng hoặc zip bomb thì trả `422 FILE_PARSE_ERROR`.
    - Các endpoint từ `/schema` trở đi chưa có, gọi sẽ nhận `404 REQUEST_INVALID`.
  - Contract giữ phần lớn shape mà FE đề xuất. Mục **API contract V0.1** dưới đây là bản chép lại cho FE, kèm cách FE dùng. **Nếu hai file lệch nhau, file của BE là chuẩn.**
- **Ràng buộc từ Notion**:
  - Logic import (mapping, transformation, validation) nằm hoàn toàn ở BE; FE chỉ cấu hình, thao tác và hiển thị.
  - Chưa dùng global state library.
  - Non-goals của V0.1: auth, AI, team, template, connector.

### Mâu thuẫn tài liệu đã phát hiện

Agent Working Rule yêu cầu báo mâu thuẫn trước khi chọn. Thứ tự ưu tiên: Requirements → System Design → Feature Breakdown.

| # | Mâu thuẫn | Chọn | Lý do |
|---|---|---|---|
| M1 | System Design §12 có wizard **6 bước**; F11 có **7 bước** (tách "Process" thành bước riêng) | 6 bước; "Chạy xử lý" là nút chính của bước 5 | System Design được ưu tiên hơn Feature Breakdown. Một bước "Process" riêng chỉ chứa một nút và spinner. |
| M2 | FR-06 yêu cầu lỗi transformation truy được tới **transformation** gây lỗi; `ImportError` ở System Design §4 chỉ có `rowNumber, fieldName, code, message, sourceValue` | Thêm `stage`, `rule`, `step` vào `ImportErrorDto` | Requirements được ưu tiên hơn System Design. BE đã nhận đề xuất này và bổ sung thêm `stage` và `step`. |

## Goals / Non-Goals

**Goals:**
- Đi hết luồng V0.1 trên UI: upload → preview → schema → mapping → transform & validate → chạy → xem kết quả → export, đáp ứng mọi mục "Done when" phía FE của F01–F11.
- Cô lập contract vào một lớp mỏng, để mọi thay đổi contract về sau chỉ phải sửa `api/dto.ts`, `api/mappers.ts` và mock.
- Làm được mà không cần BE chạy thật: có mock (MSW) và test không cần BE.
- State có thể đoán trước: luôn biết phần nào đã lưu lên BE, kết quả nào đã cũ.

**Non-Goals:**
- Router hay deep-link. Không khôi phục state sau khi tải lại trang. BE đã có `GET /api/import-sessions/{id}` trả `config` và `readiness` (từ BE-F04), nên việc khôi phục làm được về sau, nhưng V0.1 chưa làm.
- Chạy thử transformation hay validation ở FE, hoặc bất kỳ bản sao logic pipeline nào.
- Tự sinh schema từ cột nguồn, lưu template, AI mapping.
- Virtualize bảng, layout mobile, i18n nhiều ngôn ngữ.
- Upload nhiều file, huỷ process đang chạy (BE không có endpoint huỷ).
- E2E bằng trình duyệt thật (Playwright). V0.1 dùng test tích hợp với MSW, cộng checklist chạy tay với BE thật.
- Mọi thay đổi ở `apps/api`.

## Decisions

### D1. Wizard 6 bước, không router
Các bước: Upload → Source Preview → Target Schema → Mapping → Transform & Validate → Result & Export (xem M1). Bước hiện tại nằm trong state, không nằm trên URL.
- *Vì sao*: chỉ có một màn hình wizard, và V0.1 không khôi phục state (xem Non-Goals). Router chỉ thêm dependency mà không đem lại gì.
- *Phương án khác*: React Router. Loại vì lý do trên.
- *Hệ quả*: nút Back của trình duyệt sẽ rời khỏi app. Xử lý bằng cảnh báo `beforeunload` (D12).

### D2. State bằng `useReducer` + Context; guard là hàm thuần
```ts
type StepId = 'upload' | 'preview' | 'schema' | 'mapping' | 'rules' | 'result'

interface Section<T> { draft: T; saved: boolean }   // saved = draft đã PUT thành công và chưa sửa từ đó

interface WizardState {
  step: StepId
  pendingRequests: number                             // số request làm đổi state đang chạy; > 0 là đang bận
  nextFieldSeq: number                                // sinh key field có tính xác định: f1, f2, …
  session: SessionInfo | null
  preview: SourcePreview | null
  schema: Section<TargetField[]>
  mapping: Section<Record<FieldKey, FieldMapping>>
  transformations: Section<Record<FieldKey, Transformation[]>>
  validations: Section<Record<FieldKey, UserRule[]>>
  result: { summary: PipelineSummary; stale: boolean } | null
}
```
- Bảng chuyển về chưa lưu / đánh dấu cũ: xem spec `import-wizard`. Reducer là nơi duy nhất áp các quy tắc đó.
- Sửa schema đi qua một action `schemaEdited { edit }` với `edit` là `add` / `update` / `remove` / `move` (FE-F04). Reducer sinh key cho field mới, nên component không phải đoán key.
- Trong lúc PUT cấu hình đang chạy, phần sửa của bước đó bị khoá (`<fieldset disabled>`), cùng lúc với stepper và nút điều hướng.
  - *Vì sao* (review FE-F04): bản đầu cho sửa trong lúc lưu. Sửa xen vào thì lưu xong mà đứng im không báo gì, lỗi 422 gắn theo bản đã gửi nên hiện sai field, và lưu lỗi thì focus bị giật khỏi ô đang gõ.
- `sectionSaved` mang đúng bản draft đã gửi đi; reducer chỉ đánh dấu đã lưu khi draft hiện tại vẫn là bản đó (so tham chiếu, vì draft là mảng bất biến). Đây là lớp phòng thủ thứ hai sau việc khoá phần sửa.
- **Trạng thái "đang bận" là một bộ đếm, không phải boolean.**
  - Mọi request làm đổi state đều chạy qua hook `useBusyRequest()`: hook dispatch `requestStarted` trước khi chạy và `requestSettled` trong `finally`. `isBusy(state)` là `pendingRequests > 0`.
  - `sessionCreated` và `reset` giữ nguyên bộ đếm, vì các request đang chạy vẫn sẽ báo kết thúc sau đó.
  - *Vì sao* (review FE-F01): với boolean, mỗi component phải tự ghép cặp bật/tắt. Quên `finally` ở một chỗ là điều hướng bị khoá vĩnh viễn; hai request chồng nhau thì request xong trước nhả khoá trong khi request kia còn chạy. F02–F10 sẽ lặp lại pattern này ở hàng chục chỗ.
  - **Ngoại lệ: GET preview không đi qua `useBusyRequest`** (FE-F02).
    - Tiêu chí không phải "đọc hay ghi": `GET result` cũng là request đọc mà spec vẫn khoá điều hướng. Tiêu chí là: response chỉ bước đang mở dùng tới, request bị huỷ khi bước unmount, và không state nào khác phụ thuộc vào nó trong lúc nó chạy. GET preview thoả cả ba; `GET result` thì không, vì nó đi sau `process` trong cùng một trình tự.
    - Lớp bảo vệ: rời bước thì request bị huỷ (có test). Thêm một lớp: `previewLoaded` mang id mà FE đã dùng để gửi request, và reducer bỏ qua nếu id đó khác session hiện tại. **Không** so với `sessionId` BE gửi lại, vì nếu lệch (viết hoa, alias…) reducer sẽ lặng lẽ bỏ response và spinner quay mãi mà không báo lỗi (review FE-F02).
    - Nút "Tiếp" vẫn khoá, vì guard của bước Schema đòi preview đã tải.
    - ~~**Phụ thuộc cần nhớ:** `request()` không có timeout (xem D6), nên khi BE hoặc proxy treo, việc không khoá stepper là lối thoát duy nhất của user.~~ Từ FE-F04, `request()` có timeout 30 giây (D6), nên request nào treo cũng kết thúc bằng lỗi.
- `canEnter(step, state) → { allowed: boolean; reason?: string }` là hàm thuần, test được mà không cần render.
- *Vì sao*: Notion dặn chưa dùng global state library. Reducer thuần dễ test các quy tắc stale và cascade.
- *Phương án khác*: `useState` rải theo từng bước. Loại, vì quy tắc cascade (sửa schema kéo theo mapping và rules) sẽ nằm rải rác ở nhiều nơi.

### D3. Field có key nội bộ cố định
`TargetField = { key: FieldKey; name; type; required }`. Key có dạng `f1`, `f2`, … sinh từ `nextFieldSeq`. Mapping, transformation và validation gắn theo `key`; chỉ khi serialize mới đổi sang `name`.
- *Vì sao*: đổi tên field không làm mất mapping hay rule phía FE. Key tăng dần cho kết quả giống nhau mỗi lần chạy, test ổn định hơn `crypto.randomUUID()`.

### D4. Lưu theo từng bước khi bấm "Tiếp"; bỏ qua nếu đã lưu
- Schema được PUT khi rời bước 3, mapping khi rời bước 4. Transformations và validations được PUT trong trình tự của "Chạy xử lý" (spec `pipeline-run`).
- Thứ tự PUT luôn là schema → mapping → transformations → validations, trùng thứ tự bước, vì BE kiểm mapping và rule dựa trên schema.
- *Vì sao*: BE kiểm tra sớm, nên lỗi hiện đúng ở bước gây ra nó.
- *Phương án khác*: gom mọi PUT vào lúc Process. Loại, vì lỗi mapping sẽ hiện ở bước 5, xa chỗ cần sửa.
- **Tương tác với BE khi đổi tên field**:
  - BE coi đổi tên là xoá field cũ rồi thêm field mới. Khi PUT `/schema`, BE xoá mapping và rule của tên cũ, kèm warning `CONFIG_PRUNED`.
  - Cấu hình vẫn không mất, vì sau mỗi lần sửa schema, FE đánh dấu mapping, transformations và validations là chưa lưu, rồi PUT lại chúng dưới tên mới.
  - Hệ quả bắt buộc: không được tối ưu bỏ qua các PUT này sau khi schema vừa thay đổi.

### D5. Tách DTO khỏi model nội bộ
- `api/dto.ts` là contract với BE; `domain/types.ts` là model của UI; `api/mappers.ts` chuyển qua lại giữa hai bên.
- Component và reducer **không** import `dto.ts`.
- *Vì sao*: model UI gắn theo `key`, còn DTO gắn theo `name`. Contract đổi về sau thì chỉ sửa `dto.ts`, `mappers.ts` và `mocks/`.

### D6. API client: `fetch` cho JSON, XHR cho upload, `fetch` + blob cho download
- `api/client.ts`: hàm `request<T>()` gửi header `Accept: application/json, application/problem+json`, và chuẩn hoá mọi lỗi thành `ApiError` (xem bảng **Xử lý lỗi**).
  - Mỗi lệnh gọi truyền `validate` kiểm tối thiểu body 2xx theo contract (ví dụ `getPreview` kiểm `columns`, `rows`, `totalRows`). Body sai dạng hoặc không phải JSON thì báo `INVALID_RESPONSE`, giống upload, thay vì để bảng vỡ lúc render.
  - `isRetryable(error)` (lỗi mạng hoặc 5xx) và `isSessionUnusable(error)` (theo `code`, D12) nằm cạnh `ApiError` trong `api/apiError.ts`, để mọi bước chọn nút hành động ("Thử lại" / "Upload lại") theo cùng một luật.
  - **Timeout 30 giây** cho mọi lệnh `request()` (`DEFAULT_TIMEOUT_MS`, chỉnh được qua `timeoutMs`). Hết giờ là lỗi riêng `kind: 'timeout'` ("Máy chủ không phản hồi"), không lẫn với huỷ; GET có nút "Thử lại" như lỗi mạng.
    - ~~Không đặt timeout (FE-F02): timeout chung áp lên PUT, mà PUT hết giờ trong khi BE đã ghi thì FE báo lỗi sai.~~ **LÝ DO đổi (review FE-F04):** các PUT cấu hình ghi đè toàn bộ, nên gửi lại sau khi hết giờ là vô hại. Còn không có timeout thì PUT treo làm wizard kẹt hẳn, vì stepper và nút điều hướng đang khoá; user chỉ còn cách tải lại trang và mất cấu hình (D12).
    - `POST /process` (FE-F08) sẽ tự đặt `timeoutMs` riêng, vì pipeline chạy đồng bộ và có thể lâu hơn 30 giây.
  - Lỗi không phải `ApiError` (lỗi lập trình trong mapper, reducer…) hiện câu chung "Đã xảy ra lỗi không mong đợi" và được `console.error`, để còn stack mà tìm.
- `api/upload.ts`: dùng XHR, vì `fetch` không báo được tiến độ upload. Có `onProgress`, huỷ qua `AbortSignal`. Multipart part tên `file`.
- `api/download.ts`: kiểm `response.ok` trước; nếu lỗi thì parse ProblemDetail và ném `ApiError`, không trả blob. Nếu thành công thì trả `{ blob, filename }`, với tên file lấy từ `api/contentDisposition.ts`. `saveBlob()` tạo object URL, click một thẻ `<a download>`, rồi revoke URL.
- Body của PUT (`{ session, warnings }`) được bỏ qua. Warning của BE (`CONFIG_PRUNED`, `TARGET_FIELD_UNMAPPED`, `RULE_IMPLIED_BY_SCHEMA`) đều đã được FE tự tính hoặc tự tránh.
- Không tự động retry. Nút "Thử lại" chỉ có ở request đọc (GET). Ngoại lệ duy nhất là upload: user được bấm "Upload lại" để gửi lại đúng file đó, vì upload lỗi không tạo session nào. Nút này chỉ hiện khi lỗi mạng hoặc lỗi 5xx; với lỗi 4xx, gửi lại đúng file đó vẫn lỗi y hệt. Không đặt timeout cho `process`, vì pipeline chạy đồng bộ.
- Response 2xx của upload được kiểm tối thiểu (`id`, `originalFileName`, `fileType`, `sizeBytes`). Thiếu field thì báo `INVALID_RESPONSE`, một mã chỉ FE sinh ra, thay vì chạy tiếp với `id` rỗng.

### D7. Rule `required` và `type` suy ra từ schema (BE đã xác nhận, Q1)
- Payload validations chỉ chứa `email` và `unique`; BE tự áp `required` (khi `field.required`) và `type` (theo `field.type`).
- UI hiển thị hai rule suy ra này dạng chip chỉ đọc.
- `email` chỉ bật được cho field kiểu `string`. Với field kiểu `email`, BE bỏ qua rule này; với `number`, `boolean`, `date`, BE trả `422 CONFIG_INVALID`. `unique` bật được cho mọi kiểu. Mỗi rule tối đa một lần trên một field.
- Không gửi `params` cho validation, cũng như cho `trim`, `uppercase`, `lowercase`.

### D8. FE không chạy logic dữ liệu
FE chỉ kiểm **cấu hình**: tên field, field required chưa map, hằng rỗng, tham số transformation. Mục "Preview config" của F06 được hiểu là dòng tóm tắt chuỗi (`trim → uppercase`), không phải chạy thử trên dữ liệu mẫu.
- *Vì sao*: Notion yêu cầu core logic nằm ở BE và phải có tính xác định. Một bản sao ở FE sẽ lệch với BE, nhất là `dateFormat` (BE parse STRICT theo `DateTimeFormatter`) và các luật `type` (ví dụ `number` không nhận dấu phân cách hàng nghìn).

### D9. Kết quả phân trang phía server; lọc qua query (BE đã xác nhận, Q3)
`GET .../result?view=valid|invalid&page=<từ 0>&size=50[&field=&code=]`. BE cho `size` tối đa 200.
- *Vì sao*: F09 đòi "UI vẫn usable khi có nhiều errors". Nếu lọc phía client trên dữ liệu đã phân trang thì chỉ lọc được trang đang xem, tức là sai.

### D10. Export bằng `fetch` + blob
- *Vì sao*: F10 đòi "hiển thị download error nếu request thất bại". Một link `<a href>` trực tiếp không bắt được lỗi, và trình duyệt sẽ tải về chính body lỗi.
- *Đánh đổi*: cả file nằm trong RAM của trình duyệt, tối đa cỡ vài chục MB vì upload giới hạn 20 MB. V0.1 chấp nhận.
- Tên file ưu tiên `filename*` (RFC 5987; BE luôn gửi dạng này), sau đó `filename`, cuối cùng là tên dự phòng. Luôn loại `/` và `\` khỏi tên.
- FE và BE cùng origin qua Vite proxy nên không cần CORS. Nếu sau này deploy khác origin, BE phải bật `Access-Control-Expose-Headers: Content-Disposition`.

### D11. Kiểm file phía client chỉ là lớp UX
- Kiểm theo đuôi file (không phân biệt hoa thường), vì trên Windows file CSV thường mang MIME `application/vnd.ms-excel`. BE bỏ qua MIME và kiểm magic bytes.
- Chặn file 0 byte và chặn chọn nhiều file.
- Kiểm dung lượng theo `VITE_MAX_UPLOAD_MB` (mặc định **20**, khớp `IMPORTER_MAX_FILE_SIZE` của BE), để khỏi gửi hàng chục MB chỉ để nhận `413`. BE luôn trả được `413 FILE_TOO_LARGE`, vì đã đặt `max-swallow-size=-1`.
- Hiển thị gợi ý định dạng: CSV phải là UTF-8 và phân cách bằng dấu phẩy (từ Excel thì lưu dạng "CSV UTF-8"); với XLSX, hệ thống đọc sheet hiển thị đầu tiên.

### D12. Session không dùng được nữa; không khôi phục state khi tải lại trang
- Khi có session, đăng ký `beforeunload`; gỡ khi reset.
- Session được coi là không dùng được nữa khi response mang một trong hai mã sau:
  - `code = SESSION_NOT_FOUND` (404): BE xoá session không hoạt động sau 24 giờ;
  - `code = SESSION_STATE_INVALID` (409): session đã `FAILED`, ví dụ do đọc file lỗi lúc process.
- Khi đó FE báo lỗi kèm nút "Upload lại", và **không** tự reset.
- Riêng `POST /process` trả `422 FILE_PARSE_ERROR` cũng có nghĩa session đã `FAILED`, nên FE hiện nút "Upload lại" ngay, không để user bấm chạy lại vô ích. `500 INTERNAL_ERROR` thì vẫn là lỗi chung, vì có thể không làm hỏng session. Nếu session đã hỏng thật, lệnh ghi kế tiếp sẽ nhận `409 SESSION_STATE_INVALID` và rơi về luật ở trên.
- Nhận biết theo `code`, không theo status. Lý do: BE trả `404` kèm `REQUEST_INVALID` khi gọi sai đường dẫn endpoint; đó chỉ là một lỗi thường, và tự xoá state của user trong trường hợp này là phá hoại.

### D13. Upload file mới thì xoá toàn bộ cấu hình, nhưng chỉ khi đã có session mới
- Có session mới nghĩa là cột nguồn có thể khác. Để đơn giản và có tính xác định, FE không cố giữ lại một phần cấu hình cũ.
- Việc thay state xảy ra đúng lúc session mới được tạo: action `sessionCreated` thay state nguyên khối.
- User xác nhận **không** `reset` trước. Nếu upload thay thế lỗi hoặc bị huỷ, session và cấu hình hiện tại vẫn còn nguyên.
- *Vì sao* (review FE-F01): bản đầu `reset` ngay khi xác nhận. Upload thay thế bị lỗi, hoặc user bấm "Huỷ" vì chọn nhầm file, là mất sạch cấu hình, không lấy lại được. Session cũ vẫn sống trên BE, nhưng FE đã mất `id` của nó.

### D14. UI: CSS Modules + CSS variables; không UI kit; truy cập được bằng bàn phím
- Token màu, khoảng cách, font nằm trong `src/index.css`. Mỗi component có một file `*.module.css`.
- Mọi input có label. Nút chỉ có icon (Lên, Xuống, Xoá) có `aria-label`. Bảng dùng `<th scope>`.
- **Vùng live (`role="status"`)**:
  - Luôn nằm sẵn trong DOM, chỉ đổi nội dung, để screen reader bắt được thay đổi.
  - Không bọc cả khối đang đổi liên tục: tiến độ upload chỉ báo lúc bắt đầu và lúc gửi xong, còn `<progress>` tự mang giá trị. Như vậy screen reader không đọc lại cả khối ở mỗi phần trăm.
  - Class `sr-only` (trong `index.css`) dùng cho chữ chỉ dành cho screen reader, ví dụ "(đã xong)" trên stepper.
- **Focus**: khi một control biến mất hoặc bị khoá lúc đang giữ focus, focus được chuyển có chủ đích.
  - Hộp xác nhận hiện ra: focus nút xác nhận.
  - Bắt đầu upload: focus nút "Huỷ".
  - Huỷ hoặc lỗi: focus về ô chọn file.
  - Đổi bước mà focus rơi về đầu trang (nút "Tiếp"/"Quay lại"/"Upload lại" hoặc ô chọn file biến mất cùng bước cũ): `WizardShell` focus tiêu đề `h2` của bước mới (`tabIndex={-1}`). Đổi bước bằng stepper thì nút stepper vẫn còn, nên focus giữ nguyên ở đó (FE-F02).
  - Bấm "Thử lại" ở bước Xem trước: khối lỗi biến mất, focus về tiêu đề bước (FE-F02).
  - Khi focus tới một control vừa được gắn lỗi, state lỗi được render xong trước (`flushSync`), rồi mới focus. Screen reader đọc ô lúc nó nhận focus; `aria-describedby` gắn sau đó không được đọc lại (review FE-F04).
  - Bước Schema (FE-F04): "Thêm field" → ô tên của field mới; "Lên"/"Xuống" tới biên thì nút vừa bấm bị khoá → nút chiều ngược lại của cùng field; "Xoá" → ô tên của field kề bên, hết field thì nút "Thêm field"; lưu lỗi → ô tên của field lỗi đầu tiên, hoặc tiêu đề bước.
  - Tiêu đề bước có `align-self: flex-start`, để viền focus ôm theo chữ thay vì kéo hết chiều ngang.
- **Vùng nội dung căn giữa, rộng tối đa 1280px** (người dùng yêu cầu ngày 2026-09-26). Trước đó là 1120px và dồn trái, nên trên màn khoảng 2000px bên phải trống gần 600px. Màn laptop khoảng 1440px không đổi, vì vùng nội dung vẫn dùng hết bề ngang. Thay cho con số 1120px ở design D5 của change `fe-app-shell` (đã archive).
  - Vùng live của bước Xem trước là một `<p role="status">` luôn nằm trong DOM: "Đang tải…" rồi "Xem trước x / y dòng". `Spinner` chỉ để nhìn (`aria-hidden`) (FE-F02).
- Thả file ra ngoài vùng upload: chặn ở `window` (`dragover`/`drop`, đặt `dropEffect = 'none'`), để trình duyệt không mở file và rời khỏi app. Input bị khoá có `pointer-events: none`, để sự kiện thả rơi vào vùng upload.
- Sắp xếp bằng nút Lên/Xuống thay vì kéo-thả, vì dùng được bằng bàn phím và không cần thêm thư viện.
- Chuỗi giao diện bằng tiếng Việt, gom vào `shared/messages.ts`, gồm cả bảng `code → thông điệp`. BE cam kết không đổi `code`, còn `message` và `detail` của BE viết bằng tiếng Anh.
- **Giao diện theo hướng C "Khối Thuỵ Sĩ" trên Claude Design**: https://claude.ai/artifact/MCkwHpanZPv1TUiW6trSbP (artboard "C · Khối Thuỵ Sĩ"; 5 màn ở hàng đầu là bản sạch/tối giản đầu tiên, đã được thay).
  - Token (màu, bo góc, bóng đổ), font (Be Vietnam Pro, JetBrains Mono) và khung dashboard có sidebar được ghi ở change `fe-app-shell` (design D3–D5; đã archive ở `openspec/changes/archive/2026-09-26-fe-app-shell/`, spec chính ở `openspec/specs/app-shell/`).
  - Các bước sau (Preview, Schema, …) theo cùng ngôn ngữ hình ảnh: khung viền mực 2 px, không bo góc, bóng cứng, vàng làm điểm nhấn; bảng bên trong dùng đường kẻ 1 px cho khỏi rối.
- **`DataTable`** (FE-F02):
  - Bảng rộng theo nội dung (`width: max-content`, tối thiểu bằng khung) và cuộn ngang trong khung; khung cuộn nhận focus (`role="region"` có tên) để cuộn được bằng bàn phím. Cột số dòng dính bên trái khi cuộn.
  - Ô giữ nguyên giá trị (`white-space: pre-wrap`: khoảng trắng đầu/cuối và xuống dòng vẫn hiện), chỉ xuống dòng khi dài quá 40 ký tự. Giới hạn đặt ở khối bên trong ô, vì trình duyệt bỏ qua `max-width` của `<td>`.
  - *Vì sao*: bản đầu để bảng rộng 100% khung. Ảnh chụp preview XLSX 15 cột với BE thật cho thấy ô bị bóp, `84901234567` và ngày giờ bị bẻ thành 3–4 dòng.
  - Ô `null` hiện gạch ngang mờ, screen reader đọc "Ô trống" (`EmptyCell`), không bao giờ hiện chữ `null`.
  - Ô số dòng là `<th scope="row">`, để screen reader đọc số dòng khi đi ngang qua các ô. Vùng cuộn lấy tên từ `<caption>` qua `aria-labelledby`, không lặp lại bằng `aria-label`.
- Nút "Quay lại"/"Tiếp" ở chân bước là component chung `wizard/StepActions.tsx`: bị khoá khi wizard bận; khi "Tiếp" bị khoá, lý do luôn hiện cạnh nút và là `aria-describedby` của nút.
- `ErrorBanner` có hai dòng:
  - **Dòng chính** chọn theo thứ tự ưu tiên: thông điệp FE theo `code` → `detail` → `title` → thông điệp chung theo HTTP status.
  - **Dòng phụ**: khi dòng chính lấy từ bảng của FE và BE có `detail`, hiện `detail` ở đây. Ví dụ `FILE_PARSE_ERROR` có kèm số dòng bị lỗi.

### D15. Test: Vitest + Testing Library + user-event + MSW; chế độ `dev:mock`
- **Unit**: reducer, guards, mappers, `schemaRules`, `configRules`, `apiError`, `client`, `contentDisposition`, `download`, `upload`, `checkFiles`, `describeError`.
- **Component**: từng bước, render `<App/>` với MSW handler theo từng tình huống (`server.use(...)`). Setup đặt `onUnhandledRequest: 'error'`, nên request nào không có handler đều làm test fail.
- **Tích hợp**: render `<App/>` và chạy hết 6 bước với MSW, một lần cho CSV và một lần cho XLSX.
- **Mock**: `.env.mock` bật `VITE_USE_MOCK=true`; script `pnpm dev:mock` chạy `vite --mode mock`, chạy được trên Windows mà không cần `cross-env`. `main.tsx` chỉ import và khởi động MSW worker khi cờ này bật. Mock là fixture theo contract V0.1, **không** mô phỏng pipeline.
- Vitest phải chọn bản có peerDependency khớp Vite 8 (đang dùng 5.0.1).
- **jsdom ghim ở `^29.1.1`.** Vitest 5.0.1 bọc `Request` để đổi `FormData`/`Blob` của jsdom sang bản của Node, dựa vào một symbol ẩn mà jsdom 30 không còn để lộ. Kết quả: gửi `FormData` có file thì crash. Nâng jsdom thì chạy lại test upload trước (tasks 1.3).
- **Upload dùng XHR giả khi test tiến độ và huỷ.** Interceptor XHR của MSW bỏ qua `abort()` khi handler còn treo: request vẫn hoàn tất với 201, khác trình duyệt. Các test này thay `XMLHttpRequest` bằng `src/test/fakeXhr.ts` qua `vi.stubGlobal`; mọi test upload khác vẫn đi qua MSW. XHR giả cũng theo đúng trình duyệt ở điểm này: gọi `abort()` sau khi request đã xong thì không phát sự kiện nào.
- **Test focus sau khi chọn file dùng kéo-thả, không dùng `user.upload`.** Sau sự kiện `change`, user-event giả lập "hộp chọn file đóng thì trả focus về ô input". Lúc đó input đã bị khoá, nên user-event `blur` luôn nút vừa nhận focus. Trình duyệt thật trả focus về input trước khi phát `change`, nên `autoFocus` vẫn thắng.
- **Mutation check**: test nào không được thấy fail trước khi có code (vì code đã có sẵn) thì phải được kiểm bằng cách tạm làm hỏng code, rồi khôi phục.

### D16. Cấu hình môi trường
| Biến | Đọc ở | Mặc định | Ý nghĩa |
|---|---|---|---|
| `API_PROXY_TARGET` | `vite.config.ts` (qua `loadEnv`, không lộ ra client) | `http://localhost:8080` | Đích proxy `/api` khi chạy dev |
| `VITE_MAX_UPLOAD_MB` | `src/config.ts` | `20` | Giới hạn kiểm trước khi upload; phải khớp `IMPORTER_MAX_FILE_SIZE` của BE |
| `VITE_USE_MOCK` | `src/main.tsx` | `false` | Bật MSW worker trên trình duyệt |

`API_BASE` luôn là đường dẫn tương đối `/api`. Có `.env.example` mô tả các biến; `.env` bị gitignore ở gốc repo.

### D17. Cấu trúc thư mục
```
src/
  main.tsx                 # bootstrap; khởi động MSW khi VITE_USE_MOCK
  App.tsx                  # <AppShell tools={tools} /> (change fe-app-shell)
  app/                     # khung dashboard: AppShell, danh sách công cụ, ImportTool = <WizardProvider><WizardShell/></WizardProvider>
  config.ts                # đọc import.meta.env, có giá trị mặc định
  env.d.ts                 # kiểu của ImportMetaEnv
  api/
    dto.ts                 # contract V0.1 (mục API contract V0.1)
    apiError.ts            # ApiError, parse ProblemDetail; dùng chung cho XHR và fetch
    client.ts              # request() bằng fetch
    upload.ts              # XHR có tiến độ và huỷ
    download.ts            # fetch blob, saveBlob()
    contentDisposition.ts
    endpoints.ts           # các hàm gọi endpoint FE dùng
    mappers.ts             # DTO ↔ model nội bộ
  domain/
    types.ts               # TargetField (có key), FieldMapping, Transformation, UserRule, …
    schemaRules.ts         # kiểm tên field
    configRules.ts         # lỗi/cảnh báo của mapping và rules; rule suy ra
  wizard/
    state.ts  reducer.ts  guards.ts
    context.ts             # WizardContext và hook useWizard
    WizardProvider.tsx     # tách khỏi context.ts theo luật react/only-export-components
    WizardShell.tsx        # header, Stepper, bước hiện tại; tính trạng thái bước bằng canEnter
    StepActions.tsx        # nút "Quay lại"/"Tiếp" ở chân bước, kèm lý do khoá
    useBeforeUnload.ts
    useBusyRequest.ts      # bọc request làm bận wizard (bộ đếm trong reducer, D2)
    usePreventFileDrop.ts  # chặn trình duyệt mở file khi thả ra ngoài vùng upload (D14)
  features/
    upload/                # UploadStep.tsx, checkFiles.ts
    preview/               # PreviewStep.tsx
    preview/  schema/  mapping/  rules/  run/  result/  export/
  shared/
    ui/                    # Stepper (generic), ErrorBanner, ConfirmPanel, DataTable, EmptyState, Spinner, Pagination
    messages.ts  format.ts
    describeError.ts       # ApiError → { headline, detail, code } cho ErrorBanner
  mocks/
    fixtures.ts  handlers.ts  node.ts  browser.ts
  test/
    setup.ts               # jest-dom, vòng đời MSW server
    http.ts  files.ts  fakeXhr.ts   # helper chỉ dùng trong test
```

### D18. Kết quả cũ khớp với `RESULT_NOT_AVAILABLE` của BE
- BE xoá kết quả khi một lệnh PUT làm config thay đổi (so bằng `configHash`). Sau đó `GET result` và các endpoint export trả `409 RESULT_NOT_AVAILABLE`.
- FE đánh dấu kết quả là cũ ngay khi user sửa, tức là sớm hơn hoặc cùng lúc với BE, không bao giờ muộn hơn.
- Khi kết quả đã cũ:
  - FE giữ lại phần tóm tắt và trang đang xem;
  - khoá đổi trang, đổi tab, bộ lọc và export;
  - chỉ còn nút "Chạy lại".
- Nếu vẫn nhận `409 RESULT_NOT_AVAILABLE` (ví dụ do nguyên nhân phía BE), FE cũng đánh dấu kết quả là cũ và hiện cảnh báo.

### D19. `dateFormat` trên field kiểu `date` luôn xuất ISO
- Kiểu `date` chỉ nhận `yyyy-MM-dd` sau transformation, và BE trả `422 CONFIG_INVALID` nếu `outputFormat` khác ISO. Vì vậy, với field kiểu `date`, ô `outputFormat` bị khoá ở `yyyy-MM-dd`.
- Khi một field đổi kiểu sang `date`, reducer đặt lại `outputFormat` của mọi `dateFormat` trên field đó thành `yyyy-MM-dd`, giống cách đổi kiểu khỏi `string` thì xoá rule `email`.
- Với field kiểu khác, `outputFormat` cho nhập tự do và được điền sẵn `yyyy-MM-dd`.
- BE parse STRICT và tự đổi `y` thành `u`, nên các mẫu gợi ý dùng `yyyy` như bình thường.

### D20. Sinh schema từ cột nguồn (người dùng yêu cầu ngày 2026-09-26)
- Preview tải xong thì reducer (`previewLoaded`) sinh sẵn schema: mỗi cột một field cùng tên, cùng thứ tự, không bắt buộc. Chỉ sinh khi schema đang trống, và preview chỉ tải một lần cho mỗi session, nên không bao giờ đè lên field user đã sửa.
- Kiểu được đoán bằng hàm thuần `domain/inferSchema.ts`, dùng đúng luật kiểm kiểu của BE-F07 (không trim), để kiểu đoán ra không tự sinh lỗi `VALIDATION_TYPE` trên các dòng đã xem. Chỉ xét tối đa 50 dòng preview, nên dòng sau vẫn có thể sai kiểu; lỗi đó hiện ở bước Kết quả và user đổi kiểu được.
- Cột chỉ có `1`/`0` là `number`, không phải `boolean`: hay gặp ở cột số lượng hơn cột đúng/sai. `boolean` cần ít nhất một ô `true`/`false`.
- "Tạo lại từ file" thay toàn bộ field bằng bản sinh mới, hỏi xác nhận khi đang có field. Field sinh lại nhận key mới (tiếp theo `nextFieldSeq`), để cấu hình Mapping và rules gắn theo key cũ không bám nhầm vào field mới.
- FE-F05 dự kiến map mặc định mỗi field sang cột nguồn cùng tên.

## API contract V0.1 (đã chốt với BE)

Quy ước chung:
- Base path `/api/import-sessions`, JSON UTF-8, thời gian dạng ISO-8601 UTC.
- `page` bắt đầu từ 0. `limit` (preview) và `size` (result) mặc định 50, tối đa 200.
- Mọi lỗi trả `application/problem+json` và luôn có `code`.
- Enum: `status`, `fileType`, `mappingType` viết HOA; field type viết thường; transformation type viết camelCase.

### Endpoint

| # | Method + path | Request | Thành công | Lỗi chính | FE dùng |
|---|---|---|---|---|---|
| 1 | `POST /api/import-sessions` | multipart, part `file` | `201` `ImportSessionDto` | `400`, `413 FILE_TOO_LARGE`, `415 FILE_UNSUPPORTED`, `422 FILE_EMPTY` / `FILE_PARSE_ERROR` | Upload |
| 2 | `GET /api/import-sessions/{id}` | — | `200` `ImportSessionDto` (có `config`, `readiness` từ BE-F04) | `400`, `404` | Chưa dùng ở V0.1 |
| 3 | `GET /api/import-sessions/{id}/preview?limit=50` | — | `200` `SourcePreviewDto` | `400`, `404` | Preview |
| 4 | `PUT /api/import-sessions/{id}/schema` | `TargetSchemaDto` | `200` `ConfigUpdateResponseDto` | `404`, `409`, `422 SCHEMA_INVALID` | Schema |
| 5 | `PUT /api/import-sessions/{id}/mapping` | `MappingConfigDto` | `200` `ConfigUpdateResponseDto` | `404`, `409`, `422 MAPPING_INVALID` / `SOURCE_COLUMN_NOT_FOUND` | Mapping |
| 6 | `PUT /api/import-sessions/{id}/transformations` | `TransformationConfigDto` | `200` `ConfigUpdateResponseDto` | `404`, `409`, `422 CONFIG_INVALID` | Chạy xử lý |
| 7 | `PUT /api/import-sessions/{id}/validations` | `ValidationConfigDto` | `200` `ConfigUpdateResponseDto` | `404`, `409`, `422 CONFIG_INVALID` | Chạy xử lý |
| 8 | `POST /api/import-sessions/{id}/process` | — | `200` `PipelineSummaryDto` | `404`, `409 SESSION_NOT_READY` / `SESSION_STATE_INVALID`; đọc file lỗi thì trả `422 FILE_PARSE_ERROR` (sai cấu trúc) hoặc `500 INTERNAL_ERROR` (IO, mất file), và session chuyển sang `FAILED` | Chạy xử lý |
| 9 | `GET /api/import-sessions/{id}/result?view=valid\|invalid&page=0&size=50&field=&code=` | — | `200` `PipelineResultDto` | `400`, `404`, `409 RESULT_NOT_AVAILABLE` | Result |
| 10 | `GET /api/import-sessions/{id}/export?format=json\|csv` | — | `200` file + `Content-Disposition` | `400`, `404`, `409`, `500 EXPORT_FAILED` | Export |
| 11 | `GET /api/import-sessions/{id}/errors/export` | — | `200` file CSV + `Content-Disposition` | `404`, `409`, `500 EXPORT_FAILED` | Export |

### Kiểu dữ liệu
```ts
type SessionStatus = 'UPLOADED' | 'CONFIGURING' | 'READY' | 'PROCESSED' | 'FAILED'
type SourceFileType = 'CSV' | 'XLSX'
type FieldType = 'string' | 'number' | 'boolean' | 'date' | 'email'
type MappingType = 'SOURCE_COLUMN' | 'CONSTANT'
type TransformationType = 'trim' | 'uppercase' | 'lowercase' | 'defaultValue' | 'dateFormat'
type UserValidationType = 'email' | 'unique'            // required/type do BE suy ra từ schema

interface ProblemItemDto { field: string | null; code: string; message: string }   // field = tên target field

interface ApiProblemDto {                                // application/problem+json
  type: string; title: string; status: number; detail?: string; instance?: string
  code: string                                           // luôn có
  errors?: ProblemItemDto[]                              // chỉ có khi lỗi gắn với từng field, hoặc là danh sách readiness issue
}

interface ImportSessionDto {
  id: string                                             // UUID
  status: SessionStatus
  originalFileName: string
  fileType: SourceFileType
  sizeBytes: number
  createdAt: string
  updatedAt: string
  config?: { schema: TargetSchemaDto; mapping: MappingConfigDto;
             transformations: TransformationConfigDto; validations: ValidationConfigDto }   // từ BE-F04; FE V0.1 không dùng
  readiness?: { ready: boolean; issues: ProblemItemDto[] }                                 // từ BE-F04; FE V0.1 không dùng
}

interface ConfigUpdateResponseDto { session: ImportSessionDto; warnings: ProblemItemDto[] }  // FE bỏ qua (D6)

interface SourcePreviewDto {
  sessionId: string
  fileType: SourceFileType
  sheetName: string | null                               // XLSX: sheet hiển thị đầu tiên; CSV: null
  columns: { index: number; name: string }[]             // đúng thứ tự gốc; name luôn duy nhất và không rỗng
  rows: { rowNumber: number; values: (string | null)[] }[]   // values[i] ứng với columns[i]; giá trị gốc, không trim; ô rỗng là null
  previewLimit: number
  totalRows: number                                      // số dòng dữ liệu không trống; luôn có
}

interface TargetSchemaDto {
  fields: { name: string; type: FieldType; required: boolean; order: number }[]   // tên 1–100 ký tự sau trim; order từ 0
}

interface MappingConfigDto {
  mappings: { targetField: string; mappingType: MappingType;
              sourceColumn: string | null; constantValue: string | null }[]      // chỉ gồm field đã map
}

interface TransformationConfigDto {
  transformations: { targetField: string; order: number; type: TransformationType;
                     params?: Record<string, string> }[]
  // defaultValue: { value }; dateFormat: { inputFormat, outputFormat? = 'yyyy-MM-dd' }; loại khác: FE không gửi params
  // order bắt đầu từ 0, không trùng trong cùng một targetField
}

interface ValidationConfigDto {
  validations: { targetField: string; type: UserValidationType; params?: Record<string, string> }[]   // FE không gửi params
}

interface PipelineSummaryDto {
  sessionId: string; status: SessionStatus
  total: number; valid: number; invalid: number          // total = valid + invalid
  errorCountsByCode: Record<string, number>
  errorCountsByField: Record<string, number>
  processedAt: string
}

interface PipelineResultDto {
  summary: PipelineSummaryDto
  view: 'valid' | 'invalid'
  page: { number: number; size: number; totalElements: number; totalPages: number }
  rows: { rowNumber: number; valid: boolean; values: Record<string, unknown>; errors: ImportErrorDto[] }[]
  // values: row hợp lệ đã ép kiểu (number, boolean, ngày 'yyyy-MM-dd'); row lỗi là chuỗi sau transformation,
  // hoặc null ở field có transformation lỗi. FE hiển thị theo thứ tự schema, không theo thứ tự key.
}

interface ImportErrorDto {                               // mỗi field chỉ báo lỗi đầu tiên; một row vẫn có thể có lỗi ở nhiều field
  rowNumber: number                                      // như khi mở file bằng Excel: header là dòng 1
  fieldName: string                                      // luôn có
  stage: 'TRANSFORMATION' | 'VALIDATION'
  rule: string                                           // 'dateFormat', 'required', 'type', 'email', 'unique', …
  step: number | null                                    // order của transformation; null với validation
  code: string                                           // TRANSFORMATION_FAILED | VALIDATION_*
  message: string
  sourceValue: string | null                             // giá trị trước transformation
}
```

### Mã lỗi và các mã khác

| Nhóm | Mã |
|---|---|
| Lỗi API (HTTP) | `400 REQUEST_INVALID` (lỗi 4xx của framework cũng dùng mã này nhưng giữ status gốc, ví dụ `404` khi sai endpoint), `404 SESSION_NOT_FOUND`, `409 SESSION_NOT_READY`, `409 SESSION_STATE_INVALID`, `409 RESULT_NOT_AVAILABLE`, `413 FILE_TOO_LARGE`, `415 FILE_UNSUPPORTED`, `422 FILE_EMPTY`, `422 FILE_PARSE_ERROR`, `422 SCHEMA_INVALID`, `422 MAPPING_INVALID`, `422 SOURCE_COLUMN_NOT_FOUND`, `422 CONFIG_INVALID`, `500 EXPORT_FAILED`, `500 INTERNAL_ERROR` |
| Lỗi theo row (`ImportErrorDto.code`) | `TRANSFORMATION_FAILED`, `VALIDATION_REQUIRED`, `VALIDATION_TYPE`, `VALIDATION_EMAIL`, `VALIDATION_UNIQUE` |
| Readiness issue (`errors[]` của `409 SESSION_NOT_READY`) | `SCHEMA_EMPTY`, `TARGET_FIELD_REQUIRED` |
| Warning kèm PUT (FE bỏ qua) | `CONFIG_PRUNED`, `TARGET_FIELD_UNMAPPED`, `RULE_IMPLIED_BY_SCHEMA` |

`shared/messages.ts` MUST có thông điệp tiếng Việt cho mọi mã trong ba nhóm đầu. Với mã lạ, FE dùng `detail` hoặc `message` của BE.

### File export
- **JSON**: mảng object; key theo thứ tự schema; giá trị đúng kiểu.
- **CSV**: header theo thứ tự schema; UTF-8 có BOM; BE đã chống formula injection.
- **Error report**: CSV, mỗi lỗi một dòng, các cột `rowNumber,fieldName,stage,rule,step,code,message,sourceValue`.
- **Tên file** do BE đặt: `<tên-gốc>-valid.json`, `<tên-gốc>-valid.csv`, `<tên-gốc>-errors.csv`. Tên dự phòng phía FE dùng cùng mẫu này.

### Xử lý lỗi (chuẩn hoá thành `ApiError`)

```ts
class ApiError extends Error {
  kind: 'http' | 'network' | 'aborted'
  status: number | null
  code: string | null
  detail: string | null
  fieldErrors: ProblemItemDto[]
}
```

| Tình huống | Cách nhận biết | Kết quả trong `ApiError` | UI |
|---|---|---|---|
| ProblemDetail | content-type chứa `json` | `kind=http`, `status`, `code`, `detail`, `fieldErrors` | `ErrorBanner` (D14) |
| Lỗi theo field | có `errors[]` | `fieldErrors` | lỗi hiện tại field có tên khớp, phần còn lại ở đầu form; với `SESSION_NOT_READY` thì hiện dạng danh sách readiness issue |
| Session không dùng được | `code` là `SESSION_NOT_FOUND` hoặc `SESSION_STATE_INVALID` | như trên | báo lỗi kèm nút "Upload lại", không tự reset (D12) |
| Kết quả cũ | `code = RESULT_NOT_AVAILABLE` | như trên | đánh dấu kết quả là cũ (D18) |
| Body không phải JSON (HTML 502 từ proxy khi BE tắt…) | content-type không chứa `json` | `code=null`; riêng `413` thì `code='FILE_TOO_LARGE'` | "Máy chủ đang lỗi (502)"; có nút "Thử lại" nếu là GET |
| Mất mạng | `fetch` reject với `TypeError`; hoặc XHR `onerror` | `kind=network` | "Không kết nối được máy chủ"; có nút "Thử lại" nếu là GET |
| Huỷ | `AbortError`; hoặc XHR `onabort` | `kind=aborted` | không hiện lỗi |

## Risks / Trade-offs

- [Bản chép contract trong file này trôi dần khỏi file của BE] → File BE là chuẩn (xem Context). Mọi thay đổi contract phải sửa ở cả `design.md` này lẫn `dto.ts`, `mappers.ts`, `mocks/`. Task 13.4 kiểm lại với BE thật.
- [Q1 lệch trong thực tế: `required` và `type` không chạy mà không báo gì] → BE đã xác nhận Q1, nhưng checklist 13.4 vẫn có ca "field required để trống" và ca "sai kiểu", chạy với BE thật.
- [Số rất lớn (trên khoảng 15 chữ số) hiển thị sai trên bảng kết quả, vì `JSON.parse` dùng số thực double] → Chỉ ảnh hưởng phần hiển thị; file export do BE ghi nên vẫn đúng. Gợi ý user dùng kiểu `string` cho mã số dài.
- [File export nằm trọn trong RAM trình duyệt] → Bị chặn trên bởi giới hạn upload 20 MB (D10). Về sau đổi sang tải qua link trực tiếp.
- [Mất state khi tải lại trang hoặc bấm Back của trình duyệt] → cảnh báo `beforeunload` (D12). Về sau có thể khôi phục qua `GET /api/import-sessions/{id}`.
- [User bấm "Huỷ" sau khi đã gửi 100% file, lúc BE đang đọc file] → BE có thể vẫn tạo session, và FE bỏ qua nó. Session mồ côi này bị BE xoá sau 24 giờ không hoạt động. FE giữ nút Huỷ ở pha này, vì bắt user chờ một máy chủ chậm còn tệ hơn, và hiển thị "Đang đọc file trên máy chủ…" để user biết việc gì đang diễn ra.
- [Process đồng bộ chạy lâu và không huỷ được] → có chỉ báo "Đang xử lý…" và khoá điều hướng; thời gian bị chặn trên bởi giới hạn upload. Chạy nền là non-goal của V0.1.
- [Mock trôi dần khỏi BE thật] → mock chỉ là fixture của contract V0.1. Việc kiểm với BE thật ở task 13.4 là bắt buộc trước khi coi V0.1 là xong.

## Migration Plan

- Không có dữ liệu hay người dùng hiện hữu; thay template trực tiếp.
- Làm theo nhánh gợi ý trong Notion: `feature/fe-f01-upload`, `feature/fe-f02-source-preview`, …, `feature/fe-f11-wizard-polish`.
- Luồng nhánh (người dùng chốt ngày 2026-09-26): nhánh feature merge vào `dev`; `dev` chỉ vào `main` khi mọi feature đã xong. Không commit thẳng lên `dev` hay `main`; merge vào nhánh chung phải hỏi trước.
- Nối BE thật theo từng feature, khi feature BE tương ứng đã có trên nhánh của BE. Rollback bằng cách revert nhánh.

## Open Questions

### Đã được BE trả lời (2026-09-25)

Nguồn: `openspec/changes/archive/2026-09-25-be-f01-import-session/design.md`, mục "Trả lời Open Questions của FE".

- **Q1**: Có. Payload validations chỉ gồm `email` và `unique`; BE tự áp `required` và `type` từ schema. Nếu nhận `required` hoặc `type`, BE bỏ qua và trả warning `RULE_IMPLIED_BY_SCHEMA`. → FE giữ nguyên D7.
- **Q2**: Config sai trả `422` kèm `errors[{field, code, message}]`. JSON hỏng trả `400 REQUEST_INVALID`. PUT thành công trả `200 {session, warnings}`. → FE bỏ qua body của PUT (D6).
- **Q3**: Có phân trang (`size` tối đa 200), lọc (`field`, `code`), `errorCountsByCode` và `errorCountsByField`. Result khi chưa process hoặc đã cũ trả `409 RESULT_NOT_AVAILABLE`. → Giữ task 11.4; thêm D18.
- **Q4**: `rowNumber` theo Excel: header là dòng 1; dòng trống bị bỏ qua nhưng vẫn được đếm số. → FE hiển thị nguyên giá trị.
- **Q5**: Pattern theo `DateTimeFormatter` của Java, parse STRICT (BE tự đổi `y` thành `u`). Kiểu `date` chỉ nhận ISO `yyyy-MM-dd` sau transformation. `dateFormat` trên field `date` mà output khác ISO trả `422 CONFIG_INVALID`. → Thêm D19.
- **Q6**: Error report là CSV với các cột `rowNumber,fieldName,stage,rule,step,code,message,sourceValue`.
- **Q7**: Part tên `file`; giới hạn 20 MB; vượt giới hạn luôn trả `413 FILE_TOO_LARGE`. → `VITE_MAX_UPLOAD_MB=20` (D11, D16).
- **Q8**: Có trường `rule`, thêm `stage` và `step`; nhận đủ các mã FE đề xuất; thêm `FILE_EMPTY`, `REQUEST_INVALID`, `SESSION_STATE_INVALID`, `RESULT_NOT_AVAILABLE`, `INTERNAL_ERROR`.
- **Q9**: Header trùng hoặc rỗng được đặt lại tên (`Email (2)`, `Column C`), nên `columns[].name` luôn duy nhất và không rỗng. Ô XLSX: số dạng chuỗi plain, ngày ISO `yyyy-MM-dd` (có giờ thì `yyyy-MM-dd'T'HH:mm:ss`), ô chỉ có giờ dạng `HH:mm:ss` (chốt thêm ngày 2026-09-25), boolean `TRUE`/`FALSE`. → Bỏ task 6.3; FE hiển thị nguyên giá trị.
- **Q10**: BE chạy cổng 8080; FE gọi cùng origin qua Vite proxy; V0.1 không có CORS.

Ngoài 10 câu trên, BE còn cho biết:
- Upload đọc toàn bộ file ngay lúc đó, nên lỗi `FILE_EMPTY` và `FILE_PARSE_ERROR` (kể cả workbook hoặc sheet rỗng) trả về ở **bước Upload**, không phải ở bước Preview.
- `POST /process` mà đọc file nguồn lỗi thì session chuyển sang `FAILED`, và response là `422 FILE_PARSE_ERROR` (sai cấu trúc) hoặc `500 INTERNAL_ERROR` (IO, mất file). Mọi lệnh ghi sau đó trả `409 SESSION_STATE_INVALID`. → FE xử lý theo D12.

### Còn mở

- **Khôi phục state sau khi tải lại trang**: có đưa vào V0.1 không? BE đã có `GET /api/import-sessions/{id}` trả kèm `config` và `readiness`. Mặc định: chưa làm ở V0.1. Cần user quyết.
