package com.universaldatatools.core.common;

/**
 * One detail of an error, warning or readiness issue.
 *
 * @param field   target field name, or {@code null} when the item is not about a single field
 * @param pointer JSON Pointer (RFC 6901) into the request body at the wrong value, or {@code null}; the importer
 *                never sets it, so its errors look as they did in V0.1
 */
public record ProblemItem(String field, String code, String message, String pointer) {

    public ProblemItem(String field, String code, String message) {
        this(field, code, message, null);
    }
}
