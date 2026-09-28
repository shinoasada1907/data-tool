package com.universaldatatools.tools.importer.infrastructure.export;

import com.universaldatatools.core.format.json.JsonTableWriter;
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
 * Valid rows as a JSON array (design F10-D3), written by the shared {@link JsonTableWriter} (core-02 IO10): numbers
 * are plain, keys follow the schema whatever the order of the stored values, and JSON is never escaped against
 * formulas.
 */
@Component
public class JsonValidRowsExporter implements ValidRowsExporter {

    private final JsonTableWriter writer = new JsonTableWriter();

    @Override
    public ExportFormat format() {
        return ExportFormat.JSON;
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public String fileSuffix() {
        return "-valid.json";
    }

    /** Ends the array only on success: a failed export must not become a well-formed file with rows missing. */
    @Override
    public void write(List<TargetField> fields, Stream<RowResult> rows, OutputStream out) throws IOException {
        RowSink sink = writer.open(out, ExportCells.columns(fields.stream().map(TargetField::name).toList()),
                WriteOptions.jsonDefaults());
        Stream<List<TypedCell>> cells = rows.filter(ExportStreams::isValid)
                .map(row -> fields.stream().map(field -> ExportCells.of(row.values().get(field.name()))).toList());
        ExportCells.writeAll(sink, cells::iterator);
    }
}
