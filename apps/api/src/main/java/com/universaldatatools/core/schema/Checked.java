package com.universaldatatools.core.schema;

import com.universaldatatools.core.common.ProblemItem;

import java.util.List;

/** A checked definition, or every problem found in it. */
public sealed interface Checked<T> {

    record Ok<T>(T value) implements Checked<T> {
    }

    /** @param problems in the order they were found, never empty */
    record Invalid<T>(List<ProblemItem> problems) implements Checked<T> {
    }
}
