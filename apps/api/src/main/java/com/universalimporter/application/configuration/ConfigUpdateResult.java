package com.universalimporter.application.configuration;

import com.universalimporter.application.importsession.SessionDetails;
import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.config.Readiness;
import com.universalimporter.domain.importsession.ImportSession;

import java.util.List;

/** Outcome of a configuration PUT: the session as stored, and what the change pruned on the way. */
public record ConfigUpdateResult(ImportSession session, ImportConfiguration configuration, Readiness readiness,
                                 List<ProblemItem> warnings) {

    public ConfigUpdateResult {
        warnings = List.copyOf(warnings);
    }

    public SessionDetails details() {
        return new SessionDetails(session, configuration, readiness);
    }
}
