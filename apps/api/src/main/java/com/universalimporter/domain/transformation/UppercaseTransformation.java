package com.universalimporter.domain.transformation;

import com.universalimporter.domain.common.TextValues;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Upper case by {@link Locale#ROOT}, so the JVM's default locale never changes the result. */
public final class UppercaseTransformation implements Transformation {

    @Override
    public String type() {
        return "uppercase";
    }

    @Override
    public List<String> validate(TransformationContext context) {
        return TransformationParams.unknown(context, Set.of(), type());
    }

    @Override
    public String transform(String value, TransformationContext context) {
        return TextValues.isEmpty(value) ? value : value.toUpperCase(Locale.ROOT);
    }
}
