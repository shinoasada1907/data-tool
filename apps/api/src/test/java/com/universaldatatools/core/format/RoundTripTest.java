package com.universaldatatools.core.format;

import com.universaldatatools.core.format.csv.CsvTableReader;
import com.universaldatatools.core.format.csv.CsvTableWriter;
import com.universaldatatools.core.format.json.JsonTableReader;
import com.universaldatatools.core.format.json.JsonTableWriter;
import com.universaldatatools.core.format.xlsx.XlsxLimits;
import com.universaldatatools.core.format.xlsx.XlsxTableReader;
import com.universaldatatools.core.format.xlsx.XlsxTableWriter;
import com.universaldatatools.core.format.xlsx.XlsxZipGuard;
import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.OutputColumn;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.RowSink;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.table.TableReader;
import com.universaldatatools.core.table.TableWriter;
import com.universaldatatools.core.table.TypedCell;
import com.universaldatatools.core.table.Typing;
import com.universaldatatools.core.table.WriteOptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Reading a table, writing it with {@link Typing#PRESERVE} and reading it back keeps every value (spec: dataset-io,
 * "Round-trip không làm mất dữ liệu"). Between two typed formats a value keeps its meaning rather than its spelling:
 * XLSX shows booleans as TRUE/FALSE and numbers without trailing zeros, and JSON has no date type.
 */
class RoundTripTest {

    private static final List<String> NAMES = List.of("id", "name", "note", "ok", "joined");
    private static final List<CellKind> KINDS =
            List.of(CellKind.NUMBER, CellKind.TEXT, CellKind.TEXT, CellKind.BOOLEAN, CellKind.DATE);

    private static final Map<DataFormat, TableReader> READERS = Map.of(
            DataFormat.CSV, new CsvTableReader(),
            DataFormat.JSON, new JsonTableReader(),
            DataFormat.XLSX, new XlsxTableReader(new XlsxZipGuard(new XlsxLimits(200L * 1024 * 1024, 100, 10_000))));
    private static final Map<DataFormat, TableWriter> WRITERS = Map.of(
            DataFormat.CSV, new CsvTableWriter(),
            DataFormat.JSON, new JsonTableWriter(),
            DataFormat.XLSX, new XlsxTableWriter());

    static Stream<Arguments> pairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (DataFormat source : DataFormat.values()) {
            for (DataFormat target : DataFormat.values()) {
                pairs.add(arguments(source, target));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("pairs")
    void every_value_survives(DataFormat source, DataFormat target) throws IOException {
        byte[] original = write(WRITERS.get(source), NAMES, sample());
        Table first = read(source, original);

        byte[] converted = write(WRITERS.get(target), names(first.info()), cells(first.rows()));
        Table second = read(target, converted);

        assertThat(names(second.info())).isEqualTo(NAMES);
        assertThat(second.rows()).hasSameSizeAs(first.rows()).hasSize(50);
        boolean typed = source != DataFormat.CSV && target != DataFormat.CSV;
        for (int r = 0; r < first.rows().size(); r++) {
            Row before = first.rows().get(r);
            Row after = second.rows().get(r);
            for (int c = 0; c < NAMES.size(); c++) {
                String where = source + "→" + target + " row " + r + " " + NAMES.get(c);
                if (!typed) {
                    assertThat(after.value(c)).as(where).isEqualTo(before.value(c));
                    continue;
                }
                assertSameValue(before.value(c), after.value(c), before.kind(c), where);
                CellKind expectedKind = before.kind(c) == CellKind.DATE && target == DataFormat.JSON
                        ? CellKind.TEXT : before.kind(c);
                assertThat(after.kind(c)).as(where).isEqualTo(expectedKind);
            }
        }
    }

    private static void assertSameValue(String before, String after, CellKind kind, String where) {
        if (before == null || kind == null) {
            assertThat(after).as(where).isEqualTo(before);
        } else if (kind == CellKind.NUMBER) {
            assertThat(new BigDecimal(after)).as(where).isEqualByComparingTo(new BigDecimal(before));
        } else if (kind == CellKind.BOOLEAN) {
            assertThat(after).as(where).isEqualToIgnoringCase(before);
        } else {
            assertThat(after).as(where).isEqualTo(before);
        }
    }

    /** 50 rows: numbers with and without decimals, Vietnamese, formulas, line breaks, quotes, empty cells. */
    private static List<List<TypedCell>> sample() {
        String[] notes = {"=x", "dòng 1\ndòng 2", "say \"hi\"", "Nguyễn Văn A", "-5", "", "@home"};
        List<List<TypedCell>> rows = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String id = i % 3 == 0 ? i + ".50" : String.valueOf(i);
            String note = notes[i % notes.length];
            String ok = i % 5 == 4 ? null : String.valueOf(i % 2 == 0);
            String joined = "2024-01-" + String.format("%02d", i % 28 + 1);
            List<String> texts = List.of(id, "Tên " + i, note, ok == null ? "" : ok, joined);
            List<TypedCell> row = new ArrayList<>();
            for (int c = 0; c < texts.size(); c++) {
                String text = texts.get(c).isEmpty() ? null : texts.get(c);
                row.add(new TypedCell(text, text == null ? null : KINDS.get(c)));
            }
            rows.add(row);
        }
        return rows;
    }

    private static byte[] write(TableWriter writer, List<String> names, List<List<TypedCell>> rows)
            throws IOException {
        WriteOptions options = new WriteOptions(Delimiter.COMMA, true, true, false, false, Typing.PRESERVE, "Data");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (RowSink sink = writer.open(out, names.stream().map(name -> new OutputColumn(name, null)).toList(),
                options)) {
            for (List<TypedCell> row : rows) {
                sink.write(row);
            }
        }
        return out.toByteArray();
    }

    private record Table(TableInfo info, List<Row> rows) {
    }

    private static Table read(DataFormat format, byte[] file) {
        TableReader reader = READERS.get(format);
        TableInfo info = reader.inspect(new ByteArrayInputStream(file), ReadOptions.defaults());
        try (Stream<Row> rows = reader.read(new ByteArrayInputStream(file), info)) {
            return new Table(info, rows.toList());
        }
    }

    private static List<String> names(TableInfo info) {
        return info.columns().stream().map(Column::name).toList();
    }

    private static List<List<TypedCell>> cells(List<Row> rows) {
        List<List<TypedCell>> cells = new ArrayList<>();
        for (Row row : rows) {
            List<TypedCell> typed = new ArrayList<>();
            for (int c = 0; c < row.values().size(); c++) {
                typed.add(new TypedCell(row.value(c), row.kind(c)));
            }
            cells.add(typed);
        }
        return cells;
    }
}
