package com.universaldatatools.core.transform;

import com.universaldatatools.core.common.TextValues;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the usual ways of writing "nothing" into a real empty value, {@code null} (core-03 SR8). A value is
 * compared trimmed, but a value that stays is kept untrimmed. {@code tokens} is a list ({@link ListParams}).
 */
public final class NormalizeNullTransformation implements Transformation {

    public static final List<String> DEFAULT_TOKENS = List.of("null", "n/a", "na", "none", "-");
    private static final String TOKENS = "tokens";
    private static final String CASE_SENSITIVE = "caseSensitive";
    private static final int MAX_TOKENS = 50;

    @Override
    public String type() {
        return "normalizeNull";
    }

    @Override
    public List<String> validate(TransformationContext context) {
        List<String> problems = new ArrayList<>(TransformationParams.unknown(context, Set.of(TOKENS, CASE_SENSITIVE), type()));
        String tokens = context.params().get(TOKENS);
        if (tokens != null && ListParams.split(tokens).size() > MAX_TOKENS) {
            problems.add("Parameter 'tokens' must have at most 50 tokens.");
        }
        problems.addAll(TransformationParams.bool(context, CASE_SENSITIVE));
        return problems;
    }

    @Override
    public String transform(String value, TransformationContext context) {
        if (value == null) {
            return null;
        }
        String stripped = TextValues.strip(value);
        if (stripped.isEmpty()) {
            return null;
        }
        boolean caseSensitive = "true".equals(context.params().get(CASE_SENSITIVE));
        String tokens = context.params().get(TOKENS);
        String compared = caseSensitive ? stripped : stripped.toLowerCase(Locale.ROOT);
        for (String token : tokens == null ? DEFAULT_TOKENS : ListParams.split(tokens)) {
            String candidate = TextValues.strip(token);
            if (compared.equals(caseSensitive ? candidate : candidate.toLowerCase(Locale.ROOT))) {
                return null;
            }
        }
        return value;
    }
}
