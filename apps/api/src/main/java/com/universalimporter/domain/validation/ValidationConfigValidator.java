package com.universalimporter.domain.validation;

import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.WarningCode;
import com.universalimporter.domain.schema.FieldType;
import com.universalimporter.domain.schema.TargetField;
import com.universalimporter.domain.schema.TargetSchema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the rules a client sent against the schema (design V5). Every error is reported at once; rules the schema
 * already implies are dropped with a warning; what is left is the effective configuration.
 */
public final class ValidationConfigValidator {

    private static final Set<String> KNOWN = Set.of("required", "type", "email", "unique");
    private static final Set<String> FROM_SCHEMA = Set.of("required", "type");

    public ValidationConfigCheck check(ValidationConfig config, TargetSchema schema) {
        List<ProblemItem> errors = new ArrayList<>();
        List<ProblemItem> warnings = new ArrayList<>();
        List<ValidationRuleConfig> effective = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ValidationRuleConfig rule : config.validations()) {
            String field = rule.targetField();
            String type = rule.type();
            Optional<TargetField> target = field == null ? Optional.empty() : schema.field(field);
            // Set.of rejects contains(null): a rule sent without a type would otherwise be a 500.
            boolean known = type != null && KNOWN.contains(type);
            List<String> problems = new ArrayList<>();
            if (target.isEmpty()) {
                problems.add("Target field does not exist.");
            }
            if (!known) {
                problems.add("Unknown validation rule '" + type + "'.");
            }
            if (target.isPresent() && "email".equals(type) && !canHoldEmail(target.get().type())) {
                problems.add("Rule 'email' only applies to fields of type string.");
            }
            if (target.isPresent() && known && !seen.add(field + "\u0000" + type)) {
                problems.add("Duplicate rule '" + type + "' for field '" + field + "'.");
            }
            if (known) {
                rule.params().keySet().stream().sorted()
                        .forEach(name -> problems.add("Unknown parameter '" + name + "' for '" + type + "'."));
            }
            problems.forEach(message -> errors.add(new ProblemItem(field, ErrorCode.CONFIG_INVALID.name(), message)));
            if (problems.isEmpty()) {
                keepOrIgnore(rule, target.get(), effective, warnings);
            }
        }
        ValidationConfig kept = errors.isEmpty() ? new ValidationConfig(effective).normalized(schema) : null;
        return new ValidationConfigCheck(kept, warnings, errors);
    }

    private static void keepOrIgnore(ValidationRuleConfig rule, TargetField target, List<ValidationRuleConfig> effective,
                                     List<ProblemItem> warnings) {
        if (FROM_SCHEMA.contains(rule.type())) {
            warnings.add(implied(rule, "Rule '" + rule.type() + "' is derived from the schema and was ignored."));
        } else if ("email".equals(rule.type()) && target.type() == FieldType.EMAIL) {
            warnings.add(implied(rule, "Rule 'email' is implied by field type email and was ignored."));
        } else {
            effective.add(rule);
        }
    }

    /** String fields take the rule; email fields already have it; other types can never hold an address. */
    private static boolean canHoldEmail(FieldType type) {
        return type == FieldType.STRING || type == FieldType.EMAIL;
    }

    private static ProblemItem implied(ValidationRuleConfig rule, String message) {
        return new ProblemItem(rule.targetField(), WarningCode.RULE_IMPLIED_BY_SCHEMA.name(), message);
    }
}
