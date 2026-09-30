package com.universaldatatools.core.format.xlsx;

import com.universaldatatools.core.format.KeepOpenOutputStream;
import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.CellTyping;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.OutputColumn;
import com.universaldatatools.core.table.RowSink;
import com.universaldatatools.core.table.TableWriter;
import com.universaldatatools.core.table.TypedCell;
import com.universaldatatools.core.table.WriteOptions;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * One sheet, header on row 1, streamed with fastexcel so memory stays low (core-02 IO9). Text is always written as
 * text, never as a formula. A number keeps its type only when Excel can hold it exactly (15 significant digits);
 * otherwise it is written as text so no digit is lost.
 */
public final class XlsxTableWriter implements TableWriter {

    /** Data rows an XLSX sheet can hold under its header. */
    public static final int MAX_ROWS = 1_048_575;
    /** Characters an XLSX cell can hold. */
    public static final int MAX_CELL_LENGTH = 32_767;

    private static final int FLUSH_EVERY = 1_000;
    private static final int MAX_EXACT_DIGITS = 15;

    @Override
    public DataFormat format() {
        return DataFormat.XLSX;
    }

    @Override
    public String contentType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public String extension() {
        return "xlsx";
    }

    @Override
    public RowSink open(OutputStream out, List<OutputColumn> columns, WriteOptions options) {
        Workbook workbook = new Workbook(new KeepOpenOutputStream(out), "Universal Data Tools", "1.0");
        Worksheet sheet = workbook.newWorksheet(sheetName(options.sheetName()));
        for (int c = 0; c < columns.size(); c++) {
            sheet.value(0, c, columns.get(c).name());
        }
        return new RowSink() {
            private int row;

            @Override
            public void write(List<TypedCell> cells) throws IOException {
                row++;
                if (row > MAX_ROWS) {
                    throw new IllegalStateException("More than " + MAX_ROWS + " rows for one XLSX sheet");
                }
                for (int c = 0; c < cells.size(); c++) {
                    TypedCell cell = cells.get(c);
                    writeCell(sheet, row, c, cell.text(),
                            CellTyping.resolve(cell, options.typing(), columns.get(c).profile()));
                }
                if (row % FLUSH_EVERY == 0) {
                    sheet.flush();
                }
            }

            @Override
            public void close() throws IOException {
                workbook.finish();
            }

            @Override
            public void abort() {
                // Not finished: without its closing parts the file is not a valid workbook.
            }
        };
    }

    /** Excel refuses {@code []:*?/\} and names over 31 characters; an empty name becomes {@code Sheet1}. */
    static String sheetName(String requested) {
        String name = requested == null ? "" : requested.replaceAll("[\\[\\]:*?/\\\\]", "_");
        if (name.length() > 31) {
            name = name.substring(0, 31);
        }
        return name.isBlank() ? "Sheet1" : name;
    }

    private static void writeCell(Worksheet sheet, int row, int column, String text, CellKind kind) {
        if (kind == null) {
            return;
        }
        if (text.length() > MAX_CELL_LENGTH) {
            throw new IllegalStateException("A cell longer than " + MAX_CELL_LENGTH + " characters");
        }
        switch (kind) {
            case NUMBER -> {
                BigDecimal number = exactNumber(text);
                if (number != null) {
                    sheet.value(row, column, number);
                } else {
                    sheet.value(row, column, text);
                }
            }
            case BOOLEAN -> {
                String lower = text.toLowerCase(Locale.ROOT);
                if (lower.equals("true") || lower.equals("1")) {
                    sheet.value(row, column, true);
                } else if (lower.equals("false") || lower.equals("0")) {
                    sheet.value(row, column, false);
                } else {
                    sheet.value(row, column, text);
                }
            }
            case DATE -> writeDate(sheet, row, column, text);
            case TEXT -> sheet.value(row, column, text);
        }
    }

    /** The number, or {@code null} when it is not one or Excel would round it. */
    private static BigDecimal exactNumber(String text) {
        try {
            BigDecimal number = new BigDecimal(text);
            return number.stripTrailingZeros().precision() <= MAX_EXACT_DIGITS ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void writeDate(Worksheet sheet, int row, int column, String text) {
        try {
            if (text.length() == 10) {
                sheet.value(row, column, LocalDate.parse(text));
                sheet.style(row, column).format("yyyy-mm-dd").set();
            } else {
                sheet.value(row, column, LocalDateTime.parse(text));
                sheet.style(row, column).format("yyyy-mm-dd hh:mm:ss").set();
            }
        } catch (DateTimeException e) {
            sheet.value(row, column, text);
        }
    }
}
