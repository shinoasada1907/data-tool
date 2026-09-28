package com.universaldatatools.tools.importer.domain.importsession;

import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Row;

import java.io.InputStream;
import java.util.stream.Stream;

/**
 * Reads one kind of source file into a neutral model (design P2). Parsers know nothing about
 * mapping or validation.
 */
public interface SourceParser {

    boolean supports(DataFormat type);

    /**
     * Reads the whole file once: checks its structure, names the columns and counts the data rows.
     * Fails with {@code DomainException} ({@code FILE_EMPTY} or {@code FILE_PARSE_ERROR}).
     */
    SourceSchema inspect(InputStream input);

    /**
     * Streams the non-blank data rows in file order. Closing the stream closes {@code input}.
     */
    Stream<Row> read(InputStream input);
}
