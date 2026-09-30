package com.universaldatatools.core.table;

/**
 * The options a read actually used once everything was detected; {@code sheet} is {@code null} except for XLSX,
 * {@code delimiter} and {@code encoding} except for CSV.
 */
public record ResolvedReadOptions(String sheet, Delimiter delimiter, TextEncoding encoding, boolean hasHeader) {
}
