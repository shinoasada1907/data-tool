package com.universalimporter.domain.validation;

import java.util.regex.Pattern;

/** The one definition of an email address in V0.1 (design D10): something@something.something, no spaces. */
public final class EmailAddresses {

    public static final Pattern PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    static final String INVALID = "Value is not a valid email address.";

    private EmailAddresses() {
    }

    public static boolean isValid(String value) {
        return PATTERN.matcher(value).matches();
    }
}
