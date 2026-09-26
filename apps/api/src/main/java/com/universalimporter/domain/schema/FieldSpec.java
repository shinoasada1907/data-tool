package com.universalimporter.domain.schema;

/** A target field as the client sent it, not yet checked; {@link TargetSchema#define} checks it. */
public record FieldSpec(String name, String type, boolean required, Integer order) {
}
