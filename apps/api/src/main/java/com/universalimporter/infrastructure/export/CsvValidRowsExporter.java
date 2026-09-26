package com.universalimporter.infrastructure.export;

import com.universalimporter.domain.export.CsvFormulaGuard;
import com.universalimporter.domain.export.ExportFormat;
import com.universalimporter.domain.export.ValidRowsExporter;
import com.universalimporter.domain.pipeline.RowResult;
import com.universalimporter.domain.schema.FieldType;
import com.universalimporter.domain.schema.TargetField;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Valid rows as CSV (design F10-D4): UTF-8 BOM, RFC 4180 (CRLF, quotes only when needed), header in schema order.
 * Header cells and string/email cells go through {@link CsvFormulaGuard}; numbers, booleans and dates are validated
 * values and stay as they are, so {@code -5} is not changed.
 */
@Component
public class CsvValidRowsExporter implements ValidRowsExporter {

    @Override
    public ExportFormat format() {
        return ExportFormat.CSV;
    }

    @Override
    public String contentType() {
        return ExportStreams.CSV_CONTENT_TYPE;
    }

    @Override
    public String fileSuffix() {
        return "-valid.csv";
    }

    /** Flushes only on success, so a failure never completes the buffered part of the file. */
    @Override
    public void write(List<TargetField> fields, Stream<RowResult> rows, OutputStream out) throws IOException {
        out.write(ExportStreams.BOM);
        CSVPrinter csv = new CSVPrinter(new OutputStreamWriter(ExportStreams.keepOpen(out), StandardCharsets.UTF_8),
                CSVFormat.RFC4180);
        csv.printRecord(fields.stream().map(field -> CsvFormulaGuard.escape(field.name())).toList());
        Iterator<RowResult> valid = rows.filter(ExportStreams::isValid).iterator();
        while (valid.hasNext()) {
            RowResult row = valid.next();
            List<String> cells = new ArrayList<>(fields.size());
            for (TargetField field : fields) {
                cells.add(cell(field.type(), row.values().get(field.name())));
            }
            csv.printRecord(cells);
        }
        csv.flush();
    }

    private static String cell(FieldType type, Object value) {
        return switch (value) {
            case null -> null;
            case BigDecimal number -> number.toPlainString();
            case Number number -> new BigDecimal(number.toString()).toPlainString();
            case String text when type == FieldType.STRING || type == FieldType.EMAIL -> CsvFormulaGuard.escape(text);
            default -> value.toString();
        };
    }
}
