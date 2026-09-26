package com.universalimporter.domain.validation;

import com.universalimporter.domain.common.RowErrorCode;
import com.universalimporter.domain.schema.FieldType;
import com.universalimporter.domain.schema.TargetField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FieldValidatorTest {

    private final FieldValidator validator = new FieldValidator(ValidationRegistry.standard());
    private final UniqueTracker tracker = new UniqueTracker();

    @Test
    void an_empty_required_field_only_reports_required() {
        FieldValidation result = validate(field("email", FieldType.EMAIL, true), List.of(rule("email", "unique")), "");

        assertThat(result.failure()).isEqualTo(
                new ValidationFailure("required", RowErrorCode.VALIDATION_REQUIRED, "Value is required."));
    }

    @Test
    void a_bad_email_in_an_email_field_is_reported_as_the_email_rule() {
        FieldValidation result = validate(field("email", FieldType.EMAIL, true), List.of(rule("email", "unique")), "bad");

        assertThat(result.failure()).isEqualTo(
                new ValidationFailure("email", RowErrorCode.VALIDATION_EMAIL, "Value is not a valid email address."));
    }

    @Test
    void an_empty_optional_field_skips_every_rule() {
        FieldValidation result = validate(field("age", FieldType.NUMBER, false), List.of(rule("age", "unique")), "   ");

        assertThat(result.failed()).isFalse();
        assertThat(result.value()).isNull();
    }

    @Test
    void a_type_error_is_reported_as_the_type_rule() {
        FieldValidation result = validate(field("age", FieldType.NUMBER, false), List.of(), "abc");

        assertThat(result.failure()).isEqualTo(
                new ValidationFailure("type", RowErrorCode.VALIDATION_TYPE, "Value is not a valid number."));
    }

    @Test
    void the_email_rule_checks_string_fields() {
        FieldValidation result = validate(field("contact", FieldType.STRING, false), List.of(rule("contact", "email")), "x");

        assertThat(result.failure()).isEqualTo(
                new ValidationFailure("email", RowErrorCode.VALIDATION_EMAIL, "Value is not a valid email address."));
    }

    @Test
    void rules_run_in_a_fixed_order_whatever_the_configuration_order() {
        TargetField note = field("note", FieldType.STRING, false);
        List<ValidationRuleConfig> rules = List.of(rule("note", "unique"), rule("note", "email"));
        assertThat(validator.validate(note, rules, "a@x.com", 2, tracker).failed()).isFalse();
        tracker.commitRow(2);

        FieldValidation result = validator.validate(note, rules, "a@x.com", 3, tracker);

        assertThat(result.failure()).isEqualTo(new ValidationFailure("unique", RowErrorCode.VALIDATION_UNIQUE,
                "Duplicate value; first seen in row 2."));
    }

    @Test
    void values_come_out_converted() {
        assertThat(validate(field("active", FieldType.BOOLEAN, false), List.of(), "1").value()).isEqualTo(Boolean.TRUE);
        assertThat(validate(field("country", FieldType.STRING, true), List.of(), "VN").value()).isEqualTo("VN");
    }

    @Test
    void a_bug_in_a_rule_becomes_a_failure_without_its_message() {
        List<ValidationRule> rules = new ArrayList<>(List.of(new RequiredRule(), new TypeRule(), new EmailRule()));
        rules.add(new ExplodingRule());
        FieldValidator withBug = new FieldValidator(new ValidationRegistry(rules));

        FieldValidation result = withBug.validate(field("code", FieldType.STRING, false),
                List.of(rule("code", "unique")), "x", 5, tracker);

        assertThat(result.failure()).isEqualTo(new ValidationFailure("unique", RowErrorCode.VALIDATION_UNIQUE,
                "Unexpected error while applying rule 'unique'."));
        assertThat(result.failure().message()).doesNotContain("boom");
    }

    private FieldValidation validate(TargetField field, List<ValidationRuleConfig> rules, String value) {
        return validator.validate(field, rules, value, 2, tracker);
    }

    private static TargetField field(String name, FieldType type, boolean required) {
        return new TargetField(name, type, required, 0);
    }

    private static ValidationRuleConfig rule(String field, String type) {
        return new ValidationRuleConfig(field, type, null);
    }

    /** A unique rule with a bug. */
    static final class ExplodingRule implements ValidationRule {

        @Override
        public String type() {
            return "unique";
        }

        @Override
        public ValidationResult validate(Object value, ValidationContext context) {
            throw new IllegalStateException("boom");
        }
    }
}
