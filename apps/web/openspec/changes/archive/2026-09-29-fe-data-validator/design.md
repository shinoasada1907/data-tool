## Context

- FE hiện có một công cụ là wizard Import 6 bước (`src/wizard`, `src/features`). AppShell chỉ render công cụ đang mở, nên chuyển công cụ là mất state.
- BE Validator chạy đồng bộ và lưu kết quả thành một **run**, TTL 24h kể từ lần dùng cuối.
- Mọi ô trong API là `string|null`.
- Lỗi theo ProblemDetail. Với `SCHEMA_INVALID`, mỗi item có `pointer` tính từ gốc body, ví dụ `/schema/fields/1/constraints/min`.
- Người dùng đã giao toàn quyền thiết kế FE.
- Notion Toolbox lúc đầu muốn thiết kế cả 5 tool một lượt. Người dùng đã dừng việc đó vì tốn token. Change này chỉ làm Validator, cộng đúng phần khung cần cho nó.

## Goals / Non-Goals

**Goals:**
- Đi hết luồng Validator trên BE thật.
- `DatasetSource` đủ tổng quát để Converter dùng lại mà không phải sửa.

**Non-Goals:**
- Router hay URL theo công cụ, trang chủ.
- Schema đã lưu và `X-Guest-Token`.
- Dời code Importer sang `src/tools/import`.
- BE giả cho `dev:mock`.
- Ô nhập `defaultValue`.

## Decisions

### V1. Công cụ thứ hai và giữ trạng thái
- `tools.ts` thêm `validator` sau `import`.
- AppShell giữ danh sách công cụ **đã từng mở** và render tất cả; công cụ không đang mở thì bọc trong phần tử `hidden`.
- Công cụ chưa mở lần nào thì chưa mount, nên không gọi API thừa.
- Không đổi sang router: hai công cụ, không cần deep link.

### V2. Tên app "Universal Data Tools"
- Đổi `messages.appName` và `<title>` trong `index.html`.
- Giữ key `localStorage` của sidebar. Key là nội bộ; đổi thì user mất trạng thái đã lưu mà không được gì.

### V3. `DatasetSource` là component có điều khiển (controlled)
- State nằm trong reducer của công cụ: `dataset`, `options` (lựa chọn của user, `undefined` = để BE tự nhận) và `preview`.
- Component tự lo upload (XHR có tiến độ, huỷ được) và gọi preview. Kết quả báo lên qua callback.
- Đổi tuỳ chọn thì gọi lại preview và huỷ lượt trước.
- Run gửi **`preview.options`**, tức giá trị BE thực dùng, để chạy đúng cách đọc user vừa thấy.
- Đổi hoặc xoá file thì gọi `DELETE` dataset cũ mà không chờ. Site public nên dọn sớm, lỗi thì bỏ qua vì TTL cũng dọn.

### V4. Schema nháp theo key
- Field có key cố định `f1`, `f2`… giống Importer (design D3 của Importer), để sửa tên không làm lệch lỗi hay thứ tự.
- Ràng buộc giữ ở dạng chuỗi đang nhập. Lúc gửi mới đổi sang số, bỏ ràng buộc rỗng, và chỉ gửi ràng buộc hợp với kiểu:
  - `min`/`max` cho `number`;
  - `minLength`/`maxLength`/`pattern` cho `string`/`email`;
  - `format` cho `date`.
- Đổi kiểu không xoá chữ đã nhập, chỉ ẩn đi.
- Tự sinh từ cột: kiểu lấy theo `inferredType`, riêng `empty` thì thành `string`; `required` để `false`. Vào bước Schema lần đầu mà chưa có field nào thì tự sinh luôn.

### V5. Hai lớp kiểm schema
- **FE kiểm ngay khi gõ**:
  - tên schema rỗng;
  - tên field rỗng, hoặc trùng sau khi trim và không phân biệt hoa thường;
  - số không đọc được, hoặc độ dài âm;
  - `min > max`, `minLength > maxLength`.
- **BE kiểm lúc chạy**: pattern RE2, format ngày, `defaultValue`. Lỗi `422 SCHEMA_INVALID` được map theo `pointer` vào đúng ô; pointer không map được thì hiện ở banner.
- Không gọi `POST /api/schemas/validate` khi đang gõ: tiết kiệm request, và lượt chạy cũng trả đủ mọi lỗi.

### V6. Khớp cột trước khi chạy
- FE tính theo đúng luật BE:
  1. tên giống hệt trước;
  2. không có thì lấy cột đầu tiên còn trống mà giống khi trim và không phân biệt hoa thường;
  3. mỗi cột chỉ khớp một field.
- Mục đích chỉ để hiển thị và chặn sớm field bắt buộc không có cột.
- Kết quả chính thức lấy từ `compatibility` của run. Lỗi `SCHEMA_INCOMPATIBLE` từ BE vẫn được hiện nếu có.

### V7. File schema
- Định dạng `{ "format": "udt.schema", "version": 1, name, fields }`, do FE tự đọc và ghi.
- Mở file thì kiểm cấu trúc tối thiểu; sai thì báo và giữ nguyên schema đang có.
- `defaultValue` được giữ qua mở/lưu nhưng không có ô nhập, vì Validator không dùng.

### V8. Kết quả là ảnh chụp của một run
- Sửa schema hoặc nguồn sau khi chạy thì run bị đánh dấu **cũ**: có banner và nút "Chạy lại", nhưng vẫn xem và tải được, vì run tự nhất quán.
- Run mới thay run cũ thì `DELETE` run cũ, không chờ.
- `404 RUN_NOT_FOUND` khi tải trang hoặc tải file nghĩa là run đã hết hạn: báo và để user chạy lại.

### V9. Tải file
- Chọn nội dung (`VALID|INVALID|ERRORS`) và định dạng (`CSV|XLSX|JSON`). Các tuỳ chọn khác dùng mặc định của `OutputDto` (CSV có BOM và formula guard; typing `PRESERVE`).
- Tải bằng `POST` + blob. `download()` được mở rộng để nhận `method` và `body`.
- Form định dạng đặt ở `src/shared/output` để Converter dùng lại.

## Risks / Trade-offs

- **Khớp cột ở FE trùng luật với BE.** Lệch thì chỉ sai phần hiển thị trước khi chạy; lượt chạy vẫn đúng. Có test cho luật này.
- **Kết quả không có URL.** Tải lại trang là mất kết quả; đã có cảnh báo `beforeunload` khi có dữ liệu.
- **Giữ công cụ ẩn vẫn mount** nên tốn bộ nhớ, nhưng chỉ vài trang kết quả 50 dòng, chấp nhận được.
