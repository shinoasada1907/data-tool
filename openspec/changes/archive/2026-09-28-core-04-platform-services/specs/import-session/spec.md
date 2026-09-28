## MODIFIED Requirements

### Requirement: Dọn thư mục lưu trữ mồ côi
Mỗi lần dọn dẹp, hệ thống SHALL xoá những thư mục con của storage root thoả đồng thời:
- có tên là một UUID ở dạng chuẩn;
- là thư mục thật, không phải link hay junction;
- không thuộc tài nguyên nào mà database biết: session Importer, dataset, hoặc mọi loại tài nguyên có lưu trữ về sau;
- có thời điểm sửa cuối cũ hơn TTL nhỏ nhất của các loại tài nguyên, và cũ hơn ít nhất 1 giờ.

Thư mục hay file có tên không phải UUID MUST NOT bị xoá. Thư mục mồ côi còn mới MUST NOT bị xoá, vì có thể là upload đang dở. Việc xoá MUST NOT đi xuyên link hay junction ra ngoài thư mục đang xoá.

Storage root thuộc về một database duy nhất:
- Lần dọn đầu tiên SHALL ghi id của database (bảng `installation`) vào `{storageRoot}/.owner`, nếu storage còn trống hoặc có chứa ít nhất một tài nguyên mà database biết.
- Storage có `.owner` của database khác, hoặc chưa có `.owner` mà chỉ chứa thư mục lạ, SHALL không bị dọn mồ côi. Hệ thống SHALL ghi log lỗi.
- Một lượt thấy hơn 10 mồ côi, và số mồ côi nhiều hơn một nửa tổng số thư mục mà database biết, SHALL không xoá mồ côi nào và ghi log lỗi.

#### Scenario: Mồ côi quá hạn bị xoá
- **WHEN** storage root có thư mục `{X}` (X là UUID), không session hay dataset nào có id X, và thư mục được sửa lần cuối cách đây 25 giờ
- **THEN** cleanup xoá thư mục `{X}`, và report ghi `deletedOrphans` là `1`

#### Scenario: Thư mục của dataset không bị coi là mồ côi
- **WHEN** storage root có thư mục `{D}` của một dataset còn hạn, tạo cách đây 30 giờ nhưng được dùng lần cuối cách đây 1 giờ
- **THEN** thư mục `{D}` vẫn còn sau cleanup

#### Scenario: Mồ côi còn mới được giữ
- **WHEN** storage root có thư mục `{Y}` (Y là UUID), không tài nguyên nào có id Y, và thư mục được sửa lần cuối cách đây 1 giờ
- **THEN** thư mục `{Y}` vẫn còn sau cleanup

#### Scenario: Thư mục không phải UUID không bị đụng tới
- **WHEN** storage root có thư mục `backup` được sửa lần cuối cách đây 30 ngày
- **THEN** thư mục `backup` vẫn còn sau cleanup

#### Scenario: Storage của database khác
- **WHEN** `{storageRoot}/.owner` ghi id của một database khác, và storage có thư mục `{X}` cũ 3 ngày mà database hiện tại không biết
- **THEN** thư mục `{X}` vẫn còn sau cleanup

#### Scenario: Junction không bị đi xuyên qua
- **WHEN** bên trong thư mục của một session có một junction trỏ ra một thư mục ngoài storage, và session đó bị xoá
- **THEN** junction bị gỡ, còn thư mục bên ngoài và file trong đó vẫn còn nguyên
