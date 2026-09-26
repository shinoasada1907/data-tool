package com.universalimporter.application.result;

import com.universalimporter.domain.pipeline.ResultView;

import java.util.Objects;

/**
 * One page of a stored result (design F09-D1). The controller reports bad values to the client as
 * {@code REQUEST_INVALID}; here they are programming errors.
 *
 * @param field only invalid rows with an error on this field; {@code null} for no filter
 * @param code  only invalid rows with an error of this code; {@code null} for no filter
 */
public record ResultQuery(ResultView view, int page, int size, String field, String code) {

    public ResultQuery {
        Objects.requireNonNull(view, "view");
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or more: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be 1 or more: " + size);
        }
    }
}
