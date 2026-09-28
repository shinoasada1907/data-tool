# CORE-01 Đổi tên và tái cấu trúc package — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: dùng superpowers:executing-plans (hoặc subagent-driven-development) để làm từng task. Mỗi task là một chu kỳ TDD và kết thúc bằng một commit. Tick checkbox ngay khi xong. Chỗ nào làm khác kế hoạch: gạch ngang task cũ và ghi **LÝ DO** ngay tại đó.

**Goal:** Chuyển toàn bộ BE sang `com.universaldatatools.{core, platform, tools.importer}` theo design TD1–TD2. Đổi config sang `toolbox.*` (biến môi trường cũ vẫn chạy). OpenAPI mang tiêu đề mới và chia nhóm. **Không đổi hành vi**: mọi test hiện có vẫn xanh, và hợp đồng OpenAPI của Importer giống snapshot.

**Architecture:** Chỉ chuyển package, không đổi tên class hay logic. Việc chuyển làm bằng một lần chạy OpenRewrite: `ChangeType` cho các lớp lẻ tách khỏi package cũ, `ChangePackage` cho các package chuyển nguyên. OpenRewrite tự sửa `package`/`import` và tự thêm import cho các lớp trước kia cùng package.

**Tech Stack:** Như hiện tại. Thêm tạm `org.openrewrite.maven:rewrite-maven-plugin`, chỉ gọi qua dòng lệnh, không ghi vào `pom.xml`.

**Spec:** `specs/toolbox-platform/spec.md`, `specs/api-docs/spec.md`. Thiết kế: `design.md` (TD1, TD2, Migration Plan).

## Global Constraints

- `MAIN` = `apps/api/src/main/java/com/universaldatatools`, `TEST` = `apps/api/src/test/java/com/universaldatatools` (sau task 3). Trước task 3 là `…/com/universalimporter`.
- Làm trên nhánh `feature/core-01-toolbox-restructure`, tạo từ `dev`, trong worktree `D:/Code/Product/universal-importer-be`.
- Trước khi merge:
  - Kiểm `git -C D:/Code/Product/universal-importer status`. Nếu người dùng đang sửa file trong `apps/api/src` thì **dừng và báo**, vì merge đổi package sẽ xung đột.
- Commit kết thúc bằng dòng `Co-Authored-By` theo hướng dẫn hiện hành.
- Không đổi: bảng DB, Flyway, path API, JSON, mã lỗi, tên file export.
- Test không dùng DB hay storage chung (`src/test/resources/config/application.yaml` đã tắt cleanup).

---

## 1. Baseline và snapshot hợp đồng OpenAPI

**Files:**
- Create: `TEST/api/ApiContractSnapshotTest.java`, `src/test/resources/contract/importer-openapi.json`

- [x] 1.1 Chạy `./mvnw -q test`. Ghi số test chạy và số test pass vào phần ghi chú của task này (dạng `Tests run: N`). Mong đợi: tất cả pass.
  - Ghi chú: baseline `Tests run: 920`, pass hết.
- [x] 1.2 Viết `ApiContractSnapshotTest` (`@SpringBootTest` + MockMvc + Testcontainers, như các integration test sẵn có):
  1. `GET /v3/api-docs`.
  2. Bỏ key `info` và `servers`.
  3. Giữ lại `paths` có key bắt đầu bằng `/api/import-sessions`, và `components.schemas`.
  4. Sắp key theo thứ tự chữ cái, serialize có thụt lề.
  5. So bằng chuỗi với `contract/importer-openapi.json`.
  6. Nếu file snapshot chưa tồn tại và có system property `-Dsnapshot.write=true` thì ghi file rồi fail với message `snapshot written`.
- [x] 1.3 Chạy `./mvnw -q test -Dtest=ApiContractSnapshotTest -Dsnapshot.write=true`. Mong đợi: FAIL `snapshot written`, và file snapshot được tạo ra.
- [x] 1.4 Chạy lại, không có `-Dsnapshot.write`. Mong đợi: PASS.
- [x] 1.5 Commit: `test(api): snapshot the importer OpenAPI contract before the package move`

