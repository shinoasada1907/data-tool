package com.universaldatatools.tools.importer.domain.pipeline;

import com.universaldatatools.core.table.Row;

import java.util.stream.Stream;

/** Map → transform → validate → convert, row by row (design P1). */
public interface ImportPipeline {

    /** Consumes {@code rows} in order, hands every result to {@code sink} as it is done, and returns the counts. */
    PipelineSummary execute(Stream<Row> rows, PipelineConfig config, RowResultSink sink);
}
