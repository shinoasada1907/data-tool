package com.universaldatatools.tools.importer.infrastructure.parser;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.format.csv.CsvTableReader;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.tools.importer.domain.importsession.SourceSchema;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvSourceParserTest {

    private final TableSourceParser parser = new TableSourceParser(new CsvTableReader());

    @Test
    void supports_only_csv() {
        assertThat(parser.supports(DataFormat.CSV)).isTrue();
        assertThat(parser.supports(DataFormat.XLSX)).isFalse();
    }

    @Test
    void inspect_names_the_columns_and_counts_data_rows() {
        SourceSchema schema = parser.inspect(utf8("name,email\nAn,an@x.com\nBinh,binh@x.com\n"));

        assertThat(schema.columns()).containsExactly(new Column(0, "name"), new Column(1, "email"));
        assertThat(schema.totalRows()).isEqualTo(2);
        assertThat(schema.sheetName()).isNull();
    }

    @Test
    void read_returns_data_rows_with_spreadsheet_row_numbers() {
        assertThat(rows("name,email\nAn,an@x.com\nBinh,binh@x.com\n"))
                .containsExactly(row(2, "An", "an@x.com"), row(3, "Binh", "binh@x.com"));
    }

    @Test
    void utf8_bom_is_not_part_of_the_first_column_name() {
        byte[] withBom = concat(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, bytes("name\nAn\n"));

        assertThat(parser.inspect(new ByteArrayInputStream(withBom)).columns())
                .containsExactly(new Column(0, "name"));
    }

    @Test
    void quoted_commas_and_line_breaks_stay_inside_one_row() {
        assertThat(rows("a,b\n\"x,1\",\"l1\nl2\"\nc,d\n"))
                .containsExactly(row(2, "x,1", "l1\nl2"), row(3, "c", "d"));
    }

    @Test
    void blank_lines_are_skipped_but_still_counted() {
        assertThat(rows("a,b\n1,2\n\n3,4\n")).containsExactly(row(2, "1", "2"), row(4, "3", "4"));
        assertThat(parser.inspect(utf8("a,b\n1,2\n\n3,4\n")).totalRows()).isEqualTo(2);
    }

    @Test
    void rows_of_whitespace_only_are_blank() {
        assertThat(rows("a,b\n1,2\n , \n")).containsExactly(row(2, "1", "2"));
        assertThat(parser.inspect(utf8("a,b\n1,2\n , \n")).totalRows()).isEqualTo(1);
    }

    @Test
    void short_rows_are_padded_and_long_rows_are_cut_to_the_header_width() {
        assertThat(rows("a,b,c\n1\n1,2,3,4\n"))
                .containsExactly(row(2, "1", null, null), row(3, "1", "2", "3"));
    }

    @Test
    void empty_cells_become_null_and_values_are_not_trimmed() {
        assertThat(rows("a,b\n  ,x\n,y\n")).containsExactly(row(2, "  ", "x"), row(3, null, "y"));
    }

    @Test
    void header_only_file_has_no_data_rows() {
        assertThat(parser.inspect(utf8("name,email\n")).totalRows()).isZero();
        assertThat(rows("name,email\n")).isEmpty();
    }

    @Test
    void blank_header_cells_get_generated_names() {
        assertThat(parser.inspect(utf8(",b\n1,2\n")).columns())
                .extracting(Column::name).containsExactly("Column A", "b");
    }

    @Test
    void empty_file_is_file_empty() {
        assertFailure(() -> parser.inspect(utf8("")), ErrorCode.FILE_EMPTY, "The first row must contain column headers.");
    }

    @Test
    void blank_first_row_is_file_empty() {
        assertFailure(() -> parser.inspect(utf8("\n1,2\n")), ErrorCode.FILE_EMPTY,
                "The first row must contain column headers.");
    }

    @Test
    void invalid_utf8_names_the_row_it_is_on() {
        byte[] content = concat(bytes("a,b\n1,2\n"), new byte[]{(byte) 0xC3, 0x28}, bytes("\n"));

        assertFailure(() -> parser.inspect(new ByteArrayInputStream(content)), ErrorCode.FILE_PARSE_ERROR,
                "File is not valid UTF-8 (near row 3).");
    }

    @Test
    void invalid_utf8_deep_in_a_large_file_still_names_the_right_row() {
        // More than one 8KB decoder buffer before the bad bytes: the row must still be exact.
        StringBuilder csv = new StringBuilder("a,b\n");
        for (int i = 0; i < 2000; i++) {
            csv.append("value-").append(i).append(",x\n");
        }
        byte[] content = concat(bytes(csv.toString()), new byte[]{(byte) 0xC3, 0x28}, bytes(",y\n"));

        assertFailure(() -> parser.inspect(new ByteArrayInputStream(content)), ErrorCode.FILE_PARSE_ERROR,
                "File is not valid UTF-8 (near row 2002).");
    }

    @Test
    void read_reports_invalid_utf8_too() {
        byte[] content = concat(bytes("a,b\n"), new byte[]{(byte) 0xC3, 0x28}, bytes(",y\n"));

        assertFailure(() -> {
            try (Stream<Row> rows = parser.read(new ByteArrayInputStream(content))) {
                rows.toList();
            }
        }, ErrorCode.FILE_PARSE_ERROR, "File is not valid UTF-8 (near row 2).");
    }

    @Test
    void unterminated_quote_is_a_syntax_error_without_cell_values_in_the_message() {
        assertThatThrownBy(() -> parser.inspect(utf8("a,b\n\"unterminated,1\n")))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.FILE_PARSE_ERROR);
                    assertThat(ex.getMessage()).startsWith("CSV syntax error near row").doesNotContain("unterminated");
                });
    }

    @Test
    void closing_the_read_stream_closes_the_input() {
        TrackingInputStream input = new TrackingInputStream(bytes("a,b\n1,2\n"));

        parser.read(input).close();

        assertThat(input.closed).isTrue();
    }

    private List<Row> rows(String csv) {
        try (Stream<Row> rows = parser.read(utf8(csv))) {
            return rows.toList();
        }
    }

    private static Row row(long rowNumber, String... values) {
        return new Row(rowNumber, Arrays.asList(values));
    }

    private static void assertFailure(Runnable action, ErrorCode code, String message) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class, ex -> {
            assertThat(ex.code()).isEqualTo(code);
            assertThat(ex.getMessage()).isEqualTo(message);
        });
    }

    private static InputStream utf8(String text) {
        return new ByteArrayInputStream(bytes(text));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        boolean closed;

        TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
