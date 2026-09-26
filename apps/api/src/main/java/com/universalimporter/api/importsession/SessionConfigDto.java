package com.universalimporter.api.importsession;

import com.universalimporter.api.mapping.MappingConfigDto;
import com.universalimporter.api.schema.TargetSchemaDto;
import com.universalimporter.api.transformation.TransformationConfigDto;
import com.universalimporter.domain.config.ImportConfiguration;

/** {@code config} of a session: F07 adds validations next to the schema, mapping and transformations. */
public record SessionConfigDto(TargetSchemaDto schema, MappingConfigDto mapping,
                               TransformationConfigDto transformations) {

    public static SessionConfigDto from(ImportConfiguration configuration) {
        return new SessionConfigDto(TargetSchemaDto.from(configuration.schema()),
                MappingConfigDto.from(configuration.mapping()),
                TransformationConfigDto.from(configuration.transformations()));
    }
}
