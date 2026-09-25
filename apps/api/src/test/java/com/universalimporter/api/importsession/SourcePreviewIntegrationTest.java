package com.universalimporter.api.importsession;

import com.jayway.jsonpath.JsonPath;
import com.universalimporter.support.HttpTestClient;
import com.universalimporter.support.HttpTestClient.Response;
import com.universalimporter.support.TestcontainersConfiguration;
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
import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SourcePreviewIntegrationTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("importer.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    @Test
    void uploaded_csv_is_configuring_and_its_preview_shows_columns_rows_and_total() {
        Response upload = http.upload("customers.csv", "name,email\nAn,an@x.com\nBinh,binh@x.com\n".getBytes(UTF_8));

        assertThat(upload.status()).isEqualTo(201);
        assertThat((String) JsonPath.read(upload.body(), "$.status")).isEqualTo("CONFIGURING");

        Response preview = http.get("/api/import-sessions/" + JsonPath.read(upload.body(), "$.id") + "/preview");

        assertThat(preview.status()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(preview.body(), "$.totalRows")).isEqualTo(2);
        assertThat((String) JsonPath.read(preview.body(), "$.columns[0].name")).isEqualTo("name");
        assertThat((Integer) JsonPath.read(preview.body(), "$.rows[1].rowNumber")).isEqualTo(3);
        assertThat((List<String>) JsonPath.read(preview.body(), "$.rows[1].values"))
                .containsExactly("Binh", "binh@x.com");
    }

    @Test
    void a_broken_csv_is_rejected_at_upload_and_leaves_no_files() throws IOException {
        long directoriesBefore = sessionDirectories();

        Response upload = http.upload("broken.csv", "a,b\n\"unterminated,1\n".getBytes(UTF_8));

        assertThat(upload.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(upload.body(), "$.code")).isEqualTo("FILE_PARSE_ERROR");
        assertThat(sessionDirectories()).isEqualTo(directoriesBefore);
    }

    @Test
    void a_header_only_csv_previews_with_no_rows() {
        Response upload = http.upload("header-only.csv", "name,email\n".getBytes(UTF_8));

        Response preview = http.get("/api/import-sessions/" + JsonPath.read(upload.body(), "$.id") + "/preview");

        assertThat((Integer) JsonPath.read(preview.body(), "$.totalRows")).isZero();
        assertThat((List<Object>) JsonPath.read(preview.body(), "$.rows")).isEmpty();
    }

    @Test
    void duplicate_and_blank_headers_get_unique_names() {
        Response upload = http.upload("dup.csv", "Email,email,\n1,2,3\n".getBytes(UTF_8));

        Response preview = http.get("/api/import-sessions/" + JsonPath.read(upload.body(), "$.id") + "/preview");

        assertThat((List<String>) JsonPath.read(preview.body(), "$.columns[*].name"))
                .containsExactly("Email", "email (2)", "Column C");
    }

    @Test
    void preview_of_an_unknown_session_is_404() {
        Response preview = http.get("/api/import-sessions/11111111-2222-3333-4444-555555555555/preview");

        assertThat(preview.status()).isEqualTo(404);
        assertThat((String) JsonPath.read(preview.body(), "$.code")).isEqualTo("SESSION_NOT_FOUND");
    }

    private static long sessionDirectories() throws IOException {
        try (Stream<Path> entries = Files.list(storageDir)) {
            return entries.filter(Files::isDirectory).count();
        }
    }
}
