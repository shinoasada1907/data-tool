package com.universaldatatools.core.table;

import java.util.List;
import java.util.Set;

/**
 * What inspecting a dataset found, and everything {@link TableReader#read} needs to stream its rows again.
 *
 * @param autoDetected     names of the options the reader detected ({@code "delimiter"}, {@code "encoding"})
 * @param sheetName        sheet that was read; {@code null} except for XLSX
 * @param sheets           every sheet of a workbook, in order; empty except for XLSX
 * @param sourceKeys       JSON keys in column order, so rows map keys to columns; {@code null} for CSV and XLSX
 * @param rowCount         data rows, blank rows not counted
 * @param blankRowsSkipped blank CSV and XLSX rows left out (their row numbers stay used)
 */
public record TableInfo(DataFormat format, ResolvedReadOptions options, Set<String> autoDetected, String sheetName,
                        List<SheetInfo> sheets, List<Column> columns, List<String> sourceKeys,
                        List<ColumnProfile> profiles, long rowCount, long blankRowsSkipped) {

    public TableInfo {
        autoDetected = Set.copyOf(autoDetected);
        sheets = List.copyOf(sheets);
        columns = List.copyOf(columns);
        sourceKeys = sourceKeys == null ? null : List.copyOf(sourceKeys);
        profiles = List.copyOf(profiles);
    }

    /**
     * Enough to read a CSV or XLSX file again when its columns were stored after an earlier inspection, as the
     * Importer does; no profiles or counts.
     */
    public static TableInfo forRead(DataFormat format, ResolvedReadOptions options, List<Column> columns) {
        return new TableInfo(format, options, Set.of(), options.sheet(), List.of(), columns, null, List.of(), 0, 0);
    }
}
