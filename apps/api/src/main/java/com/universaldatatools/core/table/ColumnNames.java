package com.universaldatatools.core.table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Column naming rules shared by every parser (design D9, P4). */
public final class ColumnNames {

    private ColumnNames() {
    }

    /**
     * Trims each header; a blank header becomes {@code Column <letters>}; a name already taken
     * (case-insensitively) gets the smallest free {@code " (k)"} suffix, k ≥ 2.
     */
    public static List<SourceColumn> normalize(List<String> rawHeaders) {
        List<SourceColumn> columns = new ArrayList<>(rawHeaders.size());
        Set<String> taken = new HashSet<>();
        for (int index = 0; index < rawHeaders.size(); index++) {
            String raw = rawHeaders.get(index);
            String base = raw == null || raw.isBlank() ? "Column " + excelLetters(index) : raw.strip();
            String name = base;
            for (int k = 2; taken.contains(key(name)); k++) {
                name = base + " (" + k + ")";
            }
            taken.add(key(name));
            columns.add(new SourceColumn(index, name));
        }
        return columns;
    }

    /** Spreadsheet column letters: 0 → A, 25 → Z, 26 → AA. */
    public static String excelLetters(int index) {
        StringBuilder letters = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) {
            letters.append((char) ('A' + (n - 1) % 26));
        }
        return letters.reverse().toString();
    }

    public static boolean isBlankRow(List<String> values) {
        return values.stream().allMatch(value -> value == null || value.isBlank());
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
