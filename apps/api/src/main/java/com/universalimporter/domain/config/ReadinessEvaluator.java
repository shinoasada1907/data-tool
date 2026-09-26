package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;

import java.util.List;

/** Runs every readiness rule and collects their issues in rule order (design S4). */
public final class ReadinessEvaluator {

    private final List<ReadinessRule> rules;

    private ReadinessEvaluator(List<ReadinessRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** The rules of V0.1. F05 adds the rule that every required field is mapped. */
    public static ReadinessEvaluator standard() {
        return new ReadinessEvaluator(List.of(new SchemaNotEmptyRule()));
    }

    public Readiness evaluate(ImportConfiguration configuration) {
        List<ProblemItem> issues = rules.stream().flatMap(rule -> rule.check(configuration).stream()).toList();
        return Readiness.of(issues);
    }
}
