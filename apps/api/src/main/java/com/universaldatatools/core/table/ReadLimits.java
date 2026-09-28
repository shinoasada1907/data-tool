package com.universaldatatools.core.table;

/**
 * Upper bounds checked while a dataset is inspected (core-02 IO8); 0 means no bound.
 *
 * @param maxRows       data rows, blank rows not counted
 * @param maxColumns    columns
 * @param maxCellLength characters (code points) of one cell
 */
public record ReadLimits(long maxRows, int maxColumns, int maxCellLength) {

    /** For the Importer, whose files V0.1 does not bound. */
    public static final ReadLimits NONE = new ReadLimits(0, 0, 0);
}