## 2. Test kiến trúc cho cây package đích (RED)

**Files:**
- Modify: `TEST/ArchitectureTest.java` (ở package cũ; task 3 sẽ chuyển nó)

**Interfaces:** 7 luật của design TD1, đặt tên test:
- `core_is_plain_java`
- `core_format_uses_only_format_libraries`
- `core_has_no_framework_or_outer_layers`
- `platform_does_not_know_tools`
- `tools_do_not_depend_on_each_other`
- `each_tool_is_layered`
- `scheduling_lives_in_platform_or_tool_infrastructure`

- [x] 2.1 Viết lại `ArchitectureTest`:
  - Import `com.universaldatatools`.
  - Luật 5 dùng `slices().matching("com.universaldatatools.tools.(*)..").should().notDependOnEachOther()`.
  - Luật 6 dùng `layeredArchitecture()` cho **mỗi** tool trong `List.of("importer")`. Danh sách này tăng dần khi thêm tool.
  - Mọi luật đặt `allowEmptyShould(false)`, để test đỏ khi chưa có lớp nào ở package mới.
- [x] 2.2 Chạy `./mvnw -q test -Dtest=ArchitectureTest`. Mong đợi: FAIL, vì chưa có lớp nào trong `com.universaldatatools`.
- [x] 2.3 Không commit riêng. Task 3 làm test này xanh; commit cùng task 3.

## 3. Chuyển package bằng OpenRewrite

**Files:**
- Create tạm (không commit): `apps/api/rewrite.yml`
- Modify: mọi file `.java` trong `src/main` và `src/test`; `pom.xml` (`groupId`)

