package com.universaldatatools.platform.dataset;

import com.universaldatatools.platform.storage.StorageProperties;
import com.universaldatatools.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** core-04 task 15: expired datasets go with their files; datasets in use stay (spec: dataset-api). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatasetCleanupIntegrationTest {

    @Autowired
    DatasetService service;

    @Autowired
    DatasetCleanup cleanup;

    @Autowired
    DatasetLocks locks;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StorageProperties storage;

    @Test
    void an_expired_dataset_goes_with_its_files() {
        UUID expired = upload();
        UUID recent = upload();
        age(expired, 25);

        DatasetCleanup.Report report = cleanup.cleanupExpired(now());

        assertThat(report.deleted()).isGreaterThanOrEqualTo(1);
        assertThat(exists(expired)).isFalse();
        assertThat(storage.dir().resolve(expired.toString())).doesNotExist();
        assertThat(exists(recent)).isTrue();
        assertThat(storage.dir().resolve(recent.toString())).exists();
        assertThat(cleanup.owns(recent)).isTrue();
        assertThat(cleanup.owns(expired)).isFalse();
    }

    @Test
    void a_dataset_being_read_waits_for_the_next_run() {
        UUID busy = upload();
        age(busy, 25);

        try (DatasetLocks.Held ignored = locks.read(busy)) {
            assertThat(cleanup.cleanupExpired(now()).skipped()).isGreaterThanOrEqualTo(1);
            assertThat(exists(busy)).isTrue();
        }
        cleanup.cleanupExpired(now());
        assertThat(exists(busy)).isFalse();
    }

    private UUID upload() {
        return service.upload("a.csv", new ByteArrayInputStream("a\n1\n".getBytes(StandardCharsets.UTF_8))).id();
    }

    private void age(UUID id, int hours) {
        jdbc.update("update dataset set last_used_at = now() - make_interval(hours => ?) where id = ?", hours, id);
    }

    private boolean exists(UUID id) {
        return jdbc.queryForObject("select count(*) from dataset where id = ?", Long.class, id) == 1;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
