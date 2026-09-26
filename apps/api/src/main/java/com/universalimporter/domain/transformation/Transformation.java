package com.universalimporter.domain.transformation;

import java.util.List;

/**
 * One kind of value transformation (design T1). Values stay strings, or {@code null}, until type conversion
 * (design D10); an empty value passes through unchanged unless the transformation exists to fill it.
 */
public interface Transformation {

    /** The API name, for example {@code "trim"}. */
    String type();

    /** Problems with the configured parameters, as English messages; empty when valid. */
    List<String> validate(TransformationContext context);

    /**
     * @throws TransformationFailure when this value cannot be transformed; the message must not contain the value
     */
    String transform(String value, TransformationContext context) throws TransformationFailure;
}