- [x] 3.1 Tạo `apps/api/rewrite.yml`. Thứ tự trong `recipeList` là quan trọng: lớp lẻ trước, package con trước package cha.
  ```yaml
  type: specs.openrewrite.org/v1beta/recipe
  name: udt.MoveToToolbox
  recipeList:
    # --- lớp lẻ (main) ---
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.ApiApplication, newFullyQualifiedTypeName: com.universaldatatools.ToolboxApplication }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.importsession.SourceFileType, newFullyQualifiedTypeName: com.universaldatatools.core.table.SourceFileType }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.importsession.FileTypeDetector, newFullyQualifiedTypeName: com.universaldatatools.core.table.FileTypeDetector }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.importsession.OriginalFileName, newFullyQualifiedTypeName: com.universaldatatools.core.common.OriginalFileName }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.importsession.FileStorage, newFullyQualifiedTypeName: com.universaldatatools.platform.storage.FileStorage }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.importsession.StoredEntry, newFullyQualifiedTypeName: com.universaldatatools.platform.storage.StoredEntry }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.importsession.InstallationRepository, newFullyQualifiedTypeName: com.universaldatatools.platform.storage.InstallationRepository }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.schema.FieldType, newFullyQualifiedTypeName: com.universaldatatools.core.schema.FieldType }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.transformation.TransformationConfig, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.transformation.TransformationConfig }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.transformation.TransformationConfigValidator, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.transformation.TransformationConfigValidator }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.validation.FieldValidator, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.validation.FieldValidator }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.validation.ValidationConfig, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.validation.ValidationConfig }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.validation.ValidationConfigCheck, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.validation.ValidationConfigCheck }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.validation.ValidationConfigValidator, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.validation.ValidationConfigValidator }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.validation.ValidationRuleConfig, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.validation.ValidationRuleConfig }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.infrastructure.config.ClockConfig, newFullyQualifiedTypeName: com.universaldatatools.platform.config.ClockConfig }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.infrastructure.config.EngineConfig, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.infrastructure.config.EngineConfig }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.infrastructure.scheduling.SchedulingConfig, newFullyQualifiedTypeName: com.universaldatatools.platform.config.SchedulingConfig }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.infrastructure.scheduling.SessionCleanupScheduler, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.infrastructure.scheduling.SessionCleanupScheduler }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.infrastructure.persistence.JpaInstallationRepository, newFullyQualifiedTypeName: com.universaldatatools.platform.storage.JpaInstallationRepository }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.infrastructure.parser.CsvSourceParser, newFullyQualifiedTypeName: com.universaldatatools.core.format.csv.CsvSourceParser }
    # --- test đi theo lớp lẻ ---
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.importsession.FileTypeDetectorTest, newFullyQualifiedTypeName: com.universaldatatools.core.table.FileTypeDetectorTest }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.importsession.OriginalFileNameTest, newFullyQualifiedTypeName: com.universaldatatools.core.common.OriginalFileNameTest }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.schema.FieldTypeTest, newFullyQualifiedTypeName: com.universaldatatools.core.schema.FieldTypeTest }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.transformation.TransformationConfigPruneTest, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.transformation.TransformationConfigPruneTest }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.transformation.TransformationConfigValidatorTest, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.transformation.TransformationConfigValidatorTest }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.validation.FieldValidatorTest, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.validation.FieldValidatorTest }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.validation.ValidationConfigPruneTest, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.validation.ValidationConfigPruneTest }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.domain.validation.ValidationConfigValidatorTest, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.domain.validation.ValidationConfigValidatorTest }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.infrastructure.scheduling.SessionCleanupSchedulerTest, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.infrastructure.scheduling.SessionCleanupSchedulerTest }
    - org.openrewrite.java.ChangeType: { oldFullyQualifiedTypeName: com.universalimporter.infrastructure.scheduling.SessionCleanupSchedulerStartupTest, newFullyQualifiedTypeName: com.universaldatatools.tools.importer.infrastructure.scheduling.SessionCleanupSchedulerStartupTest }
    # --- cả package, con trước cha ---
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.domain.common, newPackageName: com.universaldatatools.core.common }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.domain.source, newPackageName: com.universaldatatools.core.table }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.domain.transformation, newPackageName: com.universaldatatools.core.transform }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.domain.validation, newPackageName: com.universaldatatools.core.validate }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.domain, newPackageName: com.universaldatatools.tools.importer.domain, recursive: true }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.api.common, newPackageName: com.universaldatatools.platform.web }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.api, newPackageName: com.universaldatatools.tools.importer.api, recursive: true }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.application, newPackageName: com.universaldatatools.tools.importer.application, recursive: true }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.infrastructure.storage, newPackageName: com.universaldatatools.platform.storage }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.infrastructure.parser.xlsx, newPackageName: com.universaldatatools.core.format.xlsx }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter.infrastructure, newPackageName: com.universaldatatools.tools.importer.infrastructure, recursive: true }
    - org.openrewrite.java.ChangePackage: { oldPackageName: com.universalimporter, newPackageName: com.universaldatatools, recursive: true }
  ```
  Ghi chú:
  - Các test cùng tên với lớp chuyển nguyên package (ví dụ `LocalFileStorageTest`, `TargetSchemaTest`) tự đi theo `ChangePackage`.
  - Test cho các lớp thuộc `api/common` (package test `api.common`) đi theo `platform.web`.
- [x] 3.2 Chạy từ `apps/api`:
  ```
  ./mvnw -q -U org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.configLocation=rewrite.yml -Drewrite.activeRecipes=udt.MoveToToolbox
  ```
  Mong đợi: lệnh kết thúc không lỗi, và `git status` cho thấy các file đã đổi chỗ.
  - Nếu plugin không tải được (không có mạng): gạch task này, ghi LÝ DO, rồi làm bằng IntelliJ *Refactor → Move Package/Class* theo đúng bảng TD2. Kết quả phải như nhau.
