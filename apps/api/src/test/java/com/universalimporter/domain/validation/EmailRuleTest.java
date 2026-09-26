package com.universalimporter.domain.validation;

import com.universalimporter.domain.common.RowErrorCode;
import com.universalimporter.domain.schema.FieldType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailRuleTest {

    private static final ValidationContext CONTEXT = new ValidationContext("contact", FieldType.STRING, 2, null);

    private final EmailRule rule = new EmailRule();

    @Test
    void an_email_address_is_valid() {
        assertThat(rule.validate("an@example.com", CONTEXT)).isEqualTo(new ValidationResult.Valid("an@example.com"));
    }

    @Test
    void anything_else_is_not() {
        assertThat(rule.validate("an.example.com", CONTEXT)).isEqualTo(
                new ValidationResult.Invalid(RowErrorCode.VALIDATION_EMAIL, "Value is not a valid email address."));
        assertThat(rule.type()).isEqualTo("email");
    }
}
