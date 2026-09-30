package com.universaldatatools.tools.importer.domain.export;

import com.universaldatatools.tools.importer.domain.pipeline.RowResult;

import java.io.IOException;
import java.io.OutputStream;
import java.util.stream.Stream;

/** Writes one line per error of the invalid rows (design F10-D5). Leaves {@code out} open. */
public interface ErrorReportExporter {

    String contentType();

    /** Appended to the original file name, such as {@code -errors.csv}. */
    String fileSuffix();

    void write(Stream<RowResult> invalidRows, OutputStream out) throws IOException;
}
