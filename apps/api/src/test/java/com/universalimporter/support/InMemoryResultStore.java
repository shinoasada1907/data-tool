package com.universalimporter.support;

import com.universalimporter.domain.pipeline.ResultStore;
import com.universalimporter.domain.pipeline.ResultSummary;
import com.universalimporter.domain.pipeline.ResultView;
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
import java.util.stream.Stream;

/** Test double for the result store: committed results in memory, and a count of unfinished writers. */
public class InMemoryResultStore implements ResultStore {

    public record Stored(ResultSummary summary, List<RowResult> rows) {
    }

    private final Map<UUID, Stored> results = new HashMap<>();
    private int begun;
    private int open;
    private boolean failWrites;
    private final java.util.concurrent.atomic.AtomicInteger concurrent = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger maxConcurrent = new java.util.concurrent.atomic.AtomicInteger();
    private long commitDelayMillis;

    /** Makes each commit take this long, to widen the window in which two runs could overlap. */
    public void slowCommits(long millis) {
        this.commitDelayMillis = millis;
    }

    /** The most writers that were open at the same time. */
    public int maxConcurrentWriters() {
        return maxConcurrent.get();
    }

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

    private int rowsRead;
    private int opened;
    private int closed;
    private boolean failReads;
    private boolean failCloses;

    /** Closing a row stream fails from now on, as when a reader cannot release its file. */
    public void failCloses() {
        this.failCloses = true;
    }

    /** Row streams closed so far. */
    public int closedStreams() {
        return closed;
    }

    /** Opening rows fails from now on, as when a result file has gone missing. */
    public void failReads() {
        this.failReads = true;
    }

    /** Row streams opened so far. */
    public int opened() {
        return opened;
    }

    /** Rows handed out by {@link #readRows} so far, to check that a reader stops early. */
    public int rowsRead() {
        return rowsRead;
    }

    public int begun() {
        return begun;
    }

    /** Writers begun but not closed yet: the in-memory version of a leftover temporary directory. */
    public int open() {
        return open;
    }

    @Override
    public synchronized ResultWriter begin(UUID sessionId) {
        begun++;
        open++;
        maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
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
                try {
                    Thread.sleep(commitDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (InMemoryResultStore.this) {
                    results.put(sessionId, new Stored(summary, List.copyOf(rows)));
                }
            }

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    concurrent.decrementAndGet();
                    synchronized (InMemoryResultStore.this) {
                        open--;
                    }
                }
            }
        };
    }

    @Override
    public Optional<ResultSummary> findSummary(UUID sessionId) {
        return stored(sessionId).map(Stored::summary);
    }

    @Override
    public Stream<RowResult> readRows(UUID sessionId, ResultView view, long skip) {
        if (failReads) {
            throw new UncheckedIOException(new IOException("valid.ndjson is missing"));
        }
        opened++;
        // A copy: like the file store's detached reader, later changes to the store do not reach it.
        List<RowResult> rows = List.copyOf(stored(sessionId).map(Stored::rows)
                .orElseThrow(() -> new UncheckedIOException(new IOException("no result"))));
        return rows.stream()
                .filter(row -> row.valid() == (view == ResultView.VALID))
                .skip(skip)
                .peek(row -> rowsRead++)
                .onClose(() -> {
                    closed++;
                    if (failCloses) {
                        throw new UncheckedIOException(new IOException("cannot close"));
                    }
                });
    }

    @Override
    public void delete(UUID sessionId) {
        results.remove(sessionId);
    }
}
