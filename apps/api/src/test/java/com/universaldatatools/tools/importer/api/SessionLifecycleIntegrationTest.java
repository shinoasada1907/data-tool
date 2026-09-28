package com.universaldatatools.tools.importer.api;

import com.jayway.jsonpath.JsonPath;
import com.universaldatatools.support.E2eFlow;
import com.universaldatatools.support.HttpTestClient;
import com.universaldatatools.support.HttpTestClient.Response;
import com.universaldatatools.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Every status of design D2 in one session, up to FAILED (BE-F11 task 8). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionLifecycleIntegrationTest {

    /** The CSV transformations without lowercasing the email. */
    private static final String WITHOUT_LOWERCASE = """
            {"transformations":[{"targetField":"name","order":0,"type":"trim"},
                                {"targetField":"email","order":0,"type":"trim"},
                                {"targetField":"dob","order":0,"type":"dateFormat",
                                 "params":{"inputFormat":"dd/MM/yyyy","outputFormat":"yyyy-MM-dd"}}]}""";

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("toolbox.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    @Test
    void from_upload_to_failed() throws IOException {
        HttpTestClient http = new HttpTestClient(port);
        Response upload = http.upload("customers.csv", E2eFlow.customersCsv());
        assertThat(status(upload)).isEqualTo("CONFIGURING");
        String id = JsonPath.read(upload.body(), "$.id");
        String session = "/api/import-sessions/" + id;

        Response schema = E2eFlow.put(http, session + "/schema", E2eFlow.SCHEMA);
        assertThat(sessionStatus(schema)).isEqualTo("CONFIGURING");
        assertThat((Boolean) JsonPath.read(schema.body(), "$.session.readiness.ready")).isFalse();
        assertThat((List<String>) JsonPath.read(schema.body(),
                "$.session.readiness.issues[?(@.code == 'TARGET_FIELD_REQUIRED')].field"))
                .containsExactlyInAnyOrder("name", "email");

        Response mapping = E2eFlow.put(http, session + "/mapping", E2eFlow.MAPPING);
        assertThat(sessionStatus(mapping)).isEqualTo("READY");
        assertThat((Boolean) JsonPath.read(mapping.body(), "$.session.readiness.ready")).isTrue();
        assertThat(sessionStatus(E2eFlow.put(http, session + "/transformations", E2eFlow.CSV_TRANSFORMATIONS)))
                .isEqualTo("READY");
        assertThat(sessionStatus(E2eFlow.put(http, session + "/validations", E2eFlow.VALIDATIONS))).isEqualTo("READY");

        assertThat(status(http.post(session + "/process"))).isEqualTo("PROCESSED");

        assertThat(sessionStatus(E2eFlow.put(http, session + "/transformations", WITHOUT_LOWERCASE))).isEqualTo("READY");
        assertThat(http.get(session + "/result").status()).isEqualTo(409);
        assertThat(status(http.post(session + "/process"))).isEqualTo("PROCESSED");
        assertThat(sessionStatus(E2eFlow.put(http, session + "/transformations", WITHOUT_LOWERCASE)))
                .isEqualTo("PROCESSED");
        assertThat(http.get(session + "/result").status()).isEqualTo(200);

        Files.delete(storageDir.resolve(id).resolve("source.bin"));
        assertThat(sessionStatus(E2eFlow.put(http, session + "/validations", "{\"validations\":[]}"))).isEqualTo("READY");
        Response process = http.post(session + "/process");
        assertThat(process.status()).isEqualTo(500);
        assertThat(process.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat((String) JsonPath.read(process.body(), "$.code")).isEqualTo("INTERNAL_ERROR");
        assertThat(status(http.get(session))).isEqualTo("FAILED");

        Response write = http.putJson(session + "/schema", E2eFlow.SCHEMA);
        assertThat(write.status()).isEqualTo(409);
        assertThat((String) JsonPath.read(write.body(), "$.code")).isEqualTo("SESSION_STATE_INVALID");
        assertThat(status(http.get(session))).isEqualTo("FAILED");
        Response result = http.get(session + "/result");
        assertThat(result.status()).isEqualTo(409);
        assertThat((String) JsonPath.read(result.body(), "$.code")).isEqualTo("RESULT_NOT_AVAILABLE");
    }

    private static String status(Response response) {
        return JsonPath.read(response.body(), "$.status");
    }

    private static String sessionStatus(Response put) {
        return JsonPath.read(put.body(), "$.session.status");
    }
}
