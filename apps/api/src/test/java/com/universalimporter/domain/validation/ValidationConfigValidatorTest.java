package com.universalimporter.domain.validation;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationConfigValidatorTest {

    private static final TargetSchema SCHEMA = TargetSchema.define(List.of(
            new FieldSpec("name", "string", true, 0),
            new FieldSpec("email", "email", false, 1),
            new FieldSpec("age", "number", false, 2),
            new FieldSpec("note", "string", false, 3)));

    private final ValidationConfigValidator validator = new ValidationConfigValidator();

    @Test
    void email_and_unique_rules_are_kept() {
        assertThat(check(rule("email", "unique")).effective().validations()).containsExactly(rule("email", "unique"));
        ValidationConfigCheck both = check(rule("email", "unique"), rule("note", "email"));
        assertThat(both.effective().validations()).containsExactly(rule("email", "unique"), rule("note", "email"));
        assertThat(both.warnings()).isEmpty();
        assertThat(both.errors()).isEmpty();
    }

    @Test
    void rules_derived_from_the_schema_are_ignored_with_a_warning() {
        ValidationConfigCheck required = check(rule("name", "required"));
        assertThat(required.effective().validations()).isEmpty();
        assertThat(required.warnings()).containsExactly(new ProblemItem("name", "RULE_IMPLIED_BY_SCHEMA",
                "Rule 'required' is derived from the schema and was ignored."));

        assertThat(check(rule("age", "type")).warnings()).containsExactly(new ProblemItem("age", "RULE_IMPLIED_BY_SCHEMA",
                "Rule 'type' is derived from the schema and was ignored."));

        ValidationConfigCheck email = check(rule("email", "email"));
        assertThat(email.effective().validations()).isEmpty();
        assertThat(email.warnings()).containsExactly(new ProblemItem("email", "RULE_IMPLIED_BY_SCHEMA",
                "Rule 'email' is implied by field type email and was ignored."));
    }

    @Test
    void email_only_applies_to_string_fields() {
        assertThat(errors(rule("age", "email"))).containsExactly(error("age", "Rule 'email' only applies to fields of type string."));
    }

    @Test
    void the_target_field_must_exist() {
        assertThat(errors(rule("phone", "unique"))).containsExactly(error("phone", "Target field does not exist."));
    }

    @Test
    void the_rule_must_be_known() {
        assertThat(errors(rule("name", "regex"))).containsExactly(error("name", "Unknown validation rule 'regex'."));
    }

    @Test
    void a_rule_appears_once_per_field() {
        assertThat(errors(rule("note", "unique"), rule("note", "unique")))
                .containsExactly(error("note", "Duplicate rule 'unique' for field 'note'."));
    }

    @Test
    void rules_take_no_parameters() {
        assertThat(errors(new ValidationRuleConfig("note", "unique", Map.of("foo", "1"))))
                .containsExactly(error("note", "Unknown parameter 'foo' for 'unique'."));
    }

    @Test
    void the_effective_configuration_is_in_schema_then_rule_order() {
        assertThat(check(rule("note", "unique"), rule("note", "email"), rule("name", "unique")).effective().validations())
                .containsExactly(rule("name", "unique"), rule("note", "email"), rule("note", "unique"));
    }

    @Test
    void every_error_is_reported_in_input_order_and_nothing_is_effective() {
        ValidationConfigCheck check = check(rule("age", "email"), rule("phone", "unique"));

        assertThat(check.errors()).containsExactly(
                error("age", "Rule 'email' only applies to fields of type string."),
                error("phone", "Target field does not exist."));
        assertThat(check.effective()).isNull();
    }

    private ValidationConfigCheck check(ValidationRuleConfig... rules) {
        return validator.check(new ValidationConfig(List.of(rules)), SCHEMA);
    }

    private List<ProblemItem> errors(ValidationRuleConfig... rules) {
        return check(rules).errors();
    }

    private static ValidationRuleConfig rule(String field, String type) {
        return new ValidationRuleConfig(field, type, null);
    }

    private static ProblemItem error(String field, String message) {
        return new ProblemItem(field, "CONFIG_INVALID", message);
    }
}
