package com.universaldatatools.tools.importer.domain.export;

/**
 * Keeps a spreadsheet from running a CSV cell as a formula (D13, OWASP CSV injection): a value starting with
 * {@code = + - @}, a tab or a carriage return gets a leading {@code '}. Applied only to text the user controls.
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
