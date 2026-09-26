package com.universalimporter.domain.transformation;

import com.universalimporter.domain.schema.FieldType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransformationEngineTest {

    private final TransformationEngine engine = new TransformationEngine(new TransformationRegistry(List.of(
            new TrimTransformation(), new UppercaseTransformation(), new LowercaseTransformation(),
            new DefaultValueTransformation(), new DateFormatTransformation(), new ExplodingTransformation())));

    @Test
    void steps_run_in_order() {
        assertThat(apply("  an ", step(0, "trim"), step(1, "uppercase"))).isEqualTo(FieldTransformResult.ok("AN"));
    }

    @Test
    void steps_run_by_order_not_by_list_position() {
        assertThat(apply("  an ", step(1, "uppercase"), step(0, "trim"))).isEqualTo(FieldTransformResult.ok("AN"));
    }

    @Test
    void default_value_fills_an_empty_cell() {
        assertThat(apply(null, step(0, "defaultValue", Map.of("value", "VN")))).isEqualTo(FieldTransformResult.ok("VN"));
    }

    @Test
    void trim_before_date_format() {
        assertThat(apply(" 25/12/1990 ", step(0, "trim"), step(1, "dateFormat", Map.of("inputFormat", "dd/MM/yyyy"))))
                .isEqualTo(FieldTransformResult.ok("1990-12-25"));
    }

    @Test
    void a_failing_step_stops_the_field_with_a_structured_error() {
        FieldTransformResult result = apply("31/02/2024",
                step(0, "dateFormat", Map.of("inputFormat", "dd/MM/yyyy")), step(1, "uppercase"));

        assertThat(result.failed()).isTrue();
        assertThat(result.value()).isNull();
        assertThat(result.error())
                .isEqualTo(new TransformationError("dateFormat", 0, "Value does not match pattern dd/MM/yyyy"));
    }

    @Test
    void no_steps_keep_the_value() {
        assertThat(apply("x")).isEqualTo(FieldTransformResult.ok("x"));
    }

    @Test
    void a_bug_in_a_transformation_becomes_an_error_without_its_message() {
        FieldTransformResult result = apply("x", step(0, "explode"));

        assertThat(result.error())
                .isEqualTo(new TransformationError("explode", 0, "Unexpected error while applying transformation."));
        assertThat(result.error().message()).doesNotContain("boom");
    }

    private FieldTransformResult apply(String value, TransformationStep... steps) {
        return engine.apply("name", FieldType.STRING, value, List.of(steps));
    }

    private static TransformationStep step(int order, String type) {
        return new TransformationStep("name", order, type, null);
    }

    private static TransformationStep step(int order, String type, Map<String, String> params) {
        return new TransformationStep("name", order, type, params);
    }

    /** A transformation with a bug. */
    static final class ExplodingTransformation implements Transformation {

        @Override
        public String type() {
            return "explode";
        }

        @Override
        public List<String> validate(TransformationContext context) {
            return List.of();
        }

        @Override
        public String transform(String value, TransformationContext context) {
            throw new IllegalStateException("boom");
        }
    }
}
