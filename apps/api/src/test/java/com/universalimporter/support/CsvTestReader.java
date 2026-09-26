package com.universalimporter.support;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reads an exported CSV back: checks the UTF-8 BOM, then parses the rest as RFC 4180. */
public final class CsvTestReader {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvTestReader() {
    }

    public static List<List<String>> read(byte[] csvWithBom) {
        assertThat(Arrays.copyOf(csvWithBom, 3)).as("UTF-8 BOM").isEqualTo(BOM);
        String text = new String(csvWithBom, 3, csvWithBom.length - 3, StandardCharsets.UTF_8);
        try (CSVParser parser = CSVFormat.RFC4180.parse(new StringReader(text))) {
            return parser.getRecords().stream().map(CSVRecord::toList).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The text after the BOM, to check the raw form (quotes, CRLF). */
    public static String raw(byte[] csvWithBom) {
        return new String(csvWithBom, 3, csvWithBom.length - 3, StandardCharsets.UTF_8);
    }
}
