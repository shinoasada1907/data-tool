package com.universalimporter.api.process;

import com.universalimporter.application.pipeline.PipelineSummaryView;
import com.universalimporter.domain.importsession.SessionStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * {@code PipelineSummaryDto} of the API contract V0.1: counts of the run just made.
 *
 * @param errorCountsByCode  errors (not rows) per code, sorted by code
 * @param errorCountsByField errors per field, in schema order
 */
public record PipelineSummaryDto(UUID sessionId, SessionStatus status, long total, long valid, long invalid,
                                 Map<String, Long> errorCountsByCode, Map<String, Long> errorCountsByField,
                                 Instant processedAt) {

    public static PipelineSummaryDto from(PipelineSummaryView view) {
        return new PipelineSummaryDto(view.sessionId(), view.status(), view.summary().total(), view.summary().valid(),
                view.summary().invalid(), view.summary().errorCountsByCode(), view.summary().errorCountsByField(),
                view.summary().processedAt());
    }
}
