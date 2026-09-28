package com.universaldatatools.core.transform;

/**
 * Why a field's transformation stopped.
 *
 * @param rule    type of the failing step
 * @param step    its {@code order}
 * @param message English, never containing the cell value
 */
public record TransformationError(String rule, int step, String message) {
}
