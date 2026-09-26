package com.universalimporter.support;

import org.dhatim.fastexcel.VisibilityState;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Random;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * XLSX files generated in code for parser tests (design X7). Files that need a real spreadsheet application
 * (cached formula results, built-in formats, 1904 dates) live in src/test/resources/fixtures/xlsx instead.
 * Row and column indexes below are zero-based, as in the writer API.
 */
public final class XlsxFixtures {

    private XlsxFixtures() {
    }

    /**
     * The BE-F11 end-to-end data (task 7) with real cell types: sheet "Customers", header
     * {@code Name, Email, Birth Date, Active, Score}.
     * <ul>
     *   <li>row 2: text "  An Nguyen ", text "AN@EXAMPLE.COM", date 1990-12-25, boolean TRUE, number 10</li>
     *   <li>row 3: text "Binh", "binh@example.com", then text "31/02/1991", "yes", "x"</li>
     *   <li>row 4: text "Chi", "an@example.com", date 1992-01-01, number 0, number 7.5</li>
     *   <li>row 5: empty, text "dung@example.com", date 1993-02-02, number 1, number 8</li>
     * </ul>
     */
    public static Path e2eCustomers(Path dir) {
        return write(dir.resolve("customers.xlsx"), workbook -> {
            Worksheet sheet = workbook.newWorksheet("Customers");
            String[] header = {"Name", "Email", "Birth Date", "Active", "Score"};
            for (int col = 0; col < header.length; col++) {
                sheet.value(0, col, header[col]);
            }
            sheet.value(1, 0, "  An Nguyen ");
            sheet.value(1, 1, "AN@EXAMPLE.COM");
            date(sheet, 1, 2, LocalDate.of(1990, 12, 25));
            sheet.value(1, 3, Boolean.TRUE);
            sheet.value(1, 4, 10);
            sheet.value(2, 0, "Binh");
            sheet.value(2, 1, "binh@example.com");
            sheet.value(2, 2, "31/02/1991");
            sheet.value(2, 3, "yes");
            sheet.value(2, 4, "x");
            sheet.value(3, 0, "Chi");
            sheet.value(3, 1, "an@example.com");
            date(sheet, 3, 2, LocalDate.of(1992, 1, 1));
            sheet.value(3, 3, 0);
            sheet.value(3, 4, 7.5);
            sheet.value(4, 1, "dung@example.com");
            date(sheet, 4, 2, LocalDate.of(1993, 2, 2));
            sheet.value(4, 3, 1);
            sheet.value(4, 4, 8);
        });
    }

    /** A real date cell: a serial number with a date format, as Excel stores it. */
    private static void date(Worksheet sheet, int row, int col, LocalDate date) {
        sheet.value(row, col, date);
        sheet.style(row, col).format("yyyy-mm-dd").set();
    }

    /** Sheet 1 "Hidden" (hidden) holds [x]/[1]; sheet 2 "Visible" holds [name]/[An]. */
    public static Path hiddenFirstSheet(Path dir) {
        return write(dir.resolve("hidden-first-sheet.xlsx"), workbook -> {
            Worksheet hidden = workbook.newWorksheet("Hidden");
            hidden.value(0, 0, "x");
            hidden.value(1, 0, 1);
            hidden.setVisibilityState(VisibilityState.HIDDEN);
            Worksheet visible = workbook.newWorksheet("Visible");
            visible.value(0, 0, "name");
            visible.value(1, 0, "An");
        });
    }

    /** Header [a,b,c]; A2:B2 merged with "M", C2 = "c"; row 3 absent; row 4 = numbers 1,2,3. */
    public static Path mergedAndGaps(Path dir) {
        return write(dir.resolve("merged-and-gaps.xlsx"), workbook -> {
            Worksheet sheet = workbook.newWorksheet("Sheet1");
            sheet.value(0, 0, "a");
            sheet.value(0, 1, "b");
            sheet.value(0, 2, "c");
            sheet.value(1, 0, "M");
            sheet.range(1, 0, 1, 1).merge();
            sheet.value(1, 2, "c");
            sheet.value(3, 0, 1);
            sheet.value(3, 1, 2);
            sheet.value(3, 2, 3);
        });
    }

