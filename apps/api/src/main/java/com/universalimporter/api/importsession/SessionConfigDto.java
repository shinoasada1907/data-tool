package com.universalimporter.api.importsession;

import com.universalimporter.api.schema.TargetSchemaDto;
import com.universalimporter.domain.config.ImportConfiguration;

/** {@code config} of a session: F05-F07 add mapping, transformations and validations next to the schema. */
public record SessionConfigDto(TargetSchemaDto schema) {

    public static SessionConfigDto from(ImportConfiguration configuration) {
        return new SessionConfigDto(TargetSchemaDto.from(configuration.schema()));
    }
}
