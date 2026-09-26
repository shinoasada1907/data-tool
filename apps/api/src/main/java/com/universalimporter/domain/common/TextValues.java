package com.universalimporter.domain.common;

/**
 * What "empty" means for cell values (design D10, BE-F06 T3): {@code null}, or only whitespace, counting
 * no-break spaces (U+00A0, U+2007, U+202F) that {@link String#isBlank()} and {@link String#strip()} ignore.
 */
public final class TextValues {

    private TextValues() {
    }

    public static boolean isEmpty(String value) {
        return value == null || value.codePoints().allMatch(TextValues::isSpace);
    }

    /** The value without such spaces at either end; {@code null} stays {@code null}. */
    public static String strip(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
