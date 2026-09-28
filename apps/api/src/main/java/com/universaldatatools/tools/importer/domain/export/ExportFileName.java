package com.universaldatatools.tools.importer.domain.export;

import java.util.regex.Pattern;

/** The name of a downloaded file (design F10-D6): the original name without its last extension, plus a suffix. */
public final class ExportFileName {

    /** Quotes, slashes, every control character, and invisible format characters such as a bidi override. */
    private static final Pattern UNSAFE = Pattern.compile("[\"\\\\/\\p{Cc}\\p{Cf}]");
    private static final String FALLBACK = "export";

    private ExportFileName() {
    }

    /** {@code customers.csv} and {@code -valid.json} give {@code customers-valid.json}. */
    public static String of(String originalFileName, String suffix) {
        String name = originalFileName == null ? "" : originalFileName;
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            // Even at index 0: ".csv" is all extension, so the name falls back to "export".
            name = name.substring(0, dot);
        }
        name = UNSAFE.matcher(name).replaceAll("_");
        return (name.isBlank() ? FALLBACK : name) + suffix;
    }
}
