package com.universalimporter.domain.mapping;

/**
 * Where one target field gets its value.
 *
 * @param sourceColumn  name of the source column for {@link MappingType#SOURCE_COLUMN}, otherwise {@code null}
 * @param constantValue the value for {@link MappingType#CONSTANT}, otherwise {@code null}
 */
public record FieldMapping(String targetField, MappingType type, String sourceColumn, String constantValue) {
}
