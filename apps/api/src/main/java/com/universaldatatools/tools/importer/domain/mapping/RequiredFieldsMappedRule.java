package com.universaldatatools.tools.importer.domain.mapping;

import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.config.ReadinessIssueCode;
import com.universaldatatools.tools.importer.domain.config.ReadinessRule;
import com.universaldatatools.tools.importer.domain.schema.TargetField;

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
