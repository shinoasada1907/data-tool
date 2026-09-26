package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
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
}
