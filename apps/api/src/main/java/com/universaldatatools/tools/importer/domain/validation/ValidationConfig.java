package com.universaldatatools.tools.importer.domain.validation;

import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.tools.importer.domain.config.ConfigPruner;
import com.universaldatatools.tools.importer.domain.config.FieldScopedSection;
import com.universaldatatools.tools.importer.domain.config.Pruned;
import com.universaldatatools.tools.importer.domain.config.WarningCode;
import com.universaldatatools.tools.importer.domain.schema.TargetField;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The user's validation rules of a session: only {@code email} and {@code unique} are stored, since
 * {@code required} and {@code type} come from the schema (spec: validation).
 */
public record ValidationConfig(List<ValidationRuleConfig> validations) implements FieldScopedSection<ValidationConfig> {

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

    /**
     * What is left valid after a schema change (design V6): rules of removed fields go (through the shared
     * {@link ConfigPruner}), and so does an {@code email} rule on a field that is no longer a string, even one that
     * became an email field. {@code unique} survives a type change. Each removal comes with a warning.
     */
    public Pruned<ValidationConfig> prunedFor(TargetSchema newSchema) {
        List<ProblemItem> warnings = new ArrayList<>();
        ValidationConfig kept = ConfigPruner.prune(this, newSchema.fieldNames(), warnings);
        List<ValidationRuleConfig> rules = new ArrayList<>();
        for (ValidationRuleConfig rule : kept.validations()) {
            boolean stillString = newSchema.field(rule.targetField())
                    .map(field -> field.type() == FieldType.STRING).orElse(false);
            if ("email".equals(rule.type()) && !stillString) {
                warnings.add(new ProblemItem(rule.targetField(), WarningCode.CONFIG_PRUNED.name(),
                        "Rule 'email' removed because field '" + rule.targetField() + "' is no longer of type string."));
            } else {
                rules.add(rule);
            }
        }
        return new Pruned<>(rules.size() == kept.validations().size() ? kept : new ValidationConfig(rules), warnings);
    }

    @Override
    public String sectionLabel() {
        return "Validation rules";
    }

    @Override
    public Set<String> referencedFields() {
        Set<String> fields = new LinkedHashSet<>();
        validations.forEach(rule -> fields.add(rule.targetField()));
        return Collections.unmodifiableSet(fields);
    }

    @Override
    public ValidationConfig retainFields(Set<String> fieldNames) {
        return new ValidationConfig(validations.stream().filter(rule -> fieldNames.contains(rule.targetField())).toList());
    }

    /** The wording of the spec (validation, "Prune validation khi schema đổi"). */
    @Override
    public String prunedMessage(String field) {
        return "Validation rules removed because field '" + field + "' no longer exists.";
    }

    private static int rank(String type) {
        int rank = RULE_ORDER.indexOf(type);
        return rank < 0 ? RULE_ORDER.size() : rank;
    }
}
