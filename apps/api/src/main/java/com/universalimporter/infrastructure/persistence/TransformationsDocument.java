package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.transformation.TransformationConfig;
import com.universalimporter.domain.transformation.TransformationStep;

import java.util.List;
import java.util.Map;

/**
 * Storage format of {@link TransformationConfig} in the {@code transformations_json} column; {@code params} are
 * stored as the client sent them (design T5). Kept separate from the domain record so the stored JSON only changes
 * when this class does.
 */
record TransformationsDocument(List<StepDocument> transformations) {

    record StepDocument(String targetField, int order, String type, Map<String, String> params) {
    }

    static TransformationsDocument from(TransformationConfig config) {
        return new TransformationsDocument(config.transformations().stream()
                .map(step -> new StepDocument(step.targetField(), step.order(), step.type(), step.params()))
                .toList());
    }

    TransformationConfig toDomain() {
        return new TransformationConfig(transformations.stream()
                .map(step -> new TransformationStep(step.targetField(), step.order(), step.type(), step.params()))
                .toList());
    }
}
