package com.universaldatatools.platform.dataset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link DatasetCleanup} shortly after start-up, then every hour (core-01 TD17). Off with
 * {@code toolbox.dataset.cleanup.enabled=false}, as the tests do.
 */
@Component
@ConditionalOnProperty(name = "toolbox.dataset.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class DatasetCleanupScheduler {

    private final DatasetCleanup cleanup;

    public DatasetCleanupScheduler(DatasetCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "PT1H")
    void run() {
        cleanup.cleanupExpired();
    }
}
