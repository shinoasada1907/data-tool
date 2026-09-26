package com.universalimporter.domain.validation;

import com.universalimporter.domain.common.RowErrorCode;

/** Outcome of one rule on one value. */
public sealed interface ValidationResult {

    /** @param value the value for the next rule, converted to its real type by the {@code type} rule */
    record Valid(Object value) implements ValidationResult {
    }

    /** @param message English, never containing the cell value (design D13) */
    record Invalid(RowErrorCode code, String message) implements ValidationResult {
    }
}
