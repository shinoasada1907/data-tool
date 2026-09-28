package com.universaldatatools.tools.importer.infrastructure.parser;

import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.ReadLimits;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.ResolvedReadOptions;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.table.TableReader;
import com.universaldatatools.core.table.TextEncoding;
import com.universaldatatools.tools.importer.domain.importsession.SourceParser;
import com.universaldatatools.tools.importer.domain.importsession.SourceSchema;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

/**
 * The importer's source files, read by a shared {@link TableReader} with the V0.1 rules (core-02 IO10): UTF-8, comma,
 * header on row 1, first visible sheet, no limits. Nothing is detected, so files read exactly as before.
 */
public final class TableSourceParser implements SourceParser {

    static final ReadOptions V0_1 = new ReadOptions(null, Delimiter.COMMA, TextEncoding.UTF_8, true, ReadLimits.NONE);

    private final TableReader reader;
    private final ResolvedReadOptions resolved;

    public TableSourceParser(TableReader reader) {
        this.reader = reader;
        this.resolved = reader.format() == DataFormat.CSV
                ? new ResolvedReadOptions(null, Delimiter.COMMA, TextEncoding.UTF_8, true)
                : new ResolvedReadOptions(null, null, null, true);
    }

    @Override
    public boolean supports(DataFormat type) {
        return type == reader.format();
    }

    @Override
    public SourceSchema inspect(InputStream input) {
        TableInfo info = reader.inspect(input, V0_1);
        return new SourceSchema(info.columns(), info.rowCount(), info.sheetName());
    }

    /** The header is read again from the file, as V0.1 did; no columns are passed in. */
    @Override
    public Stream<Row> read(InputStream input) {
        return reader.read(input, TableInfo.forRead(reader.format(), resolved, List.of()));
    }
}
