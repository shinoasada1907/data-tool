package com.universaldatatools.tools.importer.infrastructure.export;

import com.universaldatatools.core.format.csv.CsvTableWriter;
import com.universaldatatools.core.table.RowSink;
import com.universaldatatools.core.table.TypedCell;
import com.universaldatatools.core.table.WriteOptions;
import com.universaldatatools.tools.importer.domain.export.ExportFormat;
import com.universaldatatools.tools.importer.domain.export.ValidRowsExporter;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import com.universaldatatools.tools.importer.domain.schema.TargetField;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

/**
 * Valid rows as CSV (design F10-D4): UTF-8 BOM, RFC 4180 (CRLF, quotes only when needed), header in schema order.
 * Written by the shared {@link CsvTableWriter} (core-02 IO10): the header and text cells go through the formula
 * guard; numbers, booleans and dates are validated values and stay as they are, so {@code -5} is not changed.
 */
@Component
public class CsvValidRowsExporter implements ValidRowsExporter {

    private final CsvTableWriter writer = new CsvTableWriter();

    @Override
    public ExportFormat format() {
        return ExportFormat.CSV;
    }

    @Override
    public String contentType() {
        return CsvTableWriter.CONTENT_TYPE;
    }

    @Override
    public String fileSuffix() {
        return "-valid.csv";
    }

    /** Completes the file only on success, so a failure never looks like a whole export. */
    @Override
    public void write(List<TargetField> fields, Stream<RowResult> rows, OutputStream out) throws IOException {
        RowSink sink = writer.open(out, ExportCells.columns(fields.stream().map(TargetField::name).toList()),
                WriteOptions.csvDefaults());
        Stream<List<TypedCell>> cells = rows.filter(ExportStreams::isValid)
                .map(row -> fields.stream().map(field -> ExportCells.of(row.values().get(field.name()))).toList());
        ExportCells.writeAll(sink, cells::iterator);
    }
}
