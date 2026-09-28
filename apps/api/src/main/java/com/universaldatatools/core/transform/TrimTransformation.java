package com.universaldatatools.core.transform;

import com.universaldatatools.core.common.TextValues;

import java.util.List;
import java.util.Set;

/** Removes whitespace, no-break spaces included, from both ends; inner spaces stay. */
public final class TrimTransformation implements Transformation {

    @Override
    public String type() {
        return "trim";
    }

    @Override
    public List<String> validate(TransformationContext context) {
        return TransformationParams.unknown(context, Set.of(), type());
    }

    @Override
    public String transform(String value, TransformationContext context) {
        return TextValues.isEmpty(value) ? value : TextValues.strip(value);
    }
}
