package com.universalimporter.api.export;

import com.jayway.jsonpath.JsonPath;
import com.universalimporter.support.CsvTestReader;
import com.universalimporter.support.HttpTestClient;
import com.universalimporter.support.HttpTestClient.Download;
import com.universalimporter.support.HttpTestClient.Response;
import com.universalimporter.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ExportApiIntegrationTest {

    private static final String CSV = """
            name,email,score,note
            An,an@x.com,10,"=HYPERLINK(""http://x"")"
            Binh,not-an-email,5,
            Chi,chi@x.com,-3,ok
            Dung,dung@x.com,0.0000001,
            """;

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("importer.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    private HttpTestClient http;
    private String id;

    @BeforeEach
    void uploadAndConfigure() {
        http = new HttpTestClient(port);
        Response upload = http.upload("customers.csv", CSV.getBytes(StandardCharsets.UTF_8));
        assertThat(upload.status()).isEqualTo(201);
        id = JsonPath.read(upload.body(), "$.id");
        uploadAndConfigureSchema();
    }

    private void uploadAndConfigureSchema() {
        assertThat(http.putJson(path("/schema"), """
                {"fields": [
                  {"name": "name", "type": "string", "required": true, "order": 0},
                  {"name": "email", "type": "email", "required": true, "order": 1},
                  {"name": "score", "type": "number", "required": false, "order": 2},
                  {"name": "note", "type": "string", "required": false, "order": 3}
                ]}""").status()).isEqualTo(200);
        assertThat(http.putJson(path("/mapping"), """
                {"mappings": [
                  {"targetField": "name", "mappingType": "SOURCE_COLUMN", "sourceColumn": "name"},
                  {"targetField": "email", "mappingType": "SOURCE_COLUMN", "sourceColumn": "email"},
                  {"targetField": "score", "mappingType": "SOURCE_COLUMN", "sourceColumn": "score"},
                  {"targetField": "note", "mappingType": "SOURCE_COLUMN", "sourceColumn": "note"}
                ]}""").status()).isEqualTo(200);
    }

    @Test
    void nothing_to_export_before_processing() {
        assertProblem(http.download(path("/export?format=json")), 409, "RESULT_NOT_AVAILABLE");
        assertProblem(http.download(path("/errors/export")), 409, "RESULT_NOT_AVAILABLE");
    }

    @Test
    void valid_rows_as_json() {
        process();

        Download json = http.download(path("/export?format=json"));

        assertThat(json.status()).isEqualTo(200);
        assertThat(json.headers().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(json.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment", "filename*=UTF-8''customers-valid.json");
        List<Map<String, Object>> rows = JsonPath.read(json.text(), "$");
        assertThat(rows).hasSize(3);
        assertThat(json.text()).isEqualTo("[{\"name\":\"An\",\"email\":\"an@x.com\",\"score\":10,"
                + "\"note\":\"=HYPERLINK(\\\"http://x\\\")\"},{\"name\":\"Chi\",\"email\":\"chi@x.com\",\"score\":-3,"
                + "\"note\":\"ok\"},{\"name\":\"Dung\",\"email\":\"dung@x.com\",\"score\":0.0000001,\"note\":null}]");
    }

    @Test
    void valid_rows_as_csv_with_formulas_guarded() {
        process();

        Download csv = http.download(path("/export?format=csv"));

        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.headers().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/csv;charset=UTF-8");
        assertThat(csv.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("filename*=UTF-8''customers-valid.csv");
        assertThat(CsvTestReader.read(csv.body())).containsExactly(
                List.of("name", "email", "score", "note"),
                List.of("An", "an@x.com", "10", "'=HYPERLINK(\"http://x\")"),
                List.of("Chi", "chi@x.com", "-3", "ok"),
                List.of("Dung", "dung@x.com", "0.0000001", ""));
        assertThat(csv.text()).doesNotContain("not-an-email");
    }

    @Test
    void the_error_report_has_one_line_per_error() {
        process();

        Download report = http.download(path("/errors/export"));

        assertThat(report.status()).isEqualTo(200);
        assertThat(report.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("customers-errors.csv");
        List<List<String>> lines = CsvTestReader.read(report.body());
        assertThat(lines).hasSize(2);
        List<String> line = new ArrayList<>(lines.get(1));
        line.set(6, "<message>");
        assertThat(line).containsExactly("3", "email", "VALIDATION", "email", "", "VALIDATION_EMAIL", "<message>",
                "not-an-email");
    }

    @Test
    void an_unknown_format_is_400() {
        assertProblem(http.download(path("/export?format=xml")), 400, "REQUEST_INVALID");
    }

    @Test
    void an_unknown_session_is_404() {
        String other = "/api/import-sessions/11111111-2222-3333-4444-555555555555";
        assertProblem(http.download(other + "/export?format=csv"), 404, "SESSION_NOT_FOUND");
        assertProblem(http.download(other + "/errors/export"), 404, "SESSION_NOT_FOUND");
    }

    @Test
    void a_missing_result_file_is_an_export_failure_before_the_first_byte() throws IOException {
        process();
        Files.delete(storageDir.resolve(id + "/result/valid.ndjson"));

        Download json = http.download(path("/export?format=json"));

        assertProblem(json, 500, "EXPORT_FAILED");
        assertThat(json.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
    }

    @Test
    void a_failure_before_anything_was_sent_is_a_clean_export_failure() throws IOException {
        process();
        corruptValidLine(2);

        Download json = http.download(path("/export?format=json"));

        assertProblem(json, 500, "EXPORT_FAILED");
        assertThat(json.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(json.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
    }

    @Test
    void a_failure_after_the_file_started_drops_the_connection_with_nothing_appended() throws IOException {
        StringBuilder big = new StringBuilder("name,email,score,note\n");
        for (int row = 0; row < 3000; row++) {
            big.append("Name").append(row).append(",u").append(row).append("@x.com,").append(row).append(",note\n");
        }
        Response upload = http.upload("big.csv", big.toString().getBytes(StandardCharsets.UTF_8));
        id = JsonPath.read(upload.body(), "$.id");
        uploadAndConfigureSchema();
        process();
        corruptValidLine(2500);

        String raw = rawGet(path("/export?format=json"));

        assertThat(raw).startsWith("HTTP/1.1 200");
        assertThat(raw).contains("Name0").doesNotContain("Name2499");
        // A dropped chunked response: no final zero-length chunk, and no error page or JSON error inside.
        assertThat(raw).doesNotEndWith("0\r\n\r\n").doesNotContain("\"timestamp\"").doesNotContain("\"code\"");
    }

    /** Reads the raw HTTP response until the server closes the connection, whether cleanly or not. */
    private String rawGet(String target) throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + target + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            try {
                for (int n; (n = in.read(buffer)) != -1; ) {
                    received.write(buffer, 0, n);
                }
            } catch (IOException reset) {
                // A reset connection is one of the ways the server drops it.
            }
        }
        return received.toString(StandardCharsets.UTF_8);
    }

    private void corruptValidLine(int lineNumber) throws IOException {
        Path valid = storageDir.resolve(id + "/result/valid.ndjson");
        List<String> lines = new ArrayList<>(Files.readAllLines(valid, StandardCharsets.UTF_8));
        lines.set(lineNumber - 1, "not json");
        Files.write(valid, lines, StandardCharsets.UTF_8);
    }

    private void process() {
        assertThat(http.post(path("/process")).status()).isEqualTo(200);
    }

    private String path(String suffix) {
        return "/api/import-sessions/" + id + suffix;
    }

    private static void assertProblem(Download download, int status, String code) {
        assertThat(download.status()).isEqualTo(status);
        assertThat((String) JsonPath.read(download.text(), "$.code")).isEqualTo(code);
    }
}
