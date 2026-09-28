package com.universaldatatools.core.transform;

import com.universaldatatools.core.common.TextValues;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lower case by {@link Locale#ROOT}, so the JVM's default locale never changes the result. */
public final class LowercaseTransformation implements Transformation {

    @Override
    public String type() {
        return "lowercase";
    }

    @Override
    public List<String> validate(TransformationContext context) {
        return TransformationParams.unknown(context, Set.of(), type());
    }

    @Override
    public String transform(String value, TransformationContext context) {
        return TextValues.isEmpty(value) ? value : value.toLowerCase(Locale.ROOT);
    }
}
