package com.universaldatatools.core.validate;

/**
 * The one definition of an email address in V0.1 (design D10): exactly one {@code @}, something before it, and
 * after it a domain with a dot that is neither its first nor its last character; at most 254 characters (RFC 5321);
 * no whitespace, no-break or zero-width spaces, or control characters anywhere.
 * <p>
 * Checked character by character rather than with a regex: {@code ^[^@\s]+@[^@\s]+\.[^@\s]+$} backtracks
 * quadratically, so one hostile 200 KB cell took minutes; and its {@code \s} misses Unicode spaces.
 */
public final class EmailAddresses {

    public static final int MAX_LENGTH = 254;

    static final String INVALID = "Value is not a valid email address.";

    private EmailAddresses() {
    }

    public static boolean isValid(String value) {
        if (value.length() > MAX_LENGTH) {
            return false;
        }
        int at = -1;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isForbidden(c)) {
                return false;
            }
            if (c == '@') {
                if (at >= 0) {
                    return false;
                }
                at = i;
            }
        }
        if (at <= 0) {
            return false;
        }
        String domain = value.substring(at + 1);
        return domain.length() >= 3 && domain.substring(1, domain.length() - 1).indexOf('.') >= 0;
    }

    private static boolean isForbidden(char c) {
        int type = Character.getType(c);
        return Character.isWhitespace(c) || Character.isSpaceChar(c)
                || type == Character.CONTROL || type == Character.FORMAT;
    }
}
