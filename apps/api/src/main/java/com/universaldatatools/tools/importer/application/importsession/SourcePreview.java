package com.universaldatatools.tools.importer.application.importsession;

import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Row;

import java.util.List;
import java.util.UUID;

/** The first rows of a session's source file, with the columns and row count found at upload. */
public record SourcePreview(UUID sessionId, DataFormat fileType, String sheetName, List<Column> columns,
                            List<Row> rows, int previewLimit, long totalRows) {
}
