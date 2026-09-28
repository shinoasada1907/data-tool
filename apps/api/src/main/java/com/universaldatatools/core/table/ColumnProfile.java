package com.universaldatatools.core.table;

/**
 * What one pass over a column found (core-02 IO7).
 *
 * @param emptyCount cells that are {@code null} or only whitespace
 * @param maxLength  characters (code points) of the longest cell
 */
public record ColumnProfile(InferredType inferredType, long emptyCount, int maxLength) {
}
