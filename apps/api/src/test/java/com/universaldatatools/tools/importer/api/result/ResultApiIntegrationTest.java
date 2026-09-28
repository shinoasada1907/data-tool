package com.universaldatatools.tools.importer.api.result;

import com.jayway.jsonpath.JsonPath;
import com.universaldatatools.support.HttpTestClient;
import com.universaldatatools.support.HttpTestClient.Response;
import com.universaldatatools.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ResultApiIntegrationTest {

    private static final String CSV = """
            name,email,score
            An,an@x.com,10
            Binh,not-an-email,5
            Chi,chi@x.com,abc
            Dung,dung@x.com,0.0000001
            """;
    private static final String SCHEMA = """
            {"fields": [
              {"name": "name", "type": "string", "required": true, "order": 0},
              {"name": "email", "type": "email", "required": true, "order": 1},
              {"name": "score", "type": "number", "required": %s, "order": 2}
            ]}""";

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("toolbox.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    private HttpTestClient http;
    private String id;

    @BeforeEach
    void uploadAndConfigure() {
        http = new HttpTestClient(port);
        Response upload = http.upload("scores.csv", CSV.getBytes(StandardCharsets.UTF_8));
        assertThat(upload.status()).isEqualTo(201);
        id = JsonPath.read(upload.body(), "$.id");
        assertThat(http.putJson(path("/schema"), SCHEMA.formatted(false)).status()).isEqualTo(200);
        assertThat(http.putJson(path("/mapping"), """
                {"mappings": [
                  {"targetField": "name", "mappingType": "SOURCE_COLUMN", "sourceColumn": "name"},
                  {"targetField": "email", "mappingType": "SOURCE_COLUMN", "sourceColumn": "email"},
                  {"targetField": "score", "mappingType": "SOURCE_COLUMN", "sourceColumn": "score"}
                ]}""").status()).isEqualTo(200);
    }

    @Test
    void there_is_no_result_before_processing() {
        Response result = http.get(path("/result"));

        assertThat(result.status()).isEqualTo(409);
        assertThat((String) JsonPath.read(result.body(), "$.code")).isEqualTo("RESULT_NOT_AVAILABLE");
    }

    @Test
    void valid_rows_come_typed_in_schema_order_with_the_summary() {
        process();

        Response result = http.get(path("/result?view=valid"));

        assertThat(result.status()).isEqualTo(200);
        assertThat(rowNumbers(result)).containsExactly(2, 5);
        assertThat(result.body()).contains("{\"rowNumber\":2,\"valid\":true,"
                + "\"values\":{\"name\":\"An\",\"email\":\"an@x.com\",\"score\":10},\"errors\":[]}");
        assertThat(result.body()).contains("\"score\":0.0000001").doesNotContain("1E-7");
        assertThat((Integer) JsonPath.read(result.body(), "$.summary.total")).isEqualTo(4);
        assertThat((Integer) JsonPath.read(result.body(), "$.summary.valid")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(result.body(), "$.summary.invalid")).isEqualTo(2);
        assertThat((String) JsonPath.read(result.body(), "$.summary.status")).isEqualTo("PROCESSED");
        assertThat((String) JsonPath.read(result.body(), "$.view")).isEqualTo("valid");
    }

    @Test
    void invalid_rows_carry_their_errors() {
        process();

        Response result = http.get(path("/result?view=invalid"));

        assertThat(rowNumbers(result)).containsExactly(3, 4);
        assertThat((String) JsonPath.read(result.body(), "$.rows[0].errors[0].fieldName")).isEqualTo("email");
        assertThat((String) JsonPath.read(result.body(), "$.rows[0].errors[0].code")).isEqualTo("VALIDATION_EMAIL");
        assertThat((String) JsonPath.read(result.body(), "$.rows[1].errors[0].fieldName")).isEqualTo("score");
        assertThat((String) JsonPath.read(result.body(), "$.rows[1].errors[0].code")).isEqualTo("VALIDATION_TYPE");
        assertThat((String) JsonPath.read(result.body(), "$.rows[1].errors[0].sourceValue")).isEqualTo("abc");
        assertThat((String) JsonPath.read(result.body(), "$.rows[1].values.score")).isEqualTo("abc");
    }

    @Test
    void invalid_rows_can_be_filtered_by_code() {
        process();

        Response result = http.get(path("/result?view=invalid&code=VALIDATION_TYPE"));

        assertThat(rowNumbers(result)).containsExactly(4);
        assertThat((Integer) JsonPath.read(result.body(), "$.page.totalElements")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(result.body(), "$.summary.invalid")).isEqualTo(2);
    }

    @Test
    void pages_split_the_rows() {
        process();

        Response result = http.get(path("/result?view=invalid&page=1&size=1"));

        assertThat(rowNumbers(result)).containsExactly(4);
        assertThat((Integer) JsonPath.read(result.body(), "$.page.totalPages")).isEqualTo(2);
    }

    @Test
    void the_same_page_twice_is_the_same_body() {
        process();

        String first = http.get(path("/result?view=invalid&page=0&size=50")).body();

        assertThat(http.get(path("/result?view=invalid&page=0&size=50")).body()).isEqualTo(first);
    }

    @Test
    void a_configuration_change_makes_the_result_unavailable() {
        process();

        assertThat(http.putJson(path("/transformations"),
                "{\"transformations\": [{\"targetField\": \"name\", \"order\": 0, \"type\": \"trim\"}]}").status())
                .isEqualTo(200);
        Response result = http.get(path("/result"));

        assertThat(result.status()).isEqualTo(409);
        assertThat((String) JsonPath.read(result.body(), "$.code")).isEqualTo("RESULT_NOT_AVAILABLE");
    }

    @Test
    void an_out_of_range_size_is_400() {
        Response result = http.get(path("/result?size=201"));

        assertThat(result.status()).isEqualTo(400);
        assertThat((String) JsonPath.read(result.body(), "$.code")).isEqualTo("REQUEST_INVALID");
    }

    @Test
    void an_unknown_session_is_404() {
        Response result = http.get("/api/import-sessions/11111111-2222-3333-4444-555555555555/result");

        assertThat(result.status()).isEqualTo(404);
        assertThat((String) JsonPath.read(result.body(), "$.code")).isEqualTo("SESSION_NOT_FOUND");
    }

    private void process() {
        assertThat(http.post(path("/process")).status()).isEqualTo(200);
    }

    private String path(String suffix) {
        return "/api/import-sessions/" + id + suffix;
    }

    private static List<Integer> rowNumbers(Response result) {
        return JsonPath.read(result.body(), "$.rows[*].rowNumber");
    }
}
