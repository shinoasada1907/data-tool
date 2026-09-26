package com.universalimporter.application.importsession;

import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.domain.source.ImportRow;
import com.universalimporter.domain.source.SourceColumn;

import java.util.List;
import java.util.UUID;

/** The first rows of a session's source file, with the columns and row count found at upload. */
public record SourcePreview(UUID sessionId, SourceFileType fileType, String sheetName, List<SourceColumn> columns,
                            List<ImportRow> rows, int previewLimit, long totalRows) {
}
