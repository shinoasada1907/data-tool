package com.universaldatatools.tools.importer.infrastructure.persistence;

import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.tools.importer.domain.schema.TargetField;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;

import java.util.List;

/**
 * Storage format of {@link TargetSchema} in the {@code target_schema_json} column; {@code type} is the lowercase
 * type code. Kept separate from the domain record so the stored JSON only changes when this class does.
 */
record TargetSchemaDocument(List<FieldDocument> fields) {

    record FieldDocument(String name, String type, boolean required, int order) {
    }

    static TargetSchemaDocument from(TargetSchema schema) {
        return new TargetSchemaDocument(schema.fields().stream()
                .map(field -> new FieldDocument(field.name(), field.type().code(), field.required(), field.order()))
                .toList());
    }

    TargetSchema toDomain() {
        return new TargetSchema(fields.stream()
                .map(field -> new TargetField(field.name(), fieldType(field.type()), field.required(), field.order()))
                .toList());
    }

    private static FieldType fieldType(String code) {
        return FieldType.fromCode(code)
                .orElseThrow(() -> new IllegalStateException("Unknown field type in stored schema: " + code));
    }
}
