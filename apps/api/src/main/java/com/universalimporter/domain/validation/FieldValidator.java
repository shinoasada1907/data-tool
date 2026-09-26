package com.universalimporter.domain.validation;

import com.universalimporter.domain.common.RowErrorCode;
import com.universalimporter.domain.common.TextValues;
import com.universalimporter.domain.common.ThrottledWarnings;
import com.universalimporter.domain.schema.FieldType;
import com.universalimporter.domain.schema.TargetField;

import java.util.List;
import java.util.Map;

/**
 * Validates one field of one row after transformation (design V2). Rules run in a fixed order, required → type →
 * email → unique, whatever the configuration order, and only the first failure is reported. An empty optional value
 * skips every rule. A rule that throws becomes a failure, so one bad row cannot stop the import.
 * <p>
 * The caller brackets each row on the {@link UniqueTracker}: {@code beginRow} before its first field, then
 * {@code commitRow} if no field of the row failed, otherwise {@code discardRow}.
 */
public final class FieldValidator {

    private static final Map<String, RowErrorCode> CODES = Map.of(
            "required", RowErrorCode.VALIDATION_REQUIRED,
            "type", RowErrorCode.VALIDATION_TYPE,
            "email", RowErrorCode.VALIDATION_EMAIL,
            "unique", RowErrorCode.VALIDATION_UNIQUE);

    private final ValidationRegistry registry;
    /** The JDK's logger: the domain stays free of logging libraries; Spring Boot routes it to the app's log. */
    private final ThrottledWarnings warnings = new ThrottledWarnings(System.getLogger(FieldValidator.class.getName()));

    public FieldValidator(ValidationRegistry registry) {
        this.registry = registry;
    }

    public FieldValidation validate(TargetField field, List<ValidationRuleConfig> userRules, String value,
                                    int rowNumber, UniqueTracker tracker) {
        ValidationContext context = new ValidationContext(field.name(), field.type(), rowNumber, tracker);
        if (TextValues.isEmpty(value)) {
            // Empty: only "required" can fail; an optional field is simply absent (null in the output).
            return field.required() ? run("required", value, context) : FieldValidation.ok(null);
        }
        FieldValidation typed = run("type", value, context);
        if (typed.failed()) {
            // A bad address in a field of type email is an email error, whichever rule found it (design D10).
            ValidationFailure failure = typed.failure();
            boolean emailField = field.type() == FieldType.EMAIL && failure.code() == RowErrorCode.VALIDATION_EMAIL;
            return emailField ? FieldValidation.failed(new ValidationFailure("email", failure.code(), failure.message()))
                    : typed;
        }
        Object current = typed.value();
        for (String rule : List.of("email", "unique")) {
            if (configured(userRules, field.name(), rule)) {
                FieldValidation result = run(rule, current, context);
                if (result.failed()) {
                    return result;
                }
                current = result.value();
            }
        }
        return FieldValidation.ok(current);
    }

    private FieldValidation run(String type, Object value, ValidationContext context) {
        ValidationRule rule = registry.find(type)
                .orElseThrow(() -> new IllegalStateException("No validation rule of type " + type));
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
            return FieldValidation.failed(new ValidationFailure(type, CODES.get(type),
                    "Unexpected error while applying rule '" + type + "'."));
        }
    }

    /** Only this field's rules count, even if the caller passes the whole configuration. */
    private static boolean configured(List<ValidationRuleConfig> userRules, String fieldName, String type) {
        return userRules.stream().anyMatch(rule -> fieldName.equals(rule.targetField()) && type.equals(rule.type()));
    }
}
