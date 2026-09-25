package com.universalimporter.domain.importsession;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static com.universalimporter.domain.common.ErrorCode.FILE_EMPTY;
import static com.universalimporter.domain.common.ErrorCode.FILE_UNSUPPORTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class FileTypeDetectorTest {

    /** ZIP local file header, the first bytes of every .xlsx. */
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void accepts_supported_files(String name, byte[] head, SourceFileType expected) {
        assertThat(FileTypeDetector.detect(name, head)).isEqualTo(expected);
    }

    static Stream<Arguments> accepts_supported_files() {
        return Stream.of(
                arguments("data.csv", utf8("a,b\n1,2"), SourceFileType.CSV),
                arguments("DATA.CSV", utf8("a,b"), SourceFileType.CSV),
                arguments("data.xlsx", ZIP, SourceFileType.XLSX)
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
}
