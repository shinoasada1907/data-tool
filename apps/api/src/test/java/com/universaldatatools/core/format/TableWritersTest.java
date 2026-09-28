package com.universaldatatools.core.format;

import com.universaldatatools.core.format.csv.CsvTableWriter;
import com.universaldatatools.core.format.json.JsonTableWriter;
import com.universaldatatools.core.format.xlsx.XlsxTableWriter;
import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.CellTyping;
import com.universaldatatools.core.table.ColumnProfile;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.InferredType;
import com.universaldatatools.core.table.OutputColumn;
import com.universaldatatools.core.table.RowSink;
import com.universaldatatools.core.table.TableWriter;
import com.universaldatatools.core.table.TypedCell;
import com.universaldatatools.core.table.Typing;
import com.universaldatatools.core.table.WriteOptions;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.universaldatatools.core.table.CellKind.BOOLEAN;
import static com.universaldatatools.core.table.CellKind.DATE;
import static com.universaldatatools.core.table.CellKind.NUMBER;
import static com.universaldatatools.core.table.CellKind.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** core-02 tasks 9–11 (spec: dataset-io, "Ghi bảng ra CSV/JSON/XLSX", "Typing khi ghi JSON và XLSX"). */
class TableWritersTest {

    // --- typing (task 9) ---

    @Test
    void typing_decides_the_kind_a_cell_is_written_as() {
        ColumnProfile number = new ColumnProfile(InferredType.NUMBER, 0, 2);
        assertThat(CellTyping.resolve(cell("12", NUMBER), Typing.STRING, null)).isEqualTo(TEXT);
        assertThat(CellTyping.resolve(cell("12", NUMBER), Typing.PRESERVE, null)).isEqualTo(NUMBER);
        assertThat(CellTyping.resolve(cell("12", null), Typing.PRESERVE, number)).isEqualTo(TEXT);
        assertThat(CellTyping.resolve(cell("12", null), Typing.INFER, number)).isEqualTo(NUMBER);
        assertThat(CellTyping.resolve(cell("x", null), Typing.INFER, number)).isEqualTo(TEXT);
        assertThat(CellTyping.resolve(cell("true", null), Typing.INFER, profile(InferredType.BOOLEAN)))
                .isEqualTo(BOOLEAN);
        assertThat(CellTyping.resolve(cell("2024-01-01", null), Typing.INFER, profile(InferredType.DATE)))
                .isEqualTo(DATE);
        assertThat(CellTyping.resolve(cell("a@x.com", null), Typing.INFER, profile(InferredType.EMAIL)))
                .isEqualTo(TEXT);
        assertThat(CellTyping.resolve(cell(null, NUMBER), Typing.PRESERVE, null)).isNull();
    }

    // --- CSV (task 9) ---

    @Test
    void csv_defaults_have_bom_header_and_crlf() throws IOException {
        byte[] file = write(new CsvTableWriter(), List.of("a", "b"), WriteOptions.csvDefaults(),
                List.of(cell("x", TEXT), cell("1", NUMBER)));

        assertThat(file).startsWith(0xEF, 0xBB, 0xBF);
        assertThat(text(file).substring(1)).isEqualTo("a,b\r\nx,1\r\n");
    }

    @Test
    void csv_options_change_separator_bom_and_header() throws IOException {
        WriteOptions options = new WriteOptions(Delimiter.SEMICOLON, true, false, true, false, Typing.STRING, null);
        assertThat(text(write(new CsvTableWriter(), List.of("a", "b"), options,
                List.of(cell("x;y", TEXT), cell("1", null))))).isEqualTo("a;b\r\n\"x;y\";1\r\n");

        WriteOptions noHeader = new WriteOptions(Delimiter.COMMA, false, false, true, false, Typing.STRING, null);
        assertThat(text(write(new CsvTableWriter(), List.of("a"), noHeader, List.of(cell("x", TEXT)))))
                .isEqualTo("x\r\n");
    }

