package com.universaldatatools.core.table;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What an untyped text looks like, by the cautious rules of core-02 IO7, shared by profiling and by
 * {@link Typing#INFER} so both always agree.
 */
final class TextShapes {

    private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9][0-9]{0,14})(\\.[0-9]+)?");
    private static final Pattern ISO_DATE_SHAPE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private TextShapes() {
    }

    /** No leading zeros, at most 15 integer digits: codes such as {@code 00123} stay text. */
    static boolean isNumber(String text) {
        return NUMBER.matcher(text).matches();
    }

    /** {@code true} or {@code false} in any case; {@code 1} and {@code 0} are numbers. */
    static boolean isBoolean(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.equals("true") || lower.equals("false");
    }

    /** A real ISO date, {@code yyyy-MM-dd}. */
    static boolean isIsoDate(String text) {
        if (!ISO_DATE_SHAPE.matcher(text).matches()) {
            return false;
        }
        try {
            LocalDate.parse(text, ISO_DATE);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }
}
