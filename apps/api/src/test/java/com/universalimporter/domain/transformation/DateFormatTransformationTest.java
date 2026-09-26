package com.universalimporter.domain.transformation;

import com.universalimporter.domain.schema.FieldType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateFormatTransformationTest {

    private final DateFormatTransformation transformation = new DateFormatTransformation();

    @ParameterizedTest
    @CsvSource(value = {
            "dd/MM/yyyy|NULL|25/12/1990|1990-12-25",
            "dd/MM/yyyy|yyyy-MM-dd|25/12/1990|1990-12-25",
            "yyyy-MM-dd|dd/MM/yyyy|1990-12-25|25/12/1990",
            "dd/MM/yyyy|NULL|29/02/2024|2024-02-29",
            "dd MMM yyyy|NULL|05 Jan 2024|2024-01-05",
            "yyyy-MM-dd'T'HH:mm:ss|NULL|1990-12-25T08:30:00|1990-12-25",
            "dd/MM/yyyy|NULL|NULL|NULL",
            "dd/MM/yyyy|NULL|\"  \"|\"  \""}, delimiter = '|', nullValues = "NULL", quoteCharacter = '"')
    void reformats_a_date(String input, String output, String value, String expected) throws TransformationFailure {
        assertThat(transformation.transform(value, context(FieldType.STRING, input, output))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"31/02/2024", "29/02/2023", "1990-12-25", "\" 25/12/1990\""}, delimiter = '|',
            quoteCharacter = '"')
    void a_value_that_does_not_match_fails_without_showing_the_value(String value) {
        assertThatThrownBy(() -> transformation.transform(value, context(FieldType.STRING, "dd/MM/yyyy", null)))
                .isInstanceOf(TransformationFailure.class)
                .hasMessage("Value does not match pattern dd/MM/yyyy");
    }

    @Test
    void the_input_format_is_required() {
        assertThat(transformation.validate(context(FieldType.STRING, null, null)))
                .containsExactly("Parameter 'inputFormat' is required.");
    }

    @Test
    void a_date_field_must_output_iso() {
        assertThat(transformation.validate(context(FieldType.DATE, "dd/MM/yyyy", "dd/MM/yyyy")))
                .containsExactly("Fields of type date must output yyyy-MM-dd.");
        assertThat(transformation.validate(context(FieldType.STRING, "dd/MM/yyyy", "dd/MM/yyyy"))).isEmpty();
        assertThat(transformation.validate(context(FieldType.DATE, "dd/MM/yyyy", null))).isEmpty();
        assertThat(transformation.validate(context(FieldType.DATE, "dd/MM/yyyy", "yyyy-MM-dd"))).isEmpty();
        assertThat(transformation.validate(context(FieldType.DATE, "dd/MM/yyyy", "uuuu-MM-dd"))).isEmpty();
    }

    @Test
    void patterns_are_checked() {
        assertThat(transformation.validate(context(FieldType.STRING, "dd/MM/yyyyb", null)))
                .containsExactly("Invalid date pattern 'dd/MM/yyyyb'.");
        assertThat(transformation.validate(context(FieldType.STRING, "MM/yyyy", null)))
                .containsExactly("Date pattern 'MM/yyyy' must contain year, month and day.");
        assertThat(transformation.validate(context(FieldType.STRING, "dd/MM/yyyy", "dd/MM/yyyy HH:mm")))
                .containsExactly("Date pattern 'dd/MM/yyyy HH:mm' must not contain time fields.");
    }

    @Test
    void other_parameters_are_rejected() {
        TransformationContext withFoo = new TransformationContext("dob", FieldType.STRING,
                Map.of("inputFormat", "dd/MM/yyyy", "foo", "1"));

        assertThat(transformation.validate(withFoo)).containsExactly("Unknown parameter 'foo' for 'dateFormat'.");
        assertThat(transformation.type()).isEqualTo("dateFormat");
    }

    private static TransformationContext context(FieldType type, String input, String output) {
        Map<String, String> params = new HashMap<>();
        if (input != null) {
            params.put("inputFormat", input);
        }
        if (output != null) {
            params.put("outputFormat", output);
        }
        return new TransformationContext("dob", type, params);
    }
}
