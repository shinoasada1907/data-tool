package com.universaldatatools.core.transform;

import com.universaldatatools.core.common.TextValues;

import java.util.List;
import java.util.Set;

/** Parameter checks shared by the transformations; messages are part of the API (spec: transformation). */
final class TransformationParams {

    private TransformationParams() {
    }

    /** One message per parameter outside {@code allowed}, in name order so the result is deterministic. */
    static List<String> unknown(TransformationContext context, Set<String> allowed, String type) {
        return context.params().keySet().stream()
                .filter(name -> !allowed.contains(name))
                .sorted()
                .map(name -> "Unknown parameter '" + name + "' for '" + type + "'.")
                .toList();
    }

    /** Empty when the parameter is present and not blank; otherwise why not. */
    static List<String> required(TransformationContext context, String name) {
        String value = context.params().get(name);
        if (value == null) {
            return List.of("Parameter '" + name + "' is required.");
        }
        return TextValues.isEmpty(value) ? List.of("Parameter '" + name + "' must not be blank.") : List.of();
    }
}
