package com.universalimporter.api.importsession;

import com.universalimporter.api.mapping.MappingConfigDto;
import com.universalimporter.api.schema.TargetSchemaDto;
import com.universalimporter.api.transformation.TransformationConfigDto;
import com.universalimporter.api.validation.ValidationConfigDto;
import com.universalimporter.domain.config.ImportConfiguration;

/** {@code config} of a session: everything configured so far, each section in canonical order. */
public record SessionConfigDto(TargetSchemaDto schema, MappingConfigDto mapping,
                               TransformationConfigDto transformations, ValidationConfigDto validations) {

    public static SessionConfigDto from(ImportConfiguration configuration) {
        return new SessionConfigDto(TargetSchemaDto.from(configuration.schema()),
                MappingConfigDto.from(configuration.mapping()),
                TransformationConfigDto.from(configuration.transformations()),
                ValidationConfigDto.from(configuration.validations()));
    }
}
