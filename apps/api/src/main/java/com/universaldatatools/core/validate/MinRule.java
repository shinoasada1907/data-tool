package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;

import java.math.BigDecimal;

/** A number must be at least a bound of the schema; naming the bound is fine, it is not data (core-03 SR5). */
public final class MinRule implements ValidationRule {

    private final BigDecimal bound;

    public MinRule(BigDecimal bound) {
        this.bound = bound;
    }

    @Override
    public String type() {
        return "min";
    }

    @Override
    public RowErrorCode code() {
        return RowErrorCode.VALIDATION_MIN;
    }

    @Override
    public ValidationResult validate(Object value, ValidationContext context) {
        return ((BigDecimal) value).compareTo(bound) < 0
                ? new ValidationResult.Invalid(code(), "Value must be at least " + bound.toPlainString() + ".")
                : new ValidationResult.Valid(value);
    }
}
