package com.universalimporter.application.pipeline;

import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.pipeline.ResultSummary;

import java.util.UUID;

/** What a successful process run answers: the session's new status and the stored summary. */
public record PipelineSummaryView(UUID sessionId, SessionStatus status, ResultSummary summary) {
}
