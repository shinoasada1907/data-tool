package com.universaldatatools.tools.importer.domain.validation;

import com.universaldatatools.core.schema.FieldConstraints;
import com.universaldatatools.core.schema.SchemaField;
import com.universaldatatools.core.validate.FieldRulePlan;
import com.universaldatatools.core.validate.FieldRuleRunner;
import com.universaldatatools.core.validate.FieldValidation;
import com.universaldatatools.core.validate.UniqueIndex;
import com.universaldatatools.core.validate.ValidationRegistry;
import com.universaldatatools.tools.importer.domain.schema.TargetField;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates one field of one row after transformation (design V2), on the shared rule engine (core-03 SR6): rules
 * run in the fixed order required → type → email → unique, whatever the configuration order, and only the first
 * failure is reported. Importer fields have no constraints; {@code email} and {@code unique} come from the
 * session's validation config.
 * <p>
 * The caller brackets each row on the {@link UniqueIndex} (scope {@code VALID_ROWS}): {@code beginRow} before its
 * first field, then {@code commitRow} if no field of the row failed, otherwise {@code discardRow}.
 */
public final class FieldValidator {

    private static final Set<String> CONFIGURABLE = Set.of("email", "unique");

    private final ValidationRegistry registry;
    private final FieldRuleRunner runner = new FieldRuleRunner();

    public FieldValidator(ValidationRegistry registry) {
        this.registry = registry;
    }

    /** The field's plan; build it once per run. Only this field's rules count, even if given the whole config. */
    public FieldRulePlan plan(TargetField field, List<ValidationRuleConfig> userRules) {
        Set<String> extra = userRules.stream()
                .filter(rule -> field.name().equals(rule.targetField()) && CONFIGURABLE.contains(rule.type()))
                .map(ValidationRuleConfig::type)
                .collect(Collectors.toUnmodifiableSet());
        SchemaField schemaField = new SchemaField(field.name(), field.type(), field.required(), FieldConstraints.NONE);
        return FieldRulePlan.of(schemaField, extra, registry);
    }

    public FieldValidation validate(FieldRulePlan plan, String value, int rowNumber, UniqueIndex unique) {
        return runner.validate(plan, value, rowNumber, unique);
    }

    public FieldValidation validate(TargetField field, List<ValidationRuleConfig> userRules, String value,
                                    int rowNumber, UniqueIndex unique) {
        return validate(plan(field, userRules), value, rowNumber, unique);
    }
}
