package com.universalimporter.application.result;

import com.universalimporter.domain.pipeline.ResultView;

/**
 * One page of a stored result (design F09-D1). The caller has checked the ranges.
 *
 * @param field only invalid rows with an error on this field; {@code null} for no filter
 * @param code  only invalid rows with an error of this code; {@code null} for no filter
 */
public record ResultQuery(ResultView view, int page, int size, String field, String code) {
}
