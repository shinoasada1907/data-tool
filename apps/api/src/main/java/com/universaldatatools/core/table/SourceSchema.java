package com.universaldatatools.core.table;

import java.util.List;

/**
 * What inspecting a source file found.
 *
 * @param totalRows non-blank data rows (the header is not counted)
 * @param sheetName sheet that was read, or {@code null} for CSV
 */
public record SourceSchema(List<SourceColumn> columns, long totalRows, String sheetName) {

    public SourceSchema {
        columns = List.copyOf(columns);
    }
}
