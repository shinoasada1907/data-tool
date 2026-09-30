package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;

/** A string must have at least N characters, counted in Unicode code points (core-03 SR5). */
public final class MinLengthRule implements ValidationRule {

    private final int limit;

    public MinLengthRule(int limit) {
        this.limit = limit;
    }

    @Override
    public String type() {
        return "minLength";
    }

    @Override
    public RowErrorCode code() {
        return RowErrorCode.VALIDATION_MIN_LENGTH;
    }

    @Override
    public ValidationResult validate(Object value, ValidationContext context) {
        String text = (String) value;
        return text.codePointCount(0, text.length()) < limit
                ? new ValidationResult.Invalid(code(), "Value must have at least " + limit + " characters.")
                : new ValidationResult.Valid(value);
    }
}
