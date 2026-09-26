package com.universalimporter.domain.pipeline;

/** Where the pipeline writes each row as soon as it is done, so rows never pile up in memory. */
@FunctionalInterface
public interface RowResultSink {

    void accept(RowResult row);
}
