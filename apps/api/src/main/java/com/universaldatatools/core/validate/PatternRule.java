package com.universaldatatools.core.validate;

import com.google.re2j.Pattern;
import com.universaldatatools.core.common.RowErrorCode;

/**
 * A string must match a pattern as a whole. RE2 runs in time linear in the value, so no pattern, however hostile,
 * can hang a run (core-03 SR5, SR10). Compiled once per run, by the plan.
 */
public final class PatternRule implements ValidationRule {

    private final Pattern pattern;

    public PatternRule(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public String type() {
        return "pattern";
    }

    @Override
    public RowErrorCode code() {
        return RowErrorCode.VALIDATION_PATTERN;
    }

    @Override
    public ValidationResult validate(Object value, ValidationContext context) {
        return pattern.matches((String) value)
                ? new ValidationResult.Valid(value)
                : new ValidationResult.Invalid(code(), "Value does not match the required pattern.");
    }
}
