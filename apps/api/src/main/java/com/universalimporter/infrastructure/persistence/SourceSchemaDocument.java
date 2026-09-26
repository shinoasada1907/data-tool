package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.source.SourceColumn;
import com.universalimporter.domain.source.SourceSchema;

import java.util.List;

/**
 * Storage format of {@link SourceSchema} in the {@code source_schema} jsonb column. Kept separate from the
 * domain record so the stored JSON only changes when this class does.
 */
record SourceSchemaDocument(List<ColumnDocument> columns, long totalRows, String sheetName) {

    record ColumnDocument(int index, String name) {
    }

    static SourceSchemaDocument from(SourceSchema schema) {
        return new SourceSchemaDocument(
                schema.columns().stream().map(column -> new ColumnDocument(column.index(), column.name())).toList(),
                schema.totalRows(),
                schema.sheetName());
    }

    SourceSchema toDomain() {
        return new SourceSchema(
                columns.stream().map(column -> new SourceColumn(column.index(), column.name())).toList(),
                totalRows,
                sheetName);
    }
}
