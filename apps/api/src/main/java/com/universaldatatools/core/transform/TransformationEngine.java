package com.universaldatatools.core.transform;

import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.core.common.ThrottledWarnings;

import java.util.Comparator;
import java.util.List;

/**
 * Runs a field's transformation steps in {@code order} (design T2). A failure never escapes: it stops the field
 * and comes back as a {@link TransformationError}, so one bad row cannot stop the whole import.
 */
public final class TransformationEngine {

    static final String UNEXPECTED = "Unexpected error while applying transformation.";

    private final TransformationRegistry registry;
    /** The JDK's logger: the domain stays free of logging libraries; Spring Boot routes it to the app's log. */
    private final ThrottledWarnings warnings =
            new ThrottledWarnings(System.getLogger(TransformationEngine.class.getName()));

    public TransformationEngine(TransformationRegistry registry) {
        this.registry = registry;
    }

    public FieldTransformResult apply(String fieldName, FieldType fieldType, String value,
                                      List<TransformationStep> steps) {
        List<TransformationStep> ordered = steps.stream()
                .sorted(Comparator.comparing(TransformationStep::order, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        String current = value;
        for (TransformationStep step : ordered) {
            int order = step.order() == null ? -1 : step.order();
            try {
                Transformation transformation = registry.find(step.type())
                        .orElseThrow(() -> new IllegalStateException("Unknown transformation type " + step.type()));
                current = transformation.transform(current, new TransformationContext(fieldName, fieldType, step.params()));
            } catch (TransformationFailure failure) {
                return FieldTransformResult.failed(new TransformationError(step.type(), order, failure.getMessage()));
            } catch (RuntimeException bug) {
                // Not the exception itself: its message may quote the cell value, which must not reach the log
                // (design D13). Its class and where it was thrown are enough to find the bug.
                StackTraceElement[] trace = bug.getStackTrace();
                warnings.warn(String.valueOf(step.type()), () -> "Transformation " + step.type()
                        + " failed unexpectedly on field " + fieldName + ": " + bug.getClass().getName()
                        + (trace.length > 0 ? " at " + trace[0] : ""));
                return FieldTransformResult.failed(new TransformationError(step.type(), order, UNEXPECTED));
            }
        }
        return FieldTransformResult.ok(current);
    }
}
