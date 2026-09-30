package com.universaldatatools.tools.importer.domain.pipeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts of one run (design P4). Errors are counted, not rows: a row with three errors adds three.
 *
 * @param errorCountsByCode  by code name, sorted by name; only codes that occurred
 * @param errorCountsByField by field, in schema order; only fields that had errors
 */
public record PipelineSummary(long total, long valid, long invalid, Map<String, Long> errorCountsByCode,
                              Map<String, Long> errorCountsByField) {

    public PipelineSummary {
        errorCountsByCode = Collections.unmodifiableMap(new LinkedHashMap<>(errorCountsByCode));
        errorCountsByField = Collections.unmodifiableMap(new LinkedHashMap<>(errorCountsByField));
    }
}
