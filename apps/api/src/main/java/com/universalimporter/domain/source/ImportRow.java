package com.universalimporter.domain.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One data row of the source file.
 *
 * @param rowNumber row number as a spreadsheet would show it (the header is row 1)
 * @param values    cell values by column index; a {@code null} cell stays {@code null}
 */
public record ImportRow(long rowNumber, List<String> values) {

    public ImportRow {
        // List.copyOf rejects nulls, and empty cells are null.
        values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    public String value(int index) {
        return index < values.size() ? values.get(index) : null;
    }
}
