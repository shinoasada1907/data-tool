package com.universaldatatools.core.format.csv;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.ColumnProfile;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.InferredType;
import com.universaldatatools.core.table.ReadLimits;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.table.TextEncoding;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** core-02 tasks 3, 4, 7 and 8 for CSV (spec: dataset-io). */
class CsvTableReaderTest {

    private final CsvTableReader reader = new CsvTableReader();

    // --- encoding (task 3) ---

    @Test
    void plain_utf8_is_detected() {
        byte[] file = utf8("a,b\n1,2\n");
        TableInfo info = inspect(file, ReadOptions.defaults());

        assertThat(names(info)).containsExactly("a", "b");
        assertThat(info.options().encoding()).isEqualTo(TextEncoding.UTF_8);
        assertThat(info.autoDetected()).contains("encoding");
        assertThat(rows(file, info)).containsExactly(new Row(2, List.of("1", "2")));
    }

    @Test
    void utf8_bom_is_dropped_and_not_reported_as_detected() {
        TableInfo info = inspect(concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, utf8("name\nAn\n")),
                ReadOptions.defaults());

        assertThat(names(info)).containsExactly("name");
        assertThat(info.autoDetected()).doesNotContain("encoding");
    }

    @Test
    void utf16_with_bom_is_detected_either_way_round() {
        byte[] little = concat(new byte[] {(byte) 0xFF, (byte) 0xFE},
                "a,b\n1,2\n".getBytes(StandardCharsets.UTF_16LE));
        TableInfo info = inspect(little, ReadOptions.defaults());
        assertThat(names(info)).containsExactly("a", "b");
        assertThat(info.options().encoding()).isEqualTo(TextEncoding.UTF_16);
        assertThat(rows(little, info)).containsExactly(new Row(2, List.of("1", "2")));

        byte[] big = concat(new byte[] {(byte) 0xFE, (byte) 0xFF}, "a\nx\n".getBytes(StandardCharsets.UTF_16BE));
        TableInfo chosen = inspect(big, options(null, TextEncoding.UTF_16, null));
        assertThat(rows(big, chosen)).containsExactly(new Row(2, List.of("x")));
    }

    @Test
    void utf16_without_bom_is_refused() {
        assertParseError(() -> inspect("a\nx\n".getBytes(StandardCharsets.UTF_16LE),
                options(null, TextEncoding.UTF_16, null)), "UTF-16 file must start with a byte order mark.");
    }

    @Test
    void windows_encodings_read_when_chosen() {
        // windows-1258 has no precomposed "ễ": files store "ê" plus a combining tilde.
        byte[] vietnamese = concat(utf8("ten\n"), "Nguyễn\n".getBytes(Charset.forName("windows-1258")));
        TableInfo info = inspect(vietnamese, options(null, TextEncoding.WINDOWS_1258, null));
        assertThat(Normalizer.normalize(rows(vietnamese, info).getFirst().value(0), Normalizer.Form.NFC))
                .isEqualTo("Nguyễn");

        byte[] latin = {0x63, 0x61, 0x66, (byte) 0xE9, '\n'};
        assertThat(names(inspect(latin, options(null, TextEncoding.WINDOWS_1252, null)))).containsExactly("café");
    }

    @Test
    void invalid_utf8_names_the_row_and_suggests_an_encoding_only_when_detected() {
        byte[] bad = concat(utf8("a,b\n1,2\n"), new byte[] {(byte) 0xC3, 0x28, '\n'});

        assertParseError(() -> inspect(bad, ReadOptions.defaults()),
                "File is not valid UTF-8 (near row 3). Choose the file's encoding.");
        assertParseError(() -> inspect(bad, options(null, TextEncoding.UTF_8, null)),
                "File is not valid UTF-8 (near row 3).");
    }

    // --- delimiter and header (task 4) ---

    @Test
    void semicolon_is_detected() {
        TableInfo info = inspect(utf8("ma;ten\n1;A\n"), ReadOptions.defaults());

        assertThat(info.options().delimiter()).isEqualTo(Delimiter.SEMICOLON);
        assertThat(info.autoDetected()).contains("delimiter");
        assertThat(names(info)).containsExactly("ma", "ten");
    }

    @Test
    void a_chosen_delimiter_is_used_as_is() {
        TableInfo info = inspect(utf8("a,b\n1,2\n"), options(Delimiter.SEMICOLON, null, null));

        assertThat(names(info)).containsExactly("a,b");
        assertThat(info.autoDetected()).doesNotContain("delimiter");
    }

    @Test
    void without_header_every_row_is_data() {
        byte[] file = utf8("1,An\n2,Binh\n");
        TableInfo info = inspect(file, options(null, null, false));

        assertThat(names(info)).containsExactly("Column A", "Column B");
        assertThat(info.rowCount()).isEqualTo(2);
        assertThat(rows(file, info).getFirst()).isEqualTo(new Row(1, List.of("1", "An")));
    }

    @Test
    void without_header_the_first_non_blank_row_sets_the_columns() {
        byte[] file = utf8("\nx,y,z\n");
        TableInfo info = inspect(file, options(null, null, false));

        assertThat(names(info)).hasSize(3);
        assertThat(info.blankRowsSkipped()).isEqualTo(1);
        assertThat(rows(file, info).getFirst().rowNumber()).isEqualTo(2);
    }

    // --- profiles and limits (tasks 7, 8) ---

    @Test
    void columns_are_profiled() {
        TableInfo info = inspect(utf8("id,code,active,joined,mail,note\n1,00123,true,2024-01-31,an@x.com,x\n"
                + "2,00456,FALSE,2024-02-29,,y\n"), ReadOptions.defaults());

        assertThat(info.profiles()).extracting(ColumnProfile::inferredType).containsExactly(InferredType.NUMBER,
                InferredType.STRING, InferredType.BOOLEAN, InferredType.DATE, InferredType.EMAIL, InferredType.STRING);
        assertThat(info.profiles().get(4).emptyCount()).isEqualTo(1);
    }

    @Test
    void blank_rows_are_counted() {
        TableInfo info = inspect(utf8("a,b\n1,2\n\n , \n3,4\n"), ReadOptions.defaults());

        assertThat(info.rowCount()).isEqualTo(2);
        assertThat(info.blankRowsSkipped()).isEqualTo(2);
    }

    @Test
    void limits_stop_the_read() {
        ReadLimits limits = new ReadLimits(3, 2, 5);
        assertLimit(utf8("a\n1\n2\n3\n4\n"), limits, "File has more than 3 rows.");
        assertLimit(utf8("a,b,c\n1,2,3\n"), limits, "File has more than 2 columns.");
        assertLimit(utf8("a,b\n1,123456\n"), limits, "Value at row 2, column \"b\" is longer than 5 characters.");
        assertThat(inspect(utf8("a,b\n1,12345\n2,x\n3,y\n"), withLimits(limits)).rowCount()).isEqualTo(3);
    }

    private static ReadOptions options(Delimiter delimiter, TextEncoding encoding, Boolean hasHeader) {
        return new ReadOptions(null, delimiter, encoding, hasHeader, ReadLimits.NONE);
    }

    private static ReadOptions withLimits(ReadLimits limits) {
        return new ReadOptions(null, null, null, null, limits);
    }

    private TableInfo inspect(byte[] file, ReadOptions options) {
        return reader.inspect(new ByteArrayInputStream(file), options);
    }

    private List<Row> rows(byte[] file, TableInfo info) {
        try (Stream<Row> rows = reader.read(new ByteArrayInputStream(file), info)) {
            return rows.toList();
        }
    }

    private void assertLimit(byte[] file, ReadLimits limits, String message) {
        assertThatThrownBy(() -> inspect(file, withLimits(limits))).isInstanceOfSatisfying(DomainException.class,
                e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.LIMIT_EXCEEDED);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
    }

    private static void assertParseError(ThrowingCallable call, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.FILE_PARSE_ERROR);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    private static List<String> names(TableInfo info) {
        return info.columns().stream().map(Column::name).toList();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(first);
        out.writeBytes(second);
        return out.toByteArray();
    }
}
