package com.universaldatatools.tools.importer.domain.transformation;

import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.core.transform.TransformationRegistry;
import com.universaldatatools.core.transform.TransformationStep;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfigValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransformationConfigValidatorTest {

    private static final TargetSchema SCHEMA = TargetSchema.define(List.of(
            new FieldSpec("name", "string", false, 0),
            new FieldSpec("dob", "date", false, 1),
            new FieldSpec("email", "email", false, 2)));

    private final TransformationConfigValidator validator =
            new TransformationConfigValidator(TransformationRegistry.standard());

    @Test
    void steps_without_params_are_valid() {
        assertThat(validate(step("name", 0, "trim"))).isEmpty();
        assertThat(validate(step("name", 0, "trim"), step("name", 1, "uppercase"), step("email", 0, "lowercase")))
                .isEmpty();
    }

    @Test
    void a_date_field_accepts_iso_output_written_either_way() {
        assertThat(validate(date("dd/MM/yyyy", "yyyy-MM-dd"))).isEmpty();
        assertThat(validate(date("dd/MM/yyyy", "uuuu-MM-dd"))).isEmpty();
    }

    @Test
    void the_target_field_must_exist() {
        assertThat(validate(step("phone", 0, "trim"))).containsExactly(item("phone", "Target field does not exist."));
    }

    @Test
    void the_type_must_be_known() {
        assertThat(validate(step("name", 0, "replace")))
                .containsExactly(item("name", "Unknown transformation type 'replace'."));
    }

    @Test
    void the_order_is_required_and_not_negative() {
        assertThat(validate(step("name", null, "trim"))).containsExactly(item("name", "Order is required."));
        assertThat(validate(step("name", -1, "trim"))).containsExactly(item("name", "Order must be >= 0."));
    }

    @Test
    void orders_are_unique_within_a_field_only() {
        assertThat(validate(step("name", 0, "trim"), step("name", 0, "uppercase")))
                .containsExactly(item("name", "Duplicate order 0 for field 'name'."));
        assertThat(validate(step("name", 0, "trim"), step("email", 0, "trim"))).isEmpty();
    }

    @Test
    void transformation_parameters_are_checked() {
        assertThat(validate(new TransformationStep("name", 0, "defaultValue", Map.of())))
                .containsExactly(item("name", "Parameter 'value' is required."));
        assertThat(validate(new TransformationStep("name", 0, "trim", Map.of("foo", "1"))))
                .containsExactly(item("name", "Unknown parameter 'foo' for 'trim'."));
    }

    @Test
    void date_patterns_are_checked() {
        assertThat(validate(date("dd/MM/yyyyb", null))).containsExactly(item("dob", "Invalid date pattern 'dd/MM/yyyyb'."));
        assertThat(validate(date("MM/yyyy", null)))
                .containsExactly(item("dob", "Date pattern 'MM/yyyy' must contain year, month and day."));
        assertThat(validate(date("dd/MM/yyyy", "dd/MM/yyyy")))
                .containsExactly(item("dob", "Fields of type date must output yyyy-MM-dd."));
    }

    @Test
    void parameters_are_checked_even_when_the_field_does_not_exist() {
        assertThat(validate(new TransformationStep("phone", 0, "dateFormat", Map.of("inputFormat", "MM/yyyy"))))
                .containsExactly(item("phone", "Target field does not exist."),
                        item("phone", "Date pattern 'MM/yyyy' must contain year, month and day."));
    }

    @Test
    void a_step_without_target_field_is_not_reported_as_a_duplicate() {
        assertThat(validate(step(null, 0, "trim"), step(null, 0, "trim")))
                .containsExactly(item(null, "Target field does not exist."), item(null, "Target field does not exist."));
    }

    @Test
    void every_problem_is_reported_in_input_order() {
        assertThat(validate(step("name", 0, "replace"), step("phone", 0, "trim"))).containsExactly(
                item("name", "Unknown transformation type 'replace'."),
                item("phone", "Target field does not exist."));
    }

    @Test
    void normalized_sorts_by_schema_position_then_order() {
        TransformationStep dob = date("dd/MM/yyyy", null);
        TransformationConfig config = new TransformationConfig(List.of(
                dob, step("name", 1, "uppercase"), step("name", 0, "trim")));

        assertThat(config.normalized(SCHEMA).transformations())
                .containsExactly(step("name", 0, "trim"), step("name", 1, "uppercase"), dob);
        assertThat(config.stepsFor("name")).containsExactly(step("name", 0, "trim"), step("name", 1, "uppercase"));
    }

    private List<ProblemItem> validate(TransformationStep... steps) {
        return validator.validate(new TransformationConfig(List.of(steps)), SCHEMA);
    }

    private static TransformationStep step(String field, Integer order, String type) {
        return new TransformationStep(field, order, type, null);
    }

    private static TransformationStep date(String input, String output) {
        return new TransformationStep("dob", 0, "dateFormat", output == null
                ? Map.of("inputFormat", input)
                : Map.of("inputFormat", input, "outputFormat", output));
    }

    private static ProblemItem item(String field, String message) {
        return new ProblemItem(field, "CONFIG_INVALID", message);
    }
}
