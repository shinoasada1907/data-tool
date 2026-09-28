package com.universaldatatools.core.table;

/**
 * A cell to write: its text ({@code null} when empty) and its kind when known ({@code null} for untyped sources).
 */
public record TypedCell(String text, CellKind kind) {

    public static TypedCell text(String text) {
        return new TypedCell(text, text == null ? null : CellKind.TEXT);
    }
}
