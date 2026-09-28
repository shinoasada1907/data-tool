package com.universaldatatools.core.format.xlsx;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Zip bomb limits for uploaded workbooks (design X4).
 *
 * @param maxUncompressedSize total size of all entries once inflated
 * @param maxInflateRatio     largest inflated/compressed ratio allowed for an entry above 1MB
 * @param maxEntries          largest number of zip entries
 */
@ConfigurationProperties("importer.xlsx")
public record XlsxLimits(DataSize maxUncompressedSize, int maxInflateRatio, int maxEntries) {
}
