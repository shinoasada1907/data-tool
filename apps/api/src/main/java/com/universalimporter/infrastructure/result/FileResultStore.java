package com.universalimporter.infrastructure.result;

import com.universalimporter.domain.pipeline.ImportError;
import com.universalimporter.domain.pipeline.ResultStore;
import com.universalimporter.domain.pipeline.ResultSummary;
import com.universalimporter.domain.pipeline.ResultWriter;
import com.universalimporter.domain.pipeline.RowResult;
import com.universalimporter.infrastructure.storage.StorageProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Keeps each session's result in {@code {root}/{sessionId}/result/} (design D7, P5): {@code valid.ndjson},
 * {@code invalid.ndjson} and {@code summary.json}, UTF-8 without BOM, lines ended by {@code \n}, numbers plain,
 * dates {@code yyyy-MM-dd}, keys in schema order. A run writes into {@code result.tmp-{uuid}/}; a commit swaps it
 * in with renames, so a reader sees either the old result, no result for a moment, or the new one, never a
 * half-written one.
 */
@Component
public class FileResultStore implements ResultStore {

    static final String RESULT = "result";
    static final String VALID = "valid.ndjson";
    static final String INVALID = "invalid.ndjson";
    static final String SUMMARY = "summary.json";

    /** Own mapper rather than the application's: the stored format must not follow API JSON settings (as D8). */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private final Path root;

    public FileResultStore(StorageProperties properties) {
        this.root = properties.dir().toAbsolutePath().normalize();
    }

    @Override
    public ResultWriter begin(UUID sessionId) {
        Path sessionDir = sessionDir(sessionId);
        try {
            Files.createDirectories(sessionDir);
            return new FileResultWriter(sessionDir, Files.createDirectory(sessionDir.resolve("result.tmp-" + UUID.randomUUID())));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start the result of session " + sessionId, e);
        }
    }

    @Override
    public Optional<ResultSummary> findSummary(UUID sessionId) {
        Path file = sessionDir(sessionId).resolve(RESULT).resolve(SUMMARY);
        if (Files.notExists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(toSummary(JSON.readTree(Files.readString(file, StandardCharsets.UTF_8))));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the result summary of session " + sessionId, e);
        }
    }

    @Override
    public void delete(UUID sessionId) {
        Path sessionDir = sessionDir(sessionId);
        if (Files.notExists(sessionDir)) {
            return;
        }
        // Also any leftover of an interrupted run; writes on a session never overlap (design D11).
        try (Stream<Path> entries = Files.list(sessionDir)) {
            for (Path entry : entries.filter(path -> path.getFileName().toString().startsWith(RESULT)).toList()) {
                deleteTree(entry);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the result of session " + sessionId, e);
        }
    }

    private Path sessionDir(UUID sessionId) {
        return root.resolve(sessionId.toString());
    }

    private static final class FileResultWriter implements ResultWriter {

        private final Path sessionDir;
        private final Path tempDir;
        private final BufferedWriter valid;
        private final BufferedWriter invalid;
        private boolean committed;
        private boolean closed;

        FileResultWriter(Path sessionDir, Path tempDir) throws IOException {
            this.sessionDir = sessionDir;
            this.tempDir = tempDir;
            this.valid = Files.newBufferedWriter(tempDir.resolve(VALID), StandardCharsets.UTF_8);
            this.invalid = Files.newBufferedWriter(tempDir.resolve(INVALID), StandardCharsets.UTF_8);
        }

        @Override
        public void accept(RowResult row) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("rowNumber", row.rowNumber());
            line.put("values", plainValues(row.values()));
            if (!row.valid()) {
                line.put("errors", row.errors().stream().map(FileResultStore::errorDocument).toList());
            }
            write(row.valid() ? valid : invalid, JSON.writeValueAsString(line));
        }

        @Override
        public void commit(ResultSummary summary) {
            try {
                closeWriters();
                Files.writeString(tempDir.resolve(SUMMARY), JSON.writeValueAsString(summaryDocument(summary)) + "\n",
                        StandardCharsets.UTF_8);
                // A directory cannot be atomically moved over another on Windows: set the old one aside first.
                Path result = sessionDir.resolve(RESULT);
                Path old = null;
                if (Files.exists(result)) {
                    old = sessionDir.resolve("result.old-" + UUID.randomUUID());
                    Files.move(result, old);
                }
                Files.move(tempDir, result);
                committed = true;
                if (old != null) {
                    deleteTree(old);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot store the result", e);
            }
        }

        @Override
        public void close() {
            try {
                closeWriters();
                if (!committed) {
                    deleteTree(tempDir);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot clean up an unfinished result", e);
            }
        }

        private void closeWriters() throws IOException {
            if (!closed) {
                closed = true;
                try (valid; invalid) {
                    valid.flush();
                    invalid.flush();
                }
            }
        }

        private static void write(BufferedWriter out, String json) {
            try {
                out.write(json);
                out.write('\n');
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write the result", e);
            }
        }
    }

    /** Keeps the key order; dates become {@code yyyy-MM-dd} text. */
    private static Map<String, Object> plainValues(Map<String, Object> values) {
        Map<String, Object> plain = new LinkedHashMap<>();
        values.forEach((field, value) -> plain.put(field, value instanceof LocalDate date ? date.toString() : value));
        return plain;
    }

    private static Map<String, Object> errorDocument(ImportError error) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("rowNumber", error.rowNumber());
        document.put("fieldName", error.fieldName());
        document.put("stage", error.stage().name());
        document.put("rule", error.rule());
        document.put("step", error.step());
        document.put("code", error.code().name());
        document.put("message", error.message());
        document.put("sourceValue", error.sourceValue());
        return document;
    }

    private static Map<String, Object> summaryDocument(ResultSummary summary) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("total", summary.total());
        document.put("valid", summary.valid());
        document.put("invalid", summary.invalid());
        document.put("errorCountsByCode", summary.errorCountsByCode());
        document.put("errorCountsByField", summary.errorCountsByField());
        document.put("processedAt", summary.processedAt().toString());
        document.put("configHash", summary.configHash());
        return document;
    }

    private static ResultSummary toSummary(JsonNode json) {
        return new ResultSummary(json.get("total").asLong(), json.get("valid").asLong(), json.get("invalid").asLong(),
                counts(json.get("errorCountsByCode")), counts(json.get("errorCountsByField")),
                Instant.parse(json.get("processedAt").asString()), json.get("configHash").asString());
    }

    private static Map<String, Long> counts(JsonNode json) {
        Map<String, Long> counts = new LinkedHashMap<>();
        json.properties().forEach(entry -> counts.put(entry.getKey(), entry.getValue().asLong()));
        return counts;
    }

    private static void deleteTree(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> deepestFirst = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path entry : deepestFirst) {
                Files.delete(entry);
            }
        }
    }
}
