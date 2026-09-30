package com.universaldatatools.core.format.json;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.CellKind;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.InferredType;
import com.universaldatatools.core.table.ReadLimits;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.TableInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** core-02 task 6 (spec: dataset-io, "Đọc JSON là mảng các object phẳng"). */
class JsonTableReaderTest {

    private final JsonTableReader reader = new JsonTableReader();

    @Test
    void keys_become_columns_in_the_order_first_seen() {
        String json = "[{\"id\":1,\"name\":\"An\"},{\"id\":2,\"email\":\"b@x.com\"}]";
        TableInfo info = inspect(json);

        assertThat(info.columns()).extracting(Column::name).containsExactly("id", "name", "email");
        List<Row> rows = rows(json, info);
        assertThat(rows.getFirst().values()).containsExactly("1", "An", null);
        assertThat(rows.getFirst().kind(0)).isEqualTo(CellKind.NUMBER);
        assertThat(rows.getFirst().kind(1)).isEqualTo(CellKind.TEXT);
        assertThat(rows.getFirst().kind(2)).isNull();
        assertThat(rows.get(1).values()).containsExactly("2", null, "b@x.com");
        assertThat(rows).extracting(Row::rowNumber).containsExactly(1L, 2L);
        assertThat(info.profiles().get(2).emptyCount()).as("missing in row 1").isEqualTo(1);
    }

    @Test
    void numbers_keep_the_exact_text_of_their_token() {
        String json = "[{\"price\":12.50,\"big\":12345678901234567890,\"exp\":1e3,\"neg\":-0}]";

        Row row = rows(json, inspect(json)).getFirst();
        assertThat(row.values()).containsExactly("12.50", "12345678901234567890", "1e3", "-0");
        assertThat(row.kind(3)).isEqualTo(CellKind.NUMBER);
        assertThat(inspect(json).profiles().getFirst().inferredType()).isEqualTo(InferredType.NUMBER);
    }

    @Test
    void booleans_and_nulls() {
        String json = "[{\"ok\":true,\"no\":false,\"n\":null}]";

        Row row = rows(json, inspect(json)).getFirst();
        assertThat(row.values()).containsExactly("true", "false", null);
        assertThat(Arrays.asList(row.kind(0), row.kind(1), row.kind(2)))
                .containsExactly(CellKind.BOOLEAN, CellKind.BOOLEAN, null);
    }

    @Test
    void an_empty_object_is_still_a_row() {
        String json = "[{},{\"a\":\"x\"}]";
        TableInfo info = inspect(json);

        assertThat(info.rowCount()).isEqualTo(2);
        assertThat(info.blankRowsSkipped()).isZero();
        assertThat(rows(json, info).getFirst().values()).containsExactly((String) null);
    }

    @Test
    void a_byte_order_mark_is_accepted() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "[{\"a\":\"x\"}]".getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, file, 0, bom.length);
        System.arraycopy(body, 0, file, bom.length, body.length);

        assertThat(reader.inspect(new ByteArrayInputStream(file), ReadOptions.defaults()).rowCount()).isEqualTo(1);
    }

    @Test
    void keys_differing_in_case_get_distinct_column_names() {
        String json = "[{\"Email\":\"a\"},{\"email\":\"b\"}]";
        TableInfo info = inspect(json);

        assertThat(info.columns()).extracting(Column::name).containsExactly("Email", "email (2)");
        assertThat(rows(json, info).get(1).values()).containsExactly(null, "b");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "[{\"id\":1},{\"id\":2,\"address\":{\"city\":\"HN\"}}] | JSON_NOT_FLAT | Nested value at row 2, key \"address\" is not supported yet.",
            "[{\"tags\":[\"a\"]}] | JSON_NOT_FLAT | Nested value at row 1, key \"tags\" is not supported yet.",
            "{\"data\":[]} | FILE_PARSE_ERROR | JSON must be an array of objects.",
            "[1,2] | FILE_PARSE_ERROR | Element 1 of the array is not an object.",
            "[{\"a\":1,\"a\":2}] | FILE_PARSE_ERROR | Duplicate key \"a\" at row 1.",
            "[{\"a\":1},{\"a\": | FILE_PARSE_ERROR | Invalid JSON near row 2.",
            "[] | FILE_EMPTY | JSON array is empty.",
            "[{\"a\":\"x\"}] trailing | FILE_PARSE_ERROR | Invalid JSON near row 1.",
    })
    void broken_files_are_refused_with_a_message_that_names_the_row(String json, ErrorCode code, String message) {
        assertThatThrownBy(() -> inspect(json)).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.code()).isEqualTo(code);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    @Test
    void messages_never_quote_the_file() {
        assertThatThrownBy(() -> inspect("[{\"a\": SECRET}]"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getMessage()).doesNotContain("SECRET"));
    }

    @Test
    void limits_apply_to_keys_as_columns() {
        assertThatThrownBy(() -> reader.inspect(stream("[{\"a\":1,\"b\":2,\"c\":3}]"),
                new ReadOptions(null, null, null, null, new ReadLimits(0, 2, 0))))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.LIMIT_EXCEEDED);
                    assertThat(e.getMessage()).isEqualTo("File has more than 2 columns.");
                });
    }

    private TableInfo inspect(String json) {
        return reader.inspect(stream(json), ReadOptions.defaults());
    }

    private List<Row> rows(String json, TableInfo info) {
        try (Stream<Row> rows = reader.read(stream(json), info)) {
            return rows.toList();
        }
    }

    private static ByteArrayInputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
