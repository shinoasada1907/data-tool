package com.universalimporter.support;

import com.universalimporter.domain.pipeline.ResultStore;
import com.universalimporter.domain.pipeline.ResultSummary;
import com.universalimporter.domain.pipeline.ResultWriter;
import com.universalimporter.domain.pipeline.RowResult;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test double for the result store: committed results in memory, and a count of unfinished writers. */
public class InMemoryResultStore implements ResultStore {

    public record Stored(ResultSummary summary, List<RowResult> rows) {
    }

    private final Map<UUID, Stored> results = new HashMap<>();
    private int begun;
    private int open;
    private boolean failWrites;

    /** Every write fails from now on, as on a full disk. */
    public void failWrites() {
        this.failWrites = true;
    }

    public Optional<Stored> stored(UUID sessionId) {
        return Optional.ofNullable(results.get(sessionId));
    }

    public void put(UUID sessionId, Stored stored) {
        results.put(sessionId, stored);
    }

    public int begun() {
        return begun;
    }

    /** Writers begun but not closed yet: the in-memory version of a leftover temporary directory. */
    public int open() {
        return open;
    }

    @Override
    public ResultWriter begin(UUID sessionId) {
        begun++;
        open++;
        List<RowResult> rows = new ArrayList<>();
        return new ResultWriter() {
            private boolean closed;

            @Override
            public void accept(RowResult row) {
                if (failWrites) {
                    throw new UncheckedIOException(new IOException("disk full"));
                }
                rows.add(row);
            }

            @Override
            public void commit(ResultSummary summary) {
                results.put(sessionId, new Stored(summary, List.copyOf(rows)));
            }

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    open--;
                }
            }
        };
    }

    @Override
    public Optional<ResultSummary> findSummary(UUID sessionId) {
        return stored(sessionId).map(Stored::summary);
    }

    @Override
    public void delete(UUID sessionId) {
        results.remove(sessionId);
    }
}
