package com.universaldatatools.core.table;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Text encodings a CSV dataset may be read in (core-02 IO2). UTF-16 needs a byte order mark. */
public enum TextEncoding {
    UTF_8("UTF-8", StandardCharsets.UTF_8),
    UTF_16("UTF-16", StandardCharsets.UTF_16),
    WINDOWS_1258("WINDOWS-1258", Charset.forName("windows-1258")),
    WINDOWS_1252("WINDOWS-1252", Charset.forName("windows-1252"));

    private final String apiName;
    private final Charset charset;

    TextEncoding(String apiName, Charset charset) {
        this.apiName = apiName;
        this.charset = charset;
    }

    /** The name used by the API and in messages, such as {@code WINDOWS-1258}. */
    public String apiName() {
        return apiName;
    }

    public Charset charset() {
        return charset;
    }
}
