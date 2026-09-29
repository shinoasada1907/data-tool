package com.universaldatatools.core.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Replaces text, literally, never as a regex (core-03 SR8). {@code match=exact} replaces a value equal to
 * {@code find}; {@code match=contains} replaces every occurrence inside the value. Without case sensitivity both
 * sides are compared lower-cased ({@link Locale#ROOT}).
 */
public final class ReplaceTransformation implements Transformation {

    private static final String FIND = "find";
    private static final String REPLACE_WITH = "replaceWith";
    private static final String MATCH = "match";
    private static final String CASE_SENSITIVE = "caseSensitive";
    private static final int MAX_FIND_LENGTH = 1000;

    @Override
    public String type() {
        return "replace";
    }

    @Override
    public List<String> validate(TransformationContext context) {
        List<String> problems = new ArrayList<>(TransformationParams.unknown(context,
                Set.of(FIND, REPLACE_WITH, MATCH, CASE_SENSITIVE), type()));
        String find = context.params().get(FIND);
        if (find == null) {
            problems.add("Parameter 'find' is required.");
        } else if (find.isEmpty() || find.length() > MAX_FIND_LENGTH) {
            problems.add("Parameter 'find' must be 1-1000 characters.");
        }
        String match = context.params().get(MATCH);
        if (match != null && !match.equals("exact") && !match.equals("contains")) {
            problems.add("Parameter 'match' must be 'exact' or 'contains'.");
        }
        problems.addAll(TransformationParams.bool(context, CASE_SENSITIVE));
        return problems;
    }

    @Override
    public String transform(String value, TransformationContext context) {
        if (value == null) {
            return null;
        }
        String find = context.params().get(FIND);
        String replaceWith = context.params().getOrDefault(REPLACE_WITH, "");
        boolean caseSensitive = !"false".equals(context.params().get(CASE_SENSITIVE));
        if (!"contains".equals(context.params().get(MATCH))) {
            boolean equal = caseSensitive ? value.equals(find)
                    : value.toLowerCase(Locale.ROOT).equals(find.toLowerCase(Locale.ROOT));
            return equal ? replaceWith : value;
        }
        if (caseSensitive) {
            return value.replace(find, replaceWith);
        }
        return replaceIgnoringCase(value, find, replaceWith);
    }

    /**
     * Lower-casing can change a string's length (U+0130 becomes two chars), and then positions in the lower-cased
     * copy no longer point into the original; those rare values fall back to comparing char by char.
     */
    private static String replaceIgnoringCase(String value, String find, String replaceWith) {
        String haystack = value.toLowerCase(Locale.ROOT);
        String needle = find.toLowerCase(Locale.ROOT);
        boolean aligned = haystack.length() == value.length() && needle.length() == find.length();
        StringBuilder out = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            boolean hit = aligned ? haystack.startsWith(needle, i) : value.regionMatches(true, i, find, 0, find.length());
            if (hit) {
                out.append(replaceWith);
                i += find.length();
            } else {
                out.append(value.charAt(i));
                i++;
            }
        }
        return out.toString();
    }
}
