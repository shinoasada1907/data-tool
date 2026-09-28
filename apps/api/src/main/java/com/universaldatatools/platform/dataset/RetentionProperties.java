package com.universaldatatools.platform.dataset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * {@code toolbox.retention}: how long toolbox data lives after its last use (core-01 TD12). A bare number of
 * {@code dataset-ttl} is hours.
 *
 * @param touchInterval the last-use time is written at most this often, not on every read
 */
@ConfigurationProperties("toolbox.retention")
public record RetentionProperties(@DefaultValue("24h") @DurationUnit(ChronoUnit.HOURS) Duration datasetTtl,
                                  @DefaultValue("10m") Duration touchInterval) {

    public RetentionProperties {
        if (datasetTtl.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("toolbox.retention.dataset-ttl must be at least 1 minute, was "
                    + datasetTtl);
        }
    }
}
