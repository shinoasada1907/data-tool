package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;

import java.util.List;
import java.util.Set;

/** Drops the configuration of fields the schema no longer has, with one warning per field (design S3, D10). */
public final class ConfigPruner {

    private ConfigPruner() {
    }

    /**
     * Names are matched exactly: they are keys of the output JSON. Warnings are added to {@code warnings},
     * sorted by field name.
     */
    public static <S extends FieldScopedSection<S>> S prune(S section, Set<String> fieldNames,
                                                            List<ProblemItem> warnings) {
        List<String> removed = section.referencedFields().stream()
                .filter(field -> !fieldNames.contains(field))
                .sorted()
                .toList();
        if (removed.isEmpty()) {
            return section;
        }
        String message = section.sectionLabel() + " for this field was removed because the field no longer exists.";
        removed.forEach(field -> warnings.add(new ProblemItem(field, WarningCode.CONFIG_PRUNED.name(), message)));
        return section.retainFields(fieldNames);
    }
}
