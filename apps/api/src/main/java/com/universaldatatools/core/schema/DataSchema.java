package com.universaldatatools.core.schema;

import java.util.List;
import java.util.Optional;

/** A checked data schema; the order of {@code fields} is the field order everywhere (core-03 SR1). */
public record DataSchema(String name, List<SchemaField> fields) {

    public DataSchema {
        fields = List.copyOf(fields);
    }

    /** Exact name match. */
    public Optional<SchemaField> field(String name) {
        return fields.stream().filter(field -> field.name().equals(name)).findFirst();
    }
}
