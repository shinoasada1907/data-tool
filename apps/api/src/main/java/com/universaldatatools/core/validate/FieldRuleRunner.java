package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.core.common.TextValues;
import com.universaldatatools.core.common.ThrottledWarnings;
import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.core.schema.SchemaField;

/**
 * Runs a {@link FieldRulePlan} on one value (core-03 SR6, V0.1 design V2). The first failing rule wins. An empty
 * value, no-break spaces counting as empty, only fails {@code required}; an optional field is then simply absent
 * ({@code null}). A rule that throws becomes a failure with the rule's code, so one bad value cannot stop a run.
 * <p>
 * The caller brackets each row on the {@link UniqueIndex}: {@code beginRow} before its first field, then
 * {@code commitRow} or {@code discardRow}.
 */
public final class FieldRuleRunner {

    /** The JDK's logger: the core stays free of logging libraries; Spring Boot routes it to the app's log. */
    private final ThrottledWarnings warnings = new ThrottledWarnings(System.getLogger(FieldRuleRunner.class.getName()));

    public FieldValidation validate(FieldRulePlan plan, String value, int rowNumber, UniqueIndex unique) {
        SchemaField field = plan.field();
        ValidationContext context =
                new ValidationContext(field.name(), field.type(), rowNumber, unique, field.constraints());
        if (TextValues.isEmpty(value)) {
            for (ValidationRule rule : plan.rules()) {
                if (rule.type().equals("required")) {
                    return run(rule, value, context);
                }
            }
            return FieldValidation.ok(null);
        }
        Object current = value;
        for (ValidationRule rule : plan.rules()) {
            if (rule.type().equals("required")) {
                continue;
            }
            FieldValidation result = run(rule, current, context);
            if (result.failed()) {
                return emailFieldAsEmail(field, result);
            }
            current = result.value();
        }
        return FieldValidation.ok(current);
    }

    /** A bad address in a field of type email is an email error, whichever rule found it (V0.1 design D10). */
    private static FieldValidation emailFieldAsEmail(SchemaField field, FieldValidation result) {
        ValidationFailure failure = result.failure();
        boolean emailField = field.type() == FieldType.EMAIL && failure.code() == RowErrorCode.VALIDATION_EMAIL;
        return emailField ? FieldValidation.failed(new ValidationFailure("email", failure.code(), failure.message()))
                : result;
    }

    private FieldValidation run(ValidationRule rule, Object value, ValidationContext context) {
        String type = rule.type();
        try {
            return switch (rule.validate(value, context)) {
                case ValidationResult.Valid valid -> FieldValidation.ok(valid.value());
                case ValidationResult.Invalid invalid ->
                        FieldValidation.failed(new ValidationFailure(type, invalid.code(), invalid.message()));
            };
        } catch (RuntimeException bug) {
            // Not the exception itself: its message may quote the cell value (design D13).
            StackTraceElement[] trace = bug.getStackTrace();
            warnings.warn(type, () -> "Validation rule " + type + " failed unexpectedly on field "
                    + context.fieldName() + ": " + bug.getClass().getName() + (trace.length > 0 ? " at " + trace[0] : ""));
            return FieldValidation.failed(new ValidationFailure(type, rule.code(),
                    "Unexpected error while applying rule '" + type + "'."));
        }
    }
}
