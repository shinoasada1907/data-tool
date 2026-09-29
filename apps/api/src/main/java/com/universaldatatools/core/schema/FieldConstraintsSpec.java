package com.universaldatatools.core.schema;

import java.math.BigDecimal;

/** Constraints as a client sent them, not yet checked; every part may be {@code null}. */
public record FieldConstraintsSpec(Boolean unique, BigDecimal min, BigDecimal max, Integer minLength,
                                   Integer maxLength, String pattern, String format, String defaultValue) {
}
