package com.universaldatatools.tools.importer.application.result;

import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.pipeline.ResultSummary;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;

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
