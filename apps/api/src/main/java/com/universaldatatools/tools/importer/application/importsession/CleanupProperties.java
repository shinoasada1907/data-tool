package com.universaldatatools.tools.importer.application.importsession;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Cleanup of expired sessions (BE-F11 D3). Checked at startup: a value that would delete sessions still in use
 * stops the application instead.
 *
 * @param sessionTtl how long a session lives after its last change ({@code TOOLBOX_IMPORTER_SESSION_TTL}, or {@code IMPORTER_SESSION_TTL} of V0.1); a bare number
 *                   is hours, so {@code 24} is a day, not 24 milliseconds
 * @param interval   time between two runs; a bare number is minutes
 */
@ConfigurationProperties("toolbox.importer.cleanup")
public record CleanupProperties(@DefaultValue("true") boolean enabled,
                                @DefaultValue("24h") @DurationUnit(ChronoUnit.HOURS) Duration sessionTtl,
                                @DefaultValue("1h") @DurationUnit(ChronoUnit.MINUTES) Duration interval) {

    static final Duration MINIMUM = Duration.ofMinutes(1);

    public CleanupProperties {
        if (sessionTtl.compareTo(MINIMUM) < 0) {
            throw new IllegalArgumentException(
                    "toolbox.toolbox.importer.cleanup.session-ttl must be at least 1 minute, was " + sessionTtl);
        }
        if (interval.compareTo(MINIMUM) < 0) {
            throw new IllegalArgumentException("toolbox.toolbox.importer.cleanup.interval must be at least 1 minute, was " + interval);
        }
    }
}
