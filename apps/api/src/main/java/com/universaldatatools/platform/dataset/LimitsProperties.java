package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.table.ReadLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code toolbox.limits}: what a dataset may hold (core-01 TD6); the Importer is not bound by them. */
@ConfigurationProperties("toolbox.limits")
public record LimitsProperties(@DefaultValue("500000") long maxRows,
                               @DefaultValue("1000") int maxColumns,
                               @DefaultValue("32767") int maxCellLength) {

    public ReadLimits toReadLimits() {
        return new ReadLimits(maxRows, maxColumns, maxCellLength);
    }
}
