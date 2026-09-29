package com.universaldatatools.core.validate;

import com.google.re2j.Pattern;
import com.universaldatatools.core.schema.FieldConstraints;
import com.universaldatatools.core.schema.SchemaField;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The rules one field runs, in the fixed order {@code required → type → email → minLength → maxLength → pattern →
 * min → max → unique} (core-03 SR6), whatever order a configuration lists them in. Built once per run, which also
 * compiles the pattern once.
 *
 * @param rules only the rules the field has; {@code type} is always there
 */
public record FieldRulePlan(SchemaField field, List<ValidationRule> rules) {

    public FieldRulePlan {
        rules = List.copyOf(rules);
    }

    /** @param extraRules rules a tool adds beyond the schema: the importer's {@code email} and {@code unique} */
    public static FieldRulePlan of(SchemaField field, Set<String> extraRules) {
        return of(field, extraRules, ValidationRegistry.standard());
    }

    /** @param base where {@code required}, {@code type}, {@code email} and {@code unique} come from */
    public static FieldRulePlan of(SchemaField field, Set<String> extraRules, ValidationRegistry base) {
        FieldConstraints c = field.constraints();
        List<ValidationRule> rules = new ArrayList<>();
        if (field.required()) {
            rules.add(base.require("required"));
        }
        rules.add(base.require("type"));
        if (extraRules.contains("email")) {
            rules.add(base.require("email"));
        }
        if (c.minLength() != null) {
            rules.add(new MinLengthRule(c.minLength()));
        }
        if (c.maxLength() != null) {
            rules.add(new MaxLengthRule(c.maxLength()));
        }
        if (c.pattern() != null) {
            rules.add(new PatternRule(Pattern.compile(c.pattern())));
        }
        if (c.min() != null) {
            rules.add(new MinRule(c.min()));
        }
        if (c.max() != null) {
            rules.add(new MaxRule(c.max()));
        }
        if (c.unique() || extraRules.contains("unique")) {
            rules.add(base.require("unique"));
        }
        return new FieldRulePlan(field, rules);
    }

    public List<String> ruleTypes() {
        return rules.stream().map(ValidationRule::type).toList();
    }
}
