package com.universaldatatools.platform.dataset;

import com.jayway.jsonpath.JsonPath;
import com.universaldatatools.platform.storage.StorageProperties;
import com.universaldatatools.support.HttpTestClient;
import com.universaldatatools.support.HttpTestClient.Response;
import com.universaldatatools.support.TestcontainersConfiguration;
import com.universaldatatools.support.XlsxFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** core-04 tasks 9–11 (spec: dataset-api). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DatasetApiIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StorageProperties storage;

    @Autowired
    DatasetLocks locks;

    @TempDir
    Path dir;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    // --- upload (task 9) ---

    @Test
    void a_csv_upload_creates_a_dataset() {
        Response upload = upload("khách hàng.csv", "ma;ten\n1;An\n");

        assertThat(upload.status()).isEqualTo(201);
        String id = JsonPath.read(upload.body(), "$.id");
        assertThat(upload.headers().getLocation()).hasToString("/api/datasets/" + id);
        assertThat((String) JsonPath.read(upload.body(), "$.format")).isEqualTo("CSV");
        assertThat((String) JsonPath.read(upload.body(), "$.originalFileName")).isEqualTo("khách hàng.csv");
        assertThat((Object) JsonPath.read(upload.body(), "$.sheets")).isNull();
        Instant created = Instant.parse(JsonPath.read(upload.body(), "$.createdAt"));
        Instant expires = Instant.parse(JsonPath.read(upload.body(), "$.expiresAt"));
        assertThat(Duration.between(created, expires)).isEqualTo(Duration.ofHours(24));
        assertThat(upload.headers().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void an_xlsx_upload_lists_its_sheets() throws IOException {
        Response upload = http.uploadTo("/api/datasets", "book.xlsx",
                Files.readAllBytes(XlsxFixtures.hiddenFirstSheet(dir)));

        assertThat(upload.status()).isEqualTo(201);
        assertThat((List<Map<String, Object>>) JsonPath.read(upload.body(), "$.sheets")).containsExactly(
                Map.of("name", "Hidden", "visible", false), Map.of("name", "Visible", "visible", true));
    }

    @Test
    void refused_uploads_leave_nothing_behind() {
        long rows = countDatasets();
        long dirs = countStorageDirectories();

        assertThat(code(upload("data.json", "{\"a\":1}"))).isEqualTo("422 FILE_PARSE_ERROR");
        assertThat(code(upload("data.xls", "x"))).isEqualTo("415 FILE_UNSUPPORTED");
        assertThat(code(upload("empty.csv", ""))).isEqualTo("422 FILE_EMPTY");
        assertThat(countDatasets()).isEqualTo(rows);
        assertThat(countStorageDirectories()).isEqualTo(dirs);
    }

    // --- preview (task 10) ---

    @Test
    void preview_detects_the_semicolon_and_profiles_columns() {
        String id = idOf(upload("vn.csv", "ma;ten\n1;An\n2;Bình\n"));

        Response preview = http.get("/api/datasets/" + id + "/preview");

        assertThat(preview.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(preview.body(), "$.options.delimiter")).isEqualTo("SEMICOLON");
        assertThat((String) JsonPath.read(preview.body(), "$.options.encoding")).isEqualTo("UTF-8");
        assertThat((List<String>) JsonPath.read(preview.body(), "$.autoDetected"))
                .containsExactly("delimiter", "encoding");
        assertThat((Integer) JsonPath.read(preview.body(), "$.totalRows")).isEqualTo(2);
        assertThat((String) JsonPath.read(preview.body(), "$.columns[0].inferredType")).isEqualTo("number");
        assertThat((Integer) JsonPath.read(preview.body(), "$.rows[0].rowNumber")).isEqualTo(2);
        assertThat((List<String>) JsonPath.read(preview.body(), "$.rows[0].values")).containsExactly("1", "An");
        assertThat(preview.headers().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void preview_with_another_delimiter_needs_no_new_upload() {
        String id = idOf(upload("vn.csv", "ma;ten\n1;An\n"));

        Response preview = http.get("/api/datasets/" + id + "/preview?delimiter=comma");

        assertThat((List<String>) JsonPath.read(preview.body(), "$.columns[*].name")).containsExactly("ma;ten");
        assertThat((List<String>) JsonPath.read(preview.body(), "$.autoDetected")).doesNotContain("delimiter");
    }

    @Test
    void preview_refuses_bad_parameters_and_reports_read_errors() {
        String csv = idOf(upload("a.csv", "a\n1\n"));
        assertThat(code(http.get("/api/datasets/" + csv + "/preview?limit=500"))).isEqualTo("400 REQUEST_INVALID");
        assertThat(code(http.get("/api/datasets/" + csv + "/preview?delimiter=COLON")))
                .isEqualTo("400 REQUEST_INVALID");

        String nested = idOf(upload("n.json", "[{\"a\":{\"b\":1}}]"));
        assertThat(code(http.get("/api/datasets/" + nested + "/preview"))).isEqualTo("422 JSON_NOT_FLAT");
    }

    @Test
    void an_unknown_sheet_is_a_configuration_error() throws IOException {
        String id = idOf(http.uploadTo("/api/datasets", "book.xlsx",
                Files.readAllBytes(XlsxFixtures.hiddenFirstSheet(dir))));

        assertThat(code(http.get("/api/datasets/" + id + "/preview?sheet=Khong%20co")))
                .isEqualTo("422 CONFIG_INVALID");
        Response hidden = http.get("/api/datasets/" + id + "/preview?sheet=Hidden");
        assertThat((String) JsonPath.read(hidden.body(), "$.sheetName")).isEqualTo("Hidden");
    }

    @Test
    void an_expired_dataset_is_not_found() {
        String id = idOf(upload("a.csv", "a\n1\n"));
        jdbc.update("update dataset set last_used_at = now() - interval '25 hours' where id = ?", UUID.fromString(id));

        assertThat(code(http.get("/api/datasets/" + id + "/preview"))).isEqualTo("404 DATASET_NOT_FOUND");
        assertThat(code(http.get("/api/datasets/" + id))).isEqualTo("404 DATASET_NOT_FOUND");
    }

    // --- read and delete (task 11) ---

    @Test
    void a_deleted_dataset_is_gone_with_its_files() {
        String id = idOf(upload("a.csv", "a\n1\n"));
        assertThat(http.get("/api/datasets/" + id).status()).isEqualTo(200);

        Response deleted = http.request(HttpMethod.DELETE, "/api/datasets/" + id, Map.of());

        assertThat(deleted.status()).isEqualTo(204);
        assertThat(code(http.get("/api/datasets/" + id + "/preview"))).isEqualTo("404 DATASET_NOT_FOUND");
        assertThat(storage.dir().resolve(id)).doesNotExist();
    }

    @Test
    void a_dataset_being_read_cannot_be_deleted_yet() {
        String id = idOf(upload("a.csv", "a\n1\n"));

        try (DatasetLocks.Held ignored = locks.read(UUID.fromString(id))) {
            Response deleted = http.request(HttpMethod.DELETE, "/api/datasets/" + id, Map.of());
            assertThat(code(deleted)).isEqualTo("503 SERVER_BUSY");
            assertThat(deleted.headers().getFirst("Retry-After")).isEqualTo("5");
        }
        assertThat(http.request(HttpMethod.DELETE, "/api/datasets/" + id, Map.of()).status()).isEqualTo(204);
    }

    @Test
    void a_malformed_id_is_a_bad_request() {
        assertThat(code(http.get("/api/datasets/not-a-uuid"))).isEqualTo("400 REQUEST_INVALID");
    }

    private Response upload(String name, String content) {
        return http.uploadTo("/api/datasets", name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String idOf(Response upload) {
        assertThat(upload.status()).as(upload.body()).isEqualTo(201);
        return JsonPath.read(upload.body(), "$.id");
    }

    private static String code(Response response) {
        return response.status() + " " + JsonPath.read(response.body(), "$.code");
    }

    private long countDatasets() {
        return jdbc.queryForObject("select count(*) from dataset", Long.class);
    }

    private long countStorageDirectories() {
        try (var entries = Files.list(storage.dir())) {
            return entries.count();
        } catch (IOException e) {
            return 0;
        }
    }
}
