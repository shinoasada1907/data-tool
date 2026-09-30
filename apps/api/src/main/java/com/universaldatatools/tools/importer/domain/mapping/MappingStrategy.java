package com.universaldatatools.tools.importer.domain.mapping;

import com.universaldatatools.core.table.Row;

/** Produces the raw value of one target field from a row. Values stay strings until type conversion (design D10). */
public interface MappingStrategy {

    MappingType type();

    String map(Row row, ResolvedMapping mapping);
}