    @Test
    void csv_formula_guard_covers_text_and_header_only() throws IOException {
        WriteOptions options = new WriteOptions(Delimiter.COMMA, true, false, true, false, Typing.STRING, null);
        String file = text(write(new CsvTableWriter(), List.of("=cmd()", "n", "u"), options,
                List.of(cell("=SUM(A1)", TEXT), cell("-5", NUMBER), cell("+1", null))));

        assertThat(file).isEqualTo("'=cmd(),n,u\r\n'=SUM(A1),-5,'+1\r\n");

        WriteOptions off = new WriteOptions(Delimiter.COMMA, false, false, false, false, Typing.STRING, null);
        assertThat(text(write(new CsvTableWriter(), List.of("a"), off, List.of(cell("=SUM(A1)", TEXT)))))
                .isEqualTo("=SUM(A1)\r\n");
    }

    @Test
    void csv_quotes_and_empty_cells() throws IOException {
        WriteOptions options = new WriteOptions(Delimiter.COMMA, false, false, false, false, Typing.STRING, null);
        assertThat(text(write(new CsvTableWriter(), List.of("a", "b", "c"), options,
                List.of(cell("say \"hi\"", TEXT), cell(null, null), cell("line1\nline2", TEXT)))))
                .isEqualTo("\"say \"\"hi\"\"\",,\"line1\nline2\"\r\n");
    }

    // --- JSON (task 10) ---

    @Test
    void json_keeps_number_literals_and_types() throws IOException {
        String file = text(write(new JsonTableWriter(), List.of("p", "ok", "n", "s", "d"), WriteOptions.jsonDefaults(),
                List.of(cell("12.50", NUMBER), cell("true", BOOLEAN), cell(null, null), cell("00123", TEXT),
                        cell("2024-01-31", DATE))));

        assertThat(file).isEqualTo("[{\"p\":12.50,\"ok\":true,\"n\":null,\"s\":\"00123\",\"d\":\"2024-01-31\"}]");
    }

    @Test
    void json_booleans_numbers_and_strings_fall_back_to_text_when_they_must() throws IOException {
        String file = text(write(new JsonTableWriter(), List.of("a", "b", "c", "d"), WriteOptions.jsonDefaults(),
                List.of(cell("TRUE", BOOLEAN), cell("0", BOOLEAN), cell("12,5", NUMBER), cell("=cmd()", TEXT))));

        assertThat(file).isEqualTo("[{\"a\":true,\"b\":false,\"c\":\"12,5\",\"d\":\"=cmd()\"}]");
    }

    @Test
    void json_string_typing_and_pretty_printing() throws IOException {
        WriteOptions strings = new WriteOptions(null, true, false, false, false, Typing.STRING, null);
        assertThat(text(write(new JsonTableWriter(), List.of("p", "ok"), strings,
                List.of(cell("12.50", NUMBER), cell("true", BOOLEAN))))).isEqualTo("[{\"p\":\"12.50\",\"ok\":\"true\"}]");

        WriteOptions pretty = new WriteOptions(null, true, false, false, true, Typing.PRESERVE, null);
        assertThat(text(write(new JsonTableWriter(), List.of("tên"), pretty, List.of(cell("Nguyễn", TEXT)))))
                .contains("\n").contains("\"tên\"").contains("\"Nguyễn\"");
    }

    @Test
    void json_without_rows_is_an_empty_array_and_an_abort_leaves_it_open() throws IOException {
        assertThat(text(write(new JsonTableWriter(), List.of("a"), WriteOptions.jsonDefaults()))).isEqualTo("[]");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RowSink sink = new JsonTableWriter().open(out, columns(List.of("a")), WriteOptions.jsonDefaults());
        sink.write(List.of(cell("x", TEXT)));
        sink.abort();
        assertThat(out.toString(StandardCharsets.UTF_8)).doesNotEndWith("]");
    }

    // --- XLSX (task 11) ---

