package com.universaldatatools.tools.importer.domain.config;

import com.universaldatatools.core.common.ProblemItem;

import java.util.List;

/** One reason a configuration may not be ready yet. */
public interface ReadinessRule {

    List<ProblemItem> check(ImportConfiguration configuration);
}
