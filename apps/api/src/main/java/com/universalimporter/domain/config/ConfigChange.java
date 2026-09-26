package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;

import java.util.List;

/** A changed configuration and what the change removed on the way ({@code CONFIG_PRUNED} and similar). */
public record ConfigChange(ImportConfiguration configuration, List<ProblemItem> warnings) {

    public ConfigChange {
        warnings = List.copyOf(warnings);
    }
}
