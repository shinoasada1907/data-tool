package com.universalimporter.domain.pipeline;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Where the result of a session's last successful run is kept (design D7, P5). Readers never see a result being
 * written, nor one made with a configuration that has since changed. I/O failures surface as
 * {@link java.io.UncheckedIOException}.
 */
public interface ResultStore {

    /** Starts writing a new result beside the current one. */
    ResultWriter begin(UUID sessionId);

    Optional<ResultSummary> findSummary(UUID sessionId);

    /**
     * Streams the valid or invalid rows of the current result, by increasing row number; the caller closes the
     * stream. Values read back are JSON types: numbers as {@link java.math.BigDecimal} with their scale, dates
     * as {@code yyyy-MM-dd} text. Only call it once {@link #findSummary} has found a result.
     */
    Stream<RowResult> readRows(UUID sessionId, ResultView view);

    /** Removes the session's result; does nothing when there is none. */
    void delete(UUID sessionId);
}
