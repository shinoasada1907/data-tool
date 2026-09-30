package com.universaldatatools.tools.importer.api.importsession;

import com.universaldatatools.tools.importer.api.mapping.MappingConfigDto;
import com.universaldatatools.tools.importer.api.schema.TargetSchemaDto;
import com.universaldatatools.tools.importer.api.transformation.TransformationConfigDto;
import com.universaldatatools.tools.importer.api.validation.ValidationConfigDto;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;

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
