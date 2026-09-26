package com.universalimporter.api.process;

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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProcessApiIntegrationTest {

    private static final String TRANSFORMATIONS = """
            {"transformations": [
              {"targetField": "name", "order": 0, "type": "trim"},
              {"targetField": "email", "order": 0, "type": "trim"},
              {"targetField": "email", "order": 1, "type": "lowercase"},
              {"targetField": "dob", "order": 0, "type": "dateFormat", "params": {"inputFormat": "dd/MM/yyyy"}}
            ]}""";
    private static final String VALIDATIONS = "{\"validations\": [{\"targetField\": \"email\", \"type\": \"unique\"}]}";

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("importer.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    private HttpTestClient http;
    private String id;

    @BeforeEach
    void uploadTheSample() throws IOException {
        http = new HttpTestClient(port);
        try (InputStream csv = getClass().getResourceAsStream("/fixtures/pipeline/customers-sample.csv")) {
            Response upload = http.upload("customers-sample.csv", csv.readAllBytes());
            assertThat(upload.status()).isEqualTo(201);
            id = JsonPath.read(upload.body(), "$.id");
        }
    }

    @Test
    void processing_the_sample_stores_its_result_and_marks_the_session_processed() throws IOException {
        configureSample();

        Response process = http.post(path("/process"));

        assertThat(process.status()).isEqualTo(200);
        assertThat((String) JsonPath.read(process.body(), "$.status")).isEqualTo("PROCESSED");
        assertThat((Integer) JsonPath.read(process.body(), "$.total")).isEqualTo(6);
        assertThat((Integer) JsonPath.read(process.body(), "$.valid")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(process.body(), "$.invalid")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(process.body(), "$.errorCountsByCode.VALIDATION_TYPE")).isEqualTo(2);
        assertThat(lines("valid.ndjson")).hasSize(3).first()
                .isEqualTo("{\"rowNumber\":2,\"values\":{\"name\":\"An\",\"email\":\"an@x.com\",\"age\":30,\"dob\":\"1990-12-25\"}}");
        assertThat(lines("invalid.ndjson")).hasSize(3);
        assertThat((String) JsonPath.read(http.get(path("")).body(), "$.status")).isEqualTo("PROCESSED");
    }

    @Test
    void processing_twice_gives_byte_identical_rows() throws IOException {
        configureSample();
        http.post(path("/process"));
        byte[] valid = Files.readAllBytes(result("valid.ndjson"));
        byte[] invalid = Files.readAllBytes(result("invalid.ndjson"));
        Map<String, Object> summary = JsonPath.read(Files.readString(result("summary.json")), "$");

        assertThat(http.post(path("/process")).status()).isEqualTo(200);

        assertThat(Files.readAllBytes(result("valid.ndjson"))).isEqualTo(valid);
        assertThat(Files.readAllBytes(result("invalid.ndjson"))).isEqualTo(invalid);
        Map<String, Object> again = JsonPath.read(Files.readString(result("summary.json")), "$");
        summary.remove("processedAt");
        again.remove("processedAt");
        assertThat(again).isEqualTo(summary);
    }

    @Test
    void changing_the_configuration_deletes_the_result() {
        configureSample();
        http.post(path("/process"));

        assertThat(http.putJson(path("/transformations"), "{\"transformations\": []}").status()).isEqualTo(200);

        assertThat(Files.exists(storageDir.resolve(id + "/result"))).isFalse();
        assertThat((String) JsonPath.read(http.get(path("")).body(), "$.status")).isEqualTo("READY");
    }

    @Test
    void re_sending_the_same_configuration_keeps_the_result() {
        configureSample();
        http.post(path("/process"));

        assertThat(http.putJson(path("/validations"), VALIDATIONS).status()).isEqualTo(200);

        assertThat(Files.exists(storageDir.resolve(id + "/result/summary.json"))).isTrue();
        assertThat((String) JsonPath.read(http.get(path("")).body(), "$.status")).isEqualTo("PROCESSED");
    }

    @Test
    void a_session_with_an_unmapped_required_field_is_not_ready() {
        http.putJson(path("/schema"), "{\"fields\": [{\"name\": \"email\", \"type\": \"email\", \"required\": true, \"order\": 0}]}");

        Response process = http.post(path("/process"));

        assertThat(process.status()).isEqualTo(409);
        assertThat((String) JsonPath.read(process.body(), "$.code")).isEqualTo("SESSION_NOT_READY");
        assertThat((String) JsonPath.read(process.body(), "$.errors[0].field")).isEqualTo("email");
        assertThat((String) JsonPath.read(process.body(), "$.errors[0].code")).isEqualTo("TARGET_FIELD_REQUIRED");
    }

    @Test
    void a_file_broken_since_upload_fails_the_session_for_good() throws IOException {
        configureSample();
        Files.writeString(storageDir.resolve(id + "/source.bin"), "name\n\"unterminated", StandardCharsets.UTF_8);

        Response process = http.post(path("/process"));

        assertThat(process.status()).isEqualTo(422);
        assertThat((String) JsonPath.read(process.body(), "$.code")).isEqualTo("FILE_PARSE_ERROR");
        assertThat((String) JsonPath.read(http.get(path("")).body(), "$.status")).isEqualTo("FAILED");
        assertThat(resultDirectories()).isEmpty();

        Response put = http.putJson(path("/transformations"), TRANSFORMATIONS);
        Response again = http.post(path("/process"));

        assertThat(put.status()).isEqualTo(409);
        assertThat((String) JsonPath.read(put.body(), "$.code")).isEqualTo("SESSION_STATE_INVALID");
        assertThat(again.status()).isEqualTo(409);
        assertThat((String) JsonPath.read(again.body(), "$.code")).isEqualTo("SESSION_STATE_INVALID");
        assertThat((String) JsonPath.read(http.get(path("")).body(), "$.status")).isEqualTo("FAILED");
    }

    @Test
    void a_missing_source_file_is_an_internal_error_that_fails_the_session() throws IOException {
        configureSample();
        Files.delete(storageDir.resolve(id + "/source.bin"));

        Response process = http.post(path("/process"));

        assertThat(process.status()).isEqualTo(500);
        assertThat((String) JsonPath.read(process.body(), "$.code")).isEqualTo("INTERNAL_ERROR");
        assertThat((String) JsonPath.read(http.get(path("")).body(), "$.status")).isEqualTo("FAILED");
    }

    @Test
    void an_unknown_session_is_404() {
        Response process = http.post("/api/import-sessions/11111111-2222-3333-4444-555555555555/process");

        assertThat(process.status()).isEqualTo(404);
        assertThat((String) JsonPath.read(process.body(), "$.code")).isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    void concurrent_runs_both_succeed_and_leave_one_complete_result() throws Exception {
        configureSample();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Response> first = pool.submit(() -> {
                start.await();
                return http.post(path("/process"));
            });
            Future<Response> second = pool.submit(() -> {
                start.await();
                return http.post(path("/process"));
            });
            start.countDown();

            assertThat(first.get(60, TimeUnit.SECONDS).status()).isEqualTo(200);
            assertThat(second.get(60, TimeUnit.SECONDS).status()).isEqualTo(200);
        }
        assertThat(resultDirectories()).containsExactly("result");
        try (Stream<Path> files = Files.list(storageDir.resolve(id + "/result"))) {
            assertThat(files.map(file -> file.getFileName().toString()))
                    .containsExactlyInAnyOrder("valid.ndjson", "invalid.ndjson", "summary.json");
        }
    }

    private void configureSample() {
        assertThat(http.putJson(path("/schema"), """
                {"fields": [
                  {"name": "name", "type": "string", "required": true, "order": 0},
                  {"name": "email", "type": "email", "required": true, "order": 1},
                  {"name": "age", "type": "number", "order": 2},
                  {"name": "dob", "type": "date", "order": 3}
                ]}""").status()).isEqualTo(200);
        assertThat(http.putJson(path("/mapping"), """
                {"mappings": [
                  {"targetField": "name", "mappingType": "SOURCE_COLUMN", "sourceColumn": "Họ tên"},
                  {"targetField": "email", "mappingType": "SOURCE_COLUMN", "sourceColumn": "Email"},
                  {"targetField": "age", "mappingType": "SOURCE_COLUMN", "sourceColumn": "Tuổi"},
                  {"targetField": "dob", "mappingType": "SOURCE_COLUMN", "sourceColumn": "Ngày sinh"}
                ]}""").status()).isEqualTo(200);
        assertThat(http.putJson(path("/transformations"), TRANSFORMATIONS).status()).isEqualTo(200);
        assertThat(http.putJson(path("/validations"), VALIDATIONS).status()).isEqualTo(200);
    }

    private String path(String suffix) {
        return "/api/import-sessions/" + id + suffix;
    }

    private Path result(String file) {
        return storageDir.resolve(id + "/result/" + file);
    }

    private List<String> lines(String file) throws IOException {
        return Files.readAllLines(result(file), StandardCharsets.UTF_8);
    }

    private List<String> resultDirectories() throws IOException {
        try (Stream<Path> entries = Files.list(storageDir.resolve(id))) {
            return entries.map(entry -> entry.getFileName().toString()).filter(name -> name.startsWith("result")).toList();
        }
    }
}
