package com.universalimporter.domain.validation;

import com.universalimporter.domain.common.ProblemItem;

import java.util.List;

/**
 * What checking a validation configuration found.
 *
 * @param effective the rules to store, in canonical order; {@code null} when there are errors
 * @param warnings  rules that were ignored ({@code RULE_IMPLIED_BY_SCHEMA})
 * @param errors    {@code CONFIG_INVALID} problems, in input order
 */
public record ValidationConfigCheck(ValidationConfig effective, List<ProblemItem> warnings, List<ProblemItem> errors) {

    public ValidationConfigCheck {
        warnings = List.copyOf(warnings);
        errors = List.copyOf(errors);
    }
}
