package com.universaldatatools.core.transform;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The transformations the application knows, looked up by their exact type. */
public final class TransformationRegistry {

    private final Map<String, Transformation> byType;

    /** @throws IllegalArgumentException when two transformations share a type */
    public TransformationRegistry(List<Transformation> transformations) {
        this.byType = transformations.stream().collect(Collectors.toUnmodifiableMap(Transformation::type,
                Function.identity(), (a, b) -> {
                    throw new IllegalArgumentException("Two transformations of type " + a.type());
                }));
    }

    /** The five transformations of V0.1. */
    public static TransformationRegistry standard() {
        return new TransformationRegistry(List.of(new TrimTransformation(), new UppercaseTransformation(),
                new LowercaseTransformation(), new DefaultValueTransformation(), new DateFormatTransformation()));
    }

    /** Case-sensitive: {@code "TRIM"} is not {@code "trim"}. */
    public Optional<Transformation> find(String type) {
        return type == null ? Optional.empty() : Optional.ofNullable(byType.get(type));
    }

    public List<String> types() {
        return byType.keySet().stream().sorted().toList();
    }
}
