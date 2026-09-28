package com.universaldatatools.core.format.xlsx;

import com.universaldatatools.core.table.SourceFileType;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.ColumnNames;
import com.universaldatatools.core.table.ImportRow;
import com.universaldatatools.core.table.SourceColumn;
import com.universaldatatools.core.table.SourceParser;
import com.universaldatatools.core.table.SourceSchema;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.ReadingOptions;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.dhatim.fastexcel.reader.SheetVisibility;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * XLSX as agreed for V0.1 (design D9, X3, X5, X6): the first visible sheet, header on row 1, the sheet's own
 * row numbers, and cell values converted to the same neutral strings as CSV.
 */
@Component
public class XlsxSourceParser implements SourceParser {

    private final XlsxZipGuard guard;

    public XlsxSourceParser(XlsxZipGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean supports(SourceFileType type) {
        return type == SourceFileType.XLSX;
    }

    /** Scans for zip bombs first, then parses: two passes, hence the temporary copy. */
    @Override
    public SourceSchema inspect(InputStream input) {
        Path file = spool(input);
        try {
            try (InputStream scan = Files.newInputStream(file)) {
                guard.check(scan);
            }
            try (Reading reading = Reading.open(file); Stream<ImportRow> rows = reading.rows()) {
                return new SourceSchema(reading.columns(), rows.count(), reading.sheetName());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            delete(file);
        }
    }

    /** No zip bomb scan: the file already passed {@link #inspect} at upload. */
    @Override
    public Stream<ImportRow> read(InputStream input) {
        Path file = spool(input);
        try {
            Reading reading = Reading.open(file);
            return reading.rows().onClose(() -> {
                reading.close();
                delete(file);
            });
        } catch (RuntimeException e) {
            delete(file);
            throw e;
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

    /** One pass over the first visible sheet: the header is read on open, data rows are streamed lazily. */
    private static final class Reading implements AutoCloseable {

        private final ReadableWorkbook workbook;
        private final boolean date1904;
        private final String sheetName;
        private final Stream<Row> sheetRows;
        private final Iterator<Row> rowIterator;
        private final List<SourceColumn> columns;

        private Reading(ReadableWorkbook workbook) {
            this.workbook = workbook;
            this.date1904 = workbook.isDate1904();
            Sheet sheet = workbook.getSheets()
                    .filter(candidate -> candidate.getVisibility() == SheetVisibility.VISIBLE)
                    .findFirst()
                    .orElseThrow(() -> new DomainException(ErrorCode.FILE_EMPTY, "Workbook has no visible sheet."));
            this.sheetName = sheet.getName();
            this.sheetRows = openRows(sheet);
            this.rowIterator = sheetRows.iterator();
            Row header = nextRow();
            if (header == null || header.getRowNum() != 1) {
                throw noHeader();
            }
            List<String> headerValues = values(header, header.getCellCount());
            if (ColumnNames.isBlankRow(headerValues)) {
                throw noHeader();
            }
            this.columns = ColumnNames.normalize(headerValues);
        }

        static Reading open(Path file) {
            ReadableWorkbook workbook;
            try {
                workbook = new ReadableWorkbook(file.toFile(), new ReadingOptions(true, false));
            } catch (IOException | RuntimeException e) {
                throw XlsxZipGuard.notAWorkbook();
            }
            try {
                return new Reading(workbook);
            } catch (RuntimeException e) {
                closeWorkbook(workbook);
                throw e;
            }
        }

        String sheetName() {
            return sheetName;
        }

        List<SourceColumn> columns() {
            return columns;
        }

        Stream<ImportRow> rows() {
            Iterator<ImportRow> rows = new Iterator<>() {
                private ImportRow next;

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
                public ImportRow next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    ImportRow row = next;
                    next = null;
                    return row;
                }
            };
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(rows, Spliterator.ORDERED), false);
        }

        /** Returns {@code null} for a blank row: it is skipped but its row number stays used. */
        private ImportRow toRow(Row row) {
            List<String> values = values(row, columns.size());
            return ColumnNames.isBlankRow(values) ? null : new ImportRow(row.getRowNum(), values);
        }

        private List<String> values(Row row, int width) {
            List<String> values = new ArrayList<>(width);
            for (int i = 0; i < width; i++) {
                values.add(i < row.getCellCount() ? text(row.getCell(i)) : null);
            }
            return values;
        }

        /** Cell → neutral string (design X3). */
        private String text(Cell cell) {
            if (cell == null) {
                return null;
            }
            return switch (cell.getType()) {
                case EMPTY -> null;
                case STRING, ERROR -> emptyToNull(cell.getRawValue());
                case NUMBER -> number(cell);
                case BOOLEAN -> Boolean.TRUE.equals(cell.getValue()) ? "TRUE" : "FALSE";
                case FORMULA -> formulaResult(cell);
            };
        }

        /** The cached result keeps its own type (spike C4): the raw value alone cannot tell TRUE from 1. */
        private String formulaResult(Cell cell) {
            return switch (cell.getValue()) {
                case BigDecimal ignored -> number(cell);
                case Boolean value -> value ? "TRUE" : "FALSE";
                case String value -> emptyToNull(value);
                case null, default -> null;
            };
        }

        private String number(Cell cell) {
            return XlsxCellValues.number(cell.getRawValue(), cell.getDataFormatId(), cell.getDataFormatString(),
                    date1904);
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

        private static String emptyToNull(String value) {
            return value == null || value.isEmpty() ? null : value;
        }

        private static DomainException noHeader() {
            return new DomainException(ErrorCode.FILE_EMPTY, "The first row must contain column headers.");
        }

        @Override
        public void close() {
            sheetRows.close();
            closeWorkbook(workbook);
        }

        private static void closeWorkbook(ReadableWorkbook workbook) {
            try {
                workbook.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
