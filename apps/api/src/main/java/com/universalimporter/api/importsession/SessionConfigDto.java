package com.universalimporter.api.importsession;

import com.universalimporter.api.mapping.MappingConfigDto;
import com.universalimporter.api.schema.TargetSchemaDto;
import com.universalimporter.domain.config.ImportConfiguration;

/** {@code config} of a session: F06-F07 add transformations and validations next to the schema and mapping. */
public record SessionConfigDto(TargetSchemaDto schema, MappingConfigDto mapping) {

    public static SessionConfigDto from(ImportConfiguration configuration) {
        return new SessionConfigDto(TargetSchemaDto.from(configuration.schema()),
                MappingConfigDto.from(configuration.mapping()));
    }
}
