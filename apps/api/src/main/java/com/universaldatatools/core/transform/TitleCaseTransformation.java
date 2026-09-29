package com.universaldatatools.core.transform;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Capitalizes the first letter of every word and lower-cases the rest (core-03 SR8): {@code nguyễn  VĂN a} becomes
 * {@code Nguyễn  Văn A}. Words are split on whitespace only, and the whitespace is kept as it is, so
 * {@code o'NEIL} becomes {@code O'neil}.
 */
public final class TitleCaseTransformation implements Transformation {

    @Override
    public String type() {
        return "titleCase";
    }

    @Override
    public List<String> validate(TransformationContext context) {
        return TransformationParams.unknown(context, Set.of(), type());
    }

    @Override
    public String transform(String value, TransformationContext context) {
        if (value == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(value.length());
        int start = 0;
        while (start < value.length()) {
            int cp = value.codePointAt(start);
            int end = start + Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                out.appendCodePoint(cp);
            } else {
                while (end < value.length()) {
                    int next = value.codePointAt(end);
                    if (Character.isWhitespace(next) || Character.isSpaceChar(next)) {
                        break;
                    }
                    end += Character.charCount(next);
                }
                out.appendCodePoint(Character.toTitleCase(cp))
                        .append(value.substring(start + Character.charCount(cp), end).toLowerCase(Locale.ROOT));
            }
            start = end;
        }
        return out.toString();
    }
}
