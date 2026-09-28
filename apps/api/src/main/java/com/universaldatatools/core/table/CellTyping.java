package com.universaldatatools.core.table;

/** The kind a cell is written as, from its own kind, the {@link Typing} asked for and its column (core-02 IO9). */
public final class CellTyping {

    private CellTyping() {
    }

    /** {@code null} for an empty cell. */
    public static CellKind resolve(TypedCell cell, Typing typing, ColumnProfile profile) {
        if (cell.text() == null) {
            return null;
        }
        return switch (typing) {
            case STRING -> CellKind.TEXT;
            case PRESERVE -> cell.kind() != null ? cell.kind() : CellKind.TEXT;
            case INFER -> cell.kind() != null ? cell.kind() : inferred(cell.text(), profile);
        };
    }

    private static CellKind inferred(String text, ColumnProfile profile) {
        if (profile == null) {
            return CellKind.TEXT;
        }
        return switch (profile.inferredType()) {
            case NUMBER -> TextShapes.isNumber(text) ? CellKind.NUMBER : CellKind.TEXT;
            case BOOLEAN -> TextShapes.isBoolean(text) ? CellKind.BOOLEAN : CellKind.TEXT;
            case DATE -> TextShapes.isIsoDate(text) ? CellKind.DATE : CellKind.TEXT;
            default -> CellKind.TEXT;
        };
    }
}
