package com.universaldatatools.core.table;

/** A column to write; {@code profile} feeds {@link Typing#INFER} and may be {@code null}. */
public record OutputColumn(String name, ColumnProfile profile) {
}
