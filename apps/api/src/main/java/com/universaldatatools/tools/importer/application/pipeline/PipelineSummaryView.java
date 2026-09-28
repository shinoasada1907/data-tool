package com.universaldatatools.tools.importer.application.pipeline;

import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.pipeline.ResultSummary;

import java.util.UUID;

/** What a successful process run answers: the session's new status and the stored summary. */
public record PipelineSummaryView(UUID sessionId, SessionStatus status, ResultSummary summary) {
}
