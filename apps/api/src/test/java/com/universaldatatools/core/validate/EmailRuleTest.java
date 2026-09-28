package com.universaldatatools.core.validate;

import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.core.common.RowErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EmailRuleTest {

    private static final ValidationContext CONTEXT = new ValidationContext("contact", FieldType.STRING, 2, null);

    private final EmailRule rule = new EmailRule();

    @Test
    void an_email_address_is_valid() {
        assertThat(rule.validate("an@example.com", CONTEXT)).isEqualTo(new ValidationResult.Valid("an@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"an@y.com\u00A0", "a\u200B@y.com", "a\u0000@y.com", "a b@y.com", "a@b@y.com", "@y.com",
            "a@.com", "a@y.", "a@y", "a@ycom"})
    void invisible_characters_and_malformed_addresses_are_not_email_addresses(String value) {
        assertThat(rule.validate(value, CONTEXT)).isInstanceOf(ValidationResult.Invalid.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@b.c", "an.nguyen+tag@mail.example.vn", "x@y.z.w"})
    void ordinary_addresses_are_valid(String value) {
        assertThat(rule.validate(value, CONTEXT)).isEqualTo(new ValidationResult.Valid(value));
    }

    @Test
    void an_address_longer_than_254_characters_is_not_valid() {
        String local = "a".repeat(64);
        String fits = local + "@" + "b".repeat(254 - 64 - 1 - 4) + ".com";
        String tooLong = local + "@" + "b".repeat(254 - 64 - 4) + ".com";

        assertThat(fits).hasSize(254);
        assertThat(rule.validate(fits, CONTEXT)).isInstanceOf(ValidationResult.Valid.class);
        assertThat(rule.validate(tooLong, CONTEXT)).isInstanceOf(ValidationResult.Invalid.class);
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void a_huge_cell_is_checked_in_linear_time() {
        // Backtracking on this shape took 100 s for 200 KB with the old regex.
        String hostile = "a@" + "a.".repeat(100_000) + " ";

        assertThat(rule.validate(hostile, CONTEXT)).isInstanceOf(ValidationResult.Invalid.class);
        assertThat(new TypeRule().validate(hostile, new ValidationContext("email", FieldType.EMAIL, 2, null)))
                .isInstanceOf(ValidationResult.Invalid.class);
    }

    @Test
    void anything_else_is_not() {
        assertThat(rule.validate("an.example.com", CONTEXT)).isEqualTo(
                new ValidationResult.Invalid(RowErrorCode.VALIDATION_EMAIL, "Value is not a valid email address."));
        assertThat(rule.type()).isEqualTo("email");
    }
}
