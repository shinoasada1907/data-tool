package com.universaldatatools.tools.importer.domain.config;

import com.universaldatatools.core.common.ProblemItem;

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
        removed.forEach(field -> warnings.add(
                new ProblemItem(field, WarningCode.CONFIG_PRUNED.name(), section.prunedMessage(field))));
        return section.retainFields(fieldNames);
    }
}
