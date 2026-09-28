package com.universaldatatools.core.format.csv;

/**
 * Stops spreadsheet programs from running a CSV cell as a formula: a value starting with {@code =}, {@code +},
 * {@code -}, {@code @}, a tab or a carriage return gets a leading {@code '} (spec: data-export).
 */
public final class CsvFormulaGuard {

    private CsvFormulaGuard() {
    }

    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> "'" + value;
            default -> value;
        };
    }
}
