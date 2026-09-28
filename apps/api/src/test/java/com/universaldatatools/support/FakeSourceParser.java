package com.universaldatatools.support;

import com.universaldatatools.core.table.SourceFileType;
import com.universaldatatools.core.table.ImportRow;
import com.universaldatatools.core.table.SourceParser;
import com.universaldatatools.core.table.SourceSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

/** Test double for a parser of one file type: returns a fixed schema and rows, or fails. */
public class FakeSourceParser implements SourceParser {

    private final SourceFileType type;
    private SourceSchema schema;
    private RuntimeException failure;
    private List<ImportRow> rows = List.of();
    private byte[] inspectedContent;
    private boolean readStreamClosed;
    private long failAtRow = -1;
    private RuntimeException closeFailure;
    private RuntimeException readFailure;

    private FakeSourceParser(SourceFileType type) {
        this.type = type;
    }

    public static FakeSourceParser forType(SourceFileType type) {
        return new FakeSourceParser(type);
    }

    public FakeSourceParser returning(SourceSchema schema) {
        this.schema = schema;
        return this;
    }

    public FakeSourceParser failingWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    public FakeSourceParser withRows(List<ImportRow> rows) {
        this.rows = List.copyOf(rows);
        return this;
    }

    /** Closing the row stream throws {@code failure}, after every row was read. */
    public FakeSourceParser failingOnClose(RuntimeException failure) {
        this.closeFailure = failure;
        return this;
    }

    /** Reading the rows throws {@code failure} when it reaches row {@code rowNumber}. */
    public FakeSourceParser failingAtRow(long rowNumber, RuntimeException failure) {
        this.failAtRow = rowNumber;
        this.readFailure = failure;
        return this;
    }

    /** The bytes {@link #inspect} was given, to check which copy of the file was read. */
    public byte[] inspectedContent() {
        return inspectedContent;
    }

    public boolean readStreamClosed() {
        return readStreamClosed;
    }

    @Override
    public boolean supports(SourceFileType type) {
        return this.type == type;
    }

    @Override
    public SourceSchema inspect(InputStream input) {
        try {
            inspectedContent = input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (failure != null) {
            throw failure;
        }
        return schema;
    }

    @Override
    public Stream<ImportRow> read(InputStream input) {
        return rows.stream()
                .peek(row -> {
                    if (row.rowNumber() == failAtRow) {
                        throw readFailure;
                    }
                })
                .onClose(() -> {
                    readStreamClosed = true;
                    if (closeFailure != null) {
                        throw closeFailure;
                    }
                });
    }
}
