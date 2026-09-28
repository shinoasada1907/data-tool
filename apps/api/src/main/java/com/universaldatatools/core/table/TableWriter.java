package com.universaldatatools.core.table;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/** Writes the neutral table model as one file format (core-02 IO9). Never closes {@code out}. */
public interface TableWriter {

    DataFormat format();

    String contentType();

    /** Without the dot: {@code csv}. */
    String extension();

    RowSink open(OutputStream out, List<OutputColumn> columns, WriteOptions options) throws IOException;
}
