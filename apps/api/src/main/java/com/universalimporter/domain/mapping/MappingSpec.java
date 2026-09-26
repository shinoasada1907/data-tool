package com.universalimporter.domain.mapping;

/** A mapping as the client sent it, not yet checked; {@link MappingConfig#define} checks it. */
public record MappingSpec(String targetField, String mappingType, String sourceColumn, String constantValue) {
}
