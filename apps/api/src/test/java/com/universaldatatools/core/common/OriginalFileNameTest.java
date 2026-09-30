package com.universaldatatools.core.common;

import com.universaldatatools.core.common.OriginalFileName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class OriginalFileNameTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    void sanitizes_client_file_name_for_display_only(String raw, String expected) {
        assertThat(OriginalFileName.sanitize(raw)).isEqualTo(expected);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                arguments("customers.csv", "customers.csv"),
                arguments("../../etc/passwd.csv", "passwd.csv"),
                arguments("C:\\Users\\an\\Desktop\\khách hàng.csv", "khách hàng.csv"),
                arguments("  report.xlsx  ", "report.xlsx"),
                arguments("a\u0000b\u001F.csv", "ab.csv"),
                // Bidi override would let "invoice\u202Evsc.csv" display as "invoicecsv.csv".
                arguments("invoice\u202Evsc.csv", "invoicevsc.csv"),
                // macOS sends NFD: 'a' + combining acute must become the single code point 'á'.
                arguments("kha\u0301ch.csv", "kh\u00E1ch.csv"),
                arguments("a".repeat(300) + ".csv", "a".repeat(251) + ".csv"),
                // The cut must not split a surrogate pair (PostgreSQL rejects the broken UTF-8).
                arguments("a".repeat(250) + "\uD83D\uDE00" + "b.csv", "a".repeat(250) + ".csv"),
                arguments(null, ""),
                arguments("   ", "")
        );
    }
}
