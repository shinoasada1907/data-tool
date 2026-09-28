package com.universaldatatools.tools.importer.application.importsession;

import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.config.Readiness;
import com.universaldatatools.tools.importer.domain.config.ReadinessEvaluator;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;

/** A session with its configuration and readiness: what every session response shows (BE-F04 design S9). */
public record SessionDetails(ImportSession session, ImportConfiguration configuration, Readiness readiness) {

    public static SessionDetails of(ImportSession session, ImportConfiguration configuration) {
        return new SessionDetails(session, configuration, ReadinessEvaluator.standard().evaluate(configuration));
    }
}
