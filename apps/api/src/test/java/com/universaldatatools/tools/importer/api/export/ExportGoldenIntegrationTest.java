package com.universaldatatools.tools.importer.api.export;

import com.jayway.jsonpath.JsonPath;
import com.universaldatatools.support.E2eFlow;
import com.universaldatatools.support.HttpTestClient;
import com.universaldatatools.support.TestcontainersConfiguration;
import com.universaldatatools.support.XlsxFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Pins the importer's export files byte for byte, so that moving the exporters onto the shared table writers
 * (core-02 IO10) cannot change a single byte. Run once with {@code -Dgolden.write=true} to record them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ExportGoldenIntegrationTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/importer");

    private static final String FORMULA_CSV = "name,contact\n=SUM(A1),an@x.com\nBinh,+84901234567\n,x@y.z\n";
    private static final String FORMULA_SCHEMA = """
            {"fields":[{"name":"=cmd()","type":"string","required":true,"order":0},
                       {"name":"contact","type":"email","required":false,"order":1},
                       {"name":"amount","type":"number","required":false,"order":2}]}""";
    private static final String FORMULA_MAPPING = """
            {"mappings":[{"targetField":"=cmd()","mappingType":"SOURCE_COLUMN","sourceColumn":"name","constantValue":null},
                         {"targetField":"contact","mappingType":"SOURCE_COLUMN","sourceColumn":"contact","constantValue":null},
                         {"targetField":"amount","mappingType":"CONSTANT","sourceColumn":null,"constantValue":"12.50"}]}""";

    @LocalServerPort
    int port;

    @TempDir
    Path dir;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    @Test
    void customers_csv_exports_are_unchanged() throws IOException {
        String session = E2eFlow.upload(http);
        E2eFlow.configureCsv(http, session);
        process(session);

        assertGolden(session + "/export?format=json", "customers-valid.json");
        assertGolden(session + "/export?format=csv", "customers-valid.csv");
        assertGolden(session + "/errors/export", "customers-errors.csv");
    }

    @Test
    void customers_xlsx_exports_are_unchanged() throws IOException {
        String session = sessionOf(http.upload("customers.xlsx", Files.readAllBytes(XlsxFixtures.e2eCustomers(dir))));
        E2eFlow.configureReady(http, session);
        E2eFlow.put(http, session + "/transformations", E2eFlow.XLSX_TRANSFORMATIONS);
        E2eFlow.put(http, session + "/validations", E2eFlow.VALIDATIONS);
        process(session);

        assertGolden(session + "/export?format=json", "xlsx-valid.json");
        assertGolden(session + "/export?format=csv", "xlsx-valid.csv");
        assertGolden(session + "/errors/export", "xlsx-errors.csv");
    }

    @Test
    void formula_guarded_exports_are_unchanged() throws IOException {
        String session = sessionOf(http.upload("formula.csv", FORMULA_CSV.getBytes(StandardCharsets.UTF_8)));
        E2eFlow.put(http, session + "/schema", FORMULA_SCHEMA);
        E2eFlow.put(http, session + "/mapping", FORMULA_MAPPING);
        process(session);

        assertGolden(session + "/export?format=json", "formula-valid.json");
        assertGolden(session + "/export?format=csv", "formula-valid.csv");
        assertGolden(session + "/errors/export", "formula-errors.csv");
    }

    private String sessionOf(HttpTestClient.Response upload) {
        assertThat(upload.status()).as(upload.body()).isEqualTo(201);
        return "/api/import-sessions/" + JsonPath.read(upload.body(), "$.id");
    }

    private void process(String session) {
        HttpTestClient.Response processed = http.post(session + "/process");
        assertThat(processed.status()).as(processed.body()).isEqualTo(200);
    }

    private void assertGolden(String path, String file) throws IOException {
        HttpTestClient.Download download = http.download(path);
        assertThat(download.status()).as(path).isEqualTo(200);
        Path golden = GOLDEN.resolve(file);
        if (Boolean.getBoolean("golden.write")) {
            Files.createDirectories(GOLDEN);
            Files.write(golden, download.body());
            return;
        }
        assertThat(download.body()).as(file).isEqualTo(Files.readAllBytes(golden));
    }

    @Test
    void golden_files_are_recorded_only_on_request() {
        if (Boolean.getBoolean("golden.write")) {
            fail("golden written");
        }
    }
}
