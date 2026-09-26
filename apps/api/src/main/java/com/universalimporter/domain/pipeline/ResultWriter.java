package com.universalimporter.domain.pipeline;

/**
 * Writes one run's result aside; nothing is visible until {@link #commit} (design P5). Always close it: closing
 * without a commit throws the partial result away.
 */
public interface ResultWriter extends RowResultSink, AutoCloseable {

    /** Writes the summary, then puts the new result in place of the previous one. */
    void commit(ResultSummary summary);

    @Override
    void close();
}
