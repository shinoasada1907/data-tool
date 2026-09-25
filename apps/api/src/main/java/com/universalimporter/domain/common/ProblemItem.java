package com.universalimporter.domain.common;

/**
 * One detail of an error, warning or readiness issue.
 *
 * @param field target field name, or {@code null} when the item is not about a single field
 */
public record ProblemItem(String field, String code, String message) {
}
