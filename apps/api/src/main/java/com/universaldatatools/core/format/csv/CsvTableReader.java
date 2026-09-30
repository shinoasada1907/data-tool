package com.universaldatatools.core.format.csv;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.ColumnNames;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.ResolvedReadOptions;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.SheetInfo;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.table.TableReader;
import com.universaldatatools.core.table.TableScan;
import com.universaldatatools.core.table.TextEncoding;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
 * CSV with a chosen or detected encoding and delimiter, RFC 4180 quoting, with or without a header row (core-02
 * IO2–IO4). Row numbers are the file's record numbers, as a spreadsheet shows them; blank rows are skipped but keep
 * their numbers. The Importer reads with UTF-8, comma and a header, exactly as V0.1 did.
 */
public final class CsvTableReader implements TableReader {

    /** How much text, at most, the delimiter is detected on. */
    static final int SAMPLE_CHARS = 64 * 1024;

    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

    /**
     * Malformed bytes are decoded to U+FFFD and detected per record. Failing inside the decoder instead
     * would report the position of an 8KB read buffer rather than the row that holds the bad bytes.
     */
    private static final char REPLACEMENT_CHARACTER = (char) 0xFFFD;

    @Override
    public DataFormat format() {
        return DataFormat.CSV;
    }

    @Override
    public List<SheetInfo> sheets(InputStream in) {
        return List.of();
    }

    @Override
    public TableInfo inspect(InputStream in, ReadOptions options) {
        Set<String> autoDetected = new HashSet<>();
        Text text = Text.open(in, options.encoding(), autoDetected);
        Delimiter delimiter = options.delimiter();
        if (delimiter == null) {
            delimiter = DelimiterDetector.detect(text.sample());
            autoDetected.add("delimiter");
        }
        ResolvedReadOptions resolved = new ResolvedReadOptions(null, delimiter, text.encoding(), options.header());
        try (Reading reading = Reading.open(text, resolved, null)) {
            TableScan scan = new TableScan(reading.columns(), options.limits());
            reading.forEachRecord(scan::accept, scan::blank);
            return new TableInfo(DataFormat.CSV, resolved, autoDetected, null, List.of(), reading.columns(), null,
                    scan.profiles(), scan.rowCount(), scan.blankRowsSkipped());
        }
    }

    @Override
    public Stream<Row> read(InputStream in, TableInfo info) {
        Text text = Text.open(in, info.options().encoding(), new HashSet<>());
        Reading reading = Reading.open(text, info.options(), info.columns().isEmpty() ? null : info.columns());
        return reading.rows().onClose(reading::close);
    }

    /** The decoded file, with the byte order mark removed and the encoding it was read in. */
    private record Text(BufferedReader reader, TextEncoding encoding, boolean detected) {

