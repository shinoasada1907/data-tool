package com.universaldatatools.core.table;

import java.io.IOException;
import java.util.List;

/**
 * Receives the rows of one file being written. {@link #close} completes the file; {@link #abort} leaves it unfinished
 * so a failed write never looks complete. Neither closes the underlying stream.
 */
public interface RowSink extends AutoCloseable {

    /** One row, a cell per column. */
    void write(List<TypedCell> cells) throws IOException;

    @Override
    void close() throws IOException;

    void abort();
}
