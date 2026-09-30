package com.universaldatatools.core.validate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The rules a {@link FieldRulePlan} takes {@code required}, {@code type}, {@code email} and {@code unique} from. */
public final class ValidationRegistry {

    private final Map<String, ValidationRule> byType;

    /** @throws IllegalArgumentException when two rules share a type */
    public ValidationRegistry(List<ValidationRule> rules) {
        this.byType = rules.stream().collect(Collectors.toUnmodifiableMap(ValidationRule::type, Function.identity(),
                (a, b) -> {
                    throw new IllegalArgumentException("Two validation rules of type " + a.type());
                }));
    }

    /** The four rules of V0.1. */
    public static ValidationRegistry standard() {
        return new ValidationRegistry(List.of(new RequiredRule(), new TypeRule(), new EmailRule(), new UniqueRule()));
    }

    /** @throws IllegalStateException when there is no such rule: a wiring bug, not user input */
    ValidationRule require(String type) {
        return find(type).orElseThrow(() -> new IllegalStateException("No validation rule of type " + type));
    }

    public Optional<ValidationRule> find(String type) {
        return type == null ? Optional.empty() : Optional.ofNullable(byType.get(type));
    }
}
