package com.universaldatatools.core.validate;

import com.google.re2j.Pattern;
import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.core.schema.FieldType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** core-03 task 4: length, pattern and range rules; values have already been through the type rule. */
class ConstraintRulesTest {

    private static final ValidationContext CONTEXT = new ValidationContext("f", FieldType.STRING, 2, null);

    @Test
    void lengths_count_code_points() {
        assertThat(new MaxLengthRule(6).validate("Nguyễn", CONTEXT)).isInstanceOf(ValidationResult.Valid.class);
        assertThat(new MaxLengthRule(5).validate("Nguyễn", CONTEXT)).isEqualTo(
                invalid(RowErrorCode.VALIDATION_MAX_LENGTH, "Value must have at most 5 characters."));
        assertThat(new MinLengthRule(3).validate("ab", CONTEXT)).isEqualTo(
                invalid(RowErrorCode.VALIDATION_MIN_LENGTH, "Value must have at least 3 characters."));
        assertThat(new MaxLengthRule(2).validate("😀😀", CONTEXT)).isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void patterns_match_the_whole_value() {
        PatternRule rule = new PatternRule(Pattern.compile("[A-Z]{3}-[0-9]{3}"));
        assertThat(rule.validate("ABC-123", CONTEXT)).isInstanceOf(ValidationResult.Valid.class);
        assertThat(rule.validate("ABC-123x", CONTEXT)).isEqualTo(
                invalid(RowErrorCode.VALIDATION_PATTERN, "Value does not match the required pattern."));
    }

    @Test
    void a_catastrophic_pattern_for_backtracking_engines_runs_in_linear_time() {
        PatternRule rule = new PatternRule(Pattern.compile("(a+)+$"));
        String hostile = "a".repeat(30_000) + "!";
        ValidationResult result = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> rule.validate(hostile, CONTEXT));
        assertThat(result).isEqualTo(invalid(RowErrorCode.VALIDATION_PATTERN, "Value does not match the required pattern."));
    }

    @Test
    void ranges_compare_numbers_by_value() {
        assertThat(new MinRule(new BigDecimal("18")).validate(new BigDecimal("17.99"), CONTEXT))
                .isEqualTo(invalid(RowErrorCode.VALIDATION_MIN, "Value must be at least 18."));
        assertThat(new MinRule(new BigDecimal("18")).validate(new BigDecimal("18"), CONTEXT))
                .isInstanceOf(ValidationResult.Valid.class);
        assertThat(new MaxRule(new BigDecimal("65")).validate(new BigDecimal("65.0"), CONTEXT))
                .isInstanceOf(ValidationResult.Valid.class);
        assertThat(new MaxRule(new BigDecimal("65")).validate(new BigDecimal("66"), CONTEXT))
                .isEqualTo(invalid(RowErrorCode.VALIDATION_MAX, "Value must be at most 65."));
        assertThat(new MinRule(new BigDecimal("0.5")).validate(new BigDecimal("0.49"), CONTEXT))
                .isEqualTo(invalid(RowErrorCode.VALIDATION_MIN, "Value must be at least 0.5."));
    }

    private static ValidationResult invalid(RowErrorCode code, String message) {
        return new ValidationResult.Invalid(code, message);
    }
}
