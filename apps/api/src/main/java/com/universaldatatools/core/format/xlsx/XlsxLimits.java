package com.universaldatatools.core.format.xlsx;

/**
 * Zip bomb limits for uploaded workbooks (design X4).
 *
 * @param maxUncompressedBytes total size of all entries once inflated
 * @param maxInflateRatio     largest inflated/compressed ratio allowed for an entry above 1MB
 * @param maxEntries          largest number of zip entries
 */
public record XlsxLimits(long maxUncompressedBytes, int maxInflateRatio, int maxEntries) {
}
