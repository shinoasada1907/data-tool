package com.universaldatatools.core.table;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static com.universaldatatools.core.common.ErrorCode.FILE_EMPTY;
import static com.universaldatatools.core.common.ErrorCode.FILE_UNSUPPORTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class FileTypeDetectorTest {

    /** ZIP local file header, the first bytes of every .xlsx. */
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void accepts_supported_files(String name, byte[] head, DataFormat expected) {
        assertThat(FileTypeDetector.detect(name, head)).isEqualTo(expected);
    }

    static Stream<Arguments> accepts_supported_files() {
        return Stream.of(
                arguments("data.csv", utf8("a,b\n1,2"), DataFormat.CSV),
                arguments("DATA.CSV", utf8("a,b"), DataFormat.CSV),
                arguments("data.xlsx", ZIP, DataFormat.XLSX)
        );
    }

    @ParameterizedTest(name = "{0} → {2}")
    @MethodSource
    void rejects_unsupported_or_empty_files(String name, byte[] head, ErrorCode expected) {
        assertThatThrownBy(() -> FileTypeDetector.detect(name, head))
                .isInstanceOfSatisfying(DomainException.class, ex -> assertThat(ex.code()).isEqualTo(expected));
    }

    static Stream<Arguments> rejects_unsupported_or_empty_files() {
        return Stream.of(
                arguments("data.xlsx", utf8("a,b,c"), FILE_UNSUPPORTED),
                arguments("tiny.xlsx", new byte[]{0x50, 0x4B}, FILE_UNSUPPORTED),
                arguments("data.csv", new byte[]{'a', ',', 0x00}, FILE_UNSUPPORTED),
                arguments("data.xls", ZIP, FILE_UNSUPPORTED),
                arguments("data.json", utf8("{}"), FILE_UNSUPPORTED),
                arguments("data", utf8("a,b"), FILE_UNSUPPORTED),
                arguments("", utf8("a,b"), FILE_UNSUPPORTED),
                arguments("empty.csv", new byte[0], FILE_EMPTY),
                // The extension is checked first: an empty .xls is still unsupported.
                arguments("empty.xls", new byte[0], FILE_UNSUPPORTED)
        );
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "dataset {0}")
    @MethodSource
    void datasets_also_accept_json_and_utf16_csv(String name, byte[] head, DataFormat expected) {
        assertThat(FileTypeDetector.detectDataset(name, head)).isEqualTo(expected);
    }

    static Stream<Arguments> datasets_also_accept_json_and_utf16_csv() {
        return Stream.of(
                arguments("data.JSON", utf8("  [{\"a\":1}]"), DataFormat.JSON),
                arguments("bom.json", new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '['}, DataFormat.JSON),
                arguments("utf16.csv", new byte[] {(byte) 0xFF, (byte) 0xFE, 'a', 0x00}, DataFormat.CSV),
                arguments("data.xlsx", ZIP, DataFormat.XLSX));
    }

    @ParameterizedTest(name = "dataset {0} → {2}")
    @MethodSource
    void datasets_refuse(String name, byte[] head, ErrorCode expected) {
        assertThatThrownBy(() -> FileTypeDetector.detectDataset(name, head))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(expected));
    }

    static Stream<Arguments> datasets_refuse() {
        return Stream.of(
                arguments("data.json", utf8("{\"a\":1}"), ErrorCode.FILE_PARSE_ERROR),
                arguments("nul.csv", new byte[] {'a', 0x00}, FILE_UNSUPPORTED),
                arguments("data.xls", utf8("x"), FILE_UNSUPPORTED));
    }

    @ParameterizedTest(name = "importer {0}")
    @MethodSource
    void the_importer_still_refuses_json_and_utf16(String name, byte[] head) {
        assertThatThrownBy(() -> FileTypeDetector.detect(name, head)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(FILE_UNSUPPORTED));
    }

    static Stream<Arguments> the_importer_still_refuses_json_and_utf16() {
        return Stream.of(
                arguments("data.json", utf8("[{}]")),
                arguments("utf16.csv", new byte[] {(byte) 0xFF, (byte) 0xFE, 'a', 0x00}));
    }
}
