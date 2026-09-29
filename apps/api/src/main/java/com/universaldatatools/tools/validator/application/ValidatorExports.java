package com.universaldatatools.tools.validator.application;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.TextValues;
import com.universaldatatools.core.format.xlsx.XlsxTableWriter;
import com.universaldatatools.core.schema.Converted;
import com.universaldatatools.core.schema.SchemaField;
import com.universaldatatools.core.schema.TypeConverter;
import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.OutputColumn;
import com.universaldatatools.core.table.RowSink;
import com.universaldatatools.core.table.TableWriter;
import com.universaldatatools.core.table.TypedCell;
import com.universaldatatools.platform.output.DownloadNames;
import com.universaldatatools.platform.output.Downloads;
import com.universaldatatools.platform.output.OutputDto;
import com.universaldatatools.platform.output.OutputSpec;
import com.universaldatatools.platform.run.RunStore;
import com.universaldatatools.tools.validator.application.ValidatorRecords.ErrorRecord;
import com.universaldatatools.tools.validator.application.ValidatorRecords.RowRecord;
import com.universaldatatools.tools.validator.application.ValidatorService.ValidatorRun;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Files from a validator run (tool-05 VD6): the valid rows typed by the schema, the invalid rows with their errors,
 * or the error list. Every check happens in {@link #prepare}, before a byte is written.
 */
@Service
public class ValidatorExports {

    public enum Content { VALID, INVALID, ERRORS }

    private static final List<String> ERROR_COLUMNS = List.of("row", "field", "code", "rule", "message", "value");
    /** Longest text of the fixed columns (a message or code), well below what any cell holds. */
    private static final int FIXED_CELL_LENGTH = 1_000;

    private final ValidatorService service;
    private final RunStore runs;
    private final Map<DataFormat, TableWriter> writers;
    private final TypeConverter converter = new TypeConverter();

    public ValidatorExports(ValidatorService service, RunStore runs, List<TableWriter> writers) {
        this.service = service;
        this.runs = runs;
        this.writers = writers.stream().collect(Collectors.toUnmodifiableMap(TableWriter::format, Function.identity()));
    }

    public record Prepared(String fileName, String contentType, Downloads.BodyWriter body) {
    }

    /**
     * @throws DomainException {@code REQUEST_INVALID} for a wrong content, {@code CONFIG_INVALID} for a wrong output,
     *                         {@code LIMIT_EXCEEDED} when XLSX cannot hold the file, {@code RUN_NOT_FOUND}
     */
    public Prepared prepare(UUID id, String content, OutputDto output) {
        Content parsed = parseContent(content);
        String suffix = parsed.name().toLowerCase(Locale.ROOT);
        OutputSpec spec = OutputSpec.from(output, suffix);
        ValidatorRun run = service.get(id);
        List<SchemaField> fields = run.config().schema().fields();
        if (spec.format() == DataFormat.XLSX) {
            checkXlsx(run, parsed);
        }
        TableWriter writer = writers.get(spec.format());
        String fileName = DownloadNames.of(run.record().sources().get(0).fileName(), "-" + suffix + "." + writer.extension());
        List<OutputColumn> columns = columns(parsed, fields);
        String section = parsed == Content.VALID ? ValidatorRecords.VALID : ValidatorRecords.INVALID;
        Downloads.BodyWriter body = out -> {
            RowSink sink = writer.open(out, columns, spec.options());
            try (Stream<RowRecord> rows = runs.read(id, section, RowRecord.class, 0)) {
                Iterator<RowRecord> iterator = rows.iterator();
                while (iterator.hasNext()) {
                    for (List<TypedCell> cells : cells(parsed, iterator.next(), fields)) {
                        sink.write(cells);
                    }
                }
            } catch (IOException | RuntimeException e) {
                sink.abort();
                throw e;
            }
            sink.close();
        };
        return new Prepared(fileName, writer.contentType(), body);
    }

    private static Content parseContent(String content) {
        try {
            return Content.valueOf(content == null ? "" : content.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, "content must be VALID, INVALID or ERRORS.");
        }
    }

    private static void checkXlsx(ValidatorRun run, Content content) {
        long rows = switch (content) {
            case VALID -> run.summary().validRows();
            case INVALID -> run.summary().invalidRows();
            case ERRORS -> run.summary().errorCount();
        };
        if (rows > XlsxTableWriter.MAX_ROWS) {
            throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "XLSX holds at most " + XlsxTableWriter.MAX_ROWS
                    + " rows; this file would have " + rows + ".");
        }
        int longest = Math.max(FIXED_CELL_LENGTH, run.config().maxLengths().stream().mapToInt(Integer::intValue).max().orElse(0));
        if (longest > XlsxTableWriter.MAX_CELL_LENGTH) {
            throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "A value is longer than "
                    + XlsxTableWriter.MAX_CELL_LENGTH + " characters, more than an XLSX cell holds.");
        }
    }

    private static List<OutputColumn> columns(Content content, List<SchemaField> fields) {
        List<String> names = new ArrayList<>();
        switch (content) {
            case VALID -> fields.forEach(field -> names.add(field.name()));
            case INVALID -> {
                names.add("_row");
                fields.forEach(field -> names.add(field.name()));
                names.add("_errors");
            }
            case ERRORS -> names.addAll(ERROR_COLUMNS);
        }
        return names.stream().map(name -> new OutputColumn(name, null)).toList();
    }

    /** The file rows one stored row becomes: one, or one per error. */
    private List<List<TypedCell>> cells(Content content, RowRecord row, List<SchemaField> fields) {
        return switch (content) {
            case VALID -> List.of(typed(row, fields));
            case INVALID -> List.of(invalid(row));
            case ERRORS -> errors(row, fields);
        };
    }

    /** Valid values in the schema's types (core-01 TD7): the type rule already accepted each of them. */
    private List<TypedCell> typed(RowRecord row, List<SchemaField> fields) {
        List<TypedCell> cells = new ArrayList<>(fields.size());
        for (int f = 0; f < fields.size(); f++) {
            String value = row.values().get(f);
            if (TextValues.isEmpty(value)) {
                cells.add(new TypedCell(null, null));
                continue;
            }
            cells.add(switch (converter.convert(value, fields.get(f))) {
                case Converted.Ok(BigDecimal number) -> new TypedCell(value, CellKind.NUMBER);
                case Converted.Ok(Boolean bool) -> new TypedCell(bool.toString(), CellKind.BOOLEAN);
                case Converted.Ok(LocalDate date) -> new TypedCell(date.toString(), CellKind.DATE);
                case Converted.Ok ok -> new TypedCell(value, CellKind.TEXT);
                case Converted.Failed failed -> new TypedCell(value, CellKind.TEXT);
            });
        }
        return cells;
    }

    private static List<TypedCell> invalid(RowRecord row) {
        List<TypedCell> cells = new ArrayList<>(row.values().size() + 2);
        cells.add(new TypedCell(Long.toString(row.rowNumber()), CellKind.NUMBER));
        row.values().forEach(value -> cells.add(new TypedCell(value, CellKind.TEXT)));
        String errors = row.errors().stream().map(error -> error.field() + ": " + error.message())
                .collect(Collectors.joining("; "));
        cells.add(new TypedCell(errors, CellKind.TEXT));
        return cells;
    }

    private static List<List<TypedCell>> errors(RowRecord row, List<SchemaField> fields) {
        if (row.errors().isEmpty()) {
            return Collections.emptyList();
        }
        List<List<TypedCell>> lines = new ArrayList<>(row.errors().size());
        for (ErrorRecord error : row.errors()) {
            String value = null;
            for (int f = 0; f < fields.size(); f++) {
                if (fields.get(f).name().equals(error.field())) {
                    value = row.values().get(f);
                    break;
                }
            }
            lines.add(List.of(new TypedCell(Long.toString(row.rowNumber()), CellKind.NUMBER),
                    new TypedCell(error.field(), CellKind.TEXT), new TypedCell(error.code(), CellKind.TEXT),
                    new TypedCell(error.rule(), CellKind.TEXT), new TypedCell(error.message(), CellKind.TEXT),
                    new TypedCell(value, CellKind.TEXT)));
        }
        return lines;
    }
}
