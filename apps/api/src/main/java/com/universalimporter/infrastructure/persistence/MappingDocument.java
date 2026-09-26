package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.mapping.FieldMapping;
import com.universalimporter.domain.mapping.MappingConfig;
import com.universalimporter.domain.mapping.MappingType;

import java.util.List;

/**
 * Storage format of {@link MappingConfig} in the {@code mapping_json} column; {@code mappingType} is the enum name.
 * Kept separate from the domain record so the stored JSON only changes when this class does.
 */
record MappingDocument(List<FieldMappingDocument> mappings) {

    record FieldMappingDocument(String targetField, String mappingType, String sourceColumn, String constantValue) {
    }

    static MappingDocument from(MappingConfig mapping) {
        return new MappingDocument(mapping.mappings().stream()
                .map(field -> new FieldMappingDocument(
                        field.targetField(), field.type().name(), field.sourceColumn(), field.constantValue()))
                .toList());
    }

    MappingConfig toDomain() {
        return new MappingConfig(mappings.stream()
                .map(field -> new FieldMapping(
                        field.targetField(), mappingType(field.mappingType()), field.sourceColumn(), field.constantValue()))
                .toList());
    }

    private static MappingType mappingType(String name) {
        return MappingType.fromName(name)
                .orElseThrow(() -> new IllegalStateException("Unknown mapping type in stored mapping: " + name));
    }
}
