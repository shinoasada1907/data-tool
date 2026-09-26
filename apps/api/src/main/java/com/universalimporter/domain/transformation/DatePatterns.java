package com.universalimporter.domain.transformation;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Date patterns as users write them (design T3). Patterns use {@link DateTimeFormatter} syntax; {@code y} (year of
 * era) is read as {@code u} (year), because {@code y} does not resolve under {@link ResolverStyle#STRICT}.
 * Month names are English, read in any case, so {@code MMM} reads {@code Jan} and {@code JAN}.
 * <p>
 * A pattern is accepted only if it cannot silently write a wrong date. Round-tripping sample dates catches most
 * mistakes, but not all: {@code yy} reads 90 as 2090, and {@code YYYY} (week-based year) or {@code DD} (day of
 * year) look right on many days. Those are rejected by name.
 */
public final class DatePatterns {

    public static final String ISO = "yyyy-MM-dd";

    /** Every field distinct, so a swapped or missing field shows; the second one is far from the 2000 pivot. */
    private static final List<LocalDateTime> SAMPLES =
            List.of(LocalDateTime.of(2001, 2, 3, 4, 5, 6), LocalDateTime.of(1968, 11, 29, 13, 14, 15));
    private static final LocalDateTime SAMPLE = SAMPLES.get(0);

    /**
     * Compiled formatters by pattern: the pipeline formats every cell, and compiling a pattern costs more than
     * using it. Formatters are immutable and thread-safe. Emptied when full, since patterns come from requests.
     */
    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();
    private static final int MAX_CACHED = 256;

    /** Week-based year, week of year, week of month, day of year, aligned week of month. */
    private static final String UNSUPPORTED_LETTERS = "YwWDF";

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
        if (FORMATTERS.size() >= MAX_CACHED) {
            FORMATTERS.clear();
        }
        return FORMATTERS.computeIfAbsent(pattern, DatePatterns::compile);
    }

    private static DateTimeFormatter compile(String pattern) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(toStrict(pattern))
                .toFormatter(Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    /**
     * Why {@code pattern} cannot read dates, if it cannot: a date formatted with it must parse back to the same
     * day, so year, month and day must all be present. Time fields are allowed, to read date-times.
     */
    public static Optional<String> checkInput(String pattern) {
        DateTimeFormatter formatter;
        try {
            formatter = formatter(pattern);
            SAMPLES.forEach(formatter::format);
        } catch (IllegalArgumentException | DateTimeException e) {
            return Optional.of(invalid(pattern));
        }
        Optional<String> unsupported = unsupportedLetter(pattern);
        if (unsupported.isPresent()) {
            return unsupported;
        }
        if (hasTwoDigitYear(pattern)) {
            return Optional.of("Date pattern '" + pattern + "' must use a 4-digit year.");
        }
        for (LocalDateTime sample : SAMPLES) {
            if (!roundTrips(formatter, sample)) {
                return Optional.of("Date pattern '" + pattern + "' must contain year, month and day.");
            }
        }
        return Optional.empty();
    }

    /** Why {@code pattern} cannot write dates, if it cannot: it must not need a time of day. */
    public static Optional<String> checkOutput(String pattern) {
        DateTimeFormatter formatter;
        try {
            formatter = formatter(pattern);
        } catch (IllegalArgumentException e) {
            return Optional.of(invalid(pattern));
        }
        Optional<String> unsupported = unsupportedLetter(pattern);
        if (unsupported.isPresent()) {
            return unsupported;
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

    private static boolean roundTrips(DateTimeFormatter formatter, LocalDateTime sample) {
        try {
            return LocalDate.parse(formatter.format(sample), formatter).equals(sample.toLocalDate());
        } catch (DateTimeException e) {
            // Something the day needs is missing.
            return false;
        }
    }

    /** The first letter outside quoted text that would write or read a date the user did not mean. */
    private static Optional<String> unsupportedLetter(String pattern) {
        for (char c : unquoted(pattern).toCharArray()) {
            if (UNSUPPORTED_LETTERS.indexOf(c) >= 0) {
                return Optional.of("Date pattern '" + pattern + "' uses unsupported letter '" + c + "'.");
            }
        }
        return Optional.empty();
    }

    /** A run of exactly two year letters: {@code yy} or {@code uu}. */
    private static boolean hasTwoDigitYear(String pattern) {
        String unquoted = unquoted(toStrict(pattern));
        int run = 0;
        for (int i = 0; i <= unquoted.length(); i++) {
            if (i < unquoted.length() && unquoted.charAt(i) == 'u') {
                run++;
            } else {
                if (run == 2) {
                    return true;
                }
                run = 0;
            }
        }
        return false;
    }

    /** The pattern with quoted text replaced by spaces, so letters inside quotes never count. */
    private static String unquoted(String pattern) {
        StringBuilder letters = new StringBuilder(pattern.length());
        boolean quoted = false;
        for (char c : pattern.toCharArray()) {
            if (c == QUOTE) {
                quoted = !quoted;
                letters.append(' ');
            } else {
                letters.append(quoted ? ' ' : c);
            }
        }
        return letters.toString();
    }

    private static final char QUOTE = 39;

    private static String invalid(String pattern) {
        return "Invalid date pattern '" + pattern + "'.";
    }
}
