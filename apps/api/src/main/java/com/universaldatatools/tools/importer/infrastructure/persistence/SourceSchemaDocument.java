package com.universaldatatools.tools.importer.infrastructure.persistence;

import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.SourceSchema;

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
                columns.stream().map(column -> new Column(column.index(), column.name())).toList(),
                totalRows,
                sheetName);
    }
}
