package com.universaldatatools.tools.importer.domain.importsession;

import com.universaldatatools.core.table.Column;

import java.util.List;

/**
 * What inspecting a source file found.
 *
 * @param totalRows non-blank data rows (the header is not counted)
 * @param sheetName sheet that was read, or {@code null} for CSV
 */
public record SourceSchema(List<Column> columns, long totalRows, String sheetName) {

    public SourceSchema {
        columns = List.copyOf(columns);
    }
}
