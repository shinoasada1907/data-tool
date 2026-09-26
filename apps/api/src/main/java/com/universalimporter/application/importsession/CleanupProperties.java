package com.universalimporter.application.importsession;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cleanup of expired sessions (BE-F11 D3).
 *
 * @param sessionTtl how long a session lives after its last change ({@code IMPORTER_SESSION_TTL})
 * @param interval   time between two runs
 */
@ConfigurationProperties("importer.cleanup")
public record CleanupProperties(@DefaultValue("true") boolean enabled,
                                @DefaultValue("24h") Duration sessionTtl,
                                @DefaultValue("1h") Duration interval) {
}
