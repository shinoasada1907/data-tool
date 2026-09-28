package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;

import java.util.Optional;

/**
 * A value may appear in only one valid row per field. The value is staged for the row being checked; it only
 * counts once the row is committed (see {@link UniqueTracker}).
 */
public final class UniqueRule implements ValidationRule {

    @Override
    public String type() {
        return "unique";
    }

    @Override
    public ValidationResult validate(Object value, ValidationContext context) {
        Object canonical = UniqueTracker.canonical(value);
        Optional<Integer> firstRow = context.uniqueTracker().firstRowOf(context.fieldName(), canonical);
        if (firstRow.isPresent()) {
            return new ValidationResult.Invalid(RowErrorCode.VALIDATION_UNIQUE,
                    "Duplicate value; first seen in row " + firstRow.get() + ".");
        }
        context.uniqueTracker().stage(context.fieldName(), canonical);
        return new ValidationResult.Valid(value);
    }
}
