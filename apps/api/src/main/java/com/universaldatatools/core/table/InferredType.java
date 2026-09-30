package com.universaldatatools.core.table;

import java.util.Locale;

/** What the values of a column look like (core-02 IO7); {@link #EMPTY} when every cell is empty. */
public enum InferredType {
    STRING, NUMBER, BOOLEAN, DATE, EMAIL, EMPTY;

    /** Lower case, as the API sends it: {@code number}. */
    public String apiName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
