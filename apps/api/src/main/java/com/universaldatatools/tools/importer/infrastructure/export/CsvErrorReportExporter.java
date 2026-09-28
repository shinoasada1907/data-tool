package com.universaldatatools.tools.importer.infrastructure.export;

import com.universaldatatools.core.format.csv.CsvTableWriter;
import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.RowSink;
import com.universaldatatools.core.table.TypedCell;
import com.universaldatatools.core.table.WriteOptions;
import com.universaldatatools.tools.importer.domain.export.ErrorReportExporter;
import com.universaldatatools.tools.importer.domain.pipeline.ImportError;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

/**
 * One CSV line per error (design F10-D5): UTF-8 BOM, a fixed header, rows by increasing number and errors in their
 * row's order. Written by the shared {@link CsvTableWriter} (core-02 IO10): the text columns go through the formula
 * guard, which changes only what the user controls ({@code fieldName}, {@code rule}, {@code message},
 * {@code sourceValue}); the others hold values the system generates.
 */
@Component
public class CsvErrorReportExporter implements ErrorReportExporter {

    private static final List<String> HEADER =
            List.of("rowNumber", "fieldName", "stage", "rule", "step", "code", "message", "sourceValue");

    private final CsvTableWriter writer = new CsvTableWriter();

    @Override
    public String contentType() {
        return CsvTableWriter.CONTENT_TYPE;
    }

    @Override
    public String fileSuffix() {
        return "-errors.csv";
    }

    @Override
    public void write(Stream<RowResult> invalidRows, OutputStream out) throws IOException {
        RowSink sink = writer.open(out, ExportCells.columns(HEADER), WriteOptions.csvDefaults());
        Stream<List<TypedCell>> lines = invalidRows.flatMap(row -> row.errors().stream())
                .map(CsvErrorReportExporter::line);
        ExportCells.writeAll(sink, lines::iterator);
    }

    private static List<TypedCell> line(ImportError error) {
        return List.of(
                new TypedCell(String.valueOf(error.rowNumber()), CellKind.NUMBER),
                TypedCell.text(error.fieldName()),
                TypedCell.text(error.stage().name()),
                TypedCell.text(error.rule()),
                new TypedCell(error.step() == null ? null : error.step().toString(), CellKind.NUMBER),
                TypedCell.text(error.code().name()),
                TypedCell.text(error.message()),
                TypedCell.text(error.sourceValue()));
    }
}
