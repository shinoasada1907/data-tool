package com.universaldatatools.core.table;

/**
 * How to write a table (core-02 IO9). Each writer reads only its own options: CSV {@code delimiter}, {@code header},
 * {@code bom}, {@code formulaGuard}; JSON {@code pretty}, {@code typing}; XLSX {@code sheetName}, {@code typing}.
 */
public record WriteOptions(Delimiter delimiter, boolean header, boolean bom, boolean formulaGuard, boolean pretty,
                           Typing typing, String sheetName) {

    public static WriteOptions csvDefaults() {
        return new WriteOptions(Delimiter.COMMA, true, true, true, false, Typing.STRING, null);
    }

    public static WriteOptions jsonDefaults() {
        return new WriteOptions(null, true, false, false, false, Typing.PRESERVE, null);
    }

    public static WriteOptions xlsxDefaults() {
        return new WriteOptions(null, true, false, false, false, Typing.PRESERVE, null);
    }
}
