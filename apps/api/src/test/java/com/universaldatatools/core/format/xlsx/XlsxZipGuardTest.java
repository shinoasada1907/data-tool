package com.universaldatatools.core.format.xlsx;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.support.XlsxFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XlsxZipGuardTest {

    private static final XlsxLimits DEFAULTS = new XlsxLimits(DataSize.ofMegabytes(200), 100, 10_000);
    private static final String TOO_BIG = "XLSX file expands beyond the allowed limits.";

    @TempDir
    Path dir;

    @Test
    void an_ordinary_workbook_passes() {
        // Written by the fastexcel writer, whose local headers declare size 0: java.util.zip rejects these.
        assertPasses(DEFAULTS, XlsxFixtures.headerOnly(dir));
    }

    @Test
    void a_workbook_saved_by_excel_passes() {
        assertPasses(DEFAULTS, Path.of("src/test/resources/fixtures/xlsx/types.xlsx"));
    }

    @Test
    void an_entry_that_inflates_far_beyond_the_ratio_is_rejected() {
        // 50MB of zeros compresses to about 50KB: a classic zip bomb, well under the total limit.
        Path bomb = XlsxFixtures.zipOfZeros(dir.resolve("bomb.xlsx"), "xl/worksheets/sheet1.xml", 50L * 1024 * 1024);

        assertRejected(DEFAULTS, bomb, TOO_BIG);
    }

    @Test
    void too_much_uncompressed_data_in_total_is_rejected() {
        Path big = XlsxFixtures.zipOfRandomText(dir.resolve("big.xlsx"), 3, 600 * 1024);

        assertRejected(new XlsxLimits(DataSize.ofMegabytes(1), 100, 10_000), big, TOO_BIG);
    }

    @Test
    void too_many_entries_are_rejected() {
        Path many = XlsxFixtures.zipOfRandomText(dir.resolve("many.xlsx"), 101, 10);

        assertRejected(new XlsxLimits(DataSize.ofMegabytes(200), 100, 100), many, TOO_BIG);
    }

    @Test
    void a_highly_compressible_entry_under_the_ratio_passes() {
        Path zeros = XlsxFixtures.zipOfZeros(dir.resolve("zeros.xlsx"), "xl/worksheets/sheet1.xml", 2L * 1024 * 1024);

        assertPasses(new XlsxLimits(DataSize.ofMegabytes(200), 5_000, 10_000), zeros);
    }

    @Test
    void bytes_that_are_not_a_zip_are_not_a_workbook() {
        InputStream hello = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new XlsxZipGuard(DEFAULTS).check(hello))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.FILE_PARSE_ERROR);
                    assertThat(ex.getMessage()).isEqualTo("File is not a valid XLSX workbook.");
                });
    }

    private static void assertPasses(XlsxLimits limits, Path file) {
        assertThatCode(() -> {
            try (InputStream in = Files.newInputStream(file)) {
                new XlsxZipGuard(limits).check(in);
            }
        }).doesNotThrowAnyException();
    }

    private static void assertRejected(XlsxLimits limits, Path file, String message) {
        assertThatThrownBy(() -> {
            try (InputStream in = Files.newInputStream(file)) {
                new XlsxZipGuard(limits).check(in);
            }
        }).isInstanceOfSatisfying(DomainException.class, ex -> {
            assertThat(ex.code()).isEqualTo(ErrorCode.FILE_PARSE_ERROR);
            assertThat(ex.getMessage()).isEqualTo(message);
        });
    }
}
