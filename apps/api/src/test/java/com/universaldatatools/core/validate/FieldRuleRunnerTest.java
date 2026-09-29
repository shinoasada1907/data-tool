package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.core.schema.FieldConstraints;
import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.core.schema.SchemaField;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** core-03 task 5: plans in the fixed order, first failure wins, empty optional values skip everything. */
class FieldRuleRunnerTest {

    private final FieldRuleRunner runner = new FieldRuleRunner();
    private final UniqueIndex unique = new UniqueIndex(UniqueScope.ALL_ROWS);

    @Test
    void the_plan_follows_the_fixed_order() {
        SchemaField field = new SchemaField("code", FieldType.STRING, true,
                new FieldConstraints(true, null, null, 2, 5, "[A-Z]+", null, null));
        assertThat(FieldRulePlan.of(field, Set.of()).ruleTypes())
                .containsExactly("required", "type", "minLength", "maxLength", "pattern", "unique");
        SchemaField age = new SchemaField("age", FieldType.NUMBER, false,
                new FieldConstraints(false, BigDecimal.ONE, BigDecimal.TEN, null, null, null, null, null));
        assertThat(FieldRulePlan.of(age, Set.of("unique")).ruleTypes()).containsExactly("type", "min", "max", "unique");
    }

    @Test
    void the_first_failure_wins() {
        FieldValidation result = validate(string(new FieldConstraints(false, null, null, 5, null, "[A-Z]+", null, null)), "ab");
        assertThat(result.failure()).isEqualTo(new ValidationFailure("minLength", RowErrorCode.VALIDATION_MIN_LENGTH,
                "Value must have at least 5 characters."));
    }

    @Test
    void an_empty_optional_value_skips_every_rule() {
        FieldValidation result = validate(string(new FieldConstraints(false, null, null, null, null, "[A-Z]+", null, null)), "");
        assertThat(result).isEqualTo(FieldValidation.ok(null));
    }

    @Test
    void no_break_spaces_are_empty_for_a_required_field() {
        SchemaField field = new SchemaField("name", FieldType.STRING, true, FieldConstraints.NONE);
        assertThat(validate(field, "  ").failure()).isEqualTo(
                new ValidationFailure("required", RowErrorCode.VALIDATION_REQUIRED, "Value is required."));
    }

    @Test
    void a_bad_address_in_an_email_field_is_an_email_error() {
        SchemaField field = new SchemaField("email", FieldType.EMAIL, false, FieldConstraints.NONE);
        assertThat(validate(field, "a@").failure()).isEqualTo(
                new ValidationFailure("email", RowErrorCode.VALIDATION_EMAIL, "Value is not a valid email address."));
    }

    @Test
    void the_importer_email_rule_on_a_string_field() {
        FieldRulePlan plan = FieldRulePlan.of(string(FieldConstraints.NONE), Set.of("email"));
        assertThat(runner.validate(plan, "x", 2, open()).failure().code()).isEqualTo(RowErrorCode.VALIDATION_EMAIL);
    }

    @Test
    void a_value_of_the_wrong_type_never_reaches_the_range() {
        SchemaField field = new SchemaField("age", FieldType.NUMBER, false,
                new FieldConstraints(false, BigDecimal.ONE, null, null, null, null, null, null));
        assertThat(validate(field, "abc").failure().code()).isEqualTo(RowErrorCode.VALIDATION_TYPE);
    }

    @Test
    void a_rule_that_breaks_fails_with_its_own_code_and_no_detail() {
        FieldRulePlan plan = new FieldRulePlan(string(FieldConstraints.NONE), List.of(new TypeRule(), new BrokenMaxLength()));
        FieldValidation result = runner.validate(plan, "x", 2, open());
        assertThat(result.failure()).isEqualTo(new ValidationFailure("maxLength", RowErrorCode.VALIDATION_MAX_LENGTH,
                "Unexpected error while applying rule 'maxLength'."));
        assertThat(result.failure().message()).doesNotContain("SECRET");
    }

    private FieldValidation validate(SchemaField field, String value) {
        return runner.validate(FieldRulePlan.of(field, Set.of()), value, 2, open());
    }

    private UniqueIndex open() {
        try {
            unique.beginRow(2);
        } catch (IllegalStateException alreadyOpen) {
            // One row for the whole test.
        }
        return unique;
    }

    private static SchemaField string(FieldConstraints constraints) {
        return new SchemaField("code", FieldType.STRING, false, constraints);
    }

    private static final class BrokenMaxLength implements ValidationRule {

        @Override
        public String type() {
            return "maxLength";
        }

        @Override
        public RowErrorCode code() {
            return RowErrorCode.VALIDATION_MAX_LENGTH;
        }

        @Override
        public ValidationResult validate(Object value, ValidationContext context) {
            throw new IllegalStateException("SECRET");
        }
    }
}
