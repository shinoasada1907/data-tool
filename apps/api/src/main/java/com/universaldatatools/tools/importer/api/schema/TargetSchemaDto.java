package com.universaldatatools.tools.importer.api.schema;

import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** {@code TargetSchemaDto} of the API contract V0.1: the body of {@code PUT /schema} and {@code config.schema}. */
public record TargetSchemaDto(@NotNull List<@NotNull @Valid TargetFieldDto> fields) {

    public static TargetSchemaDto from(TargetSchema schema) {
        return new TargetSchemaDto(schema.fields().stream().map(TargetFieldDto::from).toList());
    }

    public List<FieldSpec> toSpecs() {
        return fields.stream()
                .map(field -> new FieldSpec(
                        field.name(), field.type(), Boolean.TRUE.equals(field.required()), field.order()))
                .toList();
    }
}
