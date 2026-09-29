package com.universaldatatools.core.transform;

import java.util.Arrays;
import java.util.List;

/**
 * Transformation parameters are strings; a list travels as its items joined by a line feed (core-03 SR8). Tools
 * that take a JSON array, such as the Cleaner, join it; items therefore never contain a line feed.
 */
public final class ListParams {

    private static final String SEPARATOR = "\n";

    private ListParams() {
    }

    public static List<String> split(String joined) {
        return Arrays.asList(joined.split(SEPARATOR, -1));
    }

    /** @throws IllegalArgumentException when an item contains a line feed */
    public static String join(List<String> items) {
        for (String item : items) {
            if (item.contains(SEPARATOR)) {
                throw new IllegalArgumentException("List items must not contain line breaks");
            }
        }
        return String.join(SEPARATOR, items);
    }
}
