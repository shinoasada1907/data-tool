package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.validation.ValidationConfig;
import com.universalimporter.domain.validation.ValidationRuleConfig;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Storage format of {@link ValidationConfig} in the {@code validations_json} column. Kept separate from the domain
 * record so the stored JSON only changes when this class does.
 */
record ValidationsDocument(List<RuleDocument> validations) {

    record RuleDocument(String targetField, String type, Map<String, String> params) {
    }

    static ValidationsDocument from(ValidationConfig config) {
        return new ValidationsDocument(config.validations().stream()
                // Sorted: the domain map iterates in an order that changes from one JVM run to the next.
                .map(rule -> new RuleDocument(rule.targetField(), rule.type(), new TreeMap<>(rule.params())))
                .toList());
    }

    ValidationConfig toDomain() {
        return new ValidationConfig(validations.stream()
                .map(rule -> new ValidationRuleConfig(rule.targetField(), rule.type(), rule.params()))
                .toList());
    }
}
