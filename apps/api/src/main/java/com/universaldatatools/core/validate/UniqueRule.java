package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.core.table.Hash128;

import java.util.OptionalInt;

/**
 * A value may appear only once per field; which earlier rows count depends on the {@link UniqueIndex}'s scope.
 */
public final class UniqueRule implements ValidationRule {

    @Override
    public String type() {
        return "unique";
    }

    @Override
    public RowErrorCode code() {
        return RowErrorCode.VALIDATION_UNIQUE;
    }

    @Override
    public ValidationResult validate(Object value, ValidationContext context) {
        Hash128 key = context.unique().key(context.fieldName(), value);
        OptionalInt firstRow = context.unique().firstRowOf(key);
        if (firstRow.isPresent()) {
            return new ValidationResult.Invalid(RowErrorCode.VALIDATION_UNIQUE,
                    "Duplicate value; first seen in row " + firstRow.getAsInt() + ".");
        }
        context.unique().record(key);
        return new ValidationResult.Valid(value);
    }
}