    /** Row 1: "Email", "email", C1 empty, "x"; row 2: 1,2,3,4. */
    public static Path duplicateHeaders(Path dir) {
        return write(dir.resolve("duplicate-headers.xlsx"), workbook -> {
            Worksheet sheet = workbook.newWorksheet("Sheet1");
            sheet.value(0, 0, "Email");
            sheet.value(0, 1, "email");
            sheet.value(0, 3, "x");
            for (int col = 0; col < 4; col++) {
                sheet.value(1, col, col + 1);
            }
        });
    }

    /** Header [name,email] and no data rows. */
    public static Path headerOnly(Path dir) {
        return write(dir.resolve("header-only.xlsx"), workbook -> {
            Worksheet sheet = workbook.newWorksheet("Sheet1");
            sheet.value(0, 0, "name");
            sheet.value(0, 1, "email");
        });
    }

    /** Sheet 1 visible but empty; sheet 2 holds [a]/[1]. */
    public static Path emptyFirstSheet(Path dir) {
        return write(dir.resolve("empty-first-sheet.xlsx"), workbook -> {
            workbook.newWorksheet("Empty");
            Worksheet data = workbook.newWorksheet("Data");
            data.value(0, 0, "a");
            data.value(1, 0, 1);
        });
    }

    /** Row 1 empty; row 2 = "a","b". */
    public static Path blankFirstRow(Path dir) {
        return write(dir.resolve("blank-first-row.xlsx"), workbook -> {
            Worksheet sheet = workbook.newWorksheet("Sheet1");
            sheet.value(1, 0, "a");
            sheet.value(1, 1, "b");
        });
    }

    /** A single sheet, hidden, holding [a]/[1]. */
    public static Path allSheetsHidden(Path dir) {
        return write(dir.resolve("all-sheets-hidden.xlsx"), workbook -> {
            Worksheet sheet = workbook.newWorksheet("Hidden");
            sheet.value(0, 0, "a");
            sheet.value(1, 0, 1);
            sheet.setVisibilityState(VisibilityState.HIDDEN);
        });
    }

    /** Header c0..c{cols-1}, then {@code rows} data rows alternating text and number cells. */
    public static Path large(Path dir, int rows, int cols) {
        return write(dir.resolve("large-" + rows + "x" + cols + ".xlsx"), workbook -> {
            Worksheet sheet = workbook.newWorksheet("Sheet1");
            for (int col = 0; col < cols; col++) {
                sheet.value(0, col, "c" + col);
            }
            for (int row = 1; row <= rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if (col % 2 == 0) {
                        sheet.value(row, col, "text-" + row + "-" + col);
                    } else {
                        sheet.value(row, col, row * 10 + col);
                    }
                }
                if (row % 10_000 == 0) {
                    flush(sheet);
                }
            }
        });
    }

    /** A zip with one entry of {@code size} zero bytes: a tiny file that expands enormously. */
    public static Path zipOfZeros(Path file, String entryName, long size) {
        return zip(file, zip -> {
            zip.putNextEntry(new ZipEntry(entryName));
            byte[] zeros = new byte[64 * 1024];
            for (long written = 0; written < size; written += zeros.length) {
                zip.write(zeros, 0, (int) Math.min(zeros.length, size - written));
            }
            zip.closeEntry();
        });
    }

    /** A zip with {@code count} entries, each {@code size} bytes of pseudo-random letters (compresses poorly). */
    public static Path zipOfRandomText(Path file, int count, int size) {
        Random random = new Random(42);
        return zip(file, zip -> {
            for (int i = 0; i < count; i++) {
                zip.putNextEntry(new ZipEntry("xl/entry-" + i + ".xml"));
                byte[] text = new byte[size];
                for (int b = 0; b < size; b++) {
                    text[b] = (byte) ('a' + random.nextInt(26));
                }
                zip.write(text);
                zip.closeEntry();
            }
        });
    }

    private interface ZipContent {
        void write(ZipOutputStream zip) throws IOException;
    }

    private static Path zip(Path file, ZipContent content) {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            content.write(zip);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void flush(Worksheet sheet) {
        try {
            sheet.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path write(Path file, Consumer<Workbook> content) {
        try (OutputStream out = Files.newOutputStream(file)) {
            Workbook workbook = new Workbook(out, "universal-importer-tests", "1.0");
            content.accept(workbook);
            workbook.finish();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
