package com.universaldatatools.tools.importer.infrastructure.export;

import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.OutputColumn;
import com.universaldatatools.core.table.RowSink;
import com.universaldatatools.core.table.TypedCell;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The importer's converted values as cells for the shared table writers (core-02 IO10). */
final class ExportCells {

    private ExportCells() {
    }

    /** A validated value keeps its type; anything else is text. */
    static TypedCell of(Object value) {
        return switch (value) {
            case null -> new TypedCell(null, null);
            case BigDecimal number -> new TypedCell(number.toPlainString(), CellKind.NUMBER);
            case Number number -> new TypedCell(new BigDecimal(number.toString()).toPlainString(), CellKind.NUMBER);
            case Boolean bool -> new TypedCell(bool.toString(), CellKind.BOOLEAN);
            case LocalDate date -> new TypedCell(date.toString(), CellKind.DATE);
            default -> TypedCell.text(value.toString());
        };
    }

    static List<OutputColumn> columns(List<String> names) {
        return names.stream().map(name -> new OutputColumn(name, null)).toList();
    }

    /** Writes the rows, completing the file only when every row made it; otherwise it stays unfinished. */
    static void writeAll(RowSink sink, Iterable<List<TypedCell>> rows) throws IOException {
        try {
            for (List<TypedCell> row : rows) {
                sink.write(row);
            }
        } catch (IOException | RuntimeException e) {
            sink.abort();
            throw e;
        }
        sink.close();
    }
}
