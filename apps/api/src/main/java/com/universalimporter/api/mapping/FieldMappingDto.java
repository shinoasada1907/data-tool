package com.universalimporter.api.mapping;

import com.universalimporter.domain.mapping.FieldMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One mapping of the API contract. {@code mappingType} is a plain string: an unknown value is a 422
 * {@code MAPPING_INVALID} listed with the other problems, not a 400.
 */
public record FieldMappingDto(
        @Schema(description = "Name of a field in the target schema (exact match)", example = "name")
        String targetField,
        @Schema(description = "SOURCE_COLUMN or CONSTANT", example = "SOURCE_COLUMN")
        String mappingType,
        @Schema(description = "Exact name of a source column; only for SOURCE_COLUMN", example = "Họ tên")
        String sourceColumn,
        @Schema(description = "Non-blank value for every row; only for CONSTANT")
        String constantValue) {

    static FieldMappingDto from(FieldMapping mapping) {
        return new FieldMappingDto(mapping.targetField(), mapping.type().name(), mapping.sourceColumn(),
                mapping.constantValue());
    }
}
