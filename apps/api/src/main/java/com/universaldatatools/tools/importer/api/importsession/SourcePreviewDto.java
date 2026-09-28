package com.universaldatatools.tools.importer.api.importsession;

import com.universaldatatools.core.table.SourceFileType;
import com.universaldatatools.tools.importer.application.importsession.SourcePreview;

import java.util.List;
import java.util.UUID;

/** {@code SourcePreviewDto} of the API contract V0.1; {@code values[i]} belongs to {@code columns[i]}. */
public record SourcePreviewDto(UUID sessionId, SourceFileType fileType, String sheetName, List<ColumnDto> columns,
                               List<RowDto> rows, int previewLimit, long totalRows) {

    public record ColumnDto(int index, String name) {
    }

    public record RowDto(long rowNumber, List<String> values) {
    }

    public static SourcePreviewDto from(SourcePreview preview) {
        return new SourcePreviewDto(
                preview.sessionId(),
                preview.fileType(),
                preview.sheetName(),
                preview.columns().stream().map(column -> new ColumnDto(column.index(), column.name())).toList(),
                preview.rows().stream().map(row -> new RowDto(row.rowNumber(), row.values())).toList(),
                preview.previewLimit(),
                preview.totalRows());
    }
}
