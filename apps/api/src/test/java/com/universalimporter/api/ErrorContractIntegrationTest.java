package com.universalimporter.api;

import com.jayway.jsonpath.JsonPath;
import com.universalimporter.support.E2eFlow;
import com.universalimporter.support.HttpTestClient;
import com.universalimporter.support.HttpTestClient.Response;
import com.universalimporter.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error contract of every endpoint (spec api-errors, BE-F11 D5): each case checks the status, the code and a
 * problem+json body. Every endpoint is covered for an unknown session, every write for a failed session, and each
 * error the plan lists. The case table is also checked against the published table ({@link #ALLOWED}), so a case
 * cannot expect a code the spec does not publish for that endpoint. Oversized uploads and export failures have their
 * own tests (UploadSizeLimitIntegrationTest, ExportApiIntegrationTest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ErrorContractIntegrationTest {

    /** Spec api-errors; INTERNAL_ERROR is allowed everywhere (see {@link #allowed}). */
    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("upload", Set.of("REQUEST_INVALID", "FILE_TOO_LARGE", "FILE_UNSUPPORTED", "FILE_EMPTY",
                    "FILE_PARSE_ERROR")),
            Map.entry("session", Set.of("REQUEST_INVALID", "SESSION_NOT_FOUND")),
            Map.entry("preview", Set.of("REQUEST_INVALID", "SESSION_NOT_FOUND")),
            Map.entry("schema", Set.of("REQUEST_INVALID", "SESSION_NOT_FOUND", "SESSION_STATE_INVALID", "SCHEMA_INVALID")),
            Map.entry("mapping", Set.of("REQUEST_INVALID", "SESSION_NOT_FOUND", "SESSION_STATE_INVALID", "MAPPING_INVALID",
                    "SOURCE_COLUMN_NOT_FOUND")),
            Map.entry("transformations", Set.of("REQUEST_INVALID", "SESSION_NOT_FOUND", "SESSION_STATE_INVALID",
                    "CONFIG_INVALID")),
            Map.entry("validations", Set.of("REQUEST_INVALID", "SESSION_NOT_FOUND", "SESSION_STATE_INVALID",
                    "CONFIG_INVALID")),
            Map.entry("process", Set.of("REQUEST_INVALID", "SESSION_NOT_FOUND", "SESSION_NOT_READY",
                    "SESSION_STATE_INVALID", "FILE_PARSE_ERROR", "FILE_EMPTY")),
            Map.entry("result", Set.of("REQUEST_INVALID", "SESSION_NOT_FOUND", "RESULT_NOT_AVAILABLE")),
            Map.entry("export", Set.of("REQUEST_INVALID", "SESSION_NOT_FOUND", "RESULT_NOT_AVAILABLE", "EXPORT_FAILED")),
            Map.entry("errors-export", Set.of("REQUEST_INVALID", "SESSION_NOT_FOUND", "RESULT_NOT_AVAILABLE",
                    "EXPORT_FAILED")),
            // Paths and methods that are no endpoint: the framework's own 4xx.
            Map.entry("none", Set.of("REQUEST_INVALID")));

    /**
     * Not {@code @TempDir}: with one test instance per class the Spring context starts before JUnit fills static
     * {@code @TempDir} fields.
     */
    static final Path storageDir = temporaryDirectory();

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("error-contract");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("importer.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    @org.junit.jupiter.api.AfterAll
    void removeStorage() throws IOException {
        com.universalimporter.infrastructure.storage.FileTrees.deleteTree(storageDir);
    }

    private static Set<String> allowed(String endpoint) {
        Set<String> codes = new java.util.HashSet<>(ALLOWED.get(endpoint));
        codes.add("INTERNAL_ERROR");
        return codes;
    }

    HttpTestClient http;
    /** READY, not processed. */
    String r;
    /** name and email required but not mapped. */
    String u;
    /** processed. */
    String p;
    /** FAILED: its file went missing before processing. */
    String f;
    /** READY, then its file was replaced by bytes that are not UTF-8. */
    String g;
    /** READY, then its file was emptied. */
    String h;

    record Case(String label, int status, String code, String endpoint,
                Function<ErrorContractIntegrationTest, Response> call) {

        @Override
        public String toString() {
            return label;
        }
    }

    @BeforeAll
    void fourSessions() throws IOException {
        http = new HttpTestClient(port);
        r = E2eFlow.upload(http);
        E2eFlow.configureReady(http, r);
        u = E2eFlow.upload(http);
        E2eFlow.put(http, u + "/schema", E2eFlow.SCHEMA);
        p = E2eFlow.upload(http);
        E2eFlow.configureReady(http, p);
        assertThat(http.post(p + "/process").status()).isEqualTo(200);
        f = E2eFlow.upload(http);
        E2eFlow.configureReady(http, f);
        Files.delete(storageDir.resolve(f.substring(f.lastIndexOf('/') + 1)).resolve("source.bin"));
        assertThat(http.post(f + "/process").status()).isEqualTo(500);
        assertThat((String) JsonPath.read(http.get(f).body(), "$.status")).isEqualTo("FAILED");
        g = E2eFlow.upload(http);
        E2eFlow.configureReady(http, g);
        Files.write(sourceOf(g), new byte[] {'N', 'a', 'm', 'e', '\n', (byte) 0xC3, 0x28, '\n'});
        h = E2eFlow.upload(http);
        E2eFlow.configureReady(http, h);
        Files.write(sourceOf(h), new byte[0]);
    }

    private static Path sourceOf(String session) {
        return storageDir.resolve(session.substring(session.lastIndexOf('/') + 1)).resolve("source.bin");
    }

    Stream<Case> cases() {
        String sessions = "/api/import-sessions";
        String validSchema = "{\"fields\": [{\"name\": \"name\", \"type\": \"string\", \"required\": true, \"order\": 0}]}";
        return Stream.of(
                new Case("1 upload without a file part", 400, "REQUEST_INVALID", "upload",
                        t -> t.http.upload("other", "customers.csv", "a\n1\n".getBytes(StandardCharsets.UTF_8))),
                new Case("2 upload of .xls", 415, "FILE_UNSUPPORTED", "upload",
                        t -> t.http.upload("data.xls", new byte[] {1, 2, 3})),
                new Case("3 upload of an empty file", 422, "FILE_EMPTY", "upload",
                        t -> t.http.upload("empty.csv", new byte[0])),
                new Case("4 upload of a CSV that is not UTF-8", 422, "FILE_PARSE_ERROR", "upload",
                        t -> t.http.upload("bad.csv", new byte[] {'a', ',', 'b', '\n', (byte) 0xC3, 0x28, '\n'})),
                new Case("5 session id that is no UUID", 400, "REQUEST_INVALID", "session",
                        t -> t.http.get(sessions + "/abc")),
                new Case("6 unknown session", 404, "SESSION_NOT_FOUND", "session",
                        t -> t.http.get(sessions + "/" + UUID.randomUUID())),
                new Case("7 preview limit 0", 400, "REQUEST_INVALID", "preview",
                        t -> t.http.get(t.r + "/preview?limit=0")),
                new Case("8 broken JSON", 400, "REQUEST_INVALID", "schema",
                        t -> t.http.putJson(t.r + "/schema", "{\"fields\": [")),
                new Case("9 two fields differing by case", 422, "SCHEMA_INVALID", "schema",
                        t -> t.http.putJson(t.r + "/schema", """
                                {"fields": [{"name": "Email", "type": "email", "required": true, "order": 0},
                                            {"name": "email", "type": "email", "required": true, "order": 1}]}""")),
                new Case("10 mapping of an unknown field", 422, "MAPPING_INVALID", "mapping",
                        t -> t.http.putJson(t.r + "/mapping", """
                                {"mappings": [{"targetField": "nope", "mappingType": "SOURCE_COLUMN", "sourceColumn": "Name"}]}""")),
                new Case("11 mapping of an unknown column", 422, "SOURCE_COLUMN_NOT_FOUND", "mapping",
                        t -> t.http.putJson(t.r + "/mapping", """
                                {"mappings": [{"targetField": "name", "mappingType": "SOURCE_COLUMN", "sourceColumn": "Nope"}]}""")),
                new Case("12 unknown transformation", 422, "CONFIG_INVALID", "transformations",
                        t -> t.http.putJson(t.r + "/transformations", """
                                {"transformations": [{"targetField": "name", "order": 0, "type": "reverse"}]}""")),
                new Case("13 bad date pattern", 422, "CONFIG_INVALID", "transformations",
                        t -> t.http.putJson(t.r + "/transformations", """
                                {"transformations": [{"targetField": "dob", "order": 0, "type": "dateFormat",
                                                      "params": {"inputFormat": "dd/MM/yyyyq"}}]}""")),
                new Case("14 email rule on a number field", 422, "CONFIG_INVALID", "validations",
                        t -> t.http.putJson(t.r + "/validations", """
                                {"validations": [{"targetField": "score", "type": "email"}]}""")),
                new Case("15 process before required fields are mapped", 409, "SESSION_NOT_READY", "process",
                        t -> t.http.post(t.u + "/process")),
                new Case("16 write to a failed session", 409, "SESSION_STATE_INVALID", "schema",
                        t -> t.http.putJson(t.f + "/schema", validSchema)),
                new Case("17 result before processing", 409, "RESULT_NOT_AVAILABLE", "result",
                        t -> t.http.get(t.r + "/result")),
                new Case("18 result page size 0", 400, "REQUEST_INVALID", "result",
                        t -> t.http.get(t.p + "/result?size=0")),
                new Case("19 export as xml", 400, "REQUEST_INVALID", "export",
                        t -> t.http.get(t.p + "/export?format=xml")),
                new Case("20 export before processing", 409, "RESULT_NOT_AVAILABLE", "export",
                        t -> t.http.get(t.r + "/export?format=json")),
                new Case("21 error report before processing", 409, "RESULT_NOT_AVAILABLE", "errors-export",
                        t -> t.http.get(t.r + "/errors/export")),
                new Case("24 process a file broken since upload", 422, "FILE_PARSE_ERROR", "process",
                        t -> t.http.post(t.g + "/process")),
                new Case("25 process a file emptied since upload", 422, "FILE_EMPTY", "process",
                        t -> t.http.post(t.h + "/process")),
                new Case("26 map a failed session", 409, "SESSION_STATE_INVALID", "mapping",
                        t -> t.http.putJson(t.f + "/mapping", E2eFlow.MAPPING)),
                new Case("27 transform a failed session", 409, "SESSION_STATE_INVALID", "transformations",
                        t -> t.http.putJson(t.f + "/transformations", "{\"transformations\": []}")),
                new Case("28 validate a failed session", 409, "SESSION_STATE_INVALID", "validations",
                        t -> t.http.putJson(t.f + "/validations", "{\"validations\": []}")),
                new Case("29 process a failed session", 409, "SESSION_STATE_INVALID", "process",
                        t -> t.http.post(t.f + "/process")),
                notFound("30", "preview", unknown -> t -> t.http.get(unknown + "/preview")),
                notFound("31", "schema", unknown -> t -> t.http.putJson(unknown + "/schema", validSchema)),
                notFound("32", "mapping", unknown -> t -> t.http.putJson(unknown + "/mapping", E2eFlow.MAPPING)),
                notFound("33", "transformations",
                        unknown -> t -> t.http.putJson(unknown + "/transformations", "{\"transformations\": []}")),
                notFound("34", "validations",
                        unknown -> t -> t.http.putJson(unknown + "/validations", "{\"validations\": []}")),
                notFound("35", "process", unknown -> t -> t.http.post(unknown + "/process")),
                notFound("36", "result", unknown -> t -> t.http.get(unknown + "/result")),
                notFound("37", "export", unknown -> t -> t.http.get(unknown + "/export?format=csv")),
                notFound("38", "errors-export", unknown -> t -> t.http.get(unknown + "/errors/export")),
                new Case("22 no such path", 404, "REQUEST_INVALID", "none",
                        t -> t.http.get("/api/khong-ton-tai")),
                new Case("23 method not allowed", 405, "REQUEST_INVALID", "none",
                        t -> t.http.request(HttpMethod.DELETE, sessions, Map.of())));
    }

    private static Case notFound(String number, String endpoint,
                                 Function<String, Function<ErrorContractIntegrationTest, Response>> call) {
        return new Case(number + " " + endpoint + " of an unknown session", 404, "SESSION_NOT_FOUND", endpoint,
                call.apply("/api/import-sessions/" + UUID.randomUUID()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void each_error_is_as_published(Case c) {
        Response response = c.call().apply(this);

        assertThat(response.status()).as(response.body()).isEqualTo(c.status());
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        String code = JsonPath.read(response.body(), "$.code");
        assertThat(code).isEqualTo(c.code());
        assertThat(allowed(c.endpoint())).as("codes published for %s", c.endpoint()).contains(code);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void details_of_problem_items(Case c) {
        if (c.label().startsWith("9 ")) {
            assertThat((List<?>) JsonPath.read(c.call().apply(this).body(), "$.errors")).isNotEmpty();
        }
        if (c.label().startsWith("15 ")) {
            List<Map<String, Object>> errors = JsonPath.read(c.call().apply(this).body(), "$.errors");
            assertThat(errors).anySatisfy(item -> {
                assertThat(item.get("field")).isEqualTo("email");
                assertThat(item.get("code")).isEqualTo("TARGET_FIELD_REQUIRED");
            });
        }
    }
}
