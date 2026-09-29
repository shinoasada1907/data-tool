package com.universaldatatools.platform.run;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link RunCleanup} every hour; off with {@code toolbox.run.cleanup.enabled=false}. */
@Component
@ConditionalOnProperty(name = "toolbox.run.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RunCleanupScheduler {

    private final RunCleanup cleanup;

    public RunCleanupScheduler(RunCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "PT1H")
    void run() {
        cleanup.cleanup();
    }
}
