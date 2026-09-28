package com.universaldatatools.core.format.xlsx;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.CellKinds;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.ColumnNames;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.ResolvedReadOptions;
import com.universaldatatools.core.table.SheetInfo;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.table.TableReader;
import com.universaldatatools.core.table.TableScan;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.ReadingOptions;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.dhatim.fastexcel.reader.SheetVisibility;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * XLSX with a chosen sheet (first visible by default), with or without a header row, cells as the V0.1 strings plus
 * their kind (core-02 IO5). Row numbers are the sheet's; blank and missing rows keep their numbers and are counted.
 * Every inspection scans for zip bombs first, hence the temporary copy.
 */
public final class XlsxTableReader implements TableReader {

    private final XlsxZipGuard guard;

    public XlsxTableReader(XlsxZipGuard guard) {
        this.guard = guard;
    }

    @Override
    public DataFormat format() {
        return DataFormat.XLSX;
    }

    @Override
    public List<SheetInfo> sheets(InputStream in) {
        Path file = spool(in);
        try {
            checkZip(file);
            ReadableWorkbook workbook = openWorkbook(file);
            try {
                return workbook.getSheets()
                        .map(sheet -> new SheetInfo(sheet.getName(), sheet.getVisibility() == SheetVisibility.VISIBLE))
                        .toList();
            } finally {
                closeWorkbook(workbook);
            }
        } finally {
            delete(file);
        }
    }

    @Override
    public TableInfo inspect(InputStream in, ReadOptions options) {
        Path file = spool(in);
        try {
            checkZip(file);
            try (Reading reading = Reading.open(file, options.sheet(), options.header(), null)) {
                TableScan scan = new TableScan(reading.columns(), options.limits());
                reading.forEachRow(scan::accept, scan::blank);
                ResolvedReadOptions resolved =
                        new ResolvedReadOptions(reading.sheetName(), null, null, options.header());
                return new TableInfo(DataFormat.XLSX, resolved, Set.of(), reading.sheetName(), reading.sheets(),
                        reading.columns(), null, scan.profiles(), scan.rowCount(), scan.blankRowsSkipped());
            }
        } finally {
            delete(file);
        }
    }

    /** No zip bomb scan: the file already passed {@link #inspect}. */
    @Override
    public Stream<com.universaldatatools.core.table.Row> read(InputStream in, TableInfo info) {
        Path file = spool(in);
        try {
            Reading reading = Reading.open(file, info.options().sheet(), info.options().hasHeader(), info.columns());
            return reading.rows().onClose(() -> {
                reading.close();
                delete(file);
            });
        } catch (RuntimeException e) {
            delete(file);
            throw e;
        }
    }

