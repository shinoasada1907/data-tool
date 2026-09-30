package com.universaldatatools.core.validate;

/** Which earlier rows a value must not repeat (core-03 SR7). */
public enum UniqueScope {
    /** Only rows that turned out valid count, so an invalid row never blocks a later one (Importer, V0.1). */
    VALID_ROWS,
    /** Every row counts from the moment it is seen: a repeat is a repeat (Validator, Notion N10). */
    ALL_ROWS
}
