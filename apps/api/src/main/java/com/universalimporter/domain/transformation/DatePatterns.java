package com.universalimporter.domain.transformation;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;

/**
 * Date patterns as users write them (design T3). Patterns use {@link DateTimeFormatter} syntax; {@code y} (year of
 * era) is read as {@code u} (year), because {@code y} does not resolve under {@link ResolverStyle#STRICT}.
 * Month names are English, so {@code MMM} reads {@code Jan}.
 */
public final class DatePatterns {

    public static final String ISO = "yyyy-MM-dd";

    private static final LocalDateTime SAMPLE = LocalDateTime.of(2001, 2, 3, 4, 5, 6);

    private DatePatterns() {
    }

    /** Every {@code y} outside quoted text becomes {@code u}; {@code ''} inside quotes stays an escaped quote. */
    public static String toStrict(String pattern) {
        StringBuilder strict = new StringBuilder(pattern.length());
        boolean quoted = false;
        for (char c : pattern.toCharArray()) {
            if (c == '\'') {
                quoted = !quoted;
            }
            strict.append(!quoted && c == 'y' ? 'u' : c);
        }
        return strict.toString();
    }

    /** @throws IllegalArgumentException when the pattern is not valid {@link DateTimeFormatter} syntax */
    public static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(toStrict(pattern), Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
    }

    /**
     * Why {@code pattern} cannot read dates, if it cannot: a date formatted with it must parse back to the same
     * day, so year, month and day must all be present. Time fields are allowed, to read date-times.
     */
    public static Optional<String> checkInput(String pattern) {
        DateTimeFormatter formatter;
        String sample;
        try {
            formatter = formatter(pattern);
            sample = formatter.format(SAMPLE);
        } catch (IllegalArgumentException | DateTimeException e) {
            return Optional.of(invalid(pattern));
        }
        try {
            if (LocalDate.parse(sample, formatter).equals(SAMPLE.toLocalDate())) {
                return Optional.empty();
            }
        } catch (DateTimeException e) {
            // Falls through: something the day needs is missing.
        }
        return Optional.of("Date pattern '" + pattern + "' must contain year, month and day.");
    }

    /** Why {@code pattern} cannot write dates, if it cannot: it must not need a time of day. */
    public static Optional<String> checkOutput(String pattern) {
        DateTimeFormatter formatter;
        try {
            formatter = formatter(pattern);
        } catch (IllegalArgumentException e) {
            return Optional.of(invalid(pattern));
        }
        try {
            formatter.format(SAMPLE.toLocalDate());
            return Optional.empty();
        } catch (DateTimeException e) {
            return Optional.of("Date pattern '" + pattern + "' must not contain time fields.");
        }
    }

    /** True for {@code yyyy-MM-dd} and {@code uuuu-MM-dd}: they mean the same after the year rewrite. */
    public static boolean isIso(String pattern) {
        return toStrict(pattern).equals(toStrict(ISO));
    }

    private static String invalid(String pattern) {
        return "Invalid date pattern '" + pattern + "'.";
    }
}
