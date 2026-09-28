package com.universaldatatools.tools.importer.domain.mapping;

/**
 * A {@link FieldMapping} with its source column already turned into an index, so rows are never searched by name.
 *
 * @param columnIndex index of the source column, or {@code -1} for {@link MappingType#CONSTANT}
 */
public record ResolvedMapping(String targetField, MappingType type, int columnIndex, String constantValue) {
}
