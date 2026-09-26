package com.universalimporter.domain.transformation;

import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.schema.TargetField;
import com.universalimporter.domain.schema.TargetSchema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks a whole transformation configuration against the schema and reports every problem, step by step in
 * input order (design T4). Each problem is a {@code CONFIG_INVALID} item for the step's target field.
 */
public final class TransformationConfigValidator {

    private final TransformationRegistry registry;

    public TransformationConfigValidator(TransformationRegistry registry) {
        this.registry = registry;
    }

    public List<ProblemItem> validate(TransformationConfig config, TargetSchema schema) {
        List<ProblemItem> problems = new ArrayList<>();
        Set<String> seenOrders = new HashSet<>();
        for (TransformationStep step : config.transformations()) {
            String field = step.targetField();
            List<String> messages = new ArrayList<>();
            Optional<TargetField> target = field == null ? Optional.empty() : schema.field(field);
            if (target.isEmpty()) {
                messages.add("Target field does not exist.");
            }
            if (step.order() == null) {
                messages.add("Order is required.");
            } else if (step.order() < 0) {
                messages.add("Order must be >= 0.");
            } else if (!seenOrders.add(field + "\u0000" + step.order())) {
                messages.add("Duplicate order " + step.order() + " for field '" + field + "'.");
            }
            Optional<Transformation> transformation = registry.find(step.type());
            if (transformation.isEmpty()) {
                messages.add("Unknown transformation type '" + step.type() + "'.");
            }
            // Parameters can only be judged for a known type on a field whose type is known.
            if (target.isPresent() && transformation.isPresent()) {
                messages.addAll(transformation.get().validate(
                        new TransformationContext(field, target.get().type(), step.params())));
            }
            messages.forEach(message -> problems.add(new ProblemItem(field, ErrorCode.CONFIG_INVALID.name(), message)));
        }
        return problems;
    }
}
