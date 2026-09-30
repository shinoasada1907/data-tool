package com.universaldatatools.tools.importer.domain.mapping;

import java.util.Arrays;
import java.util.Optional;

/** Where a target field gets its value; the name is the API and storage value. */
public enum MappingType {
    SOURCE_COLUMN, CONSTANT;

    /** Exact match only: {@code "source_column"} is not a type. */
    public static Optional<MappingType> fromName(String name) {
        return Arrays.stream(values()).filter(type -> type.name().equals(name)).findFirst();
    }
}
