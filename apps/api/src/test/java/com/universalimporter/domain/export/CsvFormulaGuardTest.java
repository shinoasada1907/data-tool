package com.universalimporter.domain.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CsvFormulaGuardTest {

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', value = {
            "=SUM(A1), '=SUM(A1)",
            "+84901234567, '+84901234567",
            "-5, '-5",
            "@user, '@user",
            "a=b, a=b",
            "'quoted, 'quoted",
    })
    void a_value_starting_like_a_formula_gets_a_quote(String value, String escaped) {
        assertThat(CsvFormulaGuard.escape(value)).isEqualTo(escaped);
    }

    @Test
    void a_leading_tab_or_carriage_return_gets_a_quote() {
        assertThat(CsvFormulaGuard.escape("\tx")).isEqualTo("'\tx");
        assertThat(CsvFormulaGuard.escape("\rx")).isEqualTo("'\rx");
    }

    @Test
    void empty_and_null_stay_as_they_are() {
        assertThat(CsvFormulaGuard.escape("")).isEmpty();
        assertThat(CsvFormulaGuard.escape(null)).isNull();
    }
}
