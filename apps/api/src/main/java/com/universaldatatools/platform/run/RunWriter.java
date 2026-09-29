package com.universaldatatools.platform.run;

import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A run being written into its staging directory (core-06 RS1). {@link #commit} makes it a run; closing without
 * committing throws everything away, so a failed run leaves nothing behind. Not thread-safe.
 */
public final class RunWriter implements AutoCloseable {

    private static final Pattern SECTION_NAME = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    private final RunStore store;
    private final String tool;
    private final UUID id;
    private final Path staging;
    private final JsonMapper json;
    private final Map<String, SectionWriter> sections = new LinkedHashMap<>();
    private boolean done;

    RunWriter(RunStore store, String tool, UUID id, Path staging, JsonMapper json) {
        this.store = store;
        this.tool = tool;
        this.id = id;
        this.staging = staging;
        this.json = json;
    }

    public UUID id() {
        return id;
    }

    /**
     * The section of that name, created on first use as {@code <name>.ndjson}.
     *
     * @throws IllegalArgumentException for a name outside {@code [a-z][a-z0-9-]{0,31}}
     */
    public SectionWriter section(String name) {
        if (!SECTION_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid section name: " + name);
        }
        requireOpen();
        return sections.computeIfAbsent(name, n -> new SectionWriter(staging.resolve(n + ".ndjson"), json));
    }

    /** @param config what the tool needs later to show and export the run without the dataset */
    public RunRecord commit(List<RunSource> sources, Object config, Object summary) {
        requireOpen();
        sections.values().forEach(SectionWriter::finish);
        done = true;
        return store.commit(tool, id, staging, sources, config, summary);
    }

    @Override
    public void close() {
        if (!done) {
            done = true;
            sections.values().forEach(SectionWriter::closeQuietly);
            store.discard(staging);
        }
    }

    private void requireOpen() {
        if (done) {
            throw new IllegalStateException("Run " + id + " is already committed or closed");
        }
    }
}
