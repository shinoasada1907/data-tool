package com.universalimporter.infrastructure.scheduling;

import com.universalimporter.application.importsession.CleanupReport;
import com.universalimporter.application.importsession.SessionCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the cleanup at startup, then every {@code importer.cleanup.interval} (BE-F11 D3). Off when
 * {@code importer.cleanup.enabled} is false, as in tests.
 */
@Component
@ConditionalOnProperty(name = "importer.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SessionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final SessionCleanupService service;

    public SessionCleanupScheduler(SessionCleanupService service) {
        this.service = service;
    }

    /** Never throws: an exception would end the scheduled runs for good. */
    @Scheduled(initialDelayString = "PT0S", fixedDelayString = "${importer.cleanup.interval:PT1H}")
    public void run() {
        try {
            CleanupReport report = service.cleanupExpired();
            log.info("Session cleanup: {} sessions and {} orphan directories deleted, {} skipped, {} failed",
                    report.deletedSessions(), report.deletedOrphans(), report.skipped(), report.failures());
        } catch (RuntimeException e) {
            log.error("Session cleanup failed", e);
        }
    }
}
