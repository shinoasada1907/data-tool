package com.universaldatatools.tools.importer.infrastructure.export;

import com.universaldatatools.core.format.csv.CsvFormulaGuard;
import com.universaldatatools.tools.importer.domain.export.ErrorReportExporter;
import com.universaldatatools.tools.importer.domain.pipeline.ImportError;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * One CSV line per error (design F10-D5): UTF-8 BOM, a fixed header, rows by increasing number and errors in their
 * row's order. The columns the user controls ({@code fieldName}, {@code rule}, {@code message},
 * {@code sourceValue}) go through {@link CsvFormulaGuard}; the others hold only values the system generates.
 */
@Component
public class CsvErrorReportExporter implements ErrorReportExporter {

    private static final String[] HEADER =
            {"rowNumber", "fieldName", "stage", "rule", "step", "code", "message", "sourceValue"};

    @Override
    public String contentType() {
        return ExportStreams.CSV_CONTENT_TYPE;
    }

    @Override
    public String fileSuffix() {
        return "-errors.csv";
    }

    @Override
    public void write(java.util.stream.Stream<RowResult> invalidRows, OutputStream out) throws IOException {
        out.write(ExportStreams.BOM);
        CSVPrinter csv = new CSVPrinter(new OutputStreamWriter(ExportStreams.keepOpen(out), StandardCharsets.UTF_8),
                CSVFormat.RFC4180);
        csv.printRecord((Object[]) HEADER);
        Iterator<RowResult> rows = invalidRows.iterator();
        while (rows.hasNext()) {
            for (ImportError error : rows.next().errors()) {
                csv.printRecord(error.rowNumber(), CsvFormulaGuard.escape(error.fieldName()), error.stage().name(),
                        CsvFormulaGuard.escape(error.rule()), error.step(), error.code().name(),
                        CsvFormulaGuard.escape(error.message()), CsvFormulaGuard.escape(error.sourceValue()));
            }
        }
        csv.flush();
    }
}
