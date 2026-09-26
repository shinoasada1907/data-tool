package com.universalimporter.domain.pipeline;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The stored summary of the last successful run of a session (design P5).
 *
 * @param configHash hash of the configuration the result was made with (design D7)
 */
public record ResultSummary(long total, long valid, long invalid, Map<String, Long> errorCountsByCode,
                            Map<String, Long> errorCountsByField, Instant processedAt, String configHash) {

    public ResultSummary {
        errorCountsByCode = Collections.unmodifiableMap(new LinkedHashMap<>(errorCountsByCode));
        errorCountsByField = Collections.unmodifiableMap(new LinkedHashMap<>(errorCountsByField));
    }

    public static ResultSummary of(PipelineSummary summary, Instant processedAt, String configHash) {
        return new ResultSummary(summary.total(), summary.valid(), summary.invalid(), summary.errorCountsByCode(),
                summary.errorCountsByField(), processedAt, configHash);
    }
}
