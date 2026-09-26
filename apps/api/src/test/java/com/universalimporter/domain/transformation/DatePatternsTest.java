package com.universalimporter.domain.transformation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DatePatternsTest {

    @ParameterizedTest
    @CsvSource(value = {"dd/MM/yyyy|dd/MM/uuuu", "'year' yyyy|'year' uuuu", "yy|uu", "uuuu-MM-dd|uuuu-MM-dd",
            "'it''s' yyyy|'it''s' uuuu"}, delimiter = '|', quoteCharacter = '"')
    void to_strict_turns_year_of_era_into_year_outside_quotes(String pattern, String strict) {
        assertThat(DatePatterns.toStrict(pattern)).isEqualTo(strict);
    }

    @Test
    void check_input_accepts_patterns_with_year_month_and_day() {
        assertThat(DatePatterns.checkInput("dd/MM/yyyy")).isEmpty();
        assertThat(DatePatterns.checkInput("yyyy-MM-dd'T'HH:mm:ss")).isEmpty();
    }

    @Test
    void check_input_rejects_patterns_missing_a_date_part() {
        assertThat(DatePatterns.checkInput("MM/yyyy")).contains("Date pattern 'MM/yyyy' must contain year, month and day.");
        assertThat(DatePatterns.checkInput("HH:mm")).contains("Date pattern 'HH:mm' must contain year, month and day.");
    }

    @Test
    void check_input_rejects_invalid_syntax() {
        assertThat(DatePatterns.checkInput("dd/MM/yyyyb")).contains("Invalid date pattern 'dd/MM/yyyyb'.");
    }

    @Test
    void check_output_rejects_time_fields() {
        assertThat(DatePatterns.checkOutput("yyyy-MM-dd")).isEmpty();
        assertThat(DatePatterns.checkOutput("dd/MM/yyyy HH:mm"))
                .contains("Date pattern 'dd/MM/yyyy HH:mm' must not contain time fields.");
        assertThat(DatePatterns.checkOutput("dd/MM/yyyyb")).contains("Invalid date pattern 'dd/MM/yyyyb'.");
    }

    @Test
    void iso_is_compared_after_the_year_rewrite() {
        assertThat(DatePatterns.isIso("yyyy-MM-dd")).isTrue();
        assertThat(DatePatterns.isIso("uuuu-MM-dd")).isTrue();
        assertThat(DatePatterns.isIso("dd/MM/yyyy")).isFalse();
    }
}
