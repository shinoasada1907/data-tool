package com.universaldatatools.core.table;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ColumnNamesTest {

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource
    void normalize_gives_every_column_a_unique_non_blank_name(List<String> raw, List<String> expected) {
        List<Column> columns = ColumnNames.normalize(raw);

        assertThat(columns).extracting(Column::name).containsExactlyElementsOf(expected);
        for (int i = 0; i < columns.size(); i++) {
            assertThat(columns.get(i).index()).isEqualTo(i);
        }
    }

    static Stream<Arguments> normalize_gives_every_column_a_unique_non_blank_name() {
        List<String> twentySevenBlanks = Collections.nCopies(27, "");
        List<String> expectedBlanks = new ArrayList<>();
        for (String letters : List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O",
                "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "AA")) {
            expectedBlanks.add("Column " + letters);
        }
        return Stream.of(
                arguments(List.of("name", "email"), List.of("name", "email")),
                arguments(List.of(" name "), List.of("name")),
                arguments(List.of("Email", "email"), List.of("Email", "email (2)")),
                arguments(List.of("", " ", "x"), List.of("Column A", "Column B", "x")),
                arguments(List.of("a", "a", "a"), List.of("a", "a (2)", "a (3)")),
                arguments(List.of("a (2)", "a", "a"), List.of("a (2)", "a", "a (3)")),
                arguments(twentySevenBlanks, expectedBlanks),
                arguments(Arrays.asList(null, "b"), List.of("Column A", "b"))
        );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"0, A", "25, Z", "26, AA", "701, ZZ", "702, AAA"})
    void excel_letters_follow_spreadsheet_column_naming(int index, String expected) {
        assertThat(ColumnNames.excelLetters(index)).isEqualTo(expected);
    }

    @Test
    void a_row_of_nulls_and_whitespace_is_blank() {
        assertThat(ColumnNames.isBlankRow(Arrays.asList(null, "  "))).isTrue();
        assertThat(ColumnNames.isBlankRow(List.of())).isTrue();
    }

    @Test
    void a_row_with_any_text_is_not_blank() {
        assertThat(ColumnNames.isBlankRow(Arrays.asList(null, "x"))).isFalse();
    }
}
