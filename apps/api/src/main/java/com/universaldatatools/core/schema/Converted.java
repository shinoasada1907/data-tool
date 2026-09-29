package com.universaldatatools.core.schema;

import com.universaldatatools.core.common.RowErrorCode;

/** A text value converted to its field type, or why it could not be. */
public sealed interface Converted {

    /** @param value {@code String}, {@code BigDecimal}, {@code Boolean} or {@code LocalDate} */
    record Ok(Object value) implements Converted {
    }

    /** @param message English, never containing the value */
    record Failed(RowErrorCode code, String message) implements Converted {
    }
}
