package com.universaldatatools.core.format.csv;

import com.universaldatatools.core.table.SourceFileType;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.ColumnNames;
import com.universaldatatools.core.table.ImportRow;
import com.universaldatatools.core.table.SourceColumn;
import com.universaldatatools.core.table.SourceParser;
import com.universaldatatools.core.table.SourceSchema;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * CSV as agreed for V0.1 (design D9, P5): UTF-8 only, comma separated, RFC 4180 quoting,
 * header on row 1, row numbers as a spreadsheet shows them.
 */
public class CsvSourceParser implements SourceParser {

    /** Empty lines stay records so that record numbers keep matching spreadsheet row numbers. */
    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder().setIgnoreEmptyLines(false).get();

    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

    /**
     * Malformed bytes are decoded to U+FFFD and detected per record. Failing inside the decoder instead
     * would report the position of an 8KB read buffer rather than the row that holds the bad bytes.
     */
    private static final char REPLACEMENT_CHARACTER = (char) 0xFFFD;

    @Override
    public boolean supports(SourceFileType type) {
        return type == SourceFileType.CSV;
    }

    @Override
    public SourceSchema inspect(InputStream input) {
        try (Reading reading = Reading.open(input); Stream<ImportRow> rows = reading.rows()) {
            return new SourceSchema(reading.columns(), rows.count(), null);
        }
    }

    @Override
    public Stream<ImportRow> read(InputStream input) {
        Reading reading = Reading.open(input);
        return reading.rows().onClose(reading::close);
    }

    /** One pass over a CSV file: the header is read on open, data rows are streamed lazily. */
    private static final class Reading implements AutoCloseable {

        private final CSVParser parser;
        private final Iterator<CSVRecord> records;
        private final List<SourceColumn> columns;
        private long lastRowNumber;

        private Reading(CSVParser parser) {
            this.parser = parser;
            this.records = parser.iterator();
            CSVRecord header = nextRecord();
            if (header == null) {
                throw noHeader();
            }
            List<String> headerValues = cells(header);
            if (!headerValues.isEmpty() && headerValues.getFirst() != null
                    && headerValues.getFirst().charAt(0) == BYTE_ORDER_MARK) {
                headerValues.set(0, headerValues.getFirst().substring(1));
            }
            if (ColumnNames.isBlankRow(headerValues)) {
                throw noHeader();
            }
            this.columns = ColumnNames.normalize(headerValues);
        }

        static Reading open(InputStream input) {
            try {
                InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE));
                CSVParser parser = FORMAT.parse(reader);
                try {
                    return new Reading(parser);
                } catch (RuntimeException e) {
                    closeParser(parser);
                    throw e;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
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
                        CSVRecord record = nextRecord();
                        if (record == null) {
                            return false;
                        }
                        next = toRow(record);
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
        private ImportRow toRow(CSVRecord record) {
            List<String> values = cells(record);
            if (ColumnNames.isBlankRow(values)) {
                return null;
            }
            List<String> sized = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                sized.add(i < values.size() ? values.get(i) : null);
            }
            return new ImportRow(record.getRecordNumber(), sized);
        }

        private List<String> cells(CSVRecord record) {
            lastRowNumber = record.getRecordNumber();
            List<String> values = new ArrayList<>(record.size());
            for (String value : record) {
                if (value.indexOf(REPLACEMENT_CHARACTER) >= 0) {
                    throw new DomainException(ErrorCode.FILE_PARSE_ERROR,
                            "File is not valid UTF-8 (near row " + lastRowNumber + ").");
                }
                values.add(value.isEmpty() ? null : value);
            }
            return values;
        }

        private CSVRecord nextRecord() {
            try {
                return records.hasNext() ? records.next() : null;
            } catch (UncheckedIOException e) {
                // The message names the row only: cell values must never reach it (design D13).
                throw new DomainException(ErrorCode.FILE_PARSE_ERROR,
                        "CSV syntax error near row " + (lastRowNumber + 1) + ".");
            }
        }

        private static DomainException noHeader() {
            return new DomainException(ErrorCode.FILE_EMPTY, "The first row must contain column headers.");
        }

        @Override
        public void close() {
            closeParser(parser);
        }

        private static void closeParser(CSVParser parser) {
            try {
                parser.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
