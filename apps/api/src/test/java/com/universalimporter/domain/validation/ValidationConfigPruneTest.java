package com.universalimporter.domain.validation;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.Pruned;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationConfigPruneTest {

    @Test
    void rules_of_removed_fields_are_dropped_with_one_warning_per_field() {
        Pruned<ValidationConfig> pruned = config(rule("phone", "unique"), rule("note", "email")).prunedFor(note("string"));

        assertThat(pruned.section().validations()).containsExactly(rule("note", "email"));
        assertThat(pruned.warnings()).containsExactly(new ProblemItem("phone", "CONFIG_PRUNED",
                "Validation rules removed because field 'phone' no longer exists."));
    }

    @Test
    void the_email_rule_goes_when_the_field_stops_being_a_string_but_unique_stays() {
        Pruned<ValidationConfig> pruned = config(rule("note", "email"), rule("note", "unique")).prunedFor(note("number"));

        assertThat(pruned.section().validations()).containsExactly(rule("note", "unique"));
        assertThat(pruned.warnings()).containsExactly(new ProblemItem("note", "CONFIG_PRUNED",
                "Rule 'email' removed because field 'note' is no longer of type string."));
    }

    @Test
    void the_email_rule_also_goes_when_the_field_becomes_an_email_field() {
        Pruned<ValidationConfig> pruned = config(rule("note", "email")).prunedFor(note("email"));

        assertThat(pruned.section().validations()).isEmpty();
        assertThat(pruned.warnings()).extracting(ProblemItem::message)
                .containsExactly("Rule 'email' removed because field 'note' is no longer of type string.");
    }

    @Test
    void unique_survives_a_type_change() {
        ValidationConfig config = config(rule("note", "unique"));

        assertThat(config.prunedFor(note("number"))).isEqualTo(new Pruned<>(config, List.of()));
    }

    private static TargetSchema note(String type) {
        return TargetSchema.define(List.of(new FieldSpec("note", type, false, 0)));
    }

    private static ValidationConfig config(ValidationRuleConfig... rules) {
        return new ValidationConfig(List.of(rules));
    }

    private static ValidationRuleConfig rule(String field, String type) {
        return new ValidationRuleConfig(field, type, null);
    }
}
