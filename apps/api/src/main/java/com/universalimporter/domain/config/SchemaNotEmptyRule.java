package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;

import java.util.List;

/** Nothing can be imported into a schema without fields. */
public final class SchemaNotEmptyRule implements ReadinessRule {

    @Override
    public List<ProblemItem> check(ImportConfiguration configuration) {
        if (!configuration.schema().isEmpty()) {
            return List.of();
        }
        return List.of(new ProblemItem(null, ReadinessIssueCode.SCHEMA_EMPTY.name(), "Target schema has no fields."));
    }
}
