package com.universaldatatools.core.schema;

/** A field as a client sent it, not yet checked; every part may be {@code null}. */
public record SchemaFieldSpec(String name, String type, Boolean required, FieldConstraintsSpec constraints) {
}
