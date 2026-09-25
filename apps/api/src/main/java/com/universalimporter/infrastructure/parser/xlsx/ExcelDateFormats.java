package com.universalimporter.infrastructure.parser.xlsx;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tells date and time number formats apart from plain number formats (design X2). The reader library only
 * reports the format; deciding what it means is ours.
 */
final class ExcelDateFormats {

    /** Built-in date/time format ids of the OOXML spec (14–22 dates and times, 45–47 times). */
    private static final Set<Integer> BUILT_IN_DATE_IDS = Set.of(14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47);
    /** Built-in ids that show a time of day without a date. */
    private static final Set<Integer> BUILT_IN_TIME_ONLY_IDS = Set.of(18, 19, 20, 21, 45, 46, 47);

    /**
     * Parts that never mean a date: quoted literals, escaped characters, and bracketed sections such as
     * colours or locales. The elapsed-time markers [h], [m], [s] are kept.
     */
    private static final Pattern NOT_DATE_PARTS =
            Pattern.compile("\"[^\"]*\"|\\\\.|\\[(?![hms]]).*?]", Pattern.CASE_INSENSITIVE);

    private ExcelDateFormats() {
    }

    static boolean isDateFormat(Integer formatId, String formatString) {
        if (formatId != null && BUILT_IN_DATE_IDS.contains(formatId)) {
            return true;
        }
        String codes = dateCodes(formatString);
        return codes != null && containsAny(codes, "ymdhs");
    }

    /** A date/time format showing hours or seconds but no year or day, such as {@code h:mm} or {@code mm:ss}. */
    static boolean isTimeOnlyFormat(Integer formatId, String formatString) {
        if (!isDateFormat(formatId, formatString)) {
            return false;
        }
        String codes = dateCodes(formatString);
        if (codes == null) {
            return formatId != null && BUILT_IN_TIME_ONLY_IDS.contains(formatId);
        }
        return containsAny(codes, "hs") && !containsAny(codes, "yd");
    }

    /** The format string reduced to its date/time codes, lower-cased; {@code null} when there is none. */
    private static String dateCodes(String formatString) {
        if (formatString == null) {
            return null;
        }
        String stripped = formatString.strip();
        if (stripped.equalsIgnoreCase("General") || stripped.equals("@")) {
            return "";
        }
        return NOT_DATE_PARTS.matcher(stripped).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String letters) {
        return text.chars().anyMatch(c -> letters.indexOf(c) >= 0);
    }
}
