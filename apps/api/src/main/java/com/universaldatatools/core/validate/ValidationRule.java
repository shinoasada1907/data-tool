package com.universaldatatools.core.validate;

/**
 * One validation rule (design V1). The value is a string, or {@code null}, until the {@code type} rule converts it;
 * later rules receive the converted value.
 */
public interface ValidationRule {

    /** {@code "required"}, {@code "type"}, {@code "email"} or {@code "unique"}. */
    String type();

    ValidationResult validate(Object value, ValidationContext context);
}
