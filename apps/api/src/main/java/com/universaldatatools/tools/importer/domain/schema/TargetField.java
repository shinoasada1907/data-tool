package com.universaldatatools.tools.importer.domain.schema;

import com.universaldatatools.core.schema.FieldType;

/**
 * A field of the target schema.
 *
 * @param name  trimmed, unique case-insensitively; the key of this field in the output JSON
 * @param order position in the schema, from 0
 */
public record TargetField(String name, FieldType type, boolean required, int order) {
}
