# TOOL-02 Data Converter — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** `POST /api/converter/convert` đổi một dataset sang CSV, XLSX hoặc JSON theo `OutputDto`, mọi lỗi báo trước byte đầu tiên.

**Architecture:** design.md CV1–CV5. Nền là core-02 (reader/writer), core-04 (`DatasetSources`, `OutputSpec`, `Downloads`).

**Spec:** `specs/data-converter/spec.md`.

## Global Constraints

- core-04 đã merge. Nhánh `feature/tool-02-data-converter`.
- Tool chỉ dùng `core` và `platform`, không import tool khác (`ArchitectureTest`).

---

## 0. Đối chiếu với core đã merge

- [ ] 0.1 Kiểm các API sẽ dùng còn đúng chữ ký: `DatasetSources.open(SourceRef)`, `OpenedSource.info()/rows()`, `OutputSpec.from(OutputDto, String)`, `Downloads.send(...)`, `DownloadNames.of(...)`, `TableWriter.open(...)`. Lệch thì sửa tasks này trước.

## 1. `SourceDto` dùng chung

**Files:**
- Create: `MAIN/platform/dataset/SourceDto.java`
- Test: `TEST/platform/dataset/SourceDtoTest.java`

- [ ] 1.1 Viết test:
  - `{datasetId, options: {sheet:"S", delimiter:"semicolon", encoding:"windows-1258", hasHeader:false}}` → `SourceRef` với `SEMICOLON`, `WINDOWS_1258`, `false`;
  - `options` null → `ReadOptions.defaults()`;
  - `delimiter:"COLON"` → `CONFIG_INVALID`, message nêu `source.options.delimiter`;
  - `datasetId` null → `REQUEST_INVALID`.
- [ ] 1.2 FAIL → cài đặt → PASS.
- [ ] 1.3 Commit: `feat(dataset): shared SourceDto for tools`

## 2. `ConverterService`

**Files:**
- Create: `MAIN/tools/converter/application/ConverterService.java`
- Test: `TEST/tools/converter/application/ConverterServiceTest.java` (Spring Boot + Testcontainers, dataset upload qua `DatasetService`)

**Interfaces:**
- Produces:
  ```java
  public Prepared prepare(SourceRef source, OutputDto output);   // mọi kiểm tra; không ghi byte nào
  public record Prepared(String fileName, String contentType, Downloads.BodyWriter body, OpenedSource source) implements AutoCloseable
  ```

- [ ] 2.1 Viết test:
  - CSV `id;ten\n1;An\n2;Bình\n` → JSON: đúng byte `[{"id":"1","ten":"An"},{"id":"2","ten":"Bình"}]`; tên file `khách hàng.json`;
  - JSON `[{"price":12.50,"code":"00123","ok":true}]` → JSON mặc định: giữ nguyên;
  - CSV `id,code\n1,00123\n2,00456\n` → JSON `INFER` → `[{"id":1,"code":"00123"},{"id":2,"code":"00456"}]`;
  - JSON `[{"a":1,"b":"x"},{"a":2}]` → CSV `bom=false` → `a,b\r\n1,x\r\n2,\r\n`;
  - CSV có ô 40 000 ký tự ở cột `note` → XLSX: `LIMIT_EXCEEDED` với detail đúng spec (dataset upload với giới hạn ô > 40 000: đặt `toolbox.limits.max-cell-length=50000` trong test);
  - `output` `{"format":"JSON","csv":{…}}` → `CONFIG_INVALID`;
  - XLSX chọn sheet `Prices` → XLSX: tên sheet đích là `Prices`; CSV → XLSX: tên sheet `Sheet1`.
- [ ] 2.2 FAIL → cài đặt theo CV2–CV4 → PASS.
- [ ] 2.3 Commit: `feat(converter): convert a dataset between CSV, XLSX and JSON`

## 3. Endpoint

**Files:**
- Create: `MAIN/tools/converter/api/{ConverterController, ConvertRequest}.java`
- Modify: `TEST/ArchitectureTest.java` (`TOOL_NAMES` thêm `converter`), `MAIN/platform/web/OpenApiConfig.java` (nhóm `converter`)
- Test: `TEST/tools/converter/api/ConverterApiIntegrationTest.java`

- [ ] 3.1 Viết test qua HTTP thật, theo mọi scenario của spec:
  - CSV→JSON: header `Content-Disposition` chứa `filename*=UTF-8''kh%C3%A1ch%20h%C3%A0ng.json`, có `Cache-Control: no-store`;
  - CSV→XLSX: workbook đọc lại có 3 dòng dữ liệu;
  - XLSX sheet `Prices`→CSV có BOM;
  - JSON→CSV;
  - formula guard bật và tắt;
  - dataset đã xoá → `404 DATASET_NOT_FOUND`;
  - `{"source":` → `400 REQUEST_INVALID`;
  - lỗi không kèm `Content-Disposition`;
  - `/v3/api-docs/converter` có `/api/converter/convert`.
- [ ] 3.2 FAIL → cài đặt → PASS. `ArchitectureTest` PASS.
- [ ] 3.3 Commit: `feat(converter): POST /api/converter/convert`

## 4. Perf smoke

**Files:**
- Test: `TEST/tools/converter/ConverterPerfTest.java` (`@Tag("perf")`)

- [ ] 4.1 CSV 200 000 row × 10 cột → JSON qua `ConverterService`. Thời gian đo phải dưới 15 giây, chạy bằng `./mvnw test -Dgroups=perf -Dtest=ConverterPerfTest`. Ghi số đo vào ghi chú.
- [ ] 4.2 Commit: `test(converter): perf smoke for 200k rows`

## 5. Hoàn tất

- [ ] 5.1 `./mvnw verify`. PASS.
- [ ] 5.2 Chạy app thật ở cổng 8081 với Postgres tạm và storage tạm, rồi thử bằng curl: upload CSV → preview → convert sang XLSX và JSON. Tắt app, kiểm cổng.
- [ ] 5.3 `openspec validate tool-02-data-converter --strict`, rồi `openspec archive tool-02-data-converter -y`. Viết Purpose cho spec mới. Commit.
- [ ] 5.4 Merge `--no-ff` vào `dev`. Báo người dùng. Báo phiên FE contract thật và request/response mẫu.
- [ ] 5.5 Xoá nhánh.