    @Test
    void xlsx_cells_keep_their_type_and_text_is_never_a_formula() throws Exception {
        byte[] file = write(new XlsxTableWriter(), List.of("a", "b", "c", "d", "e", "f"), WriteOptions.xlsxDefaults(),
                List.of(cell("12.5", NUMBER), cell("TRUE", BOOLEAN), cell("2024-02-29", DATE), cell("=1+1", TEXT),
                        cell(null, null), cell("12345678901234567", NUMBER)));

        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(file))) {
            List<Row> rows = workbook.getFirstSheet().read();
            assertThat(rows.getFirst().getCell(0).getRawValue()).isEqualTo("a");
            List<Cell> cells = Arrays.asList(rows.get(1).getCell(0), rows.get(1).getCell(1), rows.get(1).getCell(2),
                    rows.get(1).getCell(3));
            assertThat(cells.get(0).getType()).isEqualTo(CellType.NUMBER);
            assertThat(cells.get(0).asNumber()).isEqualByComparingTo(new BigDecimal("12.5"));
            assertThat(cells.get(1).getType()).isEqualTo(CellType.BOOLEAN);
            assertThat(cells.get(2).getType()).isEqualTo(CellType.NUMBER);
            assertThat(cells.get(2).asDate().toLocalDate()).hasToString("2024-02-29");
            assertThat(cells.get(3).getType()).isEqualTo(CellType.STRING);
            assertThat(cells.get(3).asString()).isEqualTo("=1+1");
            assertThat(rows.get(1).getCell(5).getType()).as("too many digits for Excel").isEqualTo(CellType.STRING);
        }
    }

    @Test
    void xlsx_sheet_names_are_cleaned() throws Exception {
        assertThat(sheetName("Q1/2024: [draft]")).isEqualTo("Q1_2024_ _draft_");
        assertThat(sheetName("x".repeat(40))).hasSize(31);
        assertThat(sheetName("")).isEqualTo("Sheet1");
    }

    @Test
    void xlsx_streams_many_rows_and_an_abort_is_not_a_workbook() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (RowSink sink = new XlsxTableWriter().open(out, columns(List.of("n")), WriteOptions.xlsxDefaults())) {
            for (int i = 0; i < 2_500; i++) {
                sink.write(List.of(cell(String.valueOf(i), NUMBER)));
            }
        }
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(workbook.getFirstSheet().read()).hasSize(2_501);
        }

        ByteArrayOutputStream aborted = new ByteArrayOutputStream();
        RowSink sink = new XlsxTableWriter().open(aborted, columns(List.of("n")), WriteOptions.xlsxDefaults());
        sink.write(List.of(cell("1", NUMBER)));
        sink.abort();
        assertThatThrownBy(() -> new ReadableWorkbook(new ByteArrayInputStream(aborted.toByteArray()))
                .getFirstSheet().read()).isInstanceOf(Exception.class);
    }

    private static String sheetName(String requested) throws Exception {
        WriteOptions options = new WriteOptions(null, true, false, false, false, Typing.PRESERVE, requested);
        byte[] file = write(new XlsxTableWriter(), List.of("a"), options);
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(file))) {
            return workbook.getFirstSheet().getName();
        }
    }

    @SafeVarargs
    private static byte[] write(TableWriter writer, List<String> names, WriteOptions options,
                                List<TypedCell>... rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (RowSink sink = writer.open(out, columns(names), options)) {
            for (List<TypedCell> row : rows) {
                sink.write(row);
            }
        }
        return out.toByteArray();
    }

    private static List<OutputColumn> columns(List<String> names) {
        return names.stream().map(name -> new OutputColumn(name, null)).toList();
    }

    private static ColumnProfile profile(InferredType type) {
        return new ColumnProfile(type, 0, 10);
    }

    private static TypedCell cell(String text, CellKind kind) {
        return new TypedCell(text, kind);
    }

    private static String text(byte[] file) {
        return new String(file, StandardCharsets.UTF_8);
    }
}
