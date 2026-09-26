package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;

import java.util.List;

/** One reason a configuration may not be ready yet. */
public interface ReadinessRule {

    List<ProblemItem> check(ImportConfiguration configuration);
}
