package com.universaldatatools.core.table;

/**
 * How cells get their type when written to JSON or XLSX (core-01 TD7): {@link #PRESERVE} keeps the type the source
 * had (CSV has none, so text), {@link #STRING} makes everything text, {@link #INFER} types untyped cells by their
 * column's profile.
 */
public enum Typing {
    PRESERVE, STRING, INFER
}
