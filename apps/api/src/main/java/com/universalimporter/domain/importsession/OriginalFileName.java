package com.universalimporter.domain.importsession;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Cleans the client-supplied file name so it can be stored and shown (design D5).
 * The result is metadata only; it is never used to build a storage path.
 */
public final class OriginalFileName {

    static final int MAX_LENGTH = 255;

    /** C0/C1 controls plus bidi embedding/override/isolate marks, which can disguise an extension. */
    private static final Pattern UNSAFE = Pattern.compile("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]");

    private OriginalFileName() {
    }

    /** Returns an empty string when nothing usable is left. */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        name = UNSAFE.matcher(name).replaceAll("").strip();
        return truncateKeepingExtension(name);
    }

    private static String truncateKeepingExtension(String name) {
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 && name.length() - dot < MAX_LENGTH ? name.substring(dot) : "";
        int end = MAX_LENGTH - extension.length();
        if (Character.isHighSurrogate(name.charAt(end - 1))) {
            end--;
        }
        return name.substring(0, end) + extension;
    }
}
