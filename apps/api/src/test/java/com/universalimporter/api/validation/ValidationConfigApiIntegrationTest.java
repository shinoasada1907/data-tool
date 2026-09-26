package com.universalimporter.api.validation;

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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ValidationConfigApiIntegrationTest {

    private static final String SCHEMA = """
            {"fields": [
              {"name": "name", "type": "string", "required": true, "order": 0},
              {"name": "email", "type": "email", "order": 1},
              {"name": "age", "type": "number", "order": 2},
              {"name": "note", "type": "string", "order": 3}
            ]}""";

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("importer.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    private HttpTestClient http;
    private String sessionPath;

    @BeforeEach
    void uploadCustomersAndDefineTheSchema() {
        http = new HttpTestClient(port);
        Response upload = http.upload("customers.csv", "name,email,age,note\nAn,an@x.com,30,hi\n".getBytes(UTF_8));
        assertThat(upload.status()).isEqualTo(201);
        sessionPath = "/api/import-sessions/" + JsonPath.read(upload.body(), "$.id");
        assertThat(http.putJson(sessionPath + "/schema", SCHEMA).status()).isEqualTo(200);
    }

    @Test
    void a_rule_implied_by_the_schema_is_a_warning_and_is_not_stored() {
        Response put = http.putJson(sessionPath + "/validations", """
                {"validations": [
                  {"targetField": "email", "type": "unique"},
                  {"targetField": "name", "type": "required"}
                ]}""");

        assertThat(put.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].code")).isEqualTo("RULE_IMPLIED_BY_SCHEMA");
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].field")).isEqualTo("name");
        List<Map<String, Object>> stored = JsonPath.read(http.get(sessionPath).body(), "$.config.validations.validations");
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0)).containsEntry("targetField", "email").containsEntry("type", "unique");
    }

    @Test
    void email_on_a_number_field_is_422_and_keeps_the_stored_rules() {
        http.putJson(sessionPath + "/validations", "{\"validations\": [{\"targetField\": \"email\", \"type\": \"unique\"}]}");

        Response put = http.putJson(sessionPath + "/validations",
                "{\"validations\": [{\"targetField\": \"age\", \"type\": \"email\"}]}");

        assertThat(put.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("CONFIG_INVALID");
        assertThat((String) JsonPath.read(put.body(), "$.errors[0].message"))
                .isEqualTo("Rule 'email' only applies to fields of type string.");
        assertThat((List<String>) JsonPath.read(http.get(sessionPath).body(), "$.config.validations.validations[*].type"))
                .containsExactly("unique");
    }

    @Test
    void a_field_that_stops_being_a_string_loses_its_email_rule_but_keeps_unique() {
        http.putJson(sessionPath + "/validations", """
                {"validations": [
                  {"targetField": "note", "type": "email"},
                  {"targetField": "note", "type": "unique"}
                ]}""");

        Response put = http.putJson(sessionPath + "/schema", SCHEMA.replace(
                "{\"name\": \"note\", \"type\": \"string\"", "{\"name\": \"note\", \"type\": \"number\""));

        assertThat(put.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].field")).isEqualTo("note");
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].code")).isEqualTo("CONFIG_PRUNED");
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].message"))
                .isEqualTo("Rule 'email' removed because field 'note' is no longer of type string.");
        assertThat((List<String>) JsonPath.read(http.get(sessionPath).body(), "$.config.validations.validations[*].type"))
                .containsExactly("unique");
    }

    @Test
    void the_payload_the_frontend_sends_is_accepted() {
        Response put = http.putJson(sessionPath + "/validations", """
                {"validations": [
                  {"targetField": "email", "type": "unique"},
                  {"targetField": "note", "type": "email"}
                ]}""");

        assertThat(put.status()).isEqualTo(200);
        assertThat((List<Object>) JsonPath.read(put.body(), "$.warnings")).isEmpty();
        assertThat((List<String>) JsonPath.read(put.body(), "$.session.config.validations.validations[*].targetField"))
                .containsExactly("email", "note");
    }

    @Test
    void validations_for_an_unknown_session_are_404() {
        Response put = http.putJson("/api/import-sessions/11111111-2222-3333-4444-555555555555/validations",
                "{\"validations\": []}");

        assertThat(put.status()).isEqualTo(404);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("SESSION_NOT_FOUND");
    }
}
