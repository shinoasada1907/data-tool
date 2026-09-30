package com.universaldatatools.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.universaldatatools.core.common.ProblemItem;

/**
 * An item of {@code errors[]}. Only {@code pointer} is left out when null: {@code "field": null} has always been
 * written, and clients of V0.1 may rely on it.
 */
public record ProblemItemDto(String field, String code, String message,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String pointer) {

    static ProblemItemDto of(ProblemItem item) {
        return new ProblemItemDto(item.field(), item.code(), item.message(), item.pointer());
    }
}
