package com.universaldatatools.tools.importer.infrastructure.scheduling;

import com.universaldatatools.tools.importer.application.importsession.CleanupProperties;
import com.universaldatatools.tools.importer.application.importsession.CleanupReport;
import com.universaldatatools.tools.importer.application.importsession.SessionCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Runs the cleanup at startup, then every {@code toolbox.importer.cleanup.interval} (BE-F11 D3), taken from the checked
 * {@link CleanupProperties}: one value, one default. Off when {@code toolbox.importer.cleanup.enabled} is false, as in
 * tests.
 */
@Component
@ConditionalOnProperty(name = "toolbox.importer.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SessionCleanupScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final SessionCleanupService service;
    private final CleanupProperties properties;

    public SessionCleanupScheduler(SessionCleanupService service, CleanupProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(this::run, properties.interval(), Duration.ZERO));
    }

    /** Logs a failure itself, so that each run reports in one line. */
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
