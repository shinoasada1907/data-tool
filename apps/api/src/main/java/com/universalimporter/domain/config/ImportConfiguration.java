package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.mapping.MappingConfig;
import com.universalimporter.domain.schema.TargetField;
import com.universalimporter.domain.schema.TargetSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything configured for one session, kept apart from the session itself (design S2). A session without a
 * stored configuration has the {@link #empty} one.
 *
 * @param version persistence version, {@code null} until first stored
 */
public record ImportConfiguration(UUID sessionId, TargetSchema schema, MappingConfig mapping, Long version) {

    public ImportConfiguration {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(mapping, "mapping");
    }

    public static ImportConfiguration empty(UUID sessionId) {
        return new ImportConfiguration(sessionId, TargetSchema.empty(), MappingConfig.empty(), null);
    }

    /**
     * Replaces the schema and prunes every section that refers to fields it no longer has (design S3, BE-F05 M3).
     * A field whose type changes keeps its configuration.
     */
    public ConfigChange withSchema(TargetSchema schema) {
        List<ProblemItem> warnings = new ArrayList<>();
        MappingConfig prunedMapping = ConfigPruner.prune(mapping, schema.fieldNames(), warnings);
        return new ConfigChange(new ImportConfiguration(sessionId, schema, prunedMapping, version), warnings);
    }

    /** Replaces the whole mapping; every schema field left unmapped gets a warning, in schema order (BE-F05 M2). */
    public ConfigChange withMapping(MappingConfig mapping) {
        List<ProblemItem> warnings = schema.fields().stream()
                .map(TargetField::name)
                .filter(field -> mapping.forField(field).isEmpty())
                .map(field -> new ProblemItem(field, WarningCode.TARGET_FIELD_UNMAPPED.name(), "Field is not mapped."))
                .toList();
        return new ConfigChange(new ImportConfiguration(sessionId, schema, mapping, version), warnings);
    }
}
