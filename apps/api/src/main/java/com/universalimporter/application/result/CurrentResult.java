package com.universalimporter.application.result;

import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.pipeline.ResultSummary;
import com.universalimporter.domain.pipeline.RowResult;

import java.util.stream.Stream;

/**
 * A result that may be served, with the configuration it was made with and its rows already open. Checked and
 * opened in one step under the session lock; the rows can be read after the lock is released. Close it.
 */
public record CurrentResult(ResultSummary summary, ImportConfiguration configuration, Stream<RowResult> rows)
        implements AutoCloseable {

    @Override
    public void close() {
        rows.close();
    }
}
