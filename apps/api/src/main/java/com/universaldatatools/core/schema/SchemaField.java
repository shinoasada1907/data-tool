package com.universaldatatools.core.schema;

/** One field of a {@link DataSchema}; the name is trimmed and unique within the schema, ignoring case. */
public record SchemaField(String name, FieldType type, boolean required, FieldConstraints constraints) {
}
