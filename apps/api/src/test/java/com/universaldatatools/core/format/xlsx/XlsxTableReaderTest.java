package com.universaldatatools.core.format.xlsx;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.ReadLimits;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.SheetInfo;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.support.XlsxFixtures;
import org.dhatim.fastexcel.VisibilityState;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.universaldatatools.core.table.CellKind.BOOLEAN;
import static com.universaldatatools.core.table.CellKind.DATE;
import static com.universaldatatools.core.table.CellKind.NUMBER;
import static com.universaldatatools.core.table.CellKind.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** core-02 task 5 (spec: dataset-io, "Chọn sheet của XLSX", "Kiểu gốc của ô"). */
class XlsxTableReaderTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/xlsx");

    private final XlsxTableReader reader =
            new XlsxTableReader(new XlsxZipGuard(new XlsxLimits(200L * 1024 * 1024, 100, 10_000)));

    @TempDir
    Path dir;

    @Test
    void lists_every_sheet_in_order_with_its_visibility() {
        assertThat(reader.sheets(open(threeSheets()))).containsExactly(
                new SheetInfo("Hidden", false), new SheetInfo("Data", true), new SheetInfo("Prices", true));
    }

    @Test
    void reads_the_first_visible_sheet_unless_one_is_named() {
        Path file = threeSheets();

        assertThat(inspect(file, null).sheetName()).isEqualTo("Data");
        TableInfo prices = inspect(file, "Prices");
        assertThat(prices.sheetName()).isEqualTo("Prices");
        assertThat(prices.options().sheet()).isEqualTo("Prices");
        assertThat(prices.columns()).extracting(Column::name).containsExactly("sku", "price");
        assertThat(inspect(file, "Hidden").columns()).extracting(Column::name).containsExactly("secret");
        assertThat(prices.sheets()).hasSize(3);
    }

    @Test
    void a_sheet_that_does_not_exist_is_a_configuration_error() {
        assertThatThrownBy(() -> inspect(threeSheets(), "Khong co")).isInstanceOfSatisfying(DomainException.class,
                e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.CONFIG_INVALID);
                    assertThat(e.getMessage()).isEqualTo("Sheet \"Khong co\" does not exist.");
                });
    }

    @Test
    void cells_keep_the_v0_1_text_and_gain_their_kind() {
        Path types = FIXTURES.resolve("types.xlsx");
        TableInfo info = inspect(types, null);

        Row row = rows(types, info).getFirst();
        assertThat(row.values()).containsExactly("An", "123", "-5.25", "0.001", "84901234567", "TRUE",
                "2024-02-29", "2024-12-25", "2024-12-25T13:45:30", "246", "An!", "TRUE", "#N/A", "13:30:00");
        assertThat(IntStream.range(0, 14).mapToObj(row::kind).toList()).containsExactly(TEXT, NUMBER, NUMBER,
                NUMBER, NUMBER, BOOLEAN, DATE, DATE, DATE, NUMBER, TEXT, BOOLEAN, TEXT, TEXT);
    }

    @Test
    void without_header_every_row_is_data() {
        Path file = write("no-header.xlsx", workbook -> {
            Worksheet sheet = workbook.newWorksheet("S");
            sheet.value(1, 0, "x");
            sheet.value(1, 1, 7);
            sheet.value(2, 0, "y");
        });
        TableInfo info = reader.inspect(open(file), new ReadOptions(null, null, null, false, ReadLimits.NONE));

        assertThat(info.columns()).extracting(Column::name).containsExactly("Column A", "Column B");
        assertThat(info.blankRowsSkipped()).isEqualTo(1);
        List<Row> rows = rows(file, info);
        assertThat(rows).extracting(Row::rowNumber).containsExactly(2L, 3L);
        assertThat(rows.getFirst().kind(1)).isEqualTo(NUMBER);
    }

    @Test
    void missing_rows_between_data_count_as_blank() {
        TableInfo info = inspect(XlsxFixtures.mergedAndGaps(dir), null);

        assertThat(info.rowCount()).isEqualTo(2);
        assertThat(info.blankRowsSkipped()).isEqualTo(1);
    }

    @Test
    void zip_bombs_are_still_refused() {
        Path bomb = XlsxFixtures.zipOfZeros(dir.resolve("bomb.xlsx"), "xl/worksheets/sheet1.xml", 50L * 1024 * 1024);

        assertThatThrownBy(() -> inspect(bomb, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.FILE_PARSE_ERROR));
    }

    private TableInfo inspect(Path file, String sheet) {
        return reader.inspect(open(file), new ReadOptions(sheet, null, null, null, ReadLimits.NONE));
    }

    private List<Row> rows(Path file, TableInfo info) {
        try (Stream<Row> rows = reader.read(open(file), info)) {
            return rows.toList();
        }
    }

    private Path threeSheets() {
        return write("three.xlsx", workbook -> {
            Worksheet hidden = workbook.newWorksheet("Hidden");
            hidden.value(0, 0, "secret");
            hidden.setVisibilityState(VisibilityState.HIDDEN);
            Worksheet data = workbook.newWorksheet("Data");
            data.value(0, 0, "a");
            data.value(1, 0, "1");
            Worksheet prices = workbook.newWorksheet("Prices");
            prices.value(0, 0, "sku");
            prices.value(0, 1, "price");
            prices.value(1, 0, "P1");
            prices.value(1, 1, 10);
        });
    }

    private Path write(String name, Consumer<Workbook> content) {
        Path file = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(file)) {
            Workbook workbook = new Workbook(out, "test", "1.0");
            content.accept(workbook);
            workbook.finish();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private static InputStream open(Path file) {
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
