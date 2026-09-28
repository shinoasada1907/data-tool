package com.universaldatatools.tools.importer.domain.mapping;

import com.universaldatatools.core.table.ImportRow;

/** The same configured value for every row. */
public final class ConstantMappingStrategy implements MappingStrategy {

    @Override
    public MappingType type() {
        return MappingType.CONSTANT;
    }

    @Override
    public String map(ImportRow row, ResolvedMapping mapping) {
        return mapping.constantValue();
    }
}
