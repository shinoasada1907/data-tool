package com.universalimporter.api.schema;

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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SchemaIntegrationTest {

    private static final String FIVE_TYPES = """
            {"fields": [
              {"name": "name", "type": "string", "required": true, "order": 10},
              {"name": "age", "type": "number", "order": 20},
              {"name": "active", "type": "boolean", "order": 30},
              {"name": "joined", "type": "date", "order": 40},
              {"name": "email", "type": "email", "required": true, "order": 50}
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
    void uploadCustomers() {
        http = new HttpTestClient(port);
        Response upload = http.upload("customers.csv", "name,email\nAn,an@x.com\n".getBytes(UTF_8));
        assertThat(upload.status()).isEqualTo(201);
        sessionPath = "/api/import-sessions/" + JsonPath.read(upload.body(), "$.id");
    }

    @Test
    void a_new_session_has_an_empty_schema_and_is_not_ready() {
        Response session = http.get(sessionPath);

        assertThat((String) JsonPath.read(session.body(), "$.status")).isEqualTo("CONFIGURING");
        assertThat((List<Object>) JsonPath.read(session.body(), "$.config.schema.fields")).isEmpty();
        assertThat((Boolean) JsonPath.read(session.body(), "$.readiness.ready")).isFalse();
        assertThat((String) JsonPath.read(session.body(), "$.readiness.issues[0].code")).isEqualTo("SCHEMA_EMPTY");
    }

    @Test
    void a_schema_with_every_type_makes_the_session_ready_and_is_stored_in_order() {
        Response put = http.putJson(sessionPath + "/schema", FIVE_TYPES);

        assertThat(put.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(put.body(), "$.session.status")).isEqualTo("READY");
        assertThat((List<Object>) JsonPath.read(put.body(), "$.warnings")).isEmpty();

        Response session = http.get(sessionPath);

        assertThat((List<String>) JsonPath.read(session.body(), "$.config.schema.fields[*].name"))
                .containsExactly("name", "age", "active", "joined", "email");
        assertThat((List<String>) JsonPath.read(session.body(), "$.config.schema.fields[*].type"))
                .containsExactly("string", "number", "boolean", "date", "email");
        assertThat((List<Integer>) JsonPath.read(session.body(), "$.config.schema.fields[*].order"))
                .containsExactly(0, 1, 2, 3, 4);
        assertThat((List<Boolean>) JsonPath.read(session.body(), "$.config.schema.fields[*].required"))
                .containsExactly(true, false, false, false, true);
        assertThat((Boolean) JsonPath.read(session.body(), "$.readiness.ready")).isTrue();
    }

    @Test
    void a_second_put_replaces_the_whole_schema() {
        http.putJson(sessionPath + "/schema", FIVE_TYPES);

        Response put = http.putJson(sessionPath + "/schema",
                "{\"fields\": [{\"name\": \"phone\", \"type\": \"string\", \"order\": 0}]}");

        assertThat(put.status()).isEqualTo(200);
        assertThat((List<String>) JsonPath.read(http.get(sessionPath).body(), "$.config.schema.fields[*].name"))
                .containsExactly("phone");
    }

    @Test
    void an_invalid_schema_is_422_and_keeps_the_stored_one() {
        http.putJson(sessionPath + "/schema", FIVE_TYPES);

        Response put = http.putJson(sessionPath + "/schema", """
                {"fields": [
                  {"name": "Email", "type": "string", "order": 0},
                  {"name": "email", "type": "string", "order": 1}
                ]}""");

        assertThat(put.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("SCHEMA_INVALID");
        assertThat((String) JsonPath.read(put.body(), "$.errors[0].field")).isEqualTo("email");
        assertThat((String) JsonPath.read(put.body(), "$.errors[0].message")).isEqualTo("Duplicate field name.");
        Response session = http.get(sessionPath);
        assertThat((String) JsonPath.read(session.body(), "$.status")).isEqualTo("READY");
        assertThat((List<String>) JsonPath.read(session.body(), "$.config.schema.fields[*].name"))
                .containsExactly("name", "age", "active", "joined", "email");
    }

    @Test
    void a_value_of_another_json_type_is_400_and_not_converted() {
        Response put = http.putJson(sessionPath + "/schema",
                "{\"fields\": [{\"name\": \"a\", \"type\": \"string\", \"required\": \"true\", \"order\": 0}]}");

        assertThat(put.status()).isEqualTo(400);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("REQUEST_INVALID");
        assertThat((List<Object>) JsonPath.read(http.get(sessionPath).body(), "$.config.schema.fields")).isEmpty();
    }

    @Test
    void a_schema_for_an_unknown_session_is_404() {
        Response put = http.putJson("/api/import-sessions/11111111-2222-3333-4444-555555555555/schema", FIVE_TYPES);

        assertThat(put.status()).isEqualTo(404);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    void concurrent_puts_on_one_session_both_succeed_and_the_last_one_wins() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Response> putA = pool.submit(() -> putAfter(start, "a"));
            Future<Response> putB = pool.submit(() -> putAfter(start, "b"));
            start.countDown();
            List<Response> responses = List.of(putA.get(30, TimeUnit.SECONDS), putB.get(30, TimeUnit.SECONDS));

            assertThat(responses).extracting(Response::status).containsExactly(200, 200);
            Response last = responses.stream()
                    .max((x, y) -> updatedAt(x).compareTo(updatedAt(y)))
                    .orElseThrow();
            assertThat(updatedAt(responses.get(0))).isNotEqualTo(updatedAt(responses.get(1)));
            Map<String, Object> stored = JsonPath.read(http.get(sessionPath).body(), "$.config.schema");
            assertThat(stored).isEqualTo(JsonPath.read(last.body(), "$.session.config.schema"));
        }
    }

    private Response putAfter(CountDownLatch start, String fieldName) throws InterruptedException {
        start.await();
        return http.putJson(sessionPath + "/schema",
                "{\"fields\": [{\"name\": \"" + fieldName + "\", \"type\": \"string\", \"order\": 0}]}");
    }

    private static Instant updatedAt(Response response) {
        return Instant.parse(JsonPath.read(response.body(), "$.session.updatedAt"));
    }
}
