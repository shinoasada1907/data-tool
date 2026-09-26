package com.universalimporter.application.importsession;

import com.jayway.jsonpath.JsonPath;
import com.universalimporter.support.HttpTestClient;
import com.universalimporter.support.HttpTestClient.Response;
import com.universalimporter.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The cleanup against the real database and storage; the scheduler stays off (test config). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionCleanupIntegrationTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("importer.storage.dir", storageDir::toString);
    }

    @LocalServerPort
    int port;

    @Autowired
    SessionCleanupService cleanup;

    @Autowired
    JdbcTemplate jdbc;

    private HttpTestClient http;

    @BeforeEach
    void client() {
        http = new HttpTestClient(port);
    }

    @Test
    void an_expired_session_goes_with_its_configuration_and_files() {
        String expired = upload();
        assertThat(http.putJson("/api/import-sessions/" + expired + "/schema",
                "{\"fields\": [{\"name\": \"name\", \"type\": \"string\", \"required\": true, \"order\": 0}]}").status())
                .isEqualTo(200);
        jdbc.update("update import_session set updated_at = now() - interval '25 hours' where id = ?",
                UUID.fromString(expired));
        String live = upload();

        CleanupReport report = cleanup.cleanupExpired();

        assertThat(report.deletedSessions()).isEqualTo(1);
        Response gone = http.get("/api/import-sessions/" + expired);
        assertThat(gone.status()).isEqualTo(404);
        assertThat((String) JsonPath.read(gone.body(), "$.code")).isEqualTo("SESSION_NOT_FOUND");
        assertThat(storageDir.resolve(expired)).doesNotExist();
        assertThat(jdbc.queryForObject("select count(*) from import_configuration where session_id = ?", Long.class,
                UUID.fromString(expired))).isZero();
        assertThat(http.get("/api/import-sessions/" + live).status()).isEqualTo(200);
        assertThat(storageDir.resolve(live)).exists();
    }

    @Test
    void an_old_directory_without_a_session_goes_and_other_names_are_never_touched() throws IOException {
        Instant dayAndMore = Instant.now().minus(Duration.ofHours(25));
        Path orphan = Files.createDirectories(storageDir.resolve(UUID.randomUUID().toString()));
        Files.writeString(orphan.resolve("source.bin"), "a,b", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(orphan, FileTime.from(dayAndMore));
        Path backup = Files.createDirectories(storageDir.resolve("backup"));
        Files.setLastModifiedTime(backup, FileTime.from(Instant.now().minus(Duration.ofDays(30))));

        CleanupReport report = cleanup.cleanupExpired();

        assertThat(report.deletedOrphans()).isEqualTo(1);
        assertThat(orphan).doesNotExist();
        assertThat(backup).exists();
    }

    private String upload() {
        Response upload = http.upload("customers.csv", "name\nAn\n".getBytes(StandardCharsets.UTF_8));
        assertThat(upload.status()).isEqualTo(201);
        return JsonPath.read(upload.body(), "$.id");
    }
}
