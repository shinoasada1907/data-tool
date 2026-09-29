package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;

/** A string field that must hold an email address; same definition as fields of type email. */
public final class EmailRule implements ValidationRule {

    @Override
    public String type() {
        return "email";
    }

    @Override
    public RowErrorCode code() {
        return RowErrorCode.VALIDATION_EMAIL;
    }

    @Override
    public ValidationResult validate(Object value, ValidationContext context) {
        return value instanceof String text && EmailAddresses.isValid(text)
                ? new ValidationResult.Valid(value)
                : new ValidationResult.Invalid(RowErrorCode.VALIDATION_EMAIL, EmailAddresses.INVALID);
    }
}
