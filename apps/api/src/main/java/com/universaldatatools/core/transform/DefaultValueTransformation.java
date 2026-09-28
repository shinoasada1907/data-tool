package com.universaldatatools.core.transform;

import com.universaldatatools.core.common.TextValues;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Replaces an empty value with {@code params.value}; any other value is kept as it is. */
public final class DefaultValueTransformation implements Transformation {

    private static final String VALUE = "value";

    @Override
    public String type() {
        return "defaultValue";
    }

    @Override
    public List<String> validate(TransformationContext context) {
        List<String> problems = new ArrayList<>(TransformationParams.unknown(context, Set.of(VALUE), type()));
        problems.addAll(TransformationParams.required(context, VALUE));
        return problems;
    }

    @Override
    public String transform(String value, TransformationContext context) {
        return TextValues.isEmpty(value) ? context.params().get(VALUE) : value;
    }
}
