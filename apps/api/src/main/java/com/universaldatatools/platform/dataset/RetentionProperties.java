package com.universaldatatools.platform.dataset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * {@code toolbox.retention}: how long toolbox data lives after its last use (core-01 TD12). A bare number of
 * {@code dataset-ttl} and {@code run-ttl} is hours.
 *
 * @param touchInterval the last-use time is written at most this often, not on every read
 * @param runTtl        how long a tool run lives after its last use (core-06)
 */
@ConfigurationProperties("toolbox.retention")
public record RetentionProperties(@DefaultValue("24h") @DurationUnit(ChronoUnit.HOURS) Duration datasetTtl,
                                  @DefaultValue("10m") Duration touchInterval,
                                  @DefaultValue("24h") @DurationUnit(ChronoUnit.HOURS) Duration runTtl) {

    public RetentionProperties {
        if (datasetTtl.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("toolbox.retention.dataset-ttl must be at least 1 minute, was "
                    + datasetTtl);
        }
        if (runTtl.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("toolbox.retention.run-ttl must be at least 1 minute, was " + runTtl);
        }
    }
}
