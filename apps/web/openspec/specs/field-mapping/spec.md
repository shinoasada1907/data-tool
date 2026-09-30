# field-mapping Specification

## Purpose
TBD - created by archiving change fe-import-wizard-v0-1. Update Purpose after archive.
## Requirements
### Requirement: Chọn nguồn giá trị cho từng target field
Với mỗi target field (theo thứ tự schema), FE SHALL cho chọn một trong ba trạng thái: "Chưa map", "Cột nguồn" hoặc "Giá trị cố định". Mỗi target field MUST có tối đa một mapping. FE MUST cho phép một cột nguồn được map cho nhiều target field.

#### Scenario: Map với cột nguồn
- **WHEN** user chọn "Cột nguồn" cho field `email`
- **THEN** FE hiện danh sách cột theo đúng thứ tự `columns[]` của preview

#### Scenario: Hiển thị giá trị mẫu để map
- **WHEN** field `email` đã được map với cột `E-mail`
- **THEN** cạnh field hiển thị tối đa 3 giá trị không rỗng đầu tiên của cột `E-mail`, lấy từ preview đang có trong state và không gọi thêm API

#### Scenario: Map với giá trị cố định
- **WHEN** user chọn "Giá trị cố định" cho field `country`
- **THEN** FE hiện ô nhập giá trị cố định cho field đó

#### Scenario: Một cột nguồn dùng cho nhiều field
- **WHEN** field `name` và field `display_name` cùng được map với cột `Họ tên`
- **THEN** FE chấp nhận và không báo lỗi

### Requirement: Map mặc định theo tên cột
Field được sinh từ cột nguồn (spec target-schema, "Sinh schema từ cột nguồn") SHALL được map sẵn với cột nguồn cùng tên. Field user tự thêm SHALL bắt đầu ở trạng thái "Chưa map". Đổi tên field MUST NOT làm đổi cột nguồn đã map.

#### Scenario: Vào bước Mapping lần đầu
- **WHEN** schema được sinh từ các cột `["Mã", "Email"]` và user vào bước Mapping
- **THEN** field `Mã` map với cột `Mã`, field `Email` map với cột `Email`

#### Scenario: Đổi tên field đã map
- **WHEN** field `Email` (map với cột `Email`) được đổi tên thành `email_address` ở bước Schema
- **THEN** ở bước Mapping, field `email_address` vẫn map với cột `Email`, và lần PUT mapping kế tiếp gửi `targetField: "email_address"`

#### Scenario: Xoá field đã map
- **WHEN** user xoá field `Email` ở bước Schema
- **THEN** mapping của field đó bị xoá khỏi state và không xuất hiện trong lần PUT mapping kế tiếp

### Requirement: Cảnh báo và chặn field chưa map
FE MUST phân loại tình trạng mapping của từng field như sau:
- field optional chưa map: chỉ cảnh báo;
- field required chưa map: lỗi chặn;
- giá trị cố định rỗng: lỗi chặn.

Khi còn lỗi chặn, nút "Tiếp" MUST bị khoá, kèm lý do liệt kê tên các field gây lỗi.

#### Scenario: Field optional chưa map
- **WHEN** field `note` không required và đang "Chưa map"
- **THEN** field hiện cảnh báo "Chưa map (field không bắt buộc)"; nút "Tiếp" vẫn bấm được nếu không còn lỗi khác

#### Scenario: Field required chưa map
- **WHEN** field `email` required và đang "Chưa map"
- **THEN** field hiện lỗi, và nút "Tiếp" bị khoá kèm lý do "Field bắt buộc chưa map: email"

#### Scenario: Giá trị cố định rỗng
- **WHEN** field chọn "Giá trị cố định" nhưng ô giá trị rỗng
- **THEN** field hiện lỗi "Giá trị cố định không được để trống" và nút "Tiếp" bị khoá

### Requirement: Lưu mapping khi bấm Tiếp
Khi bấm "Tiếp" mà mapping chưa được lưu, FE SHALL gọi `PUT /api/import-sessions/{id}/mapping`. Payload chỉ gồm các field đã map, `targetField` là tên hiện tại của field. Nếu mapping đã lưu và chưa sửa gì, FE MUST NOT gọi lại PUT. Lỗi BE trả về MUST hiển thị tại dòng của target field tương ứng.

#### Scenario: Lưu thành công
- **WHEN** `email` map với cột `E-mail`, `country` map với hằng `VN`, `note` chưa map, và user bấm "Tiếp"
- **THEN** payload có đúng 2 phần tử: `{targetField: "email", mappingType: "SOURCE_COLUMN", sourceColumn: "E-mail", constantValue: null}` và `{targetField: "country", mappingType: "CONSTANT", sourceColumn: null, constantValue: "VN"}`; wizard sang bước Transform & Validate

#### Scenario: BE báo không tìm thấy cột nguồn
- **WHEN** BE trả `422` với `errors: [{field: "email", code: "SOURCE_COLUMN_NOT_FOUND", message: "..."}]`
- **THEN** lỗi hiển thị tại dòng field `email`, wizard ở lại bước Mapping và mapping vẫn là chưa lưu

