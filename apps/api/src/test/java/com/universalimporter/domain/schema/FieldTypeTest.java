package com.universalimporter.domain.schema;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FieldTypeTest {

    @ParameterizedTest
    @CsvSource({"string,STRING", "number,NUMBER", "boolean,BOOLEAN", "date,DATE", "email,EMAIL"})
    void from_code_finds_each_lowercase_code(String code, FieldType expected) {
        assertThat(FieldType.fromCode(code)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"String", "text", " string"})
    void from_code_rejects_anything_else(String code) {
        assertThat(FieldType.fromCode(code)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(FieldType.class)
    void code_is_the_lowercase_name(FieldType type) {
        assertThat(type.code()).isEqualTo(type.name().toLowerCase());
        assertThat(FieldType.fromCode(type.code())).contains(type);
    }
}
