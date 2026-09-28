package com.universaldatatools.core.transform;

import com.universaldatatools.core.schema.FieldType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TextTransformationsTest {

    private static final Locale ORIGINAL = Locale.getDefault();
    private static final TransformationContext NO_PARAMS = new TransformationContext("name", FieldType.STRING, Map.of());

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(ORIGINAL);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(new TrimTransformation(), "  An  ", "An"),
                Arguments.of(new TrimTransformation(), " Bình ", "Bình"),
                Arguments.of(new TrimTransformation(), "Nguyễn  Văn", "Nguyễn  Văn"),
                Arguments.of(new TrimTransformation(), null, null),
                Arguments.of(new TrimTransformation(), "   ", "   "),
                Arguments.of(new UppercaseTransformation(), "nguyễn văn an", "NGUYỄN VĂN AN"),
                Arguments.of(new LowercaseTransformation(), "ĐÀ NẴNG", "đà nẵng"),
                Arguments.of(new UppercaseTransformation(), "   ", "   "),
                Arguments.of(new LowercaseTransformation(), null, null));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void transforms(Transformation transformation, String input, String expected) throws TransformationFailure {
        assertThat(transformation.transform(input, NO_PARAMS)).isEqualTo(expected);
    }

    @Test
    void uppercase_ignores_a_turkish_default_locale() throws TransformationFailure {
        Locale.setDefault(Locale.forLanguageTag("tr"));

        assertThat(new UppercaseTransformation().transform("istanbul", NO_PARAMS)).isEqualTo("ISTANBUL");
    }

    @Test
    void lowercase_ignores_a_turkish_default_locale() throws TransformationFailure {
        Locale.setDefault(Locale.forLanguageTag("tr"));

        assertThat(new LowercaseTransformation().transform("TITLE", NO_PARAMS)).isEqualTo("title");
    }

    @Test
    void types_are_the_api_names() {
        assertThat(new TrimTransformation().type()).isEqualTo("trim");
        assertThat(new UppercaseTransformation().type()).isEqualTo("uppercase");
        assertThat(new LowercaseTransformation().type()).isEqualTo("lowercase");
    }

    @Test
    void text_transformations_take_no_parameters() {
        TransformationContext withFoo = new TransformationContext("name", FieldType.STRING, Map.of("foo", "1"));

        assertThat(new TrimTransformation().validate(NO_PARAMS)).isEmpty();
        assertThat(new TrimTransformation().validate(withFoo)).containsExactly("Unknown parameter 'foo' for 'trim'.");
        assertThat(new UppercaseTransformation().validate(withFoo))
                .containsExactly("Unknown parameter 'foo' for 'uppercase'.");
    }

    @Test
    void a_missing_params_map_counts_as_empty() {
        assertThat(new TransformationContext("name", FieldType.STRING, null).params()).isEmpty();
    }
}
