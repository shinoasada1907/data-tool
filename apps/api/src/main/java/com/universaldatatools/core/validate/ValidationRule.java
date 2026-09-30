package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;

/**
 * One validation rule (design V1). The value is a string, or {@code null}, until the {@code type} rule converts it;
 * later rules receive the converted value.
 */
public interface ValidationRule {

    /** {@code "required"}, {@code "type"}, {@code "email"}, {@code "minLength"}, … {@code "unique"}. */
    String type();

    /** The code of this rule's failures, also used when the rule itself breaks. */
    RowErrorCode code();

    ValidationResult validate(Object value, ValidationContext context);
}
