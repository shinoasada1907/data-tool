## ADDED Requirements

### Requirement: Header bảo vệ và quy ước file tải về
- Mọi response SHALL có `X-Content-Type-Options: nosniff`.
- Response của `/api/datasets/**` và `/api/*/runs/**` SHALL có `Cache-Control: no-store`.
- Mọi file tải về của các tool SHALL có:
  - `Content-Disposition: attachment` kèm `filename*` theo RFC 5987;
  - tên file làm sạch theo luật tên file export hiện hành: bỏ đuôi cuối; thay `"`, `\`, `/` và ký tự điều khiển bằng `_`; rỗng thì dùng `export`;
  - `Cache-Control: no-store`.
- Mọi lỗi phát hiện được trước khi gửi byte đầu tiên SHALL trả dạng `application/problem+json`.
- Lỗi xảy ra sau byte đầu tiên SHALL làm kết nối bị huỷ, và file MUST NOT kết thúc như thể trọn vẹn.

#### Scenario: Header của file tải về
- **WHEN** client tải một file kết quả của dataset có `originalFileName` là `báo cáo.xlsx`
- **THEN** response có `Content-Disposition` chứa `attachment` và `filename*=UTF-8''b%C3%A1o%20c%C3%A1o`, và có `Cache-Control: no-store`
