package com.universalimporter.domain.transformation;

import com.universalimporter.domain.schema.FieldType;

import java.util.Map;

/**
 * What a transformation step knows besides the value.
 *
 * @param params the step's parameters; {@code null} counts as none
 */
public record TransformationContext(String fieldName, FieldType fieldType, Map<String, String> params) {

    public TransformationContext {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
