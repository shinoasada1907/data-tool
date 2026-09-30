package com.universaldatatools.core.schema;

import com.universaldatatools.core.common.RowErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** core-03 task 3: the one place a text value gets its type. */
class TypeConverterTest {

    private final TypeConverter converter = new TypeConverter();

    @Test
    void numbers_keep_their_scale() {
        assertThat(convert("12.50", FieldType.NUMBER)).isEqualTo(ok(new BigDecimal("12.50")));
    }

    @ParameterizedTest
    @ValueSource(strings = {" 12", "1e3", "+1", "1,5"})
    void not_numbers(String value) {
        assertThat(convert(value, FieldType.NUMBER))
                .isEqualTo(failed(RowErrorCode.VALIDATION_TYPE, "Value is not a valid number."));
    }

    @Test
    void a_thousand_and_one_digits_is_not_a_number() {
        assertThat(convert("1".repeat(1001), FieldType.NUMBER)).isInstanceOf(Converted.Failed.class);
    }

    @Test
    void booleans() {
        assertThat(convert("TRUE", FieldType.BOOLEAN)).isEqualTo(ok(true));
        assertThat(convert("0", FieldType.BOOLEAN)).isEqualTo(ok(false));
        assertThat(convert("yes", FieldType.BOOLEAN))
                .isEqualTo(failed(RowErrorCode.VALIDATION_TYPE, "Value is not a valid boolean (true/false/1/0)."));
    }

    @Test
    void iso_dates() {
        assertThat(convert("2024-02-29", FieldType.DATE)).isEqualTo(ok(LocalDate.of(2024, 2, 29)));
        assertThat(convert("2023-02-29", FieldType.DATE))
                .isEqualTo(failed(RowErrorCode.VALIDATION_TYPE, "Value is not a valid date (yyyy-MM-dd)."));
    }

    @Test
    void dates_in_the_field_format() {
        SchemaField field = dateField("dd/MM/yyyy");
        assertThat(converter.convert("31/01/2024", field)).isEqualTo(ok(LocalDate.of(2024, 1, 31)));
        Converted wrong = failed(RowErrorCode.VALIDATION_DATE_FORMAT, "Value is not a valid date (dd/MM/yyyy).");
        assertThat(converter.convert("30/02/2024", field)).isEqualTo(wrong);
        assertThat(converter.convert("2024-01-31", field)).isEqualTo(wrong);
    }

    @Test
    void emails() {
        assertThat(convert("a@x.com", FieldType.EMAIL)).isEqualTo(ok("a@x.com"));
        assertThat(convert("a@", FieldType.EMAIL))
                .isEqualTo(failed(RowErrorCode.VALIDATION_EMAIL, "Value is not a valid email address."));
    }

    @Test
    void strings_are_taken_as_they_are() {
        assertThat(convert("=cmd()", FieldType.STRING)).isEqualTo(ok("=cmd()"));
    }

    private Converted convert(String value, FieldType type) {
        return converter.convert(value, new SchemaField("f", type, false, FieldConstraints.NONE));
    }

    private static SchemaField dateField(String format) {
        return new SchemaField("d", FieldType.DATE, false,
                new FieldConstraints(false, null, null, null, null, null, format, null));
    }

    private static Converted ok(Object value) {
        return new Converted.Ok(value);
    }

    private static Converted failed(RowErrorCode code, String message) {
        return new Converted.Failed(code, message);
    }
}
