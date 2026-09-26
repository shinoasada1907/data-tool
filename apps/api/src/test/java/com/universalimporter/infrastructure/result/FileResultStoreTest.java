package com.universalimporter.infrastructure.result;

import com.universalimporter.domain.common.RowErrorCode;
import com.universalimporter.domain.pipeline.ErrorStage;
import com.universalimporter.domain.pipeline.ImportError;
import com.universalimporter.domain.pipeline.ResultSummary;
import com.universalimporter.domain.pipeline.ResultView;
import com.universalimporter.domain.pipeline.ResultWriter;
import com.universalimporter.domain.pipeline.RowResult;
import com.universalimporter.infrastructure.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FileResultStoreTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00Z");

    @TempDir
    Path root;

    private FileResultStore store() {
        return new FileResultStore(new StorageProperties(root));
    }

    @Test
    void a_committed_result_has_one_line_per_row_and_a_summary() throws IOException {
        FileResultStore store = store();
        try (ResultWriter writer = store.begin(ID)) {
            writer.accept(new RowResult(2, true, values("name", "An", "age", new BigDecimal("30"),
                    "dob", LocalDate.of(1990, 12, 25)), List.of()));
            writer.accept(new RowResult(3, false, values("name", "Bình", "age", "abc", "dob", null), List.of(
                    new ImportError(3, "age", ErrorStage.VALIDATION, "type", null, RowErrorCode.VALIDATION_TYPE,
                            "Value is not a valid number.", "abc"))));
            writer.commit(summary("hash-1"));
        }

        assertThat(read("result/valid.ndjson"))
                .isEqualTo("{\"rowNumber\":2,\"values\":{\"name\":\"An\",\"age\":30,\"dob\":\"1990-12-25\"}}\n");
        assertThat(read("result/invalid.ndjson")).isEqualTo("{\"rowNumber\":3,\"values\":{\"name\":\"Bình\",\"age\":\"abc\","
                + "\"dob\":null},\"errors\":[{\"rowNumber\":3,\"fieldName\":\"age\",\"stage\":\"VALIDATION\",\"rule\":\"type\","
                + "\"step\":null,\"code\":\"VALIDATION_TYPE\",\"message\":\"Value is not a valid number.\","
                + "\"sourceValue\":\"abc\"}]}\n");
        assertThat(read("result/summary.json")).contains("\"configHash\":\"hash-1\"", "\"processedAt\":\"2026-09-25T10:00:00Z\"");
        assertThat(store.findSummary(ID)).contains(summary("hash-1"));
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void numbers_are_plain_and_text_is_raw_utf8_without_bom_or_carriage_returns() throws IOException {
        try (ResultWriter writer = store().begin(ID)) {
            writer.accept(new RowResult(2, true, values("big", new BigDecimal("1E+3"), "neg", new BigDecimal("-3.50"),
                    "name", "Nguyễn", "active", true), List.of()));
            writer.commit(summary("h"));
        }

        byte[] bytes = Files.readAllBytes(root.resolve(ID + "/result/valid.ndjson"));
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertThat(text).isEqualTo("{\"rowNumber\":2,\"values\":{\"big\":1000,\"neg\":-3.50,\"name\":\"Nguyễn\",\"active\":true}}\n");
        assertThat(text).doesNotContain("\\u", "\r");
        assertThat(bytes[0]).isEqualTo((byte) '{');
        for (String file : List.of("valid.ndjson", "invalid.ndjson", "summary.json")) {
            assertThat(read("result/" + file)).doesNotContain("\r");
        }
    }

    @Test
    void closing_without_commit_leaves_nothing() throws IOException {
        try (ResultWriter writer = store().begin(ID)) {
            writer.accept(new RowResult(2, true, values("name", "An"), List.of()));
        }

        assertThat(Files.exists(root.resolve(ID + "/result"))).isFalse();
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void a_new_commit_replaces_the_previous_result() throws IOException {
        FileResultStore store = store();
        try (ResultWriter first = store.begin(ID)) {
            first.accept(new RowResult(2, true, values("name", "old"), List.of()));
            first.commit(summary("old"));
        }

        try (ResultWriter second = store.begin(ID)) {
            second.accept(new RowResult(2, true, values("name", "new"), List.of()));
            second.commit(summary("new"));
        }

        assertThat(read("result/valid.ndjson")).contains("\"new\"").doesNotContain("\"old\"");
        assertThat(store.findSummary(ID)).get().extracting(ResultSummary::configHash).isEqualTo("new");
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void there_is_no_summary_before_the_first_commit() {
        assertThat(store().findSummary(ID)).isEmpty();
    }

    @Test
    void delete_removes_the_result_but_not_the_source_file() throws IOException {
        Files.createDirectories(root.resolve(ID.toString()));
        Files.writeString(root.resolve(ID + "/source.bin"), "name\nAn\n");
        FileResultStore store = store();
        try (ResultWriter writer = store.begin(ID)) {
            writer.commit(summary("h"));
        }

        store.delete(ID);
        store.delete(ID);

        assertThat(Files.exists(root.resolve(ID + "/result"))).isFalse();
        assertThat(Files.exists(root.resolve(ID + "/source.bin"))).isTrue();
    }

    @Test
    void a_failed_swap_puts_the_previous_result_back() throws IOException {
        commit(store(), "old");
        FileResultStore failing = new FileResultStore(new StorageProperties(root), (from, to) -> {
            if (from.getFileName().toString().startsWith("result.tmp-")) {
                throw new IOException("rename refused");
            }
            Files.move(from, to);
        });

        try (ResultWriter writer = failing.begin(ID)) {
            writer.accept(new RowResult(2, true, values("name", "new"), List.of()));
            assertThat(catchThrowableOfType(UncheckedIOException.class, () -> writer.commit(summary("new")))).isNotNull();
        }

        assertThat(read("result/valid.ndjson")).contains("\"old\"");
        assertThat(failing.findSummary(ID)).get().extracting(ResultSummary::configHash).isEqualTo("old");
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void a_rename_denied_for_a_moment_is_retried() throws IOException {
        commit(store(), "old");
        AtomicInteger denials = new AtomicInteger();
        FileResultStore busy = new FileResultStore(new StorageProperties(root), (from, to) -> {
            if (denials.getAndIncrement() < 2) {
                throw new AccessDeniedException(from.toString());
            }
            Files.move(from, to);
        });

        commit(busy, "new");

        assertThat(busy.findSummary(ID)).get().extracting(ResultSummary::configHash).isEqualTo("new");
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void the_previous_result_of_an_interrupted_commit_is_restored_and_leftovers_removed() throws IOException {
        commit(store(), "old");
        Path sessionDir = root.resolve(ID.toString());
        Files.move(sessionDir.resolve("result"), sessionDir.resolve("result.old-crashed"));
        Files.createDirectories(sessionDir.resolve("result.tmp-crashed"));
        Files.writeString(sessionDir.resolve("result.tmp-crashed/valid.ndjson"), "partial");

        FileResultStore store = store();
        store.begin(ID).close();

        assertThat(store.findSummary(ID)).get().extracting(ResultSummary::configHash).isEqualTo("old");
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void leftovers_beside_a_current_result_are_removed() throws IOException {
        commit(store(), "current");
        Path sessionDir = root.resolve(ID.toString());
        Files.createDirectories(sessionDir.resolve("result.old-stale"));
        Files.createDirectories(sessionDir.resolve("result.del-stale"));
        Files.createDirectories(sessionDir.resolve("result.tmp-stale"));

        FileResultStore store = store();
        store.begin(ID).close();

        assertThat(store.findSummary(ID)).get().extracting(ResultSummary::configHash).isEqualTo("current");
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void delete_moves_the_result_aside_before_removing_it() throws IOException {
        commit(store(), "h");
        List<String> moves = new ArrayList<>();
        FileResultStore store = new FileResultStore(new StorageProperties(root), (from, to) -> {
            moves.add(from.getFileName() + " -> " + to.getFileName());
            Files.move(from, to);
        });

        store.delete(ID);

        assertThat(moves).singleElement().asString().startsWith("result -> result.del-");
        assertThat(store.findSummary(ID)).isEmpty();
        assertThat(leftovers()).isEmpty();
    }

    @Test
    void a_result_being_deleted_is_not_found() throws IOException {
        commit(store(), "h");
        Path sessionDir = root.resolve(ID.toString());
        Files.move(sessionDir.resolve("result"), sessionDir.resolve("result.del-stuck"));

        assertThat(store().findSummary(ID)).isEmpty();
    }

    @Test
    void an_unreadable_summary_counts_as_no_result() throws IOException {
        Path summary = root.resolve(ID + "/result/summary.json");
        Files.createDirectories(summary.getParent());

        Files.writeString(summary, "{}");
        assertThat(store().findSummary(ID)).isEmpty();

        Files.writeString(summary, "not json");
        assertThat(store().findSummary(ID)).isEmpty();
    }

    @Test
    void rows_read_back_keep_their_order_types_and_errors() {
        FileResultStore store = store();
        Map<String, Object> typed = values("name", "Nguyễn An", "score", new BigDecimal("-3.50"),
                "tiny", new BigDecimal("0.0000001"), "count", new BigDecimal("30"), "active", true,
                "dob", LocalDate.of(1990, 12, 25), "note", null);
        ImportError transformation = new ImportError(3, "dob", ErrorStage.TRANSFORMATION, "dateFormat", 0,
                RowErrorCode.TRANSFORMATION_FAILED, "Value does not match the date pattern.", "31/02/1990");
        ImportError validation = new ImportError(3, "email", ErrorStage.VALIDATION, "email", null,
                RowErrorCode.VALIDATION_EMAIL, "Value is not a valid email address.", " ABC ");
        try (ResultWriter writer = store.begin(ID)) {
            writer.accept(new RowResult(2, true, typed, List.of()));
            writer.accept(new RowResult(3, false, values("email", "abc", "dob", null), List.of(transformation, validation)));
            writer.accept(new RowResult(4, true, values("name", "Bình"), List.of()));
            writer.commit(summary("h"));
        }

        List<RowResult> valid;
        try (Stream<RowResult> rows = store.readRows(ID, ResultView.VALID)) {
            valid = rows.toList();
        }
        List<RowResult> invalid;
        try (Stream<RowResult> rows = store.readRows(ID, ResultView.INVALID)) {
            invalid = rows.toList();
        }

        assertThat(valid).extracting(RowResult::rowNumber).containsExactly(2, 4);
        RowResult first = valid.getFirst();
        assertThat(first.valid()).isTrue();
        assertThat(first.errors()).isEmpty();
        assertThat(first.values().keySet()).containsExactly("name", "score", "tiny", "count", "active", "dob", "note");
        assertThat(new ArrayList<>(first.values().values())).containsExactly("Nguyễn An", new BigDecimal("-3.50"),
                new BigDecimal("0.0000001"), new BigDecimal("30"), true, "1990-12-25", null);
        assertThat(invalid).singleElement().satisfies(row -> {
            assertThat(row.rowNumber()).isEqualTo(3);
            assertThat(row.valid()).isFalse();
            assertThat(row.values().keySet()).containsExactly("email", "dob");
            assertThat(row.values().get("dob")).isNull();
            assertThat(row.errors()).containsExactly(transformation, validation);
        });
    }

    @Test
    void reading_rows_stops_where_the_caller_stops() {
        FileResultStore store = store();
        try (ResultWriter writer = store.begin(ID)) {
            for (int row = 2; row < 1002; row++) {
                writer.accept(new RowResult(row, true, values("n", new BigDecimal(row)), List.of()));
            }
            writer.commit(summary("h"));
        }

        try (Stream<RowResult> rows = store.readRows(ID, ResultView.VALID)) {
            assertThat(rows.skip(10).limit(2).map(RowResult::rowNumber).toList()).containsExactly(12, 13);
        }
    }

    @Test
    void reading_rows_without_a_result_is_an_io_error() {
        assertThat(catchThrowableOfType(UncheckedIOException.class, () -> store().readRows(ID, ResultView.VALID)))
                .isNotNull();
    }

    private static void commit(FileResultStore store, String hash) {
        try (ResultWriter writer = store.begin(ID)) {
            writer.accept(new RowResult(2, true, values("name", hash), List.of()));
            writer.commit(summary(hash));
        }
    }

    private static ResultSummary summary(String hash) {
        Map<String, Long> byCode = new LinkedHashMap<>();
        byCode.put("VALIDATION_TYPE", 1L);
        Map<String, Long> byField = new LinkedHashMap<>();
        byField.put("age", 1L);
        return new ResultSummary(2, 1, 1, byCode, byField, T0, hash);
    }

    private static Map<String, Object> values(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            values.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return values;
    }

    private String read(String relative) throws IOException {
        return Files.readString(root.resolve(ID + "/" + relative), StandardCharsets.UTF_8);
    }

    private List<String> leftovers() throws IOException {
        Path dir = root.resolve(ID.toString());
        if (Files.notExists(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("result.tmp-") || name.startsWith("result.old-")
                            || name.startsWith("result.del-"))
                    .toList();
        }
    }
}
