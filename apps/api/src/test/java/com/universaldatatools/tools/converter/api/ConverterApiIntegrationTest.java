package com.universaldatatools.tools.converter.api;

import com.jayway.jsonpath.JsonPath;
import com.universaldatatools.support.HttpTestClient;
import com.universaldatatools.support.HttpTestClient.Download;
import com.universaldatatools.support.HttpTestClient.Response;
import com.universaldatatools.support.TestcontainersConfiguration;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** tool-02 (spec: data-converter), every scenario through real HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "toolbox.limits.max-cell-length=50000")
class ConverterApiIntegrationTest {

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    @Test
    void csv_to_json() {
        String id = upload("khách hàng.csv", "id;ten\n1;An\n2;Bình\n");

        Download file = convert(id, "{\"format\":\"JSON\"}");

        assertThat(file.status()).isEqualTo(200);
        assertThat(text(file)).isEqualTo("[{\"id\":\"1\",\"ten\":\"An\"},{\"id\":\"2\",\"ten\":\"Bình\"}]");
        assertThat(file.headers().getFirst("Content-Disposition"))
                .contains("attachment").contains("filename*=UTF-8''kh%C3%A1ch%20h%C3%A0ng.json");
        assertThat(file.headers().getContentType()).hasToString("application/json");
        assertThat(file.headers().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void csv_to_xlsx() throws IOException {
        String id = upload("p.csv", "sku,price\nA,1\nB,2\nC,3\n");

        Download file = convert(id, "{\"format\":\"XLSX\"}");

        assertThat(file.status()).isEqualTo(200);
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(file.body()))) {
            assertThat(workbook.getFirstSheet().getName()).isEqualTo("Sheet1");
            List<Row> rows = workbook.getFirstSheet().read();
            assertThat(rows).hasSize(4);
            assertThat(rows.getFirst().getCell(0).getRawValue()).isEqualTo("sku");
        }
    }

    @Test
    void xlsx_chosen_sheet_to_csv() throws IOException {
        String id = idOf(http.uploadTo("/api/datasets", "book.xlsx", twoSheets()));

        Download file = convert(id, "{\"sheet\":\"Prices\"}", "{\"format\":\"CSV\"}");

        assertThat(file.status()).isEqualTo(200);
        assertThat(file.body()).startsWith(0xEF, 0xBB, 0xBF);
        assertThat(text(file).substring(1)).isEqualTo("sku,price\r\nP1,10\r\n");
    }

    @Test
    void json_to_csv() {
        String id = upload("d.json", "[{\"a\":1,\"b\":\"x\"},{\"a\":2}]");

        assertThat(text(convert(id, "{\"format\":\"CSV\",\"csv\":{\"bom\":false}}"))).isEqualTo("a,b\r\n1,x\r\n2,\r\n");
    }

    @Test
    void values_keep_their_meaning() {
        String json = upload("t.json", "[{\"price\":12.50,\"code\":\"00123\",\"ok\":true}]");
        assertThat(text(convert(json, "{\"format\":\"JSON\"}")))
                .isEqualTo("[{\"price\":12.50,\"code\":\"00123\",\"ok\":true}]");

        String csv = upload("t.csv", "id,code\n1,00123\n2,00456\n");
        assertThat(text(convert(csv, "{\"format\":\"JSON\",\"json\":{\"typing\":\"INFER\"}}")))
                .isEqualTo("[{\"id\":1,\"code\":\"00123\"},{\"id\":2,\"code\":\"00456\"}]");
    }

    @Test
    void formulas_are_guarded_in_csv_unless_turned_off() {
        String id = upload("f.csv", "note\n\"=HYPERLINK(\"\"x\"\")\"\n");

        assertThat(text(convert(id, "{\"format\":\"CSV\",\"csv\":{\"bom\":false}}")))
                .isEqualTo("note\r\n\"'=HYPERLINK(\"\"x\"\")\"\r\n");
        assertThat(text(convert(id, "{\"format\":\"CSV\",\"csv\":{\"bom\":false,\"formulaGuard\":false}}")))
                .isEqualTo("note\r\n\"=HYPERLINK(\"\"x\"\")\"\r\n");
    }

    @Test
    void errors_come_before_the_file() {
        String id = upload("a.csv", "a\n1\n");

        Download wrongOption = convert(id, "{\"format\":\"JSON\",\"csv\":{\"delimiter\":\"SEMICOLON\"}}");
        assertThat(problem(wrongOption)).isEqualTo("422 CONFIG_INVALID");
        assertThat(wrongOption.headers().getFirst("Content-Disposition")).isNull();
        assertThat((String) JsonPath.read(text(wrongOption), "$.errors[0].message"))
                .isEqualTo("output.csv applies to CSV only.");

        String longCell = upload("long.csv", "note\n" + "x".repeat(40_000) + "\n");
        Download tooLong = convert(longCell, "{\"format\":\"XLSX\"}");
        assertThat(problem(tooLong)).isEqualTo("422 LIMIT_EXCEEDED");
        assertThat((String) JsonPath.read(text(tooLong), "$.detail")).isEqualTo(
                "Column \"note\" has a value longer than 32767 characters, more than an XLSX cell holds.");

        http.request(HttpMethod.DELETE, "/api/datasets/" + id, Map.of());
        assertThat(problem(convert(id, "{\"format\":\"CSV\"}"))).isEqualTo("404 DATASET_NOT_FOUND");

        Download broken = http.downloadPost("/api/converter/convert", "{\"source\":");
        assertThat(problem(broken)).isEqualTo("400 REQUEST_INVALID");
    }

    @Test
    void the_endpoint_is_documented_in_its_group() {
        Response docs = http.get("/v3/api-docs/converter");

        assertThat((Map<String, Object>) JsonPath.read(docs.body(), "$.paths")).containsOnlyKeys(
                "/api/converter/convert");
    }

    private String upload(String name, String content) {
        return idOf(http.uploadTo("/api/datasets", name, content.getBytes(StandardCharsets.UTF_8)));
    }

    private static String idOf(Response upload) {
        assertThat(upload.status()).as(upload.body()).isEqualTo(201);
        return JsonPath.read(upload.body(), "$.id");
    }

    private Download convert(String datasetId, String output) {
        return convert(datasetId, null, output);
    }

    private Download convert(String datasetId, String options, String output) {
        String source = "{\"datasetId\":\"" + datasetId + "\"" + (options == null ? "" : ",\"options\":" + options)
                + "}";
        return http.downloadPost("/api/converter/convert", "{\"source\":" + source + ",\"output\":" + output + "}");
    }

    private static String text(Download file) {
        return new String(file.body(), StandardCharsets.UTF_8);
    }

    private static String problem(Download file) {
        return file.status() + " " + JsonPath.read(text(file), "$.code");
    }

    private static byte[] twoSheets() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(out, "test", "1.0");
        Worksheet data = workbook.newWorksheet("Data");
        data.value(0, 0, "a");
        data.value(1, 0, "1");
        Worksheet prices = workbook.newWorksheet("Prices");
        prices.value(0, 0, "sku");
        prices.value(0, 1, "price");
        prices.value(1, 0, "P1");
        prices.value(1, 1, 10);
        workbook.finish();
        return out.toByteArray();
    }
}
