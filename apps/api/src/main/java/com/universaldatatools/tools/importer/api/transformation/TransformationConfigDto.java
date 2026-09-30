package com.universaldatatools.tools.importer.api.transformation;

import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.core.transform.TransformationStep;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * {@code TransformationConfigDto} of the API contract V0.1: the body of {@code PUT /transformations} and
 * {@code config.transformations}. Meaning is checked by the domain, so a wrong type or parameter is a 422
 * {@code CONFIG_INVALID} listed with the other problems, not a 400.
 */
public record TransformationConfigDto(@NotNull List<@NotNull Item> transformations) {

    public record Item(
            @Schema(description = "Name of a field in the target schema", example = "dob")
            String targetField,
            @Schema(description = "Position among the field's steps, from 0; steps run in this order", example = "0")
            Integer order,
            @Schema(description = "trim, uppercase, lowercase, defaultValue or dateFormat", example = "dateFormat")
            String type,
            @Schema(description = "defaultValue: {value}; dateFormat: {inputFormat, outputFormat?}; others: none")
            Map<String, String> params) {
    }

    public static TransformationConfigDto from(TransformationConfig config) {
        return new TransformationConfigDto(config.transformations().stream()
                .map(step -> new Item(step.targetField(), step.order(), step.type(), step.params()))
                .toList());
    }

    public TransformationConfig toDomain() {
        return new TransformationConfig(transformations.stream()
                .map(item -> new TransformationStep(item.targetField(), item.order(), item.type(), item.params()))
                .toList());
    }
}
