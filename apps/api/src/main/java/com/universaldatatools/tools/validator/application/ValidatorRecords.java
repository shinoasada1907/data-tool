package com.universaldatatools.tools.validator.application;

import com.universaldatatools.core.schema.DataSchema;

import java.util.List;
import java.util.Map;

/** What a validator run stores (tool-05 VD4): its NDJSON lines, config and summary. */
public final class ValidatorRecords {

    private ValidatorRecords() {
    }

    /** Sections of a run. */
    public static final String VALID = "valid";
    public static final String INVALID = "invalid";

    /**
     * One line of a section.
     *
     * @param values source cells in field order; {@code null} for a field without a column
     */
    public record RowRecord(long rowNumber, List<String> values, List<ErrorRecord> errors) {
    }

    /** @param message English, never containing the value (the value is {@code values[field index]}) */
    public record ErrorRecord(String field, String code, String rule, String message) {
    }

    public record Matched(String field, String column) {
    }

    public record Compatibility(List<Matched> matched, List<String> missingOptional, List<String> extraColumns) {
    }

    /**
     * @param errorCountsByCode  sorted by code
     * @param errorCountsByField in field order, only fields with errors
     */
    public record Summary(long totalRows, long validRows, long invalidRows, long errorCount,
                          Map<String, Long> errorCountsByCode, Map<String, Long> errorCountsByField) {
    }

    /** @param maxLengths longest value per field, in code points: XLSX export checks it first */
    public record Config(DataSchema schema, Compatibility compatibility, List<Integer> maxLengths) {
    }
}
