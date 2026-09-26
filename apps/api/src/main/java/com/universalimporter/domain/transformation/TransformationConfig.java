package com.universalimporter.domain.transformation;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.ConfigPruner;
import com.universalimporter.domain.config.FieldScopedSection;
import com.universalimporter.domain.config.Pruned;
import com.universalimporter.domain.config.WarningCode;
import com.universalimporter.domain.schema.FieldType;
import com.universalimporter.domain.schema.TargetField;
import com.universalimporter.domain.schema.TargetSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Every transformation step of a session, as one flat list (spec: transformation). */
public record TransformationConfig(List<TransformationStep> transformations)
        implements FieldScopedSection<TransformationConfig> {

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

    /**
     * What is left valid after a schema change (design T6): steps of removed fields go (through the shared
     * {@link ConfigPruner}), and so does a {@code dateFormat} step whose output is not ISO on a field that is now
     * a date. Each removal comes with a {@code CONFIG_PRUNED} warning.
     */
    public Pruned<TransformationConfig> prunedFor(TargetSchema newSchema) {
        List<ProblemItem> warnings = new ArrayList<>();
        TransformationConfig kept = ConfigPruner.prune(this, newSchema.fieldNames(), warnings);
        List<TransformationStep> steps = new ArrayList<>();
        for (TransformationStep step : kept.transformations()) {
            if (writesNonIsoIntoDateField(step, newSchema)) {
                warnings.add(new ProblemItem(step.targetField(), WarningCode.CONFIG_PRUNED.name(),
                        step.type() + " step " + step.order() + " removed because field '" + step.targetField()
                                + "' is now of type date and must output " + DatePatterns.ISO + "."));
            } else {
                steps.add(step);
            }
        }
        return new Pruned<>(steps.size() == kept.transformations().size() ? kept : new TransformationConfig(steps),
                warnings);
    }

    @Override
    public String sectionLabel() {
        return "Transformations";
    }

    @Override
    public Set<String> referencedFields() {
        Set<String> fields = new LinkedHashSet<>();
        transformations.forEach(step -> fields.add(step.targetField()));
        return Collections.unmodifiableSet(fields);
    }

    @Override
    public TransformationConfig retainFields(Set<String> fieldNames) {
        return new TransformationConfig(
                transformations.stream().filter(step -> fieldNames.contains(step.targetField())).toList());
    }

    /** The wording of the spec (transformation, "Prune transformation khi schema đổi"). */
    @Override
    public String prunedMessage(String field) {
        return "Transformations removed because field '" + field + "' no longer exists.";
    }

    private static boolean writesNonIsoIntoDateField(TransformationStep step, TargetSchema schema) {
        if (!DateFormatTransformation.TYPE.equals(step.type())) {
            return false;
        }
        boolean dateField = schema.field(step.targetField()).map(f -> f.type() == FieldType.DATE).orElse(false);
        String output = step.params().get(DateFormatTransformation.OUTPUT_FORMAT);
        return dateField && output != null && !DatePatterns.isIso(output);
    }

    private static final Comparator<TransformationStep> BY_ORDER =
            Comparator.comparing(TransformationStep::order, Comparator.nullsLast(Comparator.naturalOrder()));
}