        static Text open(InputStream in, TextEncoding requested, Set<String> autoDetected) {
            try {
                BufferedInputStream bytes = new BufferedInputStream(in);
                bytes.mark(3);
                byte[] head = bytes.readNBytes(3);
                bytes.reset();
                boolean utf16Bom = head.length >= 2 && (head[0] == (byte) 0xFF && head[1] == (byte) 0xFE
                        || head[0] == (byte) 0xFE && head[1] == (byte) 0xFF);
                boolean utf8Bom = head.length == 3 && head[0] == (byte) 0xEF && head[1] == (byte) 0xBB
                        && head[2] == (byte) 0xBF;
                TextEncoding encoding = requested;
                boolean detected = false;
                if (encoding == null) {
                    encoding = utf16Bom ? TextEncoding.UTF_16 : TextEncoding.UTF_8;
                    detected = !utf16Bom && !utf8Bom;
                    if (detected) {
                        autoDetected.add("encoding");
                    }
                } else if (encoding == TextEncoding.UTF_16 && !utf16Bom) {
                    throw new DomainException(ErrorCode.FILE_PARSE_ERROR,
                            "UTF-16 file must start with a byte order mark.");
                }
                Reader decoded = new InputStreamReader(bytes, encoding.charset().newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE));
                BufferedReader reader = new BufferedReader(decoded, SAMPLE_CHARS);
                reader.mark(1);
                if (reader.read() != BYTE_ORDER_MARK) {
                    reader.reset();
                }
                return new Text(reader, encoding, detected);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Up to {@link #SAMPLE_CHARS} characters from the start, cut after the last full line; the reader stays where
         * it was.
         */
        String sample() {
            try {
                reader.mark(SAMPLE_CHARS + 1);
                char[] buffer = new char[SAMPLE_CHARS];
                int length = reader.read(buffer, 0, SAMPLE_CHARS);
                reader.reset();
                if (length <= 0) {
                    return "";
                }
                String sample = new String(buffer, 0, length);
                int lastLine = sample.lastIndexOf('\n');
                return length == SAMPLE_CHARS && lastLine > 0 ? sample.substring(0, lastLine + 1) : sample;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String invalidMessage(long row) {
            String suffix = detected ? " Choose the file's encoding." : "";
            return "File is not valid " + encoding.apiName() + " (near row " + row + ")." + suffix;
        }
    }

    /** One pass over the records: the header (or the first non-blank record) on open, then rows lazily. */
    private static final class Reading implements AutoCloseable {

        private final Text text;
        private final CSVParser parser;
        private final Iterator<CSVRecord> records;
        private final List<Column> columns;
        /** The first data row of a file without header, read ahead to count its columns; handed out first. */
        private Row pending;
        /** Blank records before that first row. */
        private int pendingBlanks;
        private long lastRowNumber;

        private Reading(Text text, CSVParser parser, ResolvedReadOptions options, List<Column> knownColumns) {
            this.text = text;
            this.parser = parser;
            this.records = parser.iterator();
            if (knownColumns != null) {
                this.columns = knownColumns;
                if (options.hasHeader()) {
                    nextRecord();
                }
            } else if (options.hasHeader()) {
                CSVRecord header = nextRecord();
                List<String> names = header == null ? List.of() : cells(header);
                if (ColumnNames.isBlankRow(names)) {
                    throw new DomainException(ErrorCode.FILE_EMPTY, "The first row must contain column headers.");
                }
                this.columns = ColumnNames.normalize(names);
            } else {
                List<String> first = null;
                long firstNumber = 0;
                for (CSVRecord record = nextRecord(); record != null; record = nextRecord()) {
                    List<String> values = cells(record);
                    if (!ColumnNames.isBlankRow(values)) {
                        first = values;
                        firstNumber = record.getRecordNumber();
                        break;
                    }
                    pendingBlanks++;
                }
                if (first == null) {
                    throw new DomainException(ErrorCode.FILE_EMPTY, "The file has no rows.");
                }
                this.columns = ColumnNames.normalize(Collections.nCopies(first.size(), null));
                this.pending = sized(firstNumber, first);
            }
        }

        static Reading open(Text text, ResolvedReadOptions options, List<Column> knownColumns) {
            CSVFormat format = CSVFormat.RFC4180.builder()
                    .setDelimiter(options.delimiter().symbol())
                    // Empty lines stay records so that record numbers keep matching spreadsheet row numbers.
                    .setIgnoreEmptyLines(false)
                    .get();
            try {
                CSVParser parser = format.parse(text.reader());
                try {
                    return new Reading(text, parser, options, knownColumns);
                } catch (RuntimeException e) {
                    closeQuietly(parser);
                    throw e;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        List<Column> columns() {
            return columns;
        }

        /** Hands every data row to {@code rows} and every blank record to {@code blanks}, in file order. */
        void forEachRecord(Consumer<Row> rows, Runnable blanks) {
            for (int i = 0; i < pendingBlanks; i++) {
                blanks.run();
            }
            if (pending != null) {
                rows.accept(pending);
                pending = null;
            }
            for (CSVRecord record = nextRecord(); record != null; record = nextRecord()) {
                List<String> values = cells(record);
                if (ColumnNames.isBlankRow(values)) {
                    blanks.run();
                } else {
                    rows.accept(sized(record.getRecordNumber(), values));
                }
            }
        }

        Stream<Row> rows() {
            Iterator<Row> rows = new Iterator<>() {
                private Row next = pending;

                @Override
                public boolean hasNext() {
                    while (next == null) {
                        CSVRecord record = nextRecord();
                        if (record == null) {
                            return false;
                        }
                        List<String> values = cells(record);
                        next = ColumnNames.isBlankRow(values) ? null : sized(record.getRecordNumber(), values);
                    }
                    return true;
                }

                @Override
                public Row next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    Row row = next;
                    next = null;
                    return row;
                }
            };
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(rows, Spliterator.ORDERED), false);
        }

        /** The values fitted to the columns: missing cells are {@code null}, extra cells are dropped. */
        private Row sized(long rowNumber, List<String> values) {
            List<String> sized = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                sized.add(i < values.size() ? values.get(i) : null);
            }
            return new Row(rowNumber, sized);
        }

        private List<String> cells(CSVRecord record) {
            lastRowNumber = record.getRecordNumber();
            List<String> values = new ArrayList<>(record.size());
            for (String value : record) {
                if (value.indexOf(REPLACEMENT_CHARACTER) >= 0) {
                    throw new DomainException(ErrorCode.FILE_PARSE_ERROR, text.invalidMessage(lastRowNumber));
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

        @Override
        public void close() {
            try {
                parser.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static void closeQuietly(CSVParser parser) {
            try {
                parser.close();
            } catch (IOException e) {
                // Already failing; the first error is the one to report.
            }
        }
    }
}
