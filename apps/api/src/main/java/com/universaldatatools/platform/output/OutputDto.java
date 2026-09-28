package com.universaldatatools.platform.output;

/**
 * {@code OutputDto} of the API contract Toolbox v1: the file format to write and its options. Enum values are text
 * so a wrong one is a clear {@code CONFIG_INVALID}; see {@link OutputSpec#from}.
 */
public record OutputDto(String format, CsvOptions csv, JsonOptions json, XlsxOptions xlsx) {

    public record CsvOptions(String delimiter, Boolean header, Boolean bom, Boolean formulaGuard) {
    }

    public record JsonOptions(Boolean pretty, String typing) {
    }

    public record XlsxOptions(String sheetName, String typing) {
    }
}
