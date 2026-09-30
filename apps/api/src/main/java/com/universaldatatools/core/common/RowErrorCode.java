package com.universaldatatools.core.common;

/** Codes of the errors recorded against one row of the result; the name is the API value. */
public enum RowErrorCode {
    TRANSFORMATION_FAILED,
    VALIDATION_REQUIRED,
    VALIDATION_TYPE,
    VALIDATION_EMAIL,
    VALIDATION_UNIQUE,
    VALIDATION_MIN,
    VALIDATION_MAX,
    VALIDATION_MIN_LENGTH,
    VALIDATION_MAX_LENGTH,
    VALIDATION_PATTERN,
    VALIDATION_DATE_FORMAT
}