- [x] 3.3 Xoá `rewrite.yml`. Kiểm không còn chuỗi `com.universalimporter` trong `src`: `grep -rn "com\.universalimporter" src` → không có kết quả.
- [x] 3.4 Sửa `pom.xml`: `<groupId>com.universaldatatools</groupId>`. Artifact `api` giữ nguyên.
- [x] 3.5 Chạy `./mvnw -q -DskipTests compile test-compile`. Mong đợi: compile được.
  - Lỗi còn lại chỉ có thể là truy cập package-private giữa hai lớp giờ đã khác package, ví dụ `TransformationConfig` gọi một hằng package-private của `TrimTransformation`.
  - Sửa bằng cách nâng thành `public` **chỉ đúng thành viên đó**. Ghi từng chỗ vào ghi chú của task này.
  - Ghi chú: OpenRewrite **không** thêm import cho các lớp trước kia cùng package. Đã thêm import tường minh cho `tools.importer.domain.{transformation,validation}` (main và test) bằng script, rồi sắp lại khối import. Package-private phải nâng thành `public`: `DateFormatTransformation.TYPE` và `OUTPUT_FORMAT` (vì `TransformationConfig` dùng).
- [x] 3.6 Chạy `./mvnw -q test -Dtest=ArchitectureTest`. Mong đợi: FAIL ở luật `core_has_no_framework_or_outer_layers`, vì `CsvSourceParser`, `XlsxSourceParser`, `XlsxZipGuard`, `XlsxLimits` còn annotation Spring. Task 4 sẽ sửa. Các luật khác PASS.
  - Nếu luật khác fail, đó là một phụ thuộc TD2 chưa lường tới. Ghi lại, rồi chuyển lớp đó về đúng tầng, hoặc báo trong ghi chú nếu cần đổi thiết kế.
  - Ghi chú: `scheduling_lives_in_platform_or_tool_infrastructure` cũng đỏ, vì code không có method `@Scheduled` (scheduler đăng ký bằng code). Đã giữ `allowEmptyShould(true)` cho luật method như test cũ. Luật đó không lệch thiết kế.
- [x] 3.7 Commit: `refactor: move packages to com.universaldatatools {core, platform, tools.importer}`

## 4. `core.format` không dùng Spring

**Files:**
- Modify: `MAIN/core/format/csv/CsvSourceParser.java`, `MAIN/core/format/xlsx/{XlsxSourceParser, XlsxZipGuard, XlsxLimits}.java`
- Create: `MAIN/platform/config/FormatConfig.java`, `MAIN/platform/config/XlsxLimitsProperties.java`
- Modify: `MAIN/ToolboxApplication.java` (`@ConfigurationPropertiesScan` nếu đang dùng; kiểm lại cách `XlsxLimits` được bật)
- Test: `TEST/platform/config/FormatConfigTest.java`

**Interfaces:**
- Produces:
  ```java
  public record XlsxLimits(long maxUncompressedBytes, int maxInflateRatio, int maxEntries) {}      // core.format.xlsx, không annotation
  @ConfigurationProperties("toolbox.format.xlsx")
  public record XlsxLimitsProperties(DataSize maxUncompressedSize, int maxInflateRatio, int maxEntries) {
      public XlsxLimits toLimits();
  }
  @Configuration(proxyBeanMethods = false) public class FormatConfig {
      @Bean XlsxZipGuard xlsxZipGuard(XlsxLimitsProperties p);
      @Bean CsvSourceParser csvSourceParser();
      @Bean XlsxSourceParser xlsxSourceParser(XlsxZipGuard guard);
  }
  ```

- [x] 4.1 Viết `FormatConfigTest`. Dùng `ApplicationContextRunner` với `FormatConfig` và `@EnableConfigurationProperties(XlsxLimitsProperties.class)`:
  - không có property nào: bean `XlsxZipGuard` có giới hạn `200MB` / `100` / `10000`;
  - với `toolbox.format.xlsx.max-entries=5`: guard có `maxEntries` là `5`;
  - context có đúng 2 bean kiểu `SourceParser`.
- [x] 4.2 Chạy `./mvnw -q test -Dtest=FormatConfigTest`. Mong đợi: FAIL, vì class chưa có.
- [x] 4.3 Bỏ `@Component` và `@ConfigurationProperties` khỏi 4 lớp `core.format`. Tạo `XlsxLimitsProperties` và `FormatConfig`. `XlsxZipGuard` nhận `XlsxLimits` qua constructor.
  - Ghi chú: làm luôn phần `toolbox.format.xlsx` của `application.yaml` và đổi property `importer.xlsx.*` trong test ở task này, vì prefix binding đổi ngay tại đây. `XlsxLimits` giờ là `(long maxUncompressedBytes, …)`; default nằm ở `@DefaultValue` của `XlsxLimitsProperties`.
