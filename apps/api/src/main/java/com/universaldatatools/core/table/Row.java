package com.universaldatatools.core.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One data row of a source file (core-01 TD5).
 *
 * @param rowNumber where a person finds the row in the file: the spreadsheet row for CSV and XLSX (the header is
 *                  row 1), the element number from 1 for JSON
 * @param values    cell values by column index; a {@code null} cell stays {@code null}
 * @param kinds     the source type of each cell, or {@code null} when the source has no types (CSV)
 */
public record Row(long rowNumber, List<String> values, CellKinds kinds) {

    public Row {
        // List.copyOf rejects nulls, and empty cells are null.
        values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    public Row(long rowNumber, List<String> values) {
        this(rowNumber, values, null);
    }

    public String value(int index) {
        return index < values.size() ? values.get(index) : null;
    }

    /** {@code null} when the source has no types, for an empty cell, and past the last cell. */
    public CellKind kind(int index) {
        return kinds == null ? null : kinds.get(index);
    }
}
