package com.universalimporter.domain.validation;

import com.universalimporter.domain.schema.TargetField;
import com.universalimporter.domain.schema.TargetSchema;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The user's validation rules of a session: only {@code email} and {@code unique} are stored, since
 * {@code required} and {@code type} come from the schema (spec: validation).
 */
public record ValidationConfig(List<ValidationRuleConfig> validations) {

    /** email before unique, the order they run in. */
    private static final List<String> RULE_ORDER = List.of("email", "unique");

    public ValidationConfig {
        validations = List.copyOf(validations);
    }

    public static ValidationConfig empty() {
        return new ValidationConfig(List.of());
    }

    public List<ValidationRuleConfig> rulesFor(String fieldName) {
        return validations.stream().filter(rule -> Objects.equals(rule.targetField(), fieldName)).toList();
    }

    /**
     * Sorted by the field's position in {@code schema}, then email before unique (design V5), so equal content
     * always compares and hashes equal. Rules of fields missing from the schema go last.
     */
    public ValidationConfig normalized(TargetSchema schema) {
        Comparator<ValidationRuleConfig> byField = Comparator.comparingInt(rule -> schema.field(rule.targetField())
                .map(TargetField::order).orElse(Integer.MAX_VALUE));
        Comparator<ValidationRuleConfig> byRule = Comparator.comparingInt(rule -> rank(rule.type()));
        return new ValidationConfig(validations.stream()
                .sorted(byField.thenComparing(byRule).thenComparing(ValidationRuleConfig::type,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList());
    }

    private static int rank(String type) {
        int rank = RULE_ORDER.indexOf(type);
        return rank < 0 ? RULE_ORDER.size() : rank;
    }
}
