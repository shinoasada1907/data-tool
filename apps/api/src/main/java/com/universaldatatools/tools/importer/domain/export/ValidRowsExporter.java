package com.universaldatatools.tools.importer.domain.export;

import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import com.universaldatatools.tools.importer.domain.schema.TargetField;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes the valid rows of a result as a file (design F10-D2 to D4): one entry per valid row, fields in schema
 * order. Invalid rows are left out even if they come in. Leaves {@code out} open; on a failure it propagates the
 * exception and writes nothing that would make the file look complete.
 */
public interface ValidRowsExporter {

    ExportFormat format();

    String contentType();

    /** Appended to the original file name, such as {@code -valid.json}. */
    String fileSuffix();

    void write(List<TargetField> fields, Stream<RowResult> rows, OutputStream out) throws IOException;
}
