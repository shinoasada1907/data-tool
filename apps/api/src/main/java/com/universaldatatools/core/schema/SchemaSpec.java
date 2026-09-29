package com.universaldatatools.core.schema;

import java.util.List;

/** A schema as a client sent it, not yet checked; {@link SchemaDefinition#check} turns it into a {@link DataSchema}. */
public record SchemaSpec(String name, List<SchemaFieldSpec> fields) {
}
