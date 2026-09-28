package com.universaldatatools.core.transform;

import com.universaldatatools.core.schema.FieldType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultValueTransformationTest {

    private final DefaultValueTransformation transformation = new DefaultValueTransformation();

    @ParameterizedTest
    @CsvSource(value = {"NULL|VN", "''|VN", "'  '|VN", "' JP '|' JP '"}, delimiter = '|', nullValues = "NULL")
    void an_empty_value_gets_the_default(String input, String expected) throws TransformationFailure {
        assertThat(transformation.transform(input, context(Map.of("value", "VN")))).isEqualTo(expected);
    }

    @Test
    void the_value_parameter_is_required() {
        assertThat(transformation.validate(context(Map.of()))).containsExactly("Parameter 'value' is required.");
    }

    @Test
    void the_value_parameter_must_not_be_blank() {
        assertThat(transformation.validate(context(Map.of("value", "  "))))
                .containsExactly("Parameter 'value' must not be blank.");
    }

    @Test
    void other_parameters_are_rejected() {
        assertThat(transformation.validate(context(Map.of("value", "VN", "x", "1"))))
                .containsExactly("Unknown parameter 'x' for 'defaultValue'.");
    }

    @Test
    void a_valid_configuration_has_no_problems() {
        assertThat(transformation.validate(context(Map.of("value", "VN")))).isEmpty();
        assertThat(transformation.type()).isEqualTo("defaultValue");
    }

    private static TransformationContext context(Map<String, String> params) {
        return new TransformationContext("country", FieldType.STRING, params);
    }
}
