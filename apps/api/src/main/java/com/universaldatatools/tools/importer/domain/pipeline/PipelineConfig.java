package com.universaldatatools.tools.importer.domain.pipeline;

import com.universaldatatools.tools.importer.domain.importsession.SourceSchema;
import com.universaldatatools.tools.importer.domain.mapping.MappingConfig;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfig;

/** Everything one run needs besides the rows themselves. */
public record PipelineConfig(SourceSchema source, TargetSchema schema, MappingConfig mapping,
                             TransformationConfig transformations, ValidationConfig validations) {
}
