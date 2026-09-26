package com.universalimporter.domain.config;

import java.util.Set;

/** A part of the configuration whose entries each belong to one target field, so it can be pruned (design S3). */
public interface FieldScopedSection<S extends FieldScopedSection<S>> {

    /** Name of the section in warnings, for example {@code "Mapping"}. */
    String sectionLabel();

    /** Target field names this section has entries for. */
    Set<String> referencedFields();

    /** This section without the entries of fields outside {@code fieldNames}. */
    S retainFields(Set<String> fieldNames);
}
