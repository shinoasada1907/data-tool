package com.universaldatatools.tools.importer.api;

import com.jayway.jsonpath.JsonPath;
import com.universaldatatools.support.CsvTestReader;
import com.universaldatatools.support.E2eFlow;
import com.universaldatatools.support.HttpTestClient;
import com.universaldatatools.support.HttpTestClient.Download;
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
import java.util.List;
import java.util.Map;

import static com.universaldatatools.support.E2eFlow.put;
import static org.assertj.core.api.Assertions.assertThat;

/** The V0.1 flow end to end, on the same business data as CSV and as XLSX (BE-F11 task 7, design D6). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ImportFlowIntegrationTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("toolbox.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void client() {
        http = new HttpTestClient(port);
    }

    @Test
    void csv_from_upload_to_export() {
        Response upload = http.upload("customers.csv", E2eFlow.customersCsv());
        assertThat(upload.status()).isEqualTo(201);
        assertThat((String) JsonPath.read(upload.body(), "$.status")).isEqualTo("CONFIGURING");
        String session = "/api/import-sessions/" + JsonPath.read(upload.body(), "$.id");

        Response preview = http.get(session + "/preview");
        assertThat((List<String>) JsonPath.read(preview.body(), "$.columns[*].name"))
                .containsExactly("Name", "Email", "Birth Date", "Active", "Score");
        assertThat((Integer) JsonPath.read(preview.body(), "$.totalRows")).isEqualTo(4);
        assertThat((Object) JsonPath.read(preview.body(), "$.sheetName")).isNull();
        assertThat((Integer) JsonPath.read(preview.body(), "$.rows[0].rowNumber")).isEqualTo(2);
        assertThat((String) JsonPath.read(preview.body(), "$.rows[0].values[0]")).isEqualTo("  An Nguyen ");

        put(http, session + "/schema", E2eFlow.SCHEMA);
        Response mapping = put(http, session + "/mapping", E2eFlow.MAPPING);
        assertThat((String) JsonPath.read(mapping.body(), "$.session.status")).isEqualTo("READY");
        put(http, session + "/transformations", E2eFlow.CSV_TRANSFORMATIONS);
        put(http, session + "/validations", E2eFlow.VALIDATIONS);

        Response process = http.post(session + "/process");
        assertThat(process.status()).isEqualTo(200);
        assertCounts(process, Map.of("TRANSFORMATION_FAILED", 1, "VALIDATION_TYPE", 2, "VALIDATION_UNIQUE", 1,
                "VALIDATION_REQUIRED", 1));
        // Schema order on the wire.
        assertThat(process.body())
                .contains("\"errorCountsByField\":{\"name\":1,\"email\":1,\"dob\":1,\"active\":1,\"score\":1}");

        Response valid = http.get(session + "/result?view=valid");
        assertThat((List<Integer>) JsonPath.read(valid.body(), "$.rows[*].rowNumber")).containsExactly(2);
        assertThat(valid.body()).contains("\"values\":" + E2eFlow.VALID_ROW_2);

        Response invalid = http.get(session + "/result?view=invalid");
        assertThat((List<Integer>) JsonPath.read(invalid.body(), "$.rows[*].rowNumber")).containsExactly(3, 4, 5);
        assertThat(errors(invalid, 0)).containsExactly(
                java.util.Arrays.asList("dob", "TRANSFORMATION", "dateFormat", 0, "TRANSFORMATION_FAILED", "31/02/1991"),
                java.util.Arrays.asList("active", "VALIDATION", "type", null, "VALIDATION_TYPE", "yes"),
                java.util.Arrays.asList("score", "VALIDATION", "type", null, "VALIDATION_TYPE", "x"));
        assertRows4And5(invalid);

        assertExports(session);
        Download report = http.download(session + "/errors/export");
        List<List<String>> lines = CsvTestReader.read(report.body());
        assertThat(lines.subList(1, lines.size()).stream().map(line -> line.get(0) + "," + line.get(1)))
                .containsExactly("3,dob", "3,active", "3,score", "4,email", "5,name");
    }

    @Test
    void xlsx_with_real_cell_types_from_upload_to_export(@TempDir Path dir) throws IOException {
        Response upload = http.upload("customers.xlsx", Files.readAllBytes(XlsxFixtures.e2eCustomers(dir)));
        assertThat(upload.status()).isEqualTo(201);
        assertThat((String) JsonPath.read(upload.body(), "$.status")).isEqualTo("CONFIGURING");
        String session = "/api/import-sessions/" + JsonPath.read(upload.body(), "$.id");

        Response preview = http.get(session + "/preview");
        assertThat((String) JsonPath.read(preview.body(), "$.sheetName")).isEqualTo("Customers");
        assertThat((List<String>) JsonPath.read(preview.body(), "$.rows[0].values"))
                .containsExactly("  An Nguyen ", "AN@EXAMPLE.COM", "1990-12-25", "TRUE", "10");

        E2eFlow.configureReady(http, session);
        put(http, session + "/transformations", E2eFlow.XLSX_TRANSFORMATIONS);
        put(http, session + "/validations", E2eFlow.VALIDATIONS);

        Response process = http.post(session + "/process");
        assertThat(process.status()).isEqualTo(200);
        assertCounts(process, Map.of("VALIDATION_TYPE", 3, "VALIDATION_UNIQUE", 1, "VALIDATION_REQUIRED", 1));

        Response invalid = http.get(session + "/result?view=invalid");
        assertThat(errors(invalid, 0)).containsExactly(
                java.util.Arrays.asList("dob", "VALIDATION", "type", null, "VALIDATION_TYPE", "31/02/1991"),
                java.util.Arrays.asList("active", "VALIDATION", "type", null, "VALIDATION_TYPE", "yes"),
                java.util.Arrays.asList("score", "VALIDATION", "type", null, "VALIDATION_TYPE", "x"));
        assertRows4And5(invalid);

        assertExports(session);
    }

    private void assertExports(String session) {
        Download json = http.download(session + "/export?format=json");
        assertThat(json.text()).isEqualTo("[" + E2eFlow.VALID_ROW_2 + "]");
        Download csv = http.download(session + "/export?format=csv");
        assertThat(CsvTestReader.read(csv.body())).containsExactly(
                List.of("name", "email", "dob", "active", "score", "country"),
                List.of("An Nguyen", "an@example.com", "1990-12-25", "true", "10", "VN"));
    }

    private static void assertCounts(Response process, Map<String, Integer> byCode) {
        assertThat((Integer) JsonPath.read(process.body(), "$.total")).isEqualTo(4);
        assertThat((Integer) JsonPath.read(process.body(), "$.valid")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(process.body(), "$.invalid")).isEqualTo(3);
        assertThat((Map<String, Integer>) JsonPath.read(process.body(), "$.errorCountsByCode"))
                .containsExactlyInAnyOrderEntriesOf(byCode);
    }

    private static void assertRows4And5(Response invalid) {
        assertThat(errors(invalid, 1)).containsExactly(
                java.util.Arrays.asList("email", "VALIDATION", "unique", null, "VALIDATION_UNIQUE", "an@example.com"));
        assertThat(errors(invalid, 2)).containsExactly(
                java.util.Arrays.asList("name", "VALIDATION", "required", null, "VALIDATION_REQUIRED", null));
    }

    /** fieldName, stage, rule, step, code, sourceValue of each error of the row at {@code index}. */
    private static List<List<Object>> errors(Response result, int index) {
        List<Map<String, Object>> errors = JsonPath.read(result.body(), "$.rows[" + index + "].errors");
        return errors.stream().map(error -> java.util.Arrays.asList(error.get("fieldName"), error.get("stage"),
                error.get("rule"), error.get("step"), error.get("code"), error.get("sourceValue"))).toList();
    }
}
