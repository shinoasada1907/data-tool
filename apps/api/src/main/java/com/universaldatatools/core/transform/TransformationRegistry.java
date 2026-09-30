package com.universaldatatools.core.transform;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * A tool's own subset of the {@link TransformationCatalog}.
     *
     * @throws IllegalArgumentException for a type the catalog does not have: a wiring bug, found at startup
     */
    public static TransformationRegistry of(Set<String> types) {
        Map<String, Transformation> catalog = TransformationCatalog.all().stream()
                .collect(Collectors.toMap(Transformation::type, Function.identity()));
        return new TransformationRegistry(types.stream().map(type -> {
            Transformation transformation = catalog.get(type);
            if (transformation == null) {
                throw new IllegalArgumentException("No transformation of type " + type);
            }
            return transformation;
        }).toList());
    }

    /** Case-sensitive: {@code "TRIM"} is not {@code "trim"}. */
    public Optional<Transformation> find(String type) {
        return type == null ? Optional.empty() : Optional.ofNullable(byType.get(type));
    }

    public List<String> types() {
        return byType.keySet().stream().sorted().toList();
    }
}
