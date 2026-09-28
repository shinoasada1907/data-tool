package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.TableInfo;

import java.util.stream.Stream;

/**
 * A dataset opened for reading (core-04 PL5): inspected, and locked against deletion until {@link #close}. Each
 * call to {@link #rows} reads the file again; the caller closes that stream.
 */
public interface OpenedSource extends AutoCloseable {

    Dataset dataset();

    TableInfo info();

    Stream<Row> rows();

    @Override
    void close();
}