- [x] 4.4 Chạy `./mvnw -q test -Dtest=FormatConfigTest,ArchitectureTest,XlsxUploadIntegrationTest`. Mong đợi: PASS.
- [x] 4.5 Commit: `refactor(format): core.format free of Spring; beans in platform.config.FormatConfig`

## 5. Config `toolbox.*` và fallback biến môi trường cũ

**Files:**
- Modify: `src/main/resources/application.yaml`, `src/test/resources/config/application.yaml`
- Modify: `MAIN/platform/storage/StorageProperties.java` (`toolbox.storage`), `MAIN/tools/importer/application/importsession/CleanupProperties.java` (`toolbox.importer.cleanup`)
- Modify: mọi test đặt property `importer.` (tìm bằng `grep -rln "importer\.\(storage\|cleanup\|xlsx\)" src/test`)
- Test: `TEST/platform/config/ConfigurationKeysIntegrationTest.java`

- [x] 5.1 Viết `ConfigurationKeysIntegrationTest`. Dùng `ApplicationContextRunner` nạp `application.yaml` thật (`ConfigDataApplicationContextInitializer`) cùng các lớp properties. Đặt "biến môi trường" bằng `withPropertyValues`, vì placeholder resolve trên mọi property source.

  | Đặt | Mong đợi |
  |---|---|
  | `IMPORTER_STORAGE_DIR=/x/old` | `StorageProperties.dir` = `/x/old` |
  | `TOOLBOX_STORAGE_DIR=/x/new`, `IMPORTER_STORAGE_DIR=/x/old` | `/x/new` |
  | `IMPORTER_SESSION_TTL=24` | `CleanupProperties.sessionTtl` = 24 giờ |
  | `TOOLBOX_IMPORTER_SESSION_TTL=2h`, `IMPORTER_SESSION_TTL=24` | 2 giờ |
  | `IMPORTER_XLSX_MAX_ENTRIES=7` | `XlsxLimitsProperties.maxEntries` = 7 |
  | `TOOLBOX_XLSX_MAX_ENTRIES=5` | 5 |
  | `importer.storage.dir=/x/legacy-key` (key cũ, không phải biến môi trường) | **không** có hiệu lực: `dir` là giá trị mặc định |
  | (không đặt gì) | `spring.application.name` = `universal-data-tools` |
  - Ghi chú: tên thực tế là `ConfigurationKeysTest` (không cần Spring context đầy đủ). Nạp **chỉ** `classpath:/application.yaml` qua `spring.config.location`, vì file config của test chuyển storage đi chỗ khác. Boot 4 chuyển `ConfigDataApplicationContextInitializer` sang `org.springframework.boot.test.context`.
- [x] 5.2 Chạy `./mvnw -q test -Dtest=ConfigurationKeysIntegrationTest`. Mong đợi: FAIL.
- [x] 5.3 Sửa `application.yaml` theo bảng TD2. Ví dụ `toolbox.storage.dir: ${TOOLBOX_STORAGE_DIR:${IMPORTER_STORAGE_DIR:${java.io.tmpdir}/universal-importer}}`. Các lớp properties đổi prefix. Cập nhật test config và các test đặt `importer.*`.
- [x] 5.4 Chạy `./mvnw -q test`. Mong đợi: toàn bộ PASS, trừ `ArchitectureTest` nếu task 4 chưa xong (task 4 phải xong trước task này).
- [x] 5.5 Commit: `refactor(config): toolbox.* keys; IMPORTER_* environment variables still honoured`

## 6. OpenAPI: tiêu đề mới và nhóm theo tool

**Files:**
- Modify: `MAIN/platform/web/OpenApiConfig.java`
- Modify: test api-docs sẵn có (tìm `Universal Importer API` trong `src/test`)

