package com.universaldatatools.tools.importer.domain.config;

import com.universaldatatools.core.common.ProblemItem;

import java.util.List;

/** A changed configuration and what the change removed on the way ({@code CONFIG_PRUNED} and similar). */
public record ConfigChange(ImportConfiguration configuration, List<ProblemItem> warnings) {

    public ConfigChange {
        warnings = List.copyOf(warnings);
    }
}
