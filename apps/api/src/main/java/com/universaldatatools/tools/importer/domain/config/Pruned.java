package com.universaldatatools.tools.importer.domain.config;

import com.universaldatatools.core.common.ProblemItem;

import java.util.List;

/** A configuration section after a schema change, and the {@code CONFIG_PRUNED} warnings for what it lost. */
public record Pruned<T>(T section, List<ProblemItem> warnings) {

    public Pruned {
        warnings = List.copyOf(warnings);
    }
}
