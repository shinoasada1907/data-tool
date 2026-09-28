package com.universaldatatools.core.format.csv;

import com.universaldatatools.core.table.Delimiter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** core-02 IO3 (spec: dataset-io, "Tự nhận dấu phân cách CSV"). */
class DelimiterDetectorTest {

    static Stream<Arguments> samples() {
        return Stream.of(
                arguments("ma;ten;gia\n1;Bút;5000\n2;Vở;12000\n", Delimiter.SEMICOLON),
                arguments("name,note\n\"An\",\"a; b; c\"\n\"Binh\",\"x\"\n", Delimiter.COMMA),
                arguments("a\tb\n1\t2\n", Delimiter.TAB),
                arguments("a|b|c\n1|2|3\n", Delimiter.PIPE),
                arguments("email\nan@x.com\n", Delimiter.COMMA),
                arguments("a,b;c\n1,2;3\n4,5;6\n", Delimiter.COMMA),
                arguments("a;b\n1;2\nx,y,z,w\n", Delimiter.SEMICOLON),
                arguments("\"a,b\";c\n\"1,2\";3\n", Delimiter.SEMICOLON),
                arguments("x;y\n".repeat(54) + "p,q,r,s\n" + "x;y\n".repeat(5), Delimiter.SEMICOLON));
    }

    @ParameterizedTest
    @MethodSource("samples")
    void picks_the_separator_that_splits_records_most_consistently(String sample, Delimiter expected) {
        assertThat(DelimiterDetector.detect(sample)).isEqualTo(expected);
    }
}
