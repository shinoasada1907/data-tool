package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.core.common.TextValues;

/** A required field needs a value that is not empty, no-break spaces counting as empty. Derived from the schema. */
public final class RequiredRule implements ValidationRule {

    @Override
    public String type() {
        return "required";
    }

    @Override
    public ValidationResult validate(Object value, ValidationContext context) {
        if (value == null || (value instanceof String text && TextValues.isEmpty(text))) {
            return new ValidationResult.Invalid(RowErrorCode.VALIDATION_REQUIRED, "Value is required.");
        }
        return new ValidationResult.Valid(value);
    }
}
