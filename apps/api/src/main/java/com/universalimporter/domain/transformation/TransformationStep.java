package com.universalimporter.domain.transformation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One configured transformation of a target field, as sent by the client and stored.
 *
 * @param order  position among the field's steps, from 0; {@code null} only in unchecked input
 * @param params missing, {@code null} and {@code null}-valued entries all count as absent (the client sends no
 *               params for trim, uppercase and lowercase)
 */
public record TransformationStep(String targetField, Integer order, String type, Map<String, String> params) {

    public TransformationStep {
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
