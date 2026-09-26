package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.mapping.FieldMapping;
import com.universalimporter.domain.mapping.MappingConfig;
import com.universalimporter.domain.mapping.MappingType;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import com.universalimporter.domain.transformation.TransformationConfig;
import com.universalimporter.domain.validation.ValidationConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessEvaluatorTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");

    private final ReadinessEvaluator evaluator = ReadinessEvaluator.standard();

    @Test
    void an_empty_schema_is_not_ready() {
        Readiness readiness = evaluator.evaluate(ImportConfiguration.empty(ID));

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.issues())
                .containsExactly(new ProblemItem(null, "SCHEMA_EMPTY", "Target schema has no fields."));
    }

    @Test
    void a_schema_with_one_optional_field_is_ready() {
        TargetSchema schema = TargetSchema.define(List.of(new FieldSpec("note", "string", false, 0)));

        Readiness readiness = evaluator.evaluate(ImportConfiguration.empty(ID).withSchema(schema).configuration());

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.issues()).isEmpty();
    }

    @Test
    void a_required_field_without_mapping_is_an_issue() {
        Readiness readiness = evaluator.evaluate(configuration(
                List.of(new FieldSpec("email", "email", true, 0), new FieldSpec("note", "string", false, 1)), "note"));

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.issues())
                .containsExactly(new ProblemItem("email", "TARGET_FIELD_REQUIRED", "Required field is not mapped."));
    }

    @Test
    void mapping_every_required_field_makes_it_ready() {
        Readiness readiness = evaluator.evaluate(configuration(List.of(new FieldSpec("email", "email", true, 0)), "email"));

        assertThat(readiness.ready()).isTrue();
    }

    @Test
    void an_empty_schema_only_reports_that_it_is_empty() {
        assertThat(evaluator.evaluate(ImportConfiguration.empty(ID)).issues())
                .extracting(ProblemItem::code).containsExactly("SCHEMA_EMPTY");
    }

    private static ImportConfiguration configuration(List<FieldSpec> fields, String... mapped) {
        List<FieldMapping> mappings = java.util.Arrays.stream(mapped)
                .map(field -> new FieldMapping(field, MappingType.CONSTANT, null, "x"))
                .toList();
        return new ImportConfiguration(ID, TargetSchema.define(fields), new MappingConfig(mappings),
                TransformationConfig.empty(), ValidationConfig.empty(), null);
    }
}
