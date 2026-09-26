package com.universalimporter.domain.validation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One configured validation rule, as sent by the client and stored.
 *
 * @param params missing, {@code null} and {@code null}-valued entries all count as absent (the client sends no
 *               params for email and unique)
 */
public record ValidationRuleConfig(String targetField, String type, Map<String, String> params) {

    public ValidationRuleConfig {
        Map<String, String> present = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((name, value) -> {
                if (name != null && value != null) {
                    present.put(name, value);
                }
            });
        }
        params = Map.copyOf(present);
    }
}
