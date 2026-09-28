package com.universaldatatools.tools.importer.application.configuration;

import com.universaldatatools.tools.importer.application.importsession.SessionDetails;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.config.Readiness;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;

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
