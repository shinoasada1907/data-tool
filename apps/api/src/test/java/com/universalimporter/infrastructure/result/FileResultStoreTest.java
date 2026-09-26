package com.universalimporter.infrastructure.result;

import com.universalimporter.domain.common.RowErrorCode;
import com.universalimporter.domain.pipeline.ErrorStage;
import com.universalimporter.domain.pipeline.ImportError;
import com.universalimporter.domain.pipeline.ResultSummary;
import com.universalimporter.domain.pipeline.ResultWriter;
import com.universalimporter.domain.pipeline.RowResult;
import com.universalimporter.infrastructure.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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
                    .filter(name -> name.startsWith("result.tmp-") || name.startsWith("result.old-"))
                    .toList();
        }
    }
}
