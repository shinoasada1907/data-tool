package com.universaldatatools.tools.importer.domain.transformation;

import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.core.transform.Transformation;
import com.universaldatatools.core.transform.TransformationContext;
import com.universaldatatools.core.transform.TransformationRegistry;
import com.universaldatatools.core.transform.TransformationStep;
import com.universaldatatools.tools.importer.domain.schema.TargetField;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;

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
            } else if (field != null && !seenOrders.add(field + "\u0000" + step.order())) {
                messages.add("Duplicate order " + step.order() + " for field '" + field + "'.");
            }
            Optional<Transformation> transformation = registry.find(step.type());
            if (transformation.isEmpty()) {
                messages.add("Unknown transformation type '" + step.type() + "'.");
            }
            // Checked even when the field is missing, so every problem comes back at once; checks that need the
            // field type (a date field must output ISO) simply do not apply then.
            if (transformation.isPresent()) {
                messages.addAll(transformation.get().validate(
                        new TransformationContext(field, target.map(TargetField::type).orElse(null), step.params())));
            }
            messages.forEach(message -> problems.add(new ProblemItem(field, ErrorCode.CONFIG_INVALID.name(), message)));
        }
        return problems;
    }
}
