package com.universaldatatools.core.schema;

import java.math.BigDecimal;

/**
 * What a value must satisfy besides its type (core-03 SR1). {@code null} means "no such constraint". Only
 * {@link SchemaDefinition#check} builds these from client input, so a schema never holds a constraint that does
 * not fit its field type.
 *
 * @param pattern      RE2 syntax, matched against the whole value
 * @param format       date pattern the source writes dates in, in {@code DatePatterns} syntax
 * @param defaultValue metadata only: no tool fills it in by itself
 */
public record FieldConstraints(boolean unique, BigDecimal min, BigDecimal max, Integer minLength, Integer maxLength,
                               String pattern, String format, String defaultValue) {

    public static final FieldConstraints NONE = new FieldConstraints(false, null, null, null, null, null, null, null);

    public FieldConstraints withoutUnique() {
        return new FieldConstraints(false, min, max, minLength, maxLength, pattern, format, defaultValue);
    }
}
