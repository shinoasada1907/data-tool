package com.universaldatatools.tools.importer.api.mapping;

import com.universaldatatools.tools.importer.domain.mapping.MappingConfig;
import com.universaldatatools.tools.importer.domain.mapping.MappingSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * {@code MappingConfigDto} of the API contract V0.1: the body of {@code PUT /mapping} and {@code config.mapping}.
 * Only mapped fields are listed.
 */
public record MappingConfigDto(@NotNull List<@NotNull @Valid FieldMappingDto> mappings) {

    public static MappingConfigDto from(MappingConfig mapping) {
        return new MappingConfigDto(mapping.mappings().stream().map(FieldMappingDto::from).toList());
    }

    public List<MappingSpec> toSpecs() {
        return mappings.stream()
                .map(m -> new MappingSpec(m.targetField(), m.mappingType(), m.sourceColumn(), m.constantValue()))
                .toList();
    }
}
