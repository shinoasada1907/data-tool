package com.universaldatatools.platform.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadNamesTest {

    @ParameterizedTest
    @CsvSource(quoteCharacter = '\'', value = {
            "customers.csv, -valid.json, customers-valid.json",
            "khách hàng.xlsx, -valid.csv, khách hàng-valid.csv",
            "report.final.xlsx, -errors.csv, report.final-errors.csv",
            "'a\"b.csv', -errors.csv, a_b-errors.csv",
            "'a\\b/c.csv', -errors.csv, a_b_c-errors.csv",
            "data, -valid.json, data-valid.json",
            ".csv, -valid.json, export-valid.json",
            "'', -valid.csv, export-valid.csv",
    })
    void the_name_is_the_original_without_its_last_extension_plus_the_suffix(String original, String suffix,
                                                                              String expected) {
        assertThat(DownloadNames.of(original, suffix)).isEqualTo(expected);
    }

    @Test
    void control_characters_are_replaced() {
        assertThat(DownloadNames.of("a\u0007b\nc.csv", "-valid.csv")).isEqualTo("a_b_c-valid.csv");
    }

    @Test
    void invisible_format_characters_cannot_spoof_the_extension() {
        assertThat(DownloadNames.of("invoice\u202Efdp.exe.csv", "-valid.csv")).isEqualTo("invoice_fdp.exe-valid.csv");
        assertThat(DownloadNames.of("a\u0085b.csv", "-valid.csv")).isEqualTo("a_b-valid.csv");
    }

    @Test
    void a_missing_name_is_export() {
        assertThat(DownloadNames.of(null, "-valid.csv")).isEqualTo("export-valid.csv");
    }
}
