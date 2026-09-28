package com.universaldatatools.core.validate;

import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.core.common.RowErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TypeRuleTest {

    private final TypeRule rule = new TypeRule();

    @Test
    void a_string_is_kept_as_it_is() {
        assertThat(validate(FieldType.STRING, " a ")).isEqualTo(new ValidationResult.Valid(" a "));
    }

    @Test
    void numbers_become_big_decimals() {
        assertThat(validate(FieldType.NUMBER, "42")).isEqualTo(new ValidationResult.Valid(new BigDecimal("42")));
        assertThat(validate(FieldType.NUMBER, "-3.50")).isEqualTo(new ValidationResult.Valid(new BigDecimal("-3.50")));
        ValidationResult leadingZeros = validate(FieldType.NUMBER, "007");
        assertThat(((ValidationResult.Valid) leadingZeros).value()).isInstanceOfSatisfying(BigDecimal.class,
                number -> assertThat(number.compareTo(BigDecimal.valueOf(7))).isZero());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,234", "1e3", " 42", "+5", ".5", "٤٢", "5.", "--1"})
    void anything_else_is_not_a_number(String value) {
        assertThat(validate(FieldType.NUMBER, value))
                .isEqualTo(new ValidationResult.Invalid(RowErrorCode.VALIDATION_TYPE, "Value is not a valid number."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE", "true", "1"})
    void true_values(String value) {
        assertThat(validate(FieldType.BOOLEAN, value)).isEqualTo(new ValidationResult.Valid(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"False", "0"})
    void false_values(String value) {
        assertThat(validate(FieldType.BOOLEAN, value)).isEqualTo(new ValidationResult.Valid(false));
    }

    @Test
    void a_number_may_have_up_to_1000_characters() {
        assertThat(validate(FieldType.NUMBER, "9".repeat(1000))).isInstanceOf(ValidationResult.Valid.class);
        assertThat(validate(FieldType.NUMBER, "9".repeat(1001)))
                .isEqualTo(new ValidationResult.Invalid(RowErrorCode.VALIDATION_TYPE, "Value is not a valid number."));
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void a_huge_numeric_cell_is_rejected_before_parsing() {
        // new BigDecimal of a million digits took 20 s.
        assertThat(validate(FieldType.NUMBER, "1".repeat(1_000_000))).isInstanceOf(ValidationResult.Invalid.class);
    }

    @Test
    void only_ascii_spellings_of_true_and_false_count() {
        // U+017F (long s) upper-cases to S, so equalsIgnoreCase would take "fal\u017Fe" for "false".
        assertThat(validate(FieldType.BOOLEAN, "fal\u017Fe")).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(validate(FieldType.BOOLEAN, "FaLsE")).isEqualTo(new ValidationResult.Valid(false));
    }

    @Test
    void anything_else_is_not_a_boolean() {
        assertThat(validate(FieldType.BOOLEAN, "yes")).isEqualTo(new ValidationResult.Invalid(
                RowErrorCode.VALIDATION_TYPE, "Value is not a valid boolean (true/false/1/0)."));
    }

    @Test
    void iso_dates_become_local_dates() {
        assertThat(validate(FieldType.DATE, "1990-12-25")).isEqualTo(new ValidationResult.Valid(LocalDate.of(1990, 12, 25)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-02-30", "25/12/1990", "1990-1-5", "+19900-12-25", "0000-01-01", "1990-12-25T00:00"})
    void anything_else_is_not_a_date(String value) {
        assertThat(validate(FieldType.DATE, value)).isEqualTo(
                new ValidationResult.Invalid(RowErrorCode.VALIDATION_TYPE, "Value is not a valid date (yyyy-MM-dd)."));
    }

    @Test
    void an_email_field_needs_an_email_address() {
        assertThat(validate(FieldType.EMAIL, "an@example.com")).isEqualTo(new ValidationResult.Valid("an@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"an@example", "an example@x.com", "a@b@c.com"})
    void anything_else_is_not_an_email_address(String value) {
        assertThat(validate(FieldType.EMAIL, value)).isEqualTo(
                new ValidationResult.Invalid(RowErrorCode.VALIDATION_EMAIL, "Value is not a valid email address."));
        assertThat(rule.type()).isEqualTo("type");
    }

    private ValidationResult validate(FieldType type, String value) {
        return rule.validate(value, new ValidationContext("field", type, 2, null));
    }
}
