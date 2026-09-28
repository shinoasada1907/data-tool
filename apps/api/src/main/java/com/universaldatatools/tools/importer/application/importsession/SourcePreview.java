package com.universaldatatools.tools.importer.application.importsession;

import com.universaldatatools.core.table.SourceFileType;
import com.universaldatatools.core.table.ImportRow;
import com.universaldatatools.core.table.SourceColumn;

import java.util.List;
import java.util.UUID;

/** The first rows of a session's source file, with the columns and row count found at upload. */
public record SourcePreview(UUID sessionId, SourceFileType fileType, String sheetName, List<SourceColumn> columns,
                            List<ImportRow> rows, int previewLimit, long totalRows) {
}
