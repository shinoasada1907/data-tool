package com.universaldatatools.core.format.csv;

import com.universaldatatools.core.format.KeepOpenOutputStream;
import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.CellTyping;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.OutputColumn;
import com.universaldatatools.core.table.RowSink;
import com.universaldatatools.core.table.TableWriter;
import com.universaldatatools.core.table.TypedCell;
import com.universaldatatools.core.table.Typing;
import com.universaldatatools.core.table.WriteOptions;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 CSV in UTF-8, lines ending in CRLF (core-02 IO9). The formula guard prefixes {@code '} to the header and
 * to text cells only; numbers, booleans and dates are written as they are.
 */
public final class CsvTableWriter implements TableWriter {

    public static final String CONTENT_TYPE = "text/csv;charset=UTF-8";

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Override
    public DataFormat format() {
        return DataFormat.CSV;
    }

    @Override
    public String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    public String extension() {
        return "csv";
    }

    @Override
    public RowSink open(OutputStream out, List<OutputColumn> columns, WriteOptions options) throws IOException {
        if (options.bom()) {
            out.write(BOM);
        }
        CSVFormat format = CSVFormat.RFC4180.builder().setDelimiter(options.delimiter().symbol()).get();
        CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(new KeepOpenOutputStream(out), StandardCharsets.UTF_8), format);
        if (options.header()) {
            List<String> names = new ArrayList<>(columns.size());
            for (OutputColumn column : columns) {
                names.add(options.formulaGuard() ? CsvFormulaGuard.escape(column.name()) : column.name());
            }
            printer.printRecord(names);
        }
        return new RowSink() {
            @Override
            public void write(List<TypedCell> cells) throws IOException {
                List<String> values = new ArrayList<>(cells.size());
                for (int i = 0; i < cells.size(); i++) {
                    TypedCell cell = cells.get(i);
                    boolean text = CellTyping.resolve(cell, Typing.PRESERVE, columns.get(i).profile())
                            == CellKind.TEXT;
                    values.add(text && options.formulaGuard() ? CsvFormulaGuard.escape(cell.text()) : cell.text());
                }
                printer.printRecord(values);
            }

            /** Flushes only here, so a failure never completes the buffered part of the file. */
            @Override
            public void close() throws IOException {
                printer.flush();
            }

            @Override
            public void abort() {
                // Nothing flushed: the part still buffered is dropped with the printer.
            }
        };
    }
}
