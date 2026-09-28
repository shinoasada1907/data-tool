package com.universaldatatools.tools.importer.api.mapping;

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
class MappingIntegrationTest {

    private static final String SCHEMA = """
            {"fields": [
              {"name": "name", "type": "string", "required": true, "order": 0},
              {"name": "email", "type": "email", "required": true, "order": 1},
              {"name": "country", "type": "string", "order": 2}
            ]}""";
    private static final String FULL_MAPPING = """
            {"mappings": [
              {"targetField": "name", "mappingType": "SOURCE_COLUMN", "sourceColumn": "Họ tên"},
              {"targetField": "email", "mappingType": "SOURCE_COLUMN", "sourceColumn": "email"},
              {"targetField": "country", "mappingType": "CONSTANT", "constantValue": "VN"}
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
        Response upload = http.upload("customers.csv", "Họ tên,email\nAn,an@x.com\n".getBytes(UTF_8));
        assertThat(upload.status()).isEqualTo(201);
        sessionPath = "/api/import-sessions/" + JsonPath.read(upload.body(), "$.id");
        assertThat(http.putJson(sessionPath + "/schema", SCHEMA).status()).isEqualTo(200);
    }

    @Test
    void mapping_every_field_makes_the_session_ready() {
        Response put = http.putJson(sessionPath + "/mapping", FULL_MAPPING);

        assertThat(put.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(put.body(), "$.session.status")).isEqualTo("READY");
        assertThat((List<Object>) JsonPath.read(put.body(), "$.warnings")).isEmpty();
        Response session = http.get(sessionPath);
        assertThat((List<String>) JsonPath.read(session.body(), "$.config.mapping.mappings[*].targetField"))
                .containsExactly("name", "email", "country");
        assertThat((String) JsonPath.read(session.body(), "$.config.mapping.mappings[0].sourceColumn")).isEqualTo("Họ tên");
        assertThat((String) JsonPath.read(session.body(), "$.config.mapping.mappings[2].constantValue")).isEqualTo("VN");
    }

    @Test
    void an_unmapped_required_field_keeps_the_session_configuring() {
        Response put = http.putJson(sessionPath + "/mapping",
                "{\"mappings\": [{\"targetField\": \"name\", \"mappingType\": \"SOURCE_COLUMN\", \"sourceColumn\": \"Họ tên\"}]}");

        assertThat(put.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(put.body(), "$.session.status")).isEqualTo("CONFIGURING");
        assertThat((String) JsonPath.read(put.body(), "$.session.readiness.issues[0].field")).isEqualTo("email");
        assertThat((String) JsonPath.read(put.body(), "$.session.readiness.issues[0].code")).isEqualTo("TARGET_FIELD_REQUIRED");
        assertThat((List<String>) JsonPath.read(put.body(), "$.warnings[*].field")).containsExactly("email", "country");
        assertThat((List<String>) JsonPath.read(put.body(), "$.warnings[*].code")).containsOnly("TARGET_FIELD_UNMAPPED");
    }

    @Test
    void a_missing_source_column_is_422_and_keeps_the_stored_mapping() {
        http.putJson(sessionPath + "/mapping", FULL_MAPPING);

        Response put = http.putJson(sessionPath + "/mapping",
                "{\"mappings\": [{\"targetField\": \"name\", \"mappingType\": \"SOURCE_COLUMN\", \"sourceColumn\": \"Name\"}]}");

        assertThat(put.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("SOURCE_COLUMN_NOT_FOUND");
        assertThat((String) JsonPath.read(put.body(), "$.errors[0].field")).isEqualTo("name");
        Response session = http.get(sessionPath);
        assertThat((String) JsonPath.read(session.body(), "$.status")).isEqualTo("READY");
        assertThat((List<String>) JsonPath.read(session.body(), "$.config.mapping.mappings[*].targetField"))
                .containsExactly("name", "email", "country");
    }

    @Test
    void mixed_problems_are_422_mapping_invalid_with_every_item() {
        Response put = http.putJson(sessionPath + "/mapping", """
                {"mappings": [
                  {"targetField": "name", "mappingType": "SOURCE_COLUMN", "sourceColumn": "Name"},
                  {"targetField": "name", "mappingType": "SOURCE_COLUMN", "sourceColumn": "email"},
                  {"targetField": " ", "mappingType": "CONSTANT", "constantValue": "x"}
                ]}""");

        assertThat(put.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("MAPPING_INVALID");
        assertThat((List<String>) JsonPath.read(put.body(), "$.errors[*].code"))
                .containsExactly("SOURCE_COLUMN_NOT_FOUND", "MAPPING_INVALID", "MAPPING_INVALID");
        assertThat((List<String>) JsonPath.read(put.body(), "$.errors[*].field")).containsExactly("name", "name", null);
        assertThat((String) JsonPath.read(put.body(), "$.errors[2].message")).isEqualTo("Target field is required.");
    }

    @Test
    void renaming_a_mapped_field_prunes_its_mapping() {
        http.putJson(sessionPath + "/mapping", FULL_MAPPING);

        Response put = http.putJson(sessionPath + "/schema", SCHEMA.replace("\"country\"", "\"nation\""));

        assertThat(put.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].field")).isEqualTo("country");
        assertThat((String) JsonPath.read(put.body(), "$.warnings[0].code")).isEqualTo("CONFIG_PRUNED");
        assertThat((String) JsonPath.read(put.body(), "$.session.status")).isEqualTo("READY");
        assertThat((List<String>) JsonPath.read(http.get(sessionPath).body(), "$.config.mapping.mappings[*].targetField"))
                .containsExactly("name", "email");
    }

    @Test
    void a_mapping_for_an_unknown_session_is_404() {
        Response put = http.putJson("/api/import-sessions/11111111-2222-3333-4444-555555555555/mapping", FULL_MAPPING);

        assertThat(put.status()).isEqualTo(404);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("SESSION_NOT_FOUND");
    }
}
