package com.universaldatatools.tools.importer.api.transformation;

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

import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TransformationConfigApiIntegrationTest {

    private static final String SCHEMA = """
            {"fields": [
              {"name": "name", "type": "string", "order": 0},
              {"name": "dob", "type": "date", "order": 1}
            ]}""";
    private static final String VALID = """
            {"transformations": [
              {"targetField": "dob", "order": 0, "type": "dateFormat", "params": {"inputFormat": "dd/MM/yyyy"}},
              {"targetField": "name", "order": 0, "type": "trim"}
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
    private String sessionPath;

    @BeforeEach
    void uploadCustomersAndDefineTheSchema() {
        http = new HttpTestClient(port);
        Response upload = http.upload("customers.csv", "name,dob\nAn,25/12/1990\n".getBytes(UTF_8));
        assertThat(upload.status()).isEqualTo(201);
        sessionPath = "/api/import-sessions/" + JsonPath.read(upload.body(), "$.id");
        assertThat(http.putJson(sessionPath + "/schema", SCHEMA).status()).isEqualTo(200);
    }

    @Test
    void a_valid_configuration_is_stored_in_schema_order() {
        Response put = http.putJson(sessionPath + "/transformations", VALID);

        assertThat(put.status()).isEqualTo(200);
        assertThat((List<Object>) JsonPath.read(put.body(), "$.warnings")).isEmpty();
        Response session = http.get(sessionPath);
        assertThat((List<String>) JsonPath.read(session.body(), "$.config.transformations.transformations[*].targetField"))
                .containsExactly("name", "dob");
        assertThat((List<String>) JsonPath.read(session.body(), "$.config.transformations.transformations[*].type"))
                .containsExactly("trim", "dateFormat");
        assertThat((String) JsonPath.read(session.body(), "$.config.transformations.transformations[1].params.inputFormat"))
                .isEqualTo("dd/MM/yyyy");
    }

    @Test
    void an_invalid_configuration_is_422_with_every_problem_and_keeps_the_stored_one() {
        http.putJson(sessionPath + "/transformations", VALID);

        Response put = http.putJson(sessionPath + "/transformations", """
                {"transformations": [
                  {"targetField": "name", "order": 0, "type": "replace"},
                  {"targetField": "phone", "order": 0, "type": "trim"}
                ]}""");

        assertThat(put.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("CONFIG_INVALID");
        assertThat((List<String>) JsonPath.read(put.body(), "$.errors[*].field")).containsExactly("name", "phone");
        assertThat((List<String>) JsonPath.read(put.body(), "$.errors[*].message"))
                .containsExactly("Unknown transformation type 'replace'.", "Target field does not exist.");
        assertThat((List<String>) JsonPath.read(http.get(sessionPath).body(),
                "$.config.transformations.transformations[*].type")).containsExactly("trim", "dateFormat");
    }

    @Test
    void removing_a_field_from_the_schema_prunes_its_steps() {
        http.putJson(sessionPath + "/transformations", VALID);

        Response put = http.putJson(sessionPath + "/schema",
                "{\"fields\": [{\"name\": \"name\", \"type\": \"string\", \"order\": 0}]}");

        assertThat(put.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].field")).isEqualTo("dob");
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].code")).isEqualTo("CONFIG_PRUNED");
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].message"))
                .isEqualTo("Transformations removed because field 'dob' no longer exists.");
        assertThat((List<String>) JsonPath.read(http.get(sessionPath).body(),
                "$.config.transformations.transformations[*].targetField")).containsExactly("name");
    }

    @Test
    void a_field_that_becomes_a_date_loses_a_non_iso_date_format_step() {
        http.putJson(sessionPath + "/schema", SCHEMA.replace("\"date\"", "\"string\""));
        http.putJson(sessionPath + "/transformations", """
                {"transformations": [{"targetField": "dob", "order": 0, "type": "dateFormat",
                  "params": {"inputFormat": "dd/MM/yyyy", "outputFormat": "dd/MM/yyyy"}}]}""");

        Response put = http.putJson(sessionPath + "/schema", SCHEMA);

        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].field")).isEqualTo("dob");
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].code")).isEqualTo("CONFIG_PRUNED");
        assertThat((List<Object>) JsonPath.read(http.get(sessionPath).body(), "$.config.transformations.transformations"))
                .isEmpty();
    }

    @Test
    void the_payload_the_frontend_sends_is_accepted() {
        Response put = http.putJson(sessionPath + "/transformations", """
                {"transformations": [
                  {"targetField": "name", "order": 0, "type": "trim"},
                  {"targetField": "name", "order": 1, "type": "uppercase"},
                  {"targetField": "dob", "order": 0, "type": "dateFormat",
                   "params": {"inputFormat": "dd/MM/yyyy", "outputFormat": "yyyy-MM-dd"}}
                ]}""");

        assertThat(put.status()).isEqualTo(200);
        assertThat((List<Object>) JsonPath.read(put.body(), "$.warnings")).isEmpty();
    }

    @Test
    void transformations_for_an_unknown_session_are_404() {
        Response put = http.putJson("/api/import-sessions/11111111-2222-3333-4444-555555555555/transformations", VALID);

        assertThat(put.status()).isEqualTo(404);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("SESSION_NOT_FOUND");
    }
}
