package com.universalimporter.domain.mapping;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.config.ReadinessIssueCode;
import com.universalimporter.domain.config.ReadinessRule;
import com.universalimporter.domain.schema.TargetField;

import java.util.List;

/** A required field needs a value source before the import can run; optional fields may stay unmapped. */
public final class RequiredFieldsMappedRule implements ReadinessRule {

    @Override
    public List<ProblemItem> check(ImportConfiguration configuration) {
        return configuration.schema().fields().stream()
                .filter(TargetField::required)
                .map(TargetField::name)
                .filter(field -> configuration.mapping().forField(field).isEmpty())
                .map(field -> new ProblemItem(field, ReadinessIssueCode.TARGET_FIELD_REQUIRED.name(),
                        "Required field is not mapped."))
                .toList();
    }
}