- [x] 6.1 Sửa test api-docs:
  - `info.title` là `Universal Data Tools API`.
  - Thêm case: `GET /v3/api-docs/importer` trả `200`, và mọi key của `paths` bắt đầu bằng `/api/import-sessions`.
  - Thêm case: `GET /v3/api-docs/all` trả `200`, và có `/api/import-sessions`.
- [x] 6.2 Chạy test đó. Mong đợi: FAIL.
- [x] 6.3 Sửa `OpenApiConfig`:
  - tiêu đề mới;
  - `GroupedOpenApi` `all` (`/api/**`) và `importer` (`/api/import-sessions/**`);
  - `springdoc.api-docs.path` giữ `/v3/api-docs`, để bản không nhóm vẫn trả toàn bộ path.
- [x] 6.4 Chạy lại test đó và `ApiContractSnapshotTest`. Mong đợi: PASS. Snapshot đã bỏ `info`, và nhóm không đổi nội dung `paths` hay `components`.
  - Nếu snapshot khác vì springdoc đổi thứ tự hoặc tên `operationId` khi có nhóm: xem diff. Nếu chỉ là thứ tự thì chuẩn hoá thêm trong test. Nếu nội dung đổi thật thì dừng lại và ghi LÝ DO.
- [x] 6.5 Commit: `feat(api-docs): Universal Data Tools API, grouped per tool`

## 7. Tài liệu và kiểm toàn bộ

**Files:**
- Modify: `README.md` (gốc repo): tên dự án, cách chạy `ToolboxApplication`, biến môi trường mới (ghi rõ biến cũ vẫn chạy).
- Modify: `apps/api/HELP.md` nếu có nhắc `ApiApplication`.

- [x] 7.1 Cập nhật README: tiêu đề "Universal Data Tools". Bảng biến môi trường ghi cả `TOOLBOX_*` lẫn `IMPORTER_*` (cũ, vẫn nhận).
- [x] 7.2 Chạy `./mvnw -q verify`. Mong đợi: PASS. Số test ≥ baseline ở 1.1 cộng các test mới (Snapshot, FormatConfig, ConfigurationKeys, 3 case OpenAPI).
  - Ghi chú: `Tests run: 937` (920 + 17 mới), pass hết.
- [x] 7.3 Chạy app thật ở cổng phụ để không đụng app của người dùng:
  ```
  ./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --toolbox.importer.cleanup.enabled=false"
  ```
  - `curl -s localhost:8081/actuator/health` → `{"status":"UP"}`.
  - `curl -s localhost:8081/v3/api-docs | jq -r .info.title` → `Universal Data Tools API`.
  - Upload fixture e2e `customers.csv` → `201`.
  - Tắt app, rồi kiểm `netstat` không còn gì nghe ở 8081.
  - Ghi chú: chạy với Postgres tạm (container `udt-core01-db`, cổng 55432) và storage tạm, không đụng DB hay storage dùng chung. Health `UP`, title `Universal Data Tools API`, upload `customers.csv` → `201`.
- [x] 7.4 Commit: `docs: Universal Data Tools naming in README`

## 8. Hoàn tất

- [x] 8.1 `openspec validate core-01-toolbox-restructure --strict`. Mong đợi: hợp lệ.
- [x] 8.2 `openspec archive core-01-toolbox-restructure -y`. Lệnh này gộp delta vào `openspec/specs/api-docs` và tạo `openspec/specs/toolbox-platform`. Commit: `docs(openspec): archive core-01-toolbox-restructure`.
- [x] 8.3 Kiểm thư mục chính: `git -C D:/Code/Product/universal-importer status --short`. Có file đang sửa trong `apps/api/src` thì dừng và báo người dùng. Sạch thì chạy `git -C D:/Code/Product/universal-importer merge --no-ff feature/core-01-toolbox-restructure`.
- [x] 8.4 Báo người dùng:
  - IntelliJ cần reload Maven và chạy `ToolboxApplication`;
  - biến `IMPORTER_*` vẫn dùng được;
  - DB không đổi.

  Báo phiên FE: API không đổi, chỉ đổi tiêu đề và nhóm OpenAPI.
- [x] 8.5 Xoá nhánh: `git branch -d feature/core-01-toolbox-restructure`.
