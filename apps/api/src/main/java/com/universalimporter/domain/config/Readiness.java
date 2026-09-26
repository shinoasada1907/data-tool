package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;

import java.util.List;

/** Whether a configuration can be processed; {@code ready} exactly when there are no issues. */
public record Readiness(boolean ready, List<ProblemItem> issues) {

    public Readiness {
        issues = List.copyOf(issues);
        if (ready != issues.isEmpty()) {
            throw new IllegalArgumentException("ready must be true exactly when there are no issues");
        }
    }

    public static Readiness of(List<ProblemItem> issues) {
        return new Readiness(issues.isEmpty(), issues);
    }
}
