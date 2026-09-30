package com.universaldatatools.tools.importer.api.importsession;

import com.jayway.jsonpath.JsonPath;
import com.universaldatatools.support.HttpTestClient;
import com.universaldatatools.support.HttpTestClient.Response;
import com.universaldatatools.support.TestcontainersConfiguration;
import com.universaldatatools.support.XlsxFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class XlsxUploadIntegrationTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("toolbox.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    @TempDir
    Path workDir;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    @Test
    void a_workbook_saved_by_excel_is_read_at_upload_and_previewed() throws IOException {
        Response upload = http.upload("types.xlsx",
                Files.readAllBytes(Path.of("src/test/resources/fixtures/xlsx/types.xlsx")));

        assertThat(upload.status()).isEqualTo(201);
        assertThat((String) JsonPath.read(upload.body(), "$.status")).isEqualTo("CONFIGURING");
        assertThat((String) JsonPath.read(upload.body(), "$.fileType")).isEqualTo("XLSX");

        Response preview = preview(upload);

        assertThat((String) JsonPath.read(preview.body(), "$.sheetName")).isEqualTo("Data");
        assertThat((String) JsonPath.read(preview.body(), "$.rows[0].values[4]")).isEqualTo("84901234567");
        assertThat((String) JsonPath.read(preview.body(), "$.rows[0].values[6]")).isEqualTo("2024-02-29");
        assertThat((String) JsonPath.read(preview.body(), "$.rows[0].values[13]")).isEqualTo("13:30:00");
    }

    @Test
    void the_preview_names_the_first_visible_sheet() throws IOException {
        Response upload = http.upload("hidden.xlsx", Files.readAllBytes(XlsxFixtures.hiddenFirstSheet(workDir)));

        assertThat((String) JsonPath.read(preview(upload).body(), "$.sheetName")).isEqualTo("Visible");
    }

    @Test
    void an_empty_first_sheet_is_rejected_and_leaves_no_files() throws IOException {
        long directoriesBefore = sessionDirectories();

        Response upload = http.upload("empty.xlsx", Files.readAllBytes(XlsxFixtures.emptyFirstSheet(workDir)));

        assertThat(upload.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(upload.body(), "$.code")).isEqualTo("FILE_EMPTY");
        assertThat(sessionDirectories()).isEqualTo(directoriesBefore);
    }

    @Test
    void a_zip_bomb_is_rejected() throws IOException {
        Path bomb = XlsxFixtures.zipOfZeros(workDir.resolve("bomb.xlsx"), "xl/worksheets/sheet1.xml",
                50L * 1024 * 1024);

        Response upload = http.upload("bomb.xlsx", Files.readAllBytes(bomb));

        assertThat(upload.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(upload.body(), "$.code")).isEqualTo("FILE_PARSE_ERROR");
    }

    @Test
    void a_larger_workbook_is_counted_in_full() throws IOException {
        Response upload = http.upload("large.xlsx", Files.readAllBytes(XlsxFixtures.large(workDir, 5_000, 10)));

        assertThat(upload.status()).isEqualTo(201);
        assertThat((Integer) JsonPath.read(preview(upload).body(), "$.totalRows")).isEqualTo(5_000);
    }

    private Response preview(Response upload) {
        return http.get("/api/import-sessions/" + JsonPath.read(upload.body(), "$.id") + "/preview");
    }

    private static long sessionDirectories() throws IOException {
        try (Stream<Path> entries = Files.list(storageDir)) {
            return entries.filter(Files::isDirectory).count();
        }
    }
}
