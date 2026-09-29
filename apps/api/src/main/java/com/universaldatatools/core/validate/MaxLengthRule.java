package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;

/** A string must have at most N characters, counted in Unicode code points (core-03 SR5). */
public final class MaxLengthRule implements ValidationRule {

    private final int limit;

    public MaxLengthRule(int limit) {
        this.limit = limit;
    }

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
        String text = (String) value;
        return text.codePointCount(0, text.length()) > limit
                ? new ValidationResult.Invalid(code(), "Value must have at most " + limit + " characters.")
                : new ValidationResult.Valid(value);
    }
}
