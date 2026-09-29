package com.universaldatatools.core.transform;

import java.util.List;

/** Every transformation the toolbox has (core-03 SR9); each tool picks its subset with {@link TransformationRegistry#of}. */
public final class TransformationCatalog {

    private TransformationCatalog() {
    }

    public static List<Transformation> all() {
        return List.of(new TrimTransformation(), new UppercaseTransformation(), new LowercaseTransformation(),
                new TitleCaseTransformation(), new DefaultValueTransformation(), new DateFormatTransformation(),
                new ReplaceTransformation(), new NormalizeNullTransformation());
    }
}
