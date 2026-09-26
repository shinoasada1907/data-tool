package com.universalimporter.domain.mapping;

import com.universalimporter.domain.source.ImportRow;

/** Produces the raw value of one target field from a row. Values stay strings until type conversion (design D10). */
public interface MappingStrategy {

    MappingType type();

    String map(ImportRow row, ResolvedMapping mapping);
}
