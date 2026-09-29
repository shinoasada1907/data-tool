package com.universaldatatools.platform.run;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.ResolvedReadOptions;
import com.universaldatatools.core.table.TextEncoding;
import com.universaldatatools.platform.storage.StorageProperties;
import com.universaldatatools.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** core-06 tasks 1 and 2: runs are written whole, expire, and are cleaned up (spec: tool-runs). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RunStoreIntegrationTest {

    private static final List<RunSource> SOURCES = List.of(new RunSource("source", UUID.randomUUID(), "a.csv",
            DataFormat.CSV, new ResolvedReadOptions(null, Delimiter.SEMICOLON, TextEncoding.UTF_8, true)));

    @Autowired
    RunStore store;

    @Autowired
    RunCleanup cleanup;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StorageProperties storage;

    @Test
    void a_committed_run_has_its_files_and_its_row() {
        RunRecord run = sample(3);

        assertThat(storage.dir().resolve(run.id().toString()).resolve("rows.ndjson")).exists();
        assertThat(exists(run.id())).isTrue();
        assertThat(stagingOf(run.id())).isEmpty();
        RunRecord found = store.find("sample", run.id()).orElseThrow();
        assertThat(found.sources()).isEqualTo(SOURCES);
        assertThat(found.summary().get("rows").asInt()).isEqualTo(3);
        assertThat(found.config().get("mode").asString()).isEqualTo("test");
        assertThat(store.owns(run.id())).isTrue();
    }

    @Test
    void closing_without_commit_leaves_nothing() {
        UUID id;
        try (RunWriter writer = store.begin("sample")) {
            id = writer.id();
            writer.section("rows").write(Map.of("n", 1));
            assertThat(stagingOf(id)).hasSize(1);
        }
        assertThat(stagingOf(id)).isEmpty();
        assertThat(exists(id)).isFalse();
        assertThat(storage.dir().resolve(id.toString())).doesNotExist();
    }

    @Test
    void sections_are_read_back_skipping_lines() {
        RunRecord run = sample(3);
        try (Stream<Map> rows = store.read(run.id(), "rows", Map.class, 1)) {
            assertThat(rows.map(row -> row.get("n"))).containsExactly(2, 3);
        }
    }

    @Test
    void section_names_are_restricted() {
        try (RunWriter writer = store.begin("sample")) {
            assertThatThrownBy(() -> writer.section("Rows!")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> writer.section("../x")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void another_tool_or_an_expired_run_finds_nothing() {
        RunRecord run = sample(1);
        assertThat(store.find("other", run.id())).isEmpty();
        assertThat(store.find("sample", run.id(), Instant.now().plus(25, ChronoUnit.HOURS))).isEmpty();
        assertThatThrownBy(() -> store.get("other", run.id()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RUN_NOT_FOUND));
    }

    @Test
    void delete_removes_row_and_files_once() {
        RunRecord run = sample(1);
        store.delete("sample", run.id());
        assertThat(exists(run.id())).isFalse();
        assertThat(storage.dir().resolve(run.id().toString())).doesNotExist();
        assertThatThrownBy(() -> store.delete("sample", run.id())).isInstanceOf(DomainException.class);
    }

    @Test
    void cleanup_takes_expired_runs_and_abandoned_staging_only() throws IOException {
        RunRecord expired = sample(1);
        RunRecord recent = sample(1);
        jdbc.update("update tool_run set last_used_at = now() - interval '25 hours' where id = ?", expired.id());
        Path old = Files.createDirectories(store.stagingRoot().resolve("old-" + UUID.randomUUID()));
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        Path fresh = Files.createDirectories(store.stagingRoot().resolve("fresh-" + UUID.randomUUID()));

        RunCleanup.Report report = cleanup.cleanup();

        assertThat(report.deletedRuns()).isGreaterThanOrEqualTo(1);
        assertThat(exists(expired.id())).isFalse();
        assertThat(storage.dir().resolve(expired.id().toString())).doesNotExist();
        assertThat(exists(recent.id())).isTrue();
        assertThat(old).doesNotExist();
        assertThat(fresh).exists();
    }

    private RunRecord sample(int rows) {
        try (RunWriter writer = store.begin("sample")) {
            SectionWriter section = writer.section("rows");
            for (int n = 1; n <= rows; n++) {
                section.write(Map.of("n", n));
            }
            return writer.commit(SOURCES, Map.of("mode", "test"), Map.of("rows", rows));
        }
    }

    private List<Path> stagingOf(UUID id) {
        try (Stream<Path> children = Files.list(store.stagingRoot())) {
            return children.filter(child -> child.getFileName().toString().startsWith(id.toString())).toList();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private boolean exists(UUID id) {
        return jdbc.queryForObject("select count(*) from tool_run where id = ?", Long.class, id) == 1;
    }
}
