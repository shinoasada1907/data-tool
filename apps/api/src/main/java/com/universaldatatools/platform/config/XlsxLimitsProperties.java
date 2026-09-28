package com.universaldatatools.platform.config;

import com.universaldatatools.core.format.xlsx.XlsxLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** {@code toolbox.format.xlsx}: the zip bomb limits of {@link XlsxLimits}, bound here so core.format stays free of Spring. */
@ConfigurationProperties("toolbox.format.xlsx")
public record XlsxLimitsProperties(@DefaultValue("200MB") DataSize maxUncompressedSize,
                                   @DefaultValue("100") int maxInflateRatio,
                                   @DefaultValue("10000") int maxEntries) {

    public XlsxLimits toLimits() {
        return new XlsxLimits(maxUncompressedSize.toBytes(), maxInflateRatio, maxEntries);
    }
}
