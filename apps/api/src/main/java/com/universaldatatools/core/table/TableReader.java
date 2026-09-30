package com.universaldatatools.core.table;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads one format of dataset into the neutral table model (core-02 IO1). Readers know nothing about mapping,
 * validation or any tool. Failures are {@code DomainException}s whose message never quotes a cell.
 */
public interface TableReader {

    DataFormat format();

    /** Every sheet of a workbook; empty for formats without sheets. Does not close {@code in}. */
    List<SheetInfo> sheets(InputStream in);

    /** Reads the whole file once: checks its structure and the limits, names the columns, counts and profiles. */
    TableInfo inspect(InputStream in, ReadOptions options);

    /**
     * Streams the data rows in file order with the options and columns {@code info} resolved; closing the stream
     * closes {@code in}.
     */
    Stream<Row> read(InputStream in, TableInfo info);
}
