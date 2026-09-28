package com.universaldatatools.tools.importer.domain.mapping;

import com.universaldatatools.core.table.ImportRow;

/** The cell of the mapped column; {@code null} when the cell is empty or the row is shorter than the header. */
public final class SourceColumnMappingStrategy implements MappingStrategy {

    @Override
    public MappingType type() {
        return MappingType.SOURCE_COLUMN;
    }

    @Override
    public String map(ImportRow row, ResolvedMapping mapping) {
        return row.value(mapping.columnIndex());
    }
}
