package com.universaldatatools.core.table;

/**
 * How to read a dataset (core-01 TD6); a {@code null} option is detected or defaulted by the reader.
 *
 * @param sheet     XLSX sheet by exact name; {@code null} is the first visible sheet
 * @param delimiter CSV field separator; {@code null} is detected
 * @param encoding  CSV text encoding; {@code null} is detected from the byte order mark, else UTF-8
 * @param hasHeader whether row 1 holds the column names (CSV and XLSX); {@code null} is {@code true}
 */
public record ReadOptions(String sheet, Delimiter delimiter, TextEncoding encoding, Boolean hasHeader,
                          ReadLimits limits) {

    public ReadOptions {
        limits = limits == null ? ReadLimits.NONE : limits;
    }

    /** Everything detected, no limits. */
    public static ReadOptions defaults() {
        return new ReadOptions(null, null, null, null, ReadLimits.NONE);
    }

    public boolean header() {
        return hasHeader == null || hasHeader;
    }
}
