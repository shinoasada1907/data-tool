package com.universalimporter.domain.validation;

import com.universalimporter.domain.common.RowErrorCode;
import com.universalimporter.domain.schema.FieldType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredRuleTest {

    private static final ValidationContext CONTEXT = new ValidationContext("name", FieldType.STRING, 2, null);

    private final RequiredRule rule = new RequiredRule();

    @ParameterizedTest
    @ValueSource(strings = {"An", " a "})
    void a_value_is_present(String value) {
        assertThat(rule.validate(value, CONTEXT)).isEqualTo(new ValidationResult.Valid(value));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", " ", "  "})
    void an_empty_value_is_missing(String value) {
        assertThat(rule.validate(value, CONTEXT))
                .isEqualTo(new ValidationResult.Invalid(RowErrorCode.VALIDATION_REQUIRED, "Value is required."));
        assertThat(rule.type()).isEqualTo("required");
    }
}
