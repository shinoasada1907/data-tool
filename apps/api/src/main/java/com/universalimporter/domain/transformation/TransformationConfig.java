package com.universalimporter.domain.transformation;

import com.universalimporter.domain.schema.TargetField;
import com.universalimporter.domain.schema.TargetSchema;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Every transformation step of a session, as one flat list (spec: transformation). */
public record TransformationConfig(List<TransformationStep> transformations) {

    public TransformationConfig {
        transformations = List.copyOf(transformations);
    }

    public static TransformationConfig empty() {
        return new TransformationConfig(List.of());
    }

    /** The field's steps in the order they run. */
    public List<TransformationStep> stepsFor(String fieldName) {
        return transformations.stream()
                .filter(step -> Objects.equals(step.targetField(), fieldName))
                .sorted(BY_ORDER)
                .toList();
    }

    /**
     * Sorted by the field's position in {@code schema}, then by {@code order} (design T5), so equal content always
     * compares and hashes equal. Steps of fields missing from the schema go last.
     */
    public TransformationConfig normalized(TargetSchema schema) {
        Comparator<TransformationStep> byField = Comparator.comparingInt(step -> schema.field(step.targetField())
                .map(TargetField::order).orElse(Integer.MAX_VALUE));
        return new TransformationConfig(transformations.stream().sorted(byField.thenComparing(BY_ORDER)).toList());
    }

    private static final Comparator<TransformationStep> BY_ORDER =
            Comparator.comparing(TransformationStep::order, Comparator.nullsLast(Comparator.naturalOrder()));
}
