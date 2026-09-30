package com.universaldatatools.platform.run;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.platform.dataset.RetentionProperties;
import com.universaldatatools.platform.storage.FileStorage;
import com.universaldatatools.platform.storage.FileTrees;
import com.universaldatatools.platform.storage.StorageOwner;
import com.universaldatatools.platform.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stored results of tool runs (core-06, core-04 PL6). A run exists only once complete: its sections are written to
 * {@code {root}/.staging/{id}-{nonce}/}, renamed to {@code {root}/{id}/} in one move, and only then is its row
 * inserted, so a row never points at missing files. A run is gone for every reader once it has not been used for
 * {@code toolbox.retention.run-ttl}, and to every tool but its own.
 */
@Component
public class RunStore implements StorageOwner {

    /** Not a UUID, so the storage's orphan sweep never touches it; {@link RunCleanup} does. */
    static final String STAGING = ".staging";

    private static final Logger log = LoggerFactory.getLogger(RunStore.class);
    private static final TypeReference<List<RunSource>> SOURCES = new TypeReference<>() {
    };

    private final ToolRunJpaRepository jpa;
    private final FileStorage storage;
    private final RetentionProperties retention;
    private final Clock clock;
    private final Path root;
    private final JsonMapper json = JsonMapper.builder().build();

    public RunStore(ToolRunJpaRepository jpa, FileStorage storage, StorageProperties storageProperties,
                    RetentionProperties retention, Clock clock) {
        this.jpa = jpa;
        this.storage = storage;
        this.retention = retention;
        this.clock = clock;
        this.root = storageProperties.dir().toAbsolutePath().normalize();
    }

    /** @param tool the tool's API name, such as {@code validator} */
    public RunWriter begin(String tool) {
        UUID id = UUID.randomUUID();
        Path staging = root.resolve(STAGING).resolve(id + "-" + UUID.randomUUID());
        try {
            Files.createDirectories(staging);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create a staging directory for a run", e);
        }
        return new RunWriter(this, tool, id, staging, json);
    }

    /** The run if it belongs to {@code tool} and has not expired; records the use. */
    public Optional<RunRecord> find(String tool, UUID id) {
        return find(tool, id, now());
    }

    Optional<RunRecord> find(String tool, UUID id, Instant now) {
        Optional<RunRecord> run = jpa.findById(id)
                .filter(entity -> entity.tool().equals(tool))
                .filter(entity -> entity.lastUsedAt().isAfter(now.minus(retention.runTtl())))
                .map(entity -> toRecord(entity, now));
        run.ifPresent(found -> jpa.touch(id, now, now.minus(retention.touchInterval())));
        return run;
    }

    /** @throws DomainException {@code RUN_NOT_FOUND} when there is no such live run of that tool */
    public RunRecord get(String tool, UUID id) {
        return find(tool, id).orElseThrow(RunStore::notFound);
    }

    /**
     * The records of a section from line {@code skip} on; skipped lines are not parsed. The caller closes the stream.
     */
    public <T> Stream<T> read(UUID id, String section, Class<T> type, long skip) {
        BufferedReader reader;
        try {
            reader = Files.newBufferedReader(root.resolve(id.toString()).resolve(section + ".ndjson"),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read section " + section + " of run " + id, e);
        }
        return reader.lines().skip(skip).map(line -> json.readValue(line, type)).onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** @throws DomainException {@code RUN_NOT_FOUND} when there is no such live run of that tool */
    public void delete(String tool, UUID id) {
        get(tool, id);
        jpa.deleteById(id);
        deleteFiles(id);
    }

    @Override
    public boolean owns(UUID id) {
        return jpa.existsById(id);
    }

    public JsonMapper json() {
        return json;
    }

    RunRecord commit(String tool, UUID id, Path staging, List<RunSource> sources, Object config, Object summary) {
        Path target = root.resolve(id.toString());
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            discard(staging);
            throw new UncheckedIOException("Cannot move run " + id + " into place", e);
        }
        Instant now = now();
        try {
            jpa.save(new ToolRunEntity(id, tool, now, json.writeValueAsString(sources),
                    json.writeValueAsString(config), json.writeValueAsString(summary)));
        } catch (RuntimeException e) {
            deleteFiles(id);
            throw e;
        }
        return new RunRecord(id, tool, now, now.plus(retention.runTtl()), sources, json.valueToTree(config),
                json.valueToTree(summary));
    }

    void discard(Path staging) {
        try {
            FileTrees.deleteTree(staging);
        } catch (IOException e) {
            log.warn("Staging directory {} could not be deleted; the run cleanup will: {}", staging.getFileName(),
                    e.getClass().getName());
        }
    }

    Path stagingRoot() {
        return root.resolve(STAGING);
    }

    void deleteFiles(UUID id) {
        try {
            storage.delete(id);
        } catch (RuntimeException e) {
            log.warn("Files of run {} could not be deleted; the orphan sweep will: {}", id, e.getClass().getName());
        }
    }

    private RunRecord toRecord(ToolRunEntity entity, Instant now) {
        Instant lastUsed = entity.lastUsedAt().isBefore(now.minus(retention.touchInterval())) ? now : entity.lastUsedAt();
        return new RunRecord(entity.id(), entity.tool(), entity.createdAt(), lastUsed.plus(retention.runTtl()),
                json.readValue(entity.sources(), SOURCES), json.readTree(entity.config()),
                json.readTree(entity.summary()));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.RUN_NOT_FOUND, "Run not found or expired.");
    }
}
