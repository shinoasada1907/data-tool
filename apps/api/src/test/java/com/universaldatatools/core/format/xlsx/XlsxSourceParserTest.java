package com.universaldatatools.core.format.xlsx;

import com.universaldatatools.core.table.SourceFileType;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.ImportRow;
import com.universaldatatools.core.table.SourceColumn;
import com.universaldatatools.core.table.SourceSchema;
import com.universaldatatools.support.XlsxFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XlsxSourceParserTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/xlsx");

    private final XlsxSourceParser parser =
            new XlsxSourceParser(new XlsxZipGuard(new XlsxLimits(DataSize.ofMegabytes(200), 100, 10_000)));

    @TempDir
    Path dir;

    @Test
    void supports_only_xlsx() {
        assertThat(parser.supports(SourceFileType.XLSX)).isTrue();
        assertThat(parser.supports(SourceFileType.CSV)).isFalse();
    }

    @Test
    void a_workbook_saved_by_excel_converts_every_kind_of_cell() {
        Path types = FIXTURES.resolve("types.xlsx");

        SourceSchema schema = inspect(types);

        assertThat(schema.sheetName()).isEqualTo("Data");
        assertThat(schema.columns()).extracting(SourceColumn::name).containsExactly(
                "text", "int", "decimal", "small", "big", "bool", "date_builtin", "date_custom", "datetime",
                "formula_num", "formula_text", "formula_bool", "na", "time_only");
        assertThat(schema.totalRows()).isEqualTo(1);
        assertThat(rows(types)).containsExactly(row(2, "An", "123", "-5.25", "0.001", "84901234567", "TRUE",
                "2024-02-29", "2024-12-25", "2024-12-25T13:45:30", "246", "An!", "TRUE", "#N/A", "13:30:00"));
    }

    @Test
    void dates_follow_the_1904_date_system_when_the_workbook_uses_it() {
        assertThat(rows(FIXTURES.resolve("date1904.xlsx"))).containsExactly(row(2, "2024-01-01"));
    }

    @Test
    void the_first_visible_sheet_is_read() {
        Path file = XlsxFixtures.hiddenFirstSheet(dir);

        SourceSchema schema = inspect(file);

        assertThat(schema.sheetName()).isEqualTo("Visible");
        assertThat(schema.columns()).extracting(SourceColumn::name).containsExactly("name");
        assertThat(rows(file)).containsExactly(row(2, "An"));
    }

    @Test
    void row_numbers_keep_gaps_and_merged_cells_keep_only_their_first_cell() {
        Path file = XlsxFixtures.mergedAndGaps(dir);

        assertThat(rows(file)).containsExactly(row(2, "M", null, "c"), row(4, "1", "2", "3"));
        assertThat(inspect(file).totalRows()).isEqualTo(2);
    }

    @Test
    void duplicate_and_blank_headers_get_unique_names() {
        assertThat(inspect(XlsxFixtures.duplicateHeaders(dir)).columns()).extracting(SourceColumn::name)
                .containsExactly("Email", "email (2)", "Column C", "x");
    }

    @Test
    void a_header_only_sheet_has_no_rows() {
        Path file = XlsxFixtures.headerOnly(dir);

        assertThat(inspect(file).totalRows()).isZero();
        assertThat(rows(file)).isEmpty();
    }

    @Test
    void an_empty_first_visible_sheet_is_file_empty() {
        assertFailure(XlsxFixtures.emptyFirstSheet(dir), ErrorCode.FILE_EMPTY,
                "The first row must contain column headers.");
    }

    @Test
    void a_blank_first_row_is_file_empty() {
        assertFailure(XlsxFixtures.blankFirstRow(dir), ErrorCode.FILE_EMPTY,
                "The first row must contain column headers.");
    }

    @Test
    void a_workbook_without_a_visible_sheet_is_file_empty() {
        assertFailure(XlsxFixtures.allSheetsHidden(dir), ErrorCode.FILE_EMPTY, "Workbook has no visible sheet.");
    }

    @Test
    void a_zip_signature_followed_by_garbage_is_not_a_workbook() throws IOException {
        byte[] garbage = new byte[104];
        garbage[0] = 0x50;
        garbage[1] = 0x4B;
        garbage[2] = 0x03;
        garbage[3] = 0x04;
        Arrays.fill(garbage, 4, garbage.length, (byte) 0x7A);
        Path file = Files.write(dir.resolve("garbage.xlsx"), garbage);

        assertFailure(file, ErrorCode.FILE_PARSE_ERROR, "File is not a valid XLSX workbook.");
    }

    @Test
    void a_zip_without_a_workbook_part_is_not_a_workbook() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("hello.txt"));
            zip.write("hello".getBytes(UTF_8));
            zip.closeEntry();
        }
        Path file = Files.write(dir.resolve("not-a-workbook.xlsx"), bytes.toByteArray());

        assertFailure(file, ErrorCode.FILE_PARSE_ERROR, "File is not a valid XLSX workbook.");
    }

    @Test
    void closing_the_read_stream_closes_the_input_and_leaves_no_temporary_file() throws IOException {
        List<Path> before = temporaryWorkbooks();
        TrackingInputStream input = new TrackingInputStream(Files.readAllBytes(XlsxFixtures.headerOnly(dir)));

        parser.read(input).close();

        assertThat(input.closed).isTrue();
        assertThat(temporaryWorkbooks()).isEqualTo(before);
    }

    private SourceSchema inspect(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return parser.inspect(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<ImportRow> rows(Path file) {
        try (InputStream in = Files.newInputStream(file); Stream<ImportRow> rows = parser.read(in)) {
            return rows.toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertFailure(Path file, ErrorCode code, String message) {
        assertThatThrownBy(() -> inspect(file)).isInstanceOfSatisfying(DomainException.class, ex -> {
            assertThat(ex.code()).isEqualTo(code);
            assertThat(ex.getMessage()).isEqualTo(message);
        });
    }

    private static ImportRow row(long rowNumber, String... values) {
        return new ImportRow(rowNumber, Arrays.asList(values));
    }

    private static List<Path> temporaryWorkbooks() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("xlsx-") && name.endsWith(".xlsx");
            }).sorted().toList();
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        boolean closed;

        TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