    private void checkZip(Path file) {
        try (InputStream scan = Files.newInputStream(file)) {
            guard.check(scan);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Copies the upload to a temporary file and closes the input, which is fully consumed. */
    private static Path spool(InputStream input) {
        try (InputStream in = input) {
            Path file = Files.createTempFile("xlsx-", ".xlsx");
            try {
                Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                return file;
            } catch (IOException e) {
                delete(file);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot buffer the uploaded workbook", e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete temporary workbook " + file, e);
        }
    }

    private static ReadableWorkbook openWorkbook(Path file) {
        try {
            return new ReadableWorkbook(file.toFile(), new ReadingOptions(true, false));
        } catch (IOException | RuntimeException e) {
            throw XlsxZipGuard.notAWorkbook();
        }
    }

    private static void closeWorkbook(ReadableWorkbook workbook) {
        try {
            workbook.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A cell as the neutral model sees it. */
    private record Value(String text, CellKind kind) {

        static final Value EMPTY = new Value(null, null);
    }

    /** One pass over a sheet: the header (or the first non-blank row) on open, then rows lazily. */
    private static final class Reading implements AutoCloseable {

        private final ReadableWorkbook workbook;
        private final boolean date1904;
        private final List<SheetInfo> sheets;
        private final String sheetName;
        private final Stream<Row> sheetRows;
        private final Iterator<Row> rowIterator;
        private final List<Column> columns;
        /** The first data row of a sheet without header, read ahead to count its columns; handed out first. */
        private com.universaldatatools.core.table.Row pending;
        private int pendingBlanks;
        /** Row number of the previous row of the sheet, to count the rows it leaves out as blank. */
        private int previousRowNumber;

        private Reading(ReadableWorkbook workbook, String sheetName, boolean hasHeader, List<Column> knownColumns) {
            this.workbook = workbook;
            this.date1904 = workbook.isDate1904();
            List<Sheet> all = workbook.getSheets().toList();
            this.sheets = all.stream()
                    .map(sheet -> new SheetInfo(sheet.getName(), sheet.getVisibility() == SheetVisibility.VISIBLE))
                    .toList();
            Sheet sheet = sheetName == null
                    ? all.stream().filter(candidate -> candidate.getVisibility() == SheetVisibility.VISIBLE)
                            .findFirst()
                            .orElseThrow(() -> new DomainException(ErrorCode.FILE_EMPTY,
                                    "Workbook has no visible sheet."))
                    : all.stream().filter(candidate -> candidate.getName().equals(sheetName)).findFirst()
                            .orElseThrow(() -> new DomainException(ErrorCode.CONFIG_INVALID,
                                    "Sheet \"" + sheetName + "\" does not exist."));
            this.sheetName = sheet.getName();
            this.sheetRows = openRows(sheet);
            this.rowIterator = sheetRows.iterator();
            if (knownColumns != null) {
                this.columns = knownColumns;
                if (hasHeader) {
                    nextRow();
                }
            } else if (hasHeader) {
                Row header = nextRow();
                if (header == null || header.getRowNum() != 1) {
                    throw noHeader();
                }
                List<String> names = texts(header, header.getCellCount());
                if (ColumnNames.isBlankRow(names)) {
                    throw noHeader();
                }
                this.columns = ColumnNames.normalize(names);
                this.previousRowNumber = 1;
            } else {
                Row first = null;
                for (Row row = nextRow(); row != null; row = nextRow()) {
                    if (!ColumnNames.isBlankRow(texts(row, row.getCellCount()))) {
                        first = row;
                        break;
                    }
                }
                if (first == null) {
                    throw new DomainException(ErrorCode.FILE_EMPTY, "The sheet has no rows.");
                }
                // Rows before the first one with data, missing or blank, are all blank rows.
                this.pendingBlanks = first.getRowNum() - 1;
                this.columns = ColumnNames.normalize(Collections.nCopies(first.getCellCount(), null));
                this.pending = toRow(first);
                this.previousRowNumber = first.getRowNum();
            }
        }

        static Reading open(Path file, String sheetName, boolean hasHeader, List<Column> knownColumns) {
            ReadableWorkbook workbook = openWorkbook(file);
            try {
                return new Reading(workbook, sheetName, hasHeader, knownColumns);
            } catch (RuntimeException e) {
                closeWorkbook(workbook);
                throw e;
            }
        }

        String sheetName() {
            return sheetName;
        }

        List<SheetInfo> sheets() {
            return sheets;
        }

        List<Column> columns() {
            return columns;
        }

        /** Every data row to {@code rows}; every blank or missing row between them to {@code blanks}. */
        void forEachRow(Consumer<com.universaldatatools.core.table.Row> rows, Runnable blanks) {
            for (int i = 0; i < pendingBlanks; i++) {
                blanks.run();
            }
            if (pending != null) {
                rows.accept(pending);
                pending = null;
            }
            for (Row row = nextRow(); row != null; row = nextRow()) {
                for (int missing = row.getRowNum() - previousRowNumber - 1; missing > 0; missing--) {
                    blanks.run();
                }
                previousRowNumber = row.getRowNum();
                com.universaldatatools.core.table.Row data = toRow(row);
                if (data == null) {
                    blanks.run();
                } else {
                    rows.accept(data);
                }
            }
        }

        Stream<com.universaldatatools.core.table.Row> rows() {
            Iterator<com.universaldatatools.core.table.Row> rows = new Iterator<>() {
                private com.universaldatatools.core.table.Row next = pending;

                @Override
                public boolean hasNext() {
                    while (next == null) {
                        Row row = nextRow();
                        if (row == null) {
                            return false;
                        }
                        next = toRow(row);
                    }
                    return true;
                }

                @Override
                public com.universaldatatools.core.table.Row next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    com.universaldatatools.core.table.Row row = next;
                    next = null;
                    return row;
                }
            };
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(rows, Spliterator.ORDERED), false);
        }

        /** {@code null} for a blank row: it is skipped but its row number stays used. */
        private com.universaldatatools.core.table.Row toRow(Row row) {
            List<String> texts = new ArrayList<>(columns.size());
            List<CellKind> kinds = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                Value value = i < row.getCellCount() ? value(row.getCell(i)) : Value.EMPTY;
                texts.add(value.text());
                kinds.add(value.kind());
            }
            return ColumnNames.isBlankRow(texts) ? null
                    : new com.universaldatatools.core.table.Row(row.getRowNum(), texts, CellKinds.of(kinds));
        }

        private List<String> texts(Row row, int width) {
            List<String> texts = new ArrayList<>(width);
            for (int i = 0; i < width; i++) {
                texts.add(i < row.getCellCount() ? value(row.getCell(i)).text() : null);
            }
            return texts;
        }

        /** Cell → neutral string (design X3) and its kind (core-02 IO5). */
        private Value value(Cell cell) {
            if (cell == null) {
                return Value.EMPTY;
            }
            return switch (cell.getType()) {
                case EMPTY -> Value.EMPTY;
                case STRING, ERROR -> text(cell.getRawValue());
                case NUMBER -> number(cell);
                case BOOLEAN -> bool(Boolean.TRUE.equals(cell.getValue()));
                case FORMULA -> formulaResult(cell);
            };
        }

        /** The cached result keeps its own type (spike C4): the raw value alone cannot tell TRUE from 1. */
        private Value formulaResult(Cell cell) {
            return switch (cell.getValue()) {
                case BigDecimal ignored -> number(cell);
                case Boolean value -> bool(value);
                case String value -> text(value);
                case null, default -> Value.EMPTY;
            };
        }

        private Value number(Cell cell) {
            String text = XlsxCellValues.number(cell.getRawValue(), cell.getDataFormatId(),
                    cell.getDataFormatString(), date1904);
            if (ExcelDateFormats.isTimeOnlyFormat(cell.getDataFormatId(), cell.getDataFormatString())) {
                return new Value(text, CellKind.TEXT);
            }
            return new Value(text, ExcelDateFormats.isDateFormat(cell.getDataFormatId(), cell.getDataFormatString())
                    ? CellKind.DATE : CellKind.NUMBER);
        }

        private static Value bool(boolean value) {
            return new Value(value ? "TRUE" : "FALSE", CellKind.BOOLEAN);
        }

        private static Value text(String value) {
            return value == null || value.isEmpty() ? Value.EMPTY : new Value(value, CellKind.TEXT);
        }

        private Row nextRow() {
            try {
                return rowIterator.hasNext() ? rowIterator.next() : null;
            } catch (DomainException e) {
                throw e;
            } catch (RuntimeException e) {
                // Malformed sheet XML; the message never carries cell values (design D13).
                throw XlsxZipGuard.notAWorkbook();
            }
        }

        private static Stream<Row> openRows(Sheet sheet) {
            try {
                return sheet.openStream();
            } catch (IOException e) {
                throw XlsxZipGuard.notAWorkbook();
            }
        }

        private static DomainException noHeader() {
            return new DomainException(ErrorCode.FILE_EMPTY, "The first row must contain column headers.");
        }

        @Override
        public void close() {
            sheetRows.close();
            closeWorkbook(workbook);
        }
    }
}
