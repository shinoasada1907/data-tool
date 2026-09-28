package com.universaldatatools.core.transform;

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
    void a_two_digit_year_cannot_be_read_safely() {
        // "yy" reads 90 as 2090: birth dates would move a century without any error.
        assertThat(DatePatterns.checkInput("dd/MM/yy")).contains("Date pattern 'dd/MM/yy' must use a 4-digit year.");
    }

    @Test
    void week_based_and_day_of_year_letters_are_rejected_in_both_directions() {
        for (String pattern : new String[]{"YYYY-MM-dd", "yyyy-MM-DD", "yyyy-ww", "yyyy-MM-W", "yyyy-MM-F"}) {
            assertThat(DatePatterns.checkInput(pattern)).as(pattern).isPresent().get().asString()
                    .startsWith("Date pattern '" + pattern + "' uses unsupported letter");
            assertThat(DatePatterns.checkOutput(pattern)).as(pattern).isPresent().get().asString()
                    .startsWith("Date pattern '" + pattern + "' uses unsupported letter");
        }
        assertThat(DatePatterns.checkOutput("yyyy-MM-dd 'Day' DD")).get().asString()
                .isEqualTo("Date pattern 'yyyy-MM-dd 'Day' DD' uses unsupported letter 'D'.");
        assertThat(DatePatterns.checkOutput("'Year' yyyy-MM-dd")).isEmpty();
    }

    @Test
    void iso_is_compared_after_the_year_rewrite() {
        assertThat(DatePatterns.isIso("yyyy-MM-dd")).isTrue();
        assertThat(DatePatterns.isIso("uuuu-MM-dd")).isTrue();
        assertThat(DatePatterns.isIso("dd/MM/yyyy")).isFalse();
    }
}
