package com.universalimporter.domain.transformation;

/** The transformed value of one field, or the error that stopped it (then {@code value} is {@code null}). */
public record FieldTransformResult(String value, TransformationError error) {

    public static FieldTransformResult ok(String value) {
        return new FieldTransformResult(value, null);
    }

    public static FieldTransformResult failed(TransformationError error) {
        return new FieldTransformResult(null, error);
    }

    public boolean failed() {
        return error != null;
    }
}
