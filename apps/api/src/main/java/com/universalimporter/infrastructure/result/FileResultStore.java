package com.universalimporter.infrastructure.result;

import com.universalimporter.domain.common.RowErrorCode;
import com.universalimporter.domain.pipeline.ErrorStage;
import com.universalimporter.domain.pipeline.ImportError;
import com.universalimporter.domain.pipeline.ResultStore;
import com.universalimporter.domain.pipeline.ResultSummary;
import com.universalimporter.domain.pipeline.ResultView;
import com.universalimporter.domain.pipeline.ResultWriter;
import com.universalimporter.domain.pipeline.RowResult;
import com.universalimporter.infrastructure.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Keeps each session's result in {@code {root}/{sessionId}/result/} (design D7, P5): {@code valid.ndjson},
 * {@code invalid.ndjson} and {@code summary.json}, UTF-8 without BOM, lines ended by {@code \n}, numbers plain,
 * dates {@code yyyy-MM-dd}, keys in schema order. A run writes into {@code result.tmp-{uuid}/}; a commit swaps it
 * in with renames, so a reader sees either the old result, no result for a moment, or the new one, never a
 * half-written one. A swap that fails midway puts the old result back; whatever an interrupted run leaves behind
 * ({@code result.tmp-*}, {@code result.old-*}, {@code result.del-*}, {@code result.read-*}) is cleared by the
 * next {@link #begin}.
 * <p>
 * Windows refuses a rename while another process (an indexer, an antivirus) briefly holds a file inside; such a
 * refusal is retried a few times before it counts as a failure. Windows also refuses to rename a directory while a
 * file inside it is open, so readers never open a file inside {@code result/}: each opens a hard link made beside
 * it, and removes the link at once (see {@link #readRows}).
 */
@Component
public class FileResultStore implements ResultStore {

    static final String RESULT = "result";
    static final String VALID = "valid.ndjson";
    static final String INVALID = "invalid.ndjson";
    static final String SUMMARY = "summary.json";
    private static final String TEMP = "result.tmp-";
    private static final String OLD = "result.old-";
    private static final String DELETING = "result.del-";
    private static final String READING = "result.read-";
    private static final int MOVE_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(FileResultStore.class);

    /** A rename; a seam so tests can make one fail. */
    @FunctionalInterface
    interface Moves {
        void move(Path from, Path to) throws IOException;
    }

    /**
     * Own mapper rather than the application's: the stored format must not follow API JSON settings (as D8).
     * Decimals are read back as BigDecimal, never through a double, so {@code -3.50} stays {@code -3.50}.
     */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final Path root;
    private final Moves moves;

    @Autowired
    public FileResultStore(StorageProperties properties) {
        this(properties, (from, to) -> Files.move(from, to));
    }

    FileResultStore(StorageProperties properties, Moves moves) {
        this.root = properties.dir().toAbsolutePath().normalize();
        this.moves = moves;
    }

    @Override
    public ResultWriter begin(UUID sessionId) {
        Path sessionDir = sessionDir(sessionId);
        try {
            Files.createDirectories(sessionDir);
            recover(sessionDir);
            return new FileResultWriter(sessionDir, Files.createDirectory(sessionDir.resolve(TEMP + UUID.randomUUID())));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start the result of session " + sessionId, e);
        }
    }

    /** An unreadable summary counts as no result: the session is simply processed again. */
    @Override
    public Optional<ResultSummary> findSummary(UUID sessionId) {
        Path file = sessionDir(sessionId).resolve(RESULT).resolve(SUMMARY);
        if (Files.notExists(file)) {
            return Optional.empty();
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the result summary of session " + sessionId, e);
        }
        try {
            return Optional.of(toSummary(JSON.readTree(text)));
        } catch (RuntimeException e) {
            log.warn("Result summary of session {} is unreadable and is ignored: {}", sessionId, e.getClass().getName());
            return Optional.empty();
        }
    }

    /**
     * Reads one line at a time, and parses only the lines after {@code skip}. The reader is detached: it opens a
     * hard link to the file (a copy where the file system has no hard links) and removes that link at once. The
     * open file stays readable, while {@code result/} holds no open file and can be renamed or deleted.
     */
    @Override
    public Stream<RowResult> readRows(UUID sessionId, ResultView view, long skip) {
        Path sessionDir = sessionDir(sessionId);
        Path file = sessionDir.resolve(RESULT).resolve(view == ResultView.VALID ? VALID : INVALID);
        BufferedReader reader = openDetached(sessionId, sessionDir, file);
        boolean valid = view == ResultView.VALID;
        AtomicLong lineNumber = new AtomicLong(skip);
        return reader.lines()
                .skip(skip)
                .map(line -> toRow(sessionId, lineNumber.incrementAndGet(), line, valid))
                .onClose(() -> {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException("Cannot close the result of session " + sessionId, e);
                    }
                });
    }

    /**
     * Renames the result aside first, so it is gone for readers at once, then removes the files. Only the rename
     * must succeed: files that cannot be removed yet are cleared by the next {@link #begin}.
     */
    @Override
    public void delete(UUID sessionId) {
        Path sessionDir = sessionDir(sessionId);
        Path result = sessionDir.resolve(RESULT);
        if (Files.exists(result)) {
            try {
                move(result, sessionDir.resolve(DELETING + UUID.randomUUID()));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot delete the result of session " + sessionId, e);
            }
        }
        // Also any leftover of an interrupted run; writes on a session never overlap (design D11).
        deleteLeftovers(sessionDir);
    }

    private Path sessionDir(UUID sessionId) {
        return root.resolve(sessionId.toString());
    }

    private static BufferedReader openDetached(UUID sessionId, Path sessionDir, Path file) {
        if (Files.notExists(file)) {
            throw new UncheckedIOException("No result for session " + sessionId, new NoSuchFileException(file.toString()));
        }
        Path handle = sessionDir.resolve(READING + UUID.randomUUID());
        try {
            linkOrCopy(file, handle);
            BufferedReader reader = Files.newBufferedReader(handle, StandardCharsets.UTF_8);
            // The open reader keeps the content: nothing is left behind, even if the process dies while reading.
            deleteQuietly(handle);
            return reader;
        } catch (IOException e) {
            deleteQuietly(handle);
            throw new UncheckedIOException("Cannot read the result of session " + sessionId, e);
        }
    }

    private static void linkOrCopy(Path file, Path handle) throws IOException {
        try {
            Files.createLink(handle, file);
        } catch (NoSuchFileException e) {
            throw e;
        } catch (UnsupportedOperationException | FileSystemException e) {
            // A file system without hard links: a copy is slower but just as detached.
            Files.copy(file, handle);
        }
    }

    /**
     * Puts back the previous result of a commit interrupted between its two renames, then clears leftovers. Runs
     * under the session lock, so no other run is writing here.
     */
    private void recover(Path sessionDir) throws IOException {
        Path result = sessionDir.resolve(RESULT);
        if (Files.notExists(result)) {
            Optional<Path> previous = entries(sessionDir, OLD).stream().max(Comparator.comparing(FileResultStore::modified));
            if (previous.isPresent()) {
                move(previous.get(), result);
                log.warn("Restored the result of session {} left aside by an interrupted run", sessionDir.getFileName());
            }
        }
        deleteLeftovers(sessionDir);
    }

    private void deleteLeftovers(Path sessionDir) {
        if (Files.notExists(sessionDir)) {
            return;
        }
        try {
            for (String prefix : List.of(TEMP, OLD, DELETING, READING)) {
                for (Path leftover : entries(sessionDir, prefix)) {
                    deleteQuietly(leftover);
                }
            }
        } catch (IOException e) {
            log.warn("Cannot list the leftovers of session {}: {}", sessionDir.getFileName(), e.getClass().getName());
        }
    }

    private static List<Path> entries(Path sessionDir, String prefix) throws IOException {
        try (Stream<Path> entries = Files.list(sessionDir)) {
            return entries.filter(path -> path.getFileName().toString().startsWith(prefix)).toList();
        }
    }

    private static FileTime modified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    /** Retries a refused rename a few times, with a short pause that grows each time. */
    private void move(Path from, Path to) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                moves.move(from, to);
                return;
            } catch (AccessDeniedException e) {
                if (attempt == MOVE_ATTEMPTS) {
                    throw e;
                }
                try {
                    Thread.sleep(20L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private final class FileResultWriter implements ResultWriter {

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
                    old = sessionDir.resolve(OLD + UUID.randomUUID());
                    move(result, old);
                }
                try {
                    move(tempDir, result);
                } catch (IOException e) {
                    if (old != null) {
                        restore(old, result, e);
                    }
                    throw e;
                }
                committed = true;
                if (old != null) {
                    deleteQuietly(old);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot store the result", e);
            }
        }

        /** Never throws: an unfinished result that cannot be removed now is cleared by the next begin. */
        @Override
        public void close() {
            try {
                closeWriters();
            } catch (IOException e) {
                log.warn("Cannot close the result files in {}: {}", tempDir, e.getClass().getName());
            }
            if (!committed) {
                deleteQuietly(tempDir);
            }
        }

        private void restore(Path old, Path result, IOException failure) {
            try {
                move(old, result);
            } catch (IOException e) {
                failure.addSuppressed(e);
                log.error("Cannot put back the previous result from {}; the next run restores it", old, e);
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

    /** A line that cannot be parsed is reported by its number only: Jackson's message would quote the cell (D13). */
    private static RowResult toRow(UUID sessionId, long lineNumber, String line, boolean valid) {
        try {
            return toRow(JSON.readTree(line), valid);
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("Result of session " + sessionId + " is unreadable at line " + lineNumber + ".");
        }
    }

    private static RowResult toRow(JsonNode json, boolean valid) {
        Map<String, Object> values = new LinkedHashMap<>();
        json.get("values").properties().forEach(entry -> values.put(entry.getKey(), plain(entry.getValue())));
        List<ImportError> errors = new ArrayList<>();
        JsonNode errorNodes = json.get("errors");
        if (errorNodes != null) {
            for (JsonNode error : errorNodes) {
                errors.add(toError(error));
            }
        }
        return new RowResult(json.get("rowNumber").asInt(), valid, values, errors);
    }

    private static Object plain(JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return value.asString();
    }

    private static ImportError toError(JsonNode json) {
        JsonNode step = json.get("step");
        return new ImportError(json.get("rowNumber").asInt(), text(json, "fieldName"),
                ErrorStage.valueOf(json.get("stage").asString()), text(json, "rule"),
                step == null || step.isNull() ? null : step.asInt(), RowErrorCode.valueOf(json.get("code").asString()),
                text(json, "message"), text(json, "sourceValue"));
    }

    private static String text(JsonNode json, String property) {
        JsonNode value = json.get(property);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static Map<String, Long> counts(JsonNode json) {
        Map<String, Long> counts = new LinkedHashMap<>();
        json.properties().forEach(entry -> counts.put(entry.getKey(), entry.getValue().asLong()));
        return counts;
    }

    /** Best effort: what cannot be removed now is removed by a later {@link #begin} or {@link #delete}. */
    private static void deleteQuietly(Path path) {
        try {
            deleteTree(path);
        } catch (IOException | UncheckedIOException e) {
            log.warn("Cannot remove {} yet: {}", path, e.getClass().getName());
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> deepestFirst = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path entry : deepestFirst) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
