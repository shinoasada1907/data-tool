package com.universalimporter.domain.validation;

/**
 * A field's value after validation, converted to its real type ({@code null} for an empty optional field), or the
 * first failure (then {@code value} is {@code null}).
 */
public record FieldValidation(Object value, ValidationFailure failure) {

    public static FieldValidation ok(Object value) {
        return new FieldValidation(value, null);
    }

    public static FieldValidation failed(ValidationFailure failure) {
        return new FieldValidation(null, failure);
    }

    public boolean failed() {
        return failure != null;
    }
}
