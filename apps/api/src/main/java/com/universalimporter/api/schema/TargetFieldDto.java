package com.universalimporter.api.schema;

import com.universalimporter.domain.schema.TargetField;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A target field of the API contract. Content rules (name, type, order) are checked by the domain, so a wrong
 * value is a 422 {@code SCHEMA_INVALID} with one error per problem rather than a 400.
 *
 * @param required a wrapper on purpose: Jackson 3 rejects a missing primitive, but a missing flag means false
 */
public record TargetFieldDto(
        @Schema(description = "Trimmed; 1-100 characters; unique case-insensitively", example = "email")
        String name,
        @Schema(description = "One of string, number, boolean, date, email", example = "email")
        String type,
        @Schema(description = "Defaults to false")
        Boolean required,
        @Schema(description = "Display position; values must be distinct and are renumbered 0..n-1", example = "0")
        Integer order) {

    static TargetFieldDto from(TargetField field) {
        return new TargetFieldDto(field.name(), field.type().code(), field.required(), field.order());
    }
}
