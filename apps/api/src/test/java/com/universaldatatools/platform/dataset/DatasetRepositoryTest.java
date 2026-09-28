package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.SheetInfo;
import com.universaldatatools.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** core-04 task 8: the dataset table and its sliding 24h retention (spec: dataset-api). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, DatasetRepository.class})
@EnableConfigurationProperties(RetentionProperties.class)
class DatasetRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Autowired
    DatasetRepository repository;

    @Test
    void a_stored_dataset_reads_back_with_its_sheets() {
        Dataset dataset = dataset(NOW.minus(Duration.ofHours(1)),
                List.of(new SheetInfo("Hidden", false), new SheetInfo("Data", true)));
        repository.insert(dataset);

        assertThat(repository.findLive(dataset.id(), NOW)).contains(dataset);
        assertThat(repository.expiresAt(dataset)).isEqualTo(dataset.lastUsedAt().plus(Duration.ofHours(24)));
    }

    @Test
    void an_expired_dataset_is_gone_before_the_cleanup_deletes_it() {
        Dataset old = dataset(NOW.minus(Duration.ofHours(25)), null);
        repository.insert(old);

        assertThat(repository.findLive(old.id(), NOW)).isEmpty();
        assertThat(repository.exists(old.id())).isTrue();
        assertThat(repository.listExpired(NOW, 10)).containsExactly(old.id());
    }

    @Test
    void a_use_is_recorded_at_most_once_per_touch_interval() {
        Dataset recent = dataset(NOW.minus(Duration.ofMinutes(5)), null);
        Dataset older = dataset(NOW.minus(Duration.ofMinutes(11)), null);
        repository.insert(recent);
        repository.insert(older);

        repository.touch(recent.id(), NOW);
        repository.touch(older.id(), NOW);

        assertThat(repository.findLive(recent.id(), NOW).orElseThrow().lastUsedAt()).isEqualTo(recent.lastUsedAt());
        assertThat(repository.findLive(older.id(), NOW).orElseThrow().lastUsedAt()).isEqualTo(NOW);
    }

    @Test
    void the_expiry_delete_keeps_a_dataset_used_meanwhile() {
        Dataset old = dataset(NOW.minus(Duration.ofHours(25)), null);
        repository.insert(old);
        repository.touch(old.id(), NOW);

        assertThat(repository.deleteIfExpired(old.id(), NOW)).isFalse();
        assertThat(repository.exists(old.id())).isTrue();

        Dataset gone = dataset(NOW.minus(Duration.ofHours(30)), null);
        repository.insert(gone);
        assertThat(repository.deleteIfExpired(gone.id(), NOW)).isTrue();
        assertThat(repository.exists(gone.id())).isFalse();
    }

    private static Dataset dataset(Instant lastUsed, List<SheetInfo> sheets) {
        Instant at = lastUsed.truncatedTo(ChronoUnit.MICROS);
        return new Dataset(UUID.randomUUID(), "khách hàng.csv", sheets == null ? DataFormat.CSV : DataFormat.XLSX, 42,
                sheets, at, at);
    }
}
