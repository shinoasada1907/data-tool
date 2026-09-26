package com.universalimporter.domain.pipeline;

import com.universalimporter.domain.mapping.MappingConfig;
import com.universalimporter.domain.schema.TargetSchema;
import com.universalimporter.domain.source.SourceSchema;
import com.universalimporter.domain.transformation.TransformationConfig;
import com.universalimporter.domain.validation.ValidationConfig;

/** Everything one run needs besides the rows themselves. */
public record PipelineConfig(SourceSchema source, TargetSchema schema, MappingConfig mapping,
                             TransformationConfig transformations, ValidationConfig validations) {
}
