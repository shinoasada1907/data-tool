package com.universaldatatools.core.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TextValuesTest {

    static Stream<Arguments> values() {
        return Stream.of(
                Arguments.of(null, true, null),
                Arguments.of("", true, ""),
                Arguments.of("   ", true, ""),
                Arguments.of("   ", true, ""),
                Arguments.of(" a b ", false, "a b"),
                Arguments.of(" An ", false, "An"),
                Arguments.of("\tx\n", false, "x"));
    }

    @ParameterizedTest
    @MethodSource("values")
    void blank_aware_empty_check_and_strip(String input, boolean empty, String stripped) {
        assertThat(TextValues.isEmpty(input)).isEqualTo(empty);
        assertThat(TextValues.strip(input)).isEqualTo(stripped);
    }
}
