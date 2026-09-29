package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;

import java.math.BigDecimal;

/** A number must be at most a bound of the schema; naming the bound is fine, it is not data (core-03 SR5). */
public final class MaxRule implements ValidationRule {

    private final BigDecimal bound;

    public MaxRule(BigDecimal bound) {
        this.bound = bound;
    }

    @Override
    public String type() {
        return "max";
    }

    @Override
    public RowErrorCode code() {
        return RowErrorCode.VALIDATION_MAX;
    }

    @Override
    public ValidationResult validate(Object value, ValidationContext context) {
        return ((BigDecimal) value).compareTo(bound) > 0
                ? new ValidationResult.Invalid(code(), "Value must be at most " + bound.toPlainString() + ".")
                : new ValidationResult.Valid(value);
    }
}
