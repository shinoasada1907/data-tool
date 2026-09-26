package com.universalimporter.application.importsession;

import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.config.Readiness;
import com.universalimporter.domain.config.ReadinessEvaluator;
import com.universalimporter.domain.importsession.ImportSession;

/** A session with its configuration and readiness: what every session response shows (BE-F04 design S9). */
public record SessionDetails(ImportSession session, ImportConfiguration configuration, Readiness readiness) {

    public static SessionDetails of(ImportSession session, ImportConfiguration configuration) {
        return new SessionDetails(session, configuration, ReadinessEvaluator.standard().evaluate(configuration));
    }
}
