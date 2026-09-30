package com.universaldatatools.tools.importer.api.validation;

import com.universaldatatools.tools.importer.domain.validation.ValidationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationRuleConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * {@code ValidationConfigDto} of the API contract V0.1: the body of {@code PUT /validations} and
 * {@code config.validations}. Meaning is checked by the domain, so an unknown rule is a 422, not a 400.
 */
public record ValidationConfigDto(@NotNull List<@NotNull Item> validations) {

    public record Item(
            @Schema(description = "Name of a field in the target schema", example = "email")
            String targetField,
            @Schema(description = "email (string fields only) or unique; required and type come from the schema",
                    example = "unique")
            String type,
            @Schema(description = "No rule takes parameters in V0.1")
            Map<String, String> params) {
    }

    public static ValidationConfigDto from(ValidationConfig config) {
        return new ValidationConfigDto(config.validations().stream()
                .map(rule -> new Item(rule.targetField(), rule.type(), rule.params()))
                .toList());
    }

    public ValidationConfig toDomain() {
        return new ValidationConfig(validations.stream()
                .map(item -> new ValidationRuleConfig(item.targetField(), item.type(), item.params()))
                .toList());
    }
}
